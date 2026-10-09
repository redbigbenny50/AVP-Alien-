package com.alien.common.gameplay.hive.economy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.HiveUnitPurchaseRegistry;
import com.alien.common.registry.init.AlienGameRules;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.compatibility.avp_predator.AVPPredator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-tick hive buy task. For each living location:
 * <ol>
 * <li>Counts tracked castes (loaded + reserves) and totals the population.</li>
 * <li>If population is below the per-chunk cap, buys a net-new basic unit when resources allow.</li>
 * <li>Once population is full, spends paid purchases on composition upgrades to move toward the policy ratios.</li>
 * </ol>
 * Runs every server tick from {@link HiveLocationRegistry#tick}. Per-location body is cheap (a few sums and bounded
 * purchase lookups); no throttling per the project's correctness-over-cadence preference.
 */
public final class HiveBalanceTask {

    /**
     * How many chrysalises a hive may buy before a crusher jumps the queue.
     * <p>
     * [stated] "we can also add that it alternates so for every 2 chrysalis it makes it does a crusher."
     * </p>
     */
    private static final int CHRYSALISES_PER_CRUSHER = 2;

    /**
     * Chrysalises bought since this location last bought a crusher.
     * <p>
     * TRANSIENT ON PURPOSE, like the founding-crew bootstrap set: the worst a restart can do is let one extra pair of
     * chrysalises through before the next crusher, which is not worth a persisted field. Entries are only added for
     * locations that actually buy chrysalises.
     * </p>
     */
    private static final Map<HiveLocationId, Integer> CHRYSALISES_SINCE_CRUSHER = new HashMap<>();

    private static final TagKey<EntityType<?>>[] POPULATION_FILL_CASTES = new TagKey[] {
        AlienEntityTypeTags.RUNNERS,
        AlienEntityTypeTags.DRONES
    };

    private HiveBalanceTask() {}

    /** Hard non-queen member ceiling - keeps hive fights reasonable. Raised while under empress influence. */
    /**
     * Castes with their OWN caps, which therefore do not count against the worker member cap: the standing army, the
     * queen's guard, spitters, and the whole scourge tier.
     */
    @SuppressWarnings("unchecked")
    private static final TagKey<EntityType<?>>[] MILITARY_CASTES = new TagKey[] {
        AlienEntityTypeTags.WARRIORS,
        AlienEntityTypeTags.PROWLERS,
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.CRUSHERS,
        AlienEntityTypeTags.SPITTERS,
        AlienEntityTypeTags.PREDALIENS,
        AlienEntityTypeTags.BURSTERS,
        AlienEntityTypeTags.CHRYSALISES,
        AlienEntityTypeTags.RAZOR_CLAWS,
        AlienEntityTypeTags.RAVAGERS,
        AlienEntityTypeTags.CARRIERS,
        AlienEntityTypeTags.HARBINGERS
    };

    /** The room whose count caps the harbinger. A second one requires empress influence to build. */
    private static final String RAID_CHAMBER_ROOM_TYPE = "chamber_raid";

    /** Public so the empress's last stand can measure how far short her seat is when she levies the network. */
    public static final int MEMBER_CAP = 250;

    /**
     * Lineages are processed in buckets: each one is evaluated once per this many ticks, chosen by its own id hash so
     * the empire spreads evenly across ticks instead of spiking on one. Nothing here needs per-tick resolution - it
     * only needs to happen about once a second.
     */
    private static final int LINEAGE_BUCKET_TICKS = 20;

    /**
     * Offset within the bucket window, distinct per scan, so one lineage's economy tasks land on DIFFERENT ticks.
     * Without it every scan would pick the same lineage on the same tick and the saving would be a smaller spike rather
     * than no spike.
     */
    private static final int BUCKET_PHASE = 7;

    /**
     * Reused snapshot buffer for the per-tick faction scan. The scan runs only on the single server thread, so one
     * static scratch list per scan is safe; clear+addAll keeps the same iterate-a-snapshot semantics (the loop body may
     * mutate the live faction registry) while allocating nothing once the backing array has grown - this scan used to
     * build a fresh ArrayList of every faction id EVERY TICK just to run its bucket filter.
     */
    private static final java.util.List<net.minecraft.resources.ResourceLocation> SCAN_SCRATCH =
        new java.util.ArrayList<>();

