package com.alien.common.gameplay.hive.economy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
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

    private static final int MEMBER_CAP = 250;

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

    public static void scanAll(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var populationPerChunk = config.populationPerChunk();
        var currentTick = server.overworld().getGameTime();

        for (var factionId : new ArrayList<>(Alien.MOD.factions().getAllIds())) {
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
                if (!location.isAlive() || location.isInhibited()) {
                    continue; // inhibited locations run no economy — no biomass spend, no jelly, no purchases.
                }
                evaluate(server, location, lineage, populationPerChunk);
            }
        }
    }

    private static void evaluate(MinecraftServer server, HiveLocation location, LineageFactionData lineage, int populationPerChunk) {
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
        var memberCap = com.alien.common.gameplay.hive.empress.EmpressCaps.scale(location, MEMBER_CAP);
        var retinueAllowance = Math.min(1, pop.getOrDefault(AlienEntityTypeTags.PRAETORIANS, 0))
            + Math.min(2, pop.getOrDefault(AlienEntityTypeTags.DRONES, 0));
        // Carve-crew transients (design §8.5): reserve-materialized build workers don't count against the cap while
        // assigned to the active carve site; they fold back into reserves at completion. Borrowed drones counted
        // before they picked up a shovel and still do.
        var carveCrewAllowance = location.activeCarveSite() != null
            ? location.activeCarveSite().materializedWorkerCount()
            : 0;

        // The member cap counts WORKERS only. Soldiers, spitters and the scourge tier each have their own cap
        // (max_entity_count_in_location on their purchase) and live OUTSIDE this one - otherwise raising an army
        // would squeeze out the very drones and runners it is promoted from, and the hive would eat itself.
        var workingAdults = totalPop
            - pop.getOrDefault(AlienEntityTypeTags.QUEENS, 0)
            - militaryPopulation(pop)
            - retinueAllowance
            - carveCrewAllowance;
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

        for (var caste : candidates) {
            if (tryCommitCaste(server, location, lineage, caste, totalPop, PurchasePopulationMode.NEUTRAL)) {
                return true;
            }
        }
        return false;
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
        var biomassCost = irradiated ? IrradiatedHiveRules.PROMOTION_BIOMASS_COST : biomassCost(purchase, location);
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