    public static void scanAll(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var populationPerChunk = config.populationPerChunk();
        var currentTick = server.overworld().getGameTime();

        SCAN_SCRATCH.clear();
        SCAN_SCRATCH.addAll(Alien.MOD.factions().getAllIds());
        for (var factionId : SCAN_SCRATCH) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            // A purchase that could have happened this tick happens within a second instead. This is the heaviest
            // per-tick scan in the hive system - it walks every purchase's conditions for every location.
            if (Math.floorMod(currentTick - factionId.hashCode(), LINEAGE_BUCKET_TICKS) != BUCKET_PHASE) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }
            for (var location : new ArrayList<>(lineage.locationsById().values())) {
                if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(server, location)) {
                    continue; // END-STYLE: no simulated growth and no purchasing - the bank only holds what the player
                              // supplied
                }
                if (!location.isAlive() || location.isInhibited()) {
                    continue; // inhibited locations run no economy — no biomass spend, no jelly, no purchases.
                }
                evaluate(server, location, lineage, populationPerChunk);
            }
        }
    }

    /**
     * Oct 8 - UNLOADED HIVES BREED WORKERS, AND ONLY WORKERS, UP TO 50. [stated] "set unloaded hives or hives that
     * start unloaded to a max of 50 members all worker class so runner and drone" - and, because 50 is under the 70 a
     * hive needs to spread, "it wont cause cascading expansion". This replaces the old behaviour while unloaded, where
     * the full loaded economy ran unseen (soldiers included, up to the land's cap) for established hives and NOTHING
     * ran for a founding or still-arriving daughter.
     */
    public static final int UNLOADED_WORKER_CAP = 50;

    /** Oct 8 - one unloaded worker birth per this many ticks per hive: two per five-minute window. */
    private static final long UNLOADED_BIRTH_INTERVAL_TICKS = 150L;

    /** Oct 8 - the tick of each hive's last unloaded birth (transient; a restart simply starts the clock again). */
    private static final Map<HiveLocationId, Long> LAST_UNLOADED_BIRTH = new HashMap<>();

    private static void evaluate(MinecraftServer server, HiveLocation location, LineageFactionData lineage, int populationPerChunk) {
        var level = server.getLevel(location.dimension());

        if (
            level != null
                && !com.alien.common.gameplay.hive.tick.HiveLocationLoadedTickTask.hasLoadedClaimedChunk(level, location)
        ) {
            evaluateUnloaded(server, location, lineage, level.getGameTime());
            return;
        }

        // Loaded again: the unloaded birth clock is not needed (and the entry would otherwise outlive a dead hive).
        LAST_UNLOADED_BIRTH.remove(location.id());

        // Founding lockout: a queen-founded hive buys NO population units until its queen is reproductive. Otherwise
        // this
        // task spends biomass on drones/runners the instant the hive can afford them, draining the pool the queen needs
        // to fill for her ovipositor (the "biomass resets at ~50, spawns an army, no resin" symptom). Queenless hives
        // are unaffected. See fill-then-commit founding design.
        if (location.founderId() != null && !location.reproductiveEstablished()) {
            return;
        }
        var pop = CastePopulation.popByCaste(location);
        var totalPop = pop.values().stream().mapToInt(Integer::intValue).sum();

        // Hard ceiling on the WORKING adult population - the reserve-bred adults used for parties and hive
        // defense: 250, raised to 400 under empress influence. Exempt: the queen, eggs (never tracked), and
        // the queen's founding retinue (1 praetorian + 2 drones). Members from other sources currently count
        // too (origin isn't tagged); if convoy bonuses visibly eat cap space, origin tagging is the fix.
        var memberCap = memberCapFor(location);
        var workingAdults = workingAdults(location, pop);
        if (workingAdults >= memberCap) {
            return;
        }
        var chunks = location.claimedChunks().size();
        var cap = populationPerChunk * chunks;
        if (cap <= 0 || totalPop > cap) {
            return;
        }
        if (CastePopulation.countCaste(location, AlienEntityTypeTags.QUEENS) <= 0) {
            return;
        }

        // Grow the workforce...
        if (totalPop < cap) {
            tryFillPopulation(server, location, lineage, pop, chunks, totalPop);
        }

        // ...and militarise ALONGSIDE it, rather than only after it.
        //
        // This call used to sit behind a `return`, so composition upgrades ran ONLY once the hive had already hit
        // its full population cap. Parties drain the reserves constantly, so it never got there - and the hive
        // therefore never promoted a single warrior or prowler in its entire life. It just replaced drones forever.
        //
        // Promotions are paid in ROYAL JELLY, not biomass, so they do not compete with worker production for the
        // same currency; and each one's own purchase conditions (min_population, per-caste cap) decide when it is
        // actually allowed. This simply stops the task returning before it ever asks.
        tryBalanceComposition(server, location, lineage, pop, chunks, totalPop);
    }

    /**
     * Oct 8 - the unloaded economy: drones and runners only, while the hive holds fewer than
     * {@link #UNLOADED_WORKER_CAP} members, at most one birth per {@link #UNLOADED_BIRTH_INTERVAL_TICKS}.
     * <p>
     * Runs for a FOUNDING hive too (no founding lockout while unloaded) and for a daughter whose founder queen is still
     * on her way (her convoy counts as the queen) - [stated] "count them as ready to found". The 50 counts every
     * member, banked and loaded alike, so a hive left already above 50 simply stops.
     */
    private static void evaluateUnloaded(MinecraftServer server, HiveLocation location, LineageFactionData lineage, long now) {
        var last = LAST_UNLOADED_BIRTH.get(location.id());

        if (last != null && now - last < UNLOADED_BIRTH_INTERVAL_TICKS && now >= last) {
            return;
        }

        var pop = CastePopulation.popByCaste(location);
        var totalPop = pop.values().stream().mapToInt(Integer::intValue).sum();

        if (totalPop >= UNLOADED_WORKER_CAP) {
            return;
        }

        if (CastePopulation.countCaste(location, AlienEntityTypeTags.QUEENS) <= 0 && !founderQueenEnRoute(lineage, location)) {
            return;
        }

        // Drones and runners in the usual balance order - and never through caste substitution, which can turn a
        // worker purchase into something else.
        for (var caste : populationFillOrder(pop, Math.max(1, location.claimedChunks().size()))) {
            if (caste != AlienEntityTypeTags.DRONES && caste != AlienEntityTypeTags.RUNNERS) {
                continue;
            }

            if (abstractWorkerBirth(location, lineage, caste)) {
                LAST_UNLOADED_BIRTH.put(location.id(), now);
                // Outside the loaded tick nothing else marks this lineage for saving (see blib-faction persistence).
                lineage.markDirty();
                return;
            }
        }
    }

    /**
     * Oct 8 - one ABSTRACT worker birth: pays the caste's normal biomass cost and banks one worker in reserves.
     * <p>
     * ⚠ No egg is consumed. A loaded purchase turns an ovomorph into the worker, but an unloaded hive has no laying
     * queen and a founding or arriving daughter has no eggs at all, so requiring one would mean no births ever - the
     * thing this exists to fix. Everything else a purchase checks still applies: the variant's caste type, the runner
     * cap, irradiated purchase rules, the reserve accepting the type, and affordability.
     */
    private static boolean abstractWorkerBirth(HiveLocation location, LineageFactionData lineage, TagKey<EntityType<?>> caste) {
        var outputType = CasteResolver.entityTypeForCaste(lineage.variant(), caste);

        if (outputType == null) {
            return false;
        }

        if (
            outputType.is(AlienEntityTypeTags.RUNNERS)
                && CastePopulation.countCaste(location, AlienEntityTypeTags.RUNNERS) >= runnerCapFor(location)
        ) {
            return false;
        }

        var purchase = HiveUnitPurchaseRegistry.forOutputEntity(outputType);

        if (purchase == null) {
            return false;
        }

        if (IrradiatedHiveRules.isIrradiated(location) && !IrradiatedHiveRules.allowsPurchase(purchase)) {
            return false;
        }

        if (!location.localReserves().accepts(outputType)) {
            return false;
        }

        var cost = biomassCost(purchase, location);

        if (location.biomass() < cost) {
            return false;
        }

        location.setBiomass(location.biomass() - cost);
        location.localReserves().tryAdd(outputType, 1);
        return true;
    }

    /** Oct 8 - {@return whether a founder convoy carrying a queen is heading for this hive} */
    private static boolean founderQueenEnRoute(LineageFactionData lineage, HiveLocation location) {
        for (var convoy : lineage.convoys()) {
            if (
                convoy instanceof com.alien.common.gameplay.hive.convoy.Convoy.Reinforcement reinforcement
                    && location.id().equals(reinforcement.destinationLocationId())
            ) {
                for (var type : reinforcement.composition().getAvailableEntityTypes()) {
                    if (type.is(AlienEntityTypeTags.QUEENS) && reinforcement.composition().getCount(type) > 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean tryFillPopulation(
        MinecraftServer server,
        HiveLocation location,
        LineageFactionData lineage,
        Map<TagKey<EntityType<?>>, Integer> pop,
        int chunks,
        int totalPop
    ) {
        var ordered = populationFillOrder(pop, chunks);
        for (var caste : ordered) {
            // Basic egg-born production rolls 1-in-6 for a SPITTER and 1-in-6 for a PREDALIEN instead of the
            // drone/runner it was making; if the substitute can't commit, the intended caste still gets its normal
            // attempt.
            var substituted = rollCasteSubstitution(server, caste);
            if (
                substituted != caste
                    && tryCommitCaste(server, location, lineage, substituted, totalPop, PurchasePopulationMode.NET_GAIN)
            ) {
                return true;
            }
            if (tryCommitCaste(server, location, lineage, caste, totalPop, PurchasePopulationMode.NET_GAIN)) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<TagKey<EntityType<?>>> populationFillOrder(
        Map<TagKey<EntityType<?>>, Integer> pop,
        int chunks
    ) {
        var ordered = new ArrayList<TagKey<EntityType<?>>>();
        var drones = pop.getOrDefault(AlienEntityTypeTags.DRONES, 0);
        var runners = pop.getOrDefault(AlienEntityTypeTags.RUNNERS, 0);
        var runnerBaseline = 1 + chunks / 4;

        // Early growth INTERLEAVES 2:1 rather than letting the runner baseline monopolize the single purchase
        // per cycle: with chunks/4 baselines (29-91 runners), a young hive was ALL runners for its whole early
        // life and drones were never made. Runners lead only while they haven't pulled 2x ahead of drones.
        if (runners < runnerBaseline && runners <= (drones + 1) * 2) {
            ordered.add(AlienEntityTypeTags.RUNNERS);
        }

        if (drones <= runners) {
            addIfMissing(ordered, AlienEntityTypeTags.DRONES);
            addIfMissing(ordered, AlienEntityTypeTags.RUNNERS);
        } else {
            addIfMissing(ordered, AlienEntityTypeTags.RUNNERS);
            addIfMissing(ordered, AlienEntityTypeTags.DRONES);
        }

        for (var caste : POPULATION_FILL_CASTES) {
            addIfMissing(ordered, caste);
        }
        return ordered;
    }

    private static void addIfMissing(ArrayList<TagKey<EntityType<?>>> list, TagKey<EntityType<?>> caste) {
        if (!list.contains(caste)) {
            list.add(caste);
        }
    }

    /** Everything living outside the worker member cap: the standing army, spitters, and the scourge tier. */
    /**
     * This location's WORKER ceiling: {@link #MEMBER_CAP} (250), raised to 400 under empress influence.
     * <p>
     * ⚠ NOT the same thing as {@code populationPerChunk x claimedChunks}, which is what {@code /hive} prints as
     * {@code population=X/Y}. Both are live and both can stop production; this is the one that usually does.
     * </p>
     */
    /**
     * How many runners this location may hold: half its worker ceiling.
     * <p>
     * Drones are the caste that takes losses - carve crews, egg hauling and host hunts - so without a bound on runners
     * a hive slowly converts its worker population into the caste that has no jobs. Half leaves drones a guaranteed
     * majority share while still allowing a substantial runner population.
     * </p>
     */
    public static int runnerCapFor(HiveLocation location) {
        return Math.max(1, memberCapFor(location) / 2);
    }

    public static int memberCapFor(HiveLocation location) {
        return com.alien.common.gameplay.hive.empress.EmpressCaps.scale(location, MEMBER_CAP);
    }

    /**
     * ⭐⭐ THE NUMBER {@link #memberCapFor} IS ACTUALLY MEASURED AGAINST - and, until now, the only figure in the whole
     * hive economy that decided whether a hive keeps growing while being printed NOWHERE.
     * <p>
     * THE MEMBER CAP COUNTS WORKERS ONLY. Soldiers, spitters and the scourge tier each have their own ceiling
     * ({@code max_entity_count_in_location} on their purchase) and live OUTSIDE this one - otherwise raising an army
     * would squeeze out the very drones and runners it is promoted from, and the hive would eat itself. The queen, the
     * founding retinue (1 praetorian + 2 drones) and the carve crew are exempt for the same reason: none of them is
     * discretionary workforce.
     * </p>
     * <p>
     * Public so {@code /hive inspect_settlement} can report it without restating the formula and drifting from it.
     * </p>
     */
    public static int workingAdults(HiveLocation location, Map<TagKey<EntityType<?>>, Integer> pop) {
        var totalPop = pop.values().stream().mapToInt(Integer::intValue).sum();
        var retinueAllowance = Math.min(1, pop.getOrDefault(AlienEntityTypeTags.PRAETORIANS, 0))
            + Math.min(2, pop.getOrDefault(AlienEntityTypeTags.DRONES, 0));
        // Carve-crew transients (design §8.5): reserve-materialized build workers don't count against the cap while
        // assigned to the active carve site; they fold back into reserves at completion. Borrowed drones counted
        // before they picked up a shovel and still do.
        var carveCrewAllowance = location.activeCarveSite() != null
            ? location.activeCarveSite().materializedWorkerCount()
            : 0;
        return totalPop
            - pop.getOrDefault(AlienEntityTypeTags.QUEENS, 0)
            - militaryPopulation(pop)
            - retinueAllowance
            - carveCrewAllowance;
    }

    private static int militaryPopulation(Map<TagKey<EntityType<?>>, Integer> pop) {
        var total = 0;
        for (var tag : MILITARY_CASTES) {
            total += pop.getOrDefault(tag, 0);
        }
        return total;
    }

    private static boolean tryBalanceComposition(
        MinecraftServer server,
        HiveLocation location,
        LineageFactionData lineage,
        Map<TagKey<EntityType<?>>, Integer> pop,
        int chunks,
        int totalPop
    ) {
        var deficits = computeDeficits(pop, chunks, totalPop);
        if (deficits.isEmpty()) {
            return false;
        }

        var candidates = new ArrayList<TagKey<EntityType<?>>>();
        for (var caste : CastePopulation.TRACKED_CASTES) {
            if (deficits.getOrDefault(caste, 0) > 0) {
                candidates.add(caste);
            }
        }
        candidates.sort((left, right) -> Integer.compare(deficits.get(right), deficits.get(left)));
        promoteCrusherIfDue(location, candidates);

        for (var caste : candidates) {
            if (tryCommitCaste(server, location, lineage, caste, totalPop, PurchasePopulationMode.NEUTRAL)) {
                recordAlternation(location, caste);
                return true;
            }
        }
        return false;
    }

    /**
     * Puts the crusher at the head of the queue once the hive has bought {@link #CHRYSALISES_PER_CRUSHER} chrysalises
     * since its last one.
     * <p>
     * Chrysalis and crusher are the only two promotions that eat a PROWLER, so without this they compete for one pool
     * and pure deficit ordering lets chrysalises take every prowler the hive makes. Alternating guarantees the heavy
     * gets a turn.
     * </p>
     * <p>
     * Only REORDERS - it never forces a purchase. The crusher still has to have a deficit to be a candidate at all, and
     * its own purchase conditions still decide whether it can commit; if it cannot, the loop falls straight through to
     * the next caste and the debt stays owed until it can.
     * </p>
     */
    private static void promoteCrusherIfDue(HiveLocation location, ArrayList<TagKey<EntityType<?>>> candidates) {
        if (CHRYSALISES_SINCE_CRUSHER.getOrDefault(location.id(), 0) < CHRYSALISES_PER_CRUSHER) {
            return;
        }

        if (candidates.remove(AlienEntityTypeTags.CRUSHERS)) {
            candidates.add(0, AlienEntityTypeTags.CRUSHERS);
        }
    }

    /** Counts chrysalises toward the next crusher, and clears the debt when a crusher is actually bought. */
    private static void recordAlternation(HiveLocation location, TagKey<EntityType<?>> caste) {
        if (caste.equals(AlienEntityTypeTags.CRUSHERS)) {
            CHRYSALISES_SINCE_CRUSHER.remove(location.id());
        } else if (caste.equals(AlienEntityTypeTags.CHRYSALISES)) {
            CHRYSALISES_SINCE_CRUSHER.merge(location.id(), 1, Integer::sum);
        }
    }

    /**
     * Basic egg-born production (drones, runners) can come out as something else instead. ONE d6 decides, so the two
     * substitutions are exactly 1-in-6 each and cannot compound: 0 is a spitter, 1 is a predalien, 2-5 leave the
     * intended caste alone.
     * <p>
     * Spitters were 1-in-4 and were the ONLY caste produced by substitution - they have no entry in
     * {@code computeDeficits}, so this roll is their entire supply, which is why they outnumbered everything else in
     * play. Predaliens are deliberately egg-born here rather than born from a predator host, and the roll is the only
     * place that decision is gated: with avp_predator absent the 1 lands on the intended caste, no predalien is ever
     * requested, and their purchase files simply sit unused.
     */
    private static TagKey<EntityType<?>> rollCasteSubstitution(MinecraftServer server, TagKey<EntityType<?>> caste) {
        if (caste != AlienEntityTypeTags.DRONES && caste != AlienEntityTypeTags.RUNNERS) {
            return caste;
        }

        return switch (server.overworld().getRandom().nextInt(6)) {
            case 0 -> AlienEntityTypeTags.SPITTERS;
            case 1 -> hivesMayBreedPredaliens(server) ? AlienEntityTypeTags.PREDALIENS : caste;
            default -> caste;
        };
    }

    /**
     * BOTH conditions, and the gamerule is off by default.
     * <p>
     * A hive growing predaliens out of simulated reserves was contentious - it makes them ordinary stock rather than
     * what happens when a predator meets a facehugger. So it is opt-in per world AND still requires AVP: Predator,
     * because the gamerule cannot conjure a species from a mod that is not installed.
     * <p>
     * This roll is the ONLY source of hive-grown predaliens: they have no computeDeficits entry, so switching it off
     * removes them from hive production entirely rather than merely making them rarer.
     */
    private static boolean hivesMayBreedPredaliens(MinecraftServer server) {
        return AVPPredator.MOD.isLoaded()
            && server.getGameRules().getBoolean(AlienGameRules.AVP_ALIEN_HIVES_BREED_PREDALIENS);
    }

    private static boolean tryCommitCaste(
        MinecraftServer server,
        HiveLocation location,
        LineageFactionData lineage,
        TagKey<EntityType<?>> caste,
        int totalPop,
        PurchasePopulationMode populationMode
    ) {
        // Starvation priority (design §6, step 4): while the active carve site is starved (a resin payment actually
        // bounced), caste purchases stand aside so incoming biomass finishes the frozen build first. Defense parties,
        // egg-laying and resin spread are deliberately NOT gated - survival and reproduction outrank construction,
        // and hunting parties are how a starving hive earns its way out.
        if (location.isConstructionStarved()) {
            return false;
        }

        var outputType = CasteResolver.entityTypeForCaste(lineage.variant(), caste);
        if (outputType == null) {
            return false;
        }
        // ONE HARBINGER PER RAID CHAMBER. This was a hardcoded 1, so an empress hive's extra raid chamber bought
        // nothing - see IrradiatedHiveRules.harbingerCap. The away-in-raid check stands: a harbinger out on a raid
        // still occupies its chamber.
        if (
            outputType.is(AlienEntityTypeTags.HARBINGERS)
                && (CastePopulation.countCaste(location, AlienEntityTypeTags.HARBINGERS) >= IrradiatedHiveRules.harbingerCap(location)
                    || hasHarbingerAwayInRaid(location, lineage))
        ) {
            return false;
        }

        // 🚨 RUNNERS ARE CAPPED AT HALF THE WORKER CEILING, SO THERE IS ALWAYS ROOM FOR DRONES.
        //
        // ⚠⚠ THIS IS AN ATTRITION FIX, NOT A BALANCE ONE. Drones do the dangerous work - they crew carve sites, haul
        // eggs and go out on host hunts, and a host party that runs into a predator usually loses drones. Runners do
        // none of that, so they do not die at the same rate, and over time a hive drifts runner-heavy with no drones
        // left for the jobs that actually need them.
        //
        // ⭐ A CEILING RATHER THAN A HARD SPLIT. A 125/125 split would leave a hive that legitimately wants 150
        // drones sitting at its cap with unusable headroom. This bounds only the caste that over-accumulates; drones
        // may still take the whole worker cap if the hive needs them.
        //
        // ⚠ Scales with empress influence exactly as the worker cap does, so a 400-worker hive allows 200 runners.
        if (
            outputType.is(AlienEntityTypeTags.RUNNERS)
                && CastePopulation.countCaste(location, AlienEntityTypeTags.RUNNERS) >= runnerCapFor(location)
        ) {
            return false;
        }

        var purchase = HiveUnitPurchaseRegistry.forOutputEntity(outputType);
        if (purchase == null) {
            return false;
        }

        var netPopulationChange = netPopulationChange(purchase);
        if (!populationMode.matches(netPopulationChange)) {
            return false;
        }

        if (!conditionsHold(purchase, location, totalPop)) {
            return false;
        }

        var irradiated = IrradiatedHiveRules.isIrradiated(location);

        // A converted hive cannot make anyone NEW - only promote somebody who already exists. See the rules class.
        if (irradiated && !IrradiatedHiveRules.allowsPurchase(purchase)) {
            return false;
        }

        // Vats are the hive's savings: tap them only when the bank alone can't cover the jelly cost. An irradiated
        // hive's vats are loot rather than savings, so these are no-ops there (gated inside JellyVatDisplay).
        JellyVatDisplay.coverShortfall(server, location, purchase.royalJelly());
        JellyVatDisplay.coverScourgeShortfall(server, location, purchase.scourgeJelly());

        // [stated] every promotion costs a flat 1 biomass + 1 jelly, whatever the caste. The merged pool lives in
        // royalJelly for an irradiated hive - scourgeJelly is zeroed at conversion and stays zero - so one field
        // answers the whole cost. Everything that used royal or scourge simply uses irradiated instead.
        // ⭐⭐⭐ A BREAKAWAY LINEAGE RAISES TROOPS WITHOUT PAYING BIOMASS FOR THREE DAYS.
        //
        // [stated] "member production has no biomass cost just a jelly cost. This was a hive with a grace period has
        // a period of member growth to make the war possible."
        //
        // ⚠⚠ THIS IS THE HALF THAT MAKES THE SCHISM WORK, not the absorption immunity. She founds ONE hive beside an
        // empire of eight, with no economy and no stores. Protecting her from absorption while leaving her unable to
        // BUILD would only postpone the same loss by three days - the player who spawned her would still watch her
        // do nothing and then lose.
        //
        // ⚠ THE JELLY COST STAYS, deliberately: she cannot conjure an army from nothing, she spends what she has
        // faster. Same shape as the irradiated and forced-empress carve waivers - waive the DEBT, never the TIME.
        var schismGrace = isInSchismGrace(server, location);
        var biomassCost = schismGrace
            ? 0
            : irradiated ? IrradiatedHiveRules.PROMOTION_BIOMASS_COST : biomassCost(purchase, location);
        var jellyCost = irradiated ? IrradiatedHiveRules.PROMOTION_JELLY_COST : purchase.royalJelly();
        var scourgeCost = irradiated ? 0 : purchase.scourgeJelly();

        if (
            location.biomass() < biomassCost
                || location.royalJelly() < jellyCost
                || location.scourgeJelly() < scourgeCost
        ) {
            return false;
        }

        // Confirm the concrete input entity reserves cover the purchase.
        var inputTypes = new ArrayList<EntityType<?>>(purchase.inputEntities().size());
        for (var input : purchase.inputEntities()) {
            var type = input.entity();
            // Stored nursery eggs back the ovomorph reserve: consume chamber stock when the reserve runs short. An
            // irradiated hive has no egg pipeline at all - its nurseries are dead rooms - and allowsPurchase has
            // already refused anything with an ovomorph input, so there is nothing here to top up.
            if (!irradiated) {
                EggStock.coverInputShortfall(server, location, type, input.count());
            }
            if (location.localReserves().getCount(type) < input.count()) {
                return false;
            }
            inputTypes.add(type);
        }

        if (!location.localReserves().accepts(purchase.outputEntity())) {
            return false;
        }

        // All gates pass — commit.
        location.setBiomass(location.biomass() - biomassCost);
        location.setRoyalJelly(location.royalJelly() - jellyCost);
        location.setScourgeJelly(location.scourgeJelly() - scourgeCost);

        for (var i = 0; i < purchase.inputEntities().size(); i++) {
            var count = purchase.inputEntities().get(i).count();
            location.localReserves().underlying().add(inputTypes.get(i), -count);
        }
        location.localReserves().tryAdd(purchase.outputEntity(), 1);
        return true;
    }

    /** Whether this hive's lineage is inside its breakaway grace window. */
    private static boolean isInSchismGrace(MinecraftServer server, HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        return faction != null
            && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage
            && lineage.isInSchismGrace(server.overworld().getGameTime());
    }

    private static int biomassCost(HiveUnitPurchase purchase, HiveLocation location) {
        var baseCost = Math.max(0, purchase.biomass());
        if (baseCost == 0) {
            return 0;
        }
        var outputCount = CastePopulation.countEntity(location, purchase.outputEntity());
        var scaledCost = baseCost + outputCount * (baseCost * purchase.populationBiomassCostScale());
        return Math.max(baseCost, (int) Math.ceil(scaledCost));
    }

    private static boolean hasHarbingerAwayInRaid(HiveLocation location, LineageFactionData lineage) {
        for (var convoy : lineage.convoys()) {
            if (!(convoy instanceof Convoy.Raid raid) || !raid.sourceLocationId().equals(location.id())) {
                continue;
            }
            if (raid.composition().getCountMatching(type -> type.is(AlienEntityTypeTags.HARBINGERS)) > 0) {
                return true;
            }
            if (raid.materializedCountMatching(type -> type.is(AlienEntityTypeTags.HARBINGERS)) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int netPopulationChange(HiveUnitPurchase purchase) {
        var inputs = 0;
        for (var input : purchase.inputEntities()) {
            // Eggs are STOCK, not population (ovomorphs are not a tracked caste) - consuming one to make an
            // adult is a net population GAIN. Without this, the egg cost turned every basic purchase neutral
            // and the fill path (net-gain only) could never buy a drone/runner/spitter: population froze at
            // the queen forever, with full biomass and a stocked egg bank.
            if (input.entity().is(AlienEntityTypeTags.OVOMORPHS)) {
                continue;
            }
            inputs += input.count();
        }
        return 1 - inputs;
    }

    private static Map<TagKey<EntityType<?>>, Integer> computeDeficits(
        Map<TagKey<EntityType<?>>, Integer> pop,
        int chunks,
        int totalPop
    ) {
        var drone = pop.getOrDefault(AlienEntityTypeTags.DRONES, 0);
        var runner = pop.getOrDefault(AlienEntityTypeTags.RUNNERS, 0);
        var warrior = pop.getOrDefault(AlienEntityTypeTags.WARRIORS, 0);
        var prowler = pop.getOrDefault(AlienEntityTypeTags.PROWLERS, 0);
        var praetorian = pop.getOrDefault(AlienEntityTypeTags.PRAETORIANS, 0);
        var crusher = pop.getOrDefault(AlienEntityTypeTags.CRUSHERS, 0);
        var ravager = pop.getOrDefault(AlienEntityTypeTags.RAVAGERS, 0);
        var harbinger = pop.getOrDefault(AlienEntityTypeTags.HARBINGERS, 0);

        var desired = new LinkedHashMap<TagKey<EntityType<?>>, Integer>();
        desired.put(AlienEntityTypeTags.DRONES, Math.max(drone, warrior));
        desired.put(AlienEntityTypeTags.WARRIORS, Math.max(drone, warrior));
        desired.put(AlienEntityTypeTags.RUNNERS, Math.max(runner, 1 + chunks / 4));
        desired.put(AlienEntityTypeTags.PROWLERS, Math.max(prowler, runner / 4));
        desired.put(AlienEntityTypeTags.PRAETORIANS, warrior / 12);
        desired.put(AlienEntityTypeTags.CRUSHERS, runner / 12);
        desired.put(AlienEntityTypeTags.RAVAGERS, warrior / 8);
        // The rest of the scourge tier (caps set in the economy spec: burster 40, razor claw 20, chrysalis 20,
        // carrier 10). These were purchasable on paper - recipes, jelly costs, and caps all existed - but had no
        // desired entry here, so the balance task never asked for them and the ravager was the only scourge unit
        // hives ever produced. Their harbinger>=1 purchase condition still gates when they can actually commit.
        desired.put(AlienEntityTypeTags.BURSTERS, runner / 4);
        desired.put(AlienEntityTypeTags.RAZOR_CLAWS, runner / 8);
        desired.put(AlienEntityTypeTags.CHRYSALISES, prowler / 3);
        desired.put(AlienEntityTypeTags.CARRIERS, drone / 10);
        desired.put(AlienEntityTypeTags.HARBINGERS, totalPop >= 100 && harbinger == 0 ? 1 : 0);

        var deficits = new LinkedHashMap<TagKey<EntityType<?>>, Integer>();
        for (var entry : desired.entrySet()) {
            var current = pop.getOrDefault(entry.getKey(), 0);
            var d = entry.getValue() - current;
            if (d > 0) {
                deficits.put(entry.getKey(), d);
            }
        }
        return deficits;
    }

    private static boolean conditionsHold(
        HiveUnitPurchase purchase,
        HiveLocation location,
        int totalPop
    ) {
        for (var condition : purchase.conditions()) {
            switch (condition) {
                case HiveUnitPurchaseCondition.MinPopulation min -> {
                    if (totalPop < min.value()) {
                        return false;
                    }
                }
                case HiveUnitPurchaseCondition.MinEntityCountInLocation min -> {
                    var current = CastePopulation.countEntity(location, min.entity());
                    if (current < min.value()) {
                        return false;
                    }
                }
                case HiveUnitPurchaseCondition.MaxEntityCountInLocation max -> {
                    // EVERY per-caste ceiling in every purchase file passes through here - warriors, praetorians,
                    // crushers, spitters, the lot - so scaling it here is what makes an empress hive half again
                    // bigger in its whole standing army rather than just in its worker count. The harbinger is the
                    // sole exception and needs no special-casing: it is capped by MaxPerRaidChamber below, which
                    // she already raises by granting the hive a second raid chamber.
                    var current = CastePopulation.countEntity(location, max.entity());
                    if (current >= com.alien.common.gameplay.hive.empress.EmpressCaps.scale(location, max.value())) {
                        return false;
                    }
                }
                case HiveUnitPurchaseCondition.MaxPerRaidChamber perChamber -> {
                    // One harbinger per raid chamber. An ordinary hive builds one chamber and so fields one
                    // harbinger; only an empress-influenced hive can build a second and keep raiding after you
                    // kill the first.
                    var chambers = com.alien.common.gameplay.hive.structure.HiveRouter.countRoomsOfType(
                        location,
                        RAID_CHAMBER_ROOM_TYPE
                    );
                    var current = CastePopulation.countEntity(location, perChamber.entity());
                    if (current >= Math.max(1, chambers)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private enum PurchasePopulationMode {

        NET_GAIN {

            @Override
            boolean matches(int netPopulationChange) {
                return netPopulationChange > 0;
            }
        },
        NEUTRAL {

            @Override
            boolean matches(int netPopulationChange) {
                return netPopulationChange == 0;
            }
        };

        abstract boolean matches(int netPopulationChange);
    }
}
