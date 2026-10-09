package com.alien.common.gameplay.hive.defense;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.tick.HiveTerritoryAggroTask;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.alien.common.gameplay.hive.vent.VentKind;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The hive's vent-defense response: when intruders (the same hated/high-threat set the territory aggro task targets)
 * are inside the territory and too few defenders are near them, the hive pulls working-reserve adults up through the
 * vent network - defenders emerge at the nearest vents around the intruder and the aggro task sets them on the target
 * within a second. Surface intrusions get a LIGHTER response (2/3 strength) unless the intruder has escalated into an
 * active retribution campaign; interior intrusions always get full strength (defenders boil out of chamber vents).
 * Guardrails: a per-hive wave cooldown, and a per-intruder emergence cap so one raider can't bleed the reserves dry.
 */
public final class VentDefenseTask {

    private static final long INTERVAL_TICKS = 100L; // check cadence while the hive ticks

    /**
     * How many defenders the hive wants ACTUALLY FIGHTING an interior intruder.
     * <p>
     * ⚠⚠ THIS USED TO BE 4 AND IT WAS MEASURED AGAINST THE WRONG THING - see {@link #countDefendersEngaged}. Four
     * bodies standing anywhere within 32 blocks is the NORMAL STATE of a hive corridor, so the wanted-minus-present sum
     * was almost always zero or negative and the vents never opened at all. Now that the count only admits xenomorphs
     * that have actually taken the intruder as a target, the number can be a real garrison size instead of an
     * ambient-population threshold.
     * </p>
     */
    private static final int DEFENDER_TARGET = 8;

    private static final int VENT_RANGE_CHUNKS = 3; // vents this close to the intruder can be used

    private static final long WAVE_COOLDOWN_TICKS = 120L; // min ticks between emergence waves per hive

    /**
     * Defenders any ONE intruder may pull out of the vents per {@link #INCIDENT_WINDOW_TICKS}.
     * <p>
     * ⚠ THE WINDOW IS THE POINT. This cap used to run for the whole incident and only reset when the intruder left the
     * claim or died, so a raider who parked inside a hive got eight defenders EVER and the hive then stood there and
     * watched. A refreshing budget keeps the anti-drain guarantee (a fixed worst-case spawn rate) without ever letting
     * the hive go quiet while someone is still standing in it.
     * </p>
     */
    private static final int INCIDENT_EMERGE_CAP = 8;

    /** The budget above refreshes this often. Thirty seconds - a siege pays a steady toll, not a one-off. */
    private static final long INCIDENT_WINDOW_TICKS = 600L;

    /**
     * Who answers an intrusion, in order.
     * <p>
     * The ELITES come out first. Praetorians and crushers are the queen's guard and the hive's defenders - they take no
     * part in raids, so defending the hive is their entire purpose, and they were absent from this list entirely. They
     * are also rare (20 each, and each one costs a warrior or prowler), so ELITE_DEFENDER_CAP keeps an incident from
     * emptying the guard: a couple come out, and the rank and file do the rest.
     * <p>
     * Spitters give the defence something the attacker has to close the distance against.
     * <p>
     * Workers are last, and only because a hive that has run out of soldiers still bites.
     */
    private static final List<net.minecraft.tags.TagKey<EntityType<?>>> DRAW_ORDER = List.of(
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.CRUSHERS,
        // Predaliens draw ahead of the line troops but are deliberately NOT in ELITE_CASTES: their own purchase cap
        // of 20 already limits them, and the elite cap of 2 exists to ration the queen's guard specifically.
        AlienEntityTypeTags.PREDALIENS,
        AlienEntityTypeTags.WARRIORS,
        AlienEntityTypeTags.PROWLERS,
        AlienEntityTypeTags.SPITTERS,
        AlienEntityTypeTags.RUNNERS,
        AlienEntityTypeTags.DRONES
    );

    /** Only a few of the guard answer any one incident - they are too rare to spend on every intruder. */
    private static final int ELITE_DEFENDER_CAP = 2;

    private static final List<net.minecraft.tags.TagKey<EntityType<?>>> ELITE_CASTES = List.of(
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.CRUSHERS
    );

    /**
     * ⭐⭐ WHEN THIS HIVE FIRST FAILED TO FIELD ANYTHING, or absent while it can still deploy.
     * <p>
     * ⚠⚠ THE DEADLOCK THIS EXISTS FOR: the boss bar counts loaded members PLUS the bank, decay needs that total to
     * reach zero, and a bank cannot empty when every vent is blocked. A player hunting the last "1/280" member could
     * never find it because it was never in the world. Something has to notice that the hive has no way out.
     * </p>
     * <p>
     * ⚠ CLEARED THE MOMENT A DEFENDER SPAWNS, so a hive whose mouth is briefly buried loses nothing.
     * </p>
     */
    private static final Map<HiveLocation, Long> FAILED_TO_FIELD_SINCE = new java.util.concurrent.ConcurrentHashMap<>();

    /** True when this hive has been unable to field a single defender for at least {@code ticks}. */
    public static boolean hasFailedToFieldSince(HiveLocation location, long now, long ticks) {
        var since = FAILED_TO_FIELD_SINCE.get(location);
        return since != null && now - since >= ticks;
    }

    private static final Map<HiveLocation, Long> LAST_WAVE =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Defenders emerged per intruder (by UUID) in the current budget window; cleared when the intruder is gone. */
    private static final Map<HiveLocation, Map<UUID, EmergeTally>> EMERGED =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** How many defenders one intruder has drawn, and when the window that budget belongs to opened. */
    private record EmergeTally(
        int count,
        long windowStartTick
    ) {}

    /**
     * Defenders this hive called up out of its bank, per location.
     * <p>
     * ⚠ ONLY THESE ARE RECALLED. A hive's ordinary members are living where they live; a vent defender is a body the
     * hive SPENT from its reserve for one incident, and leaving it standing out there is what made the reserve drain
     * away permanently.
     * </p>
     */
    private static final Map<HiveLocation, Set<UUID>> RECALLABLE =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** When the territory last became free of intruders, per location. */
    private static final Map<HiveLocation, Long> QUIET_SINCE =
        Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * How long the territory must be clear before defenders stand down.
     * <p>
     * The same minute the harbinger uses, and for the same reason: long enough that stepping out to heal or reload does
     * not hand the hive its garrison back, short enough that the crowd clears while you are still nearby.
     * </p>
     */
    private static final long RECALL_QUIET_TICKS = 20L * 60L;

    /**
     * Sends surviving vent defenders back into the bank once the incident is over.
     * <p>
     * !!! NOTHING EVER RECALLED THEM. VentDefenseTask had no stand-down of any kind - defenders came out and stayed
     * out, so every wave added bodies and none went back. A live log caught 54 emergences in three and a half minutes,
     * repeatedly eight at a time from one hive, against an intruder that never left; reported as warriors, praetorians
     * and crushers crowding the throne room and getting in each other's way.
     * </p>
     * <p>
     * ⚠ SAME GUARDS AS THE HARBINGER STAND-DOWN. Banking stores a TYPE and a COUNT and discards the body, so a named,
     * leashed or ridden defender is left alone - it would be destroyed, not stored. One mid-fight is left alone too: a
     * defender still holding a target has not finished.
     * </p>
     */
    /**
     * Castes the hive fields as defenders - the ones a vent response draws from.
     * <p>
     * ⚠ WORKERS ARE NOT ON THIS LIST. Drones and runners crew carve sites and haul eggs; banking them would stall
     * construction, which is the opposite of the problem being fixed here.
     * </p>
     */
    /** Public alias so combat targeting uses the SAME caste list - two copies would drift. */
    public static boolean isDefenderCasteType(net.minecraft.world.entity.EntityType<?> type) {
        return isDefenderCaste(type);
    }

    private static boolean isDefenderCaste(net.minecraft.world.entity.EntityType<?> type) {
        return type.is(AlienEntityTypeTags.XENOMORPHS)
            && !type.is(AlienEntityTypeTags.DRONES)
            && !type.is(AlienEntityTypeTags.RUNNERS)
            && !type.is(AlienEntityTypeTags.QUEENS)
            && !type.is(AlienEntityTypeTags.EMPRESSES)
            && !type.is(AlienEntityTypeTags.HARBINGERS);
    }

    private static void recallDefenders(ServerLevel level, HiveLocation location) {
        // ⚠⚠ NO EARLY-OUT ON AN EMPTY TRACKED SET. That guard would defeat the whole sweep: a hive crowded by waves
        // from before this code existed has an EMPTY RECALLABLE, which is precisely the case that needs clearing.
        var recallable = RECALLABLE.get(location);

        var since = QUIET_SINCE.putIfAbsent(location, level.getGameTime());
        if (since == null || level.getGameTime() - since < RECALL_QUIET_TICKS) {
            return;
        }

        QUIET_SINCE.remove(location);

        // 🚨 SWEEP THE WHOLE GARRISON, NOT JUST THE ONES THIS SESSION SPAWNED.
        //
        // ⚠⚠ TRACKED-ONLY WAS USELESS ON AN ALREADY-CROWDED HIVE. RECALLABLE is populated when a defender EMERGES, so
        // every soldier summoned before this code existed - or before the last restart, since the set is in memory -
        // was invisible to the recall and would have stood in the throne room forever. A live report had warriors,
        // praetorians and crushers packed into a chamber getting in each other's way, and NONE of them were tracked.
        //
        // ⭐ So the tracked set is treated as a HINT, not the roster: it is drained first, then the location's own
        // loaded members are swept for defender castes idling inside the hive. The guards below are what keep this
        // from banking things it should not - it is deliberately narrow rather than deliberately small.
        var candidates = new HashSet<UUID>();
        if (recallable != null) {
            candidates.addAll(recallable);
            recallable.clear();
        }

        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!isDefenderCaste(entry.getKey())) {
                continue;
            }
            candidates.addAll(entry.getValue());
        }

        var recalled = 0;
        var now = level.getGameTime();

        for (var uuid : candidates) {
            if (!(level.getEntity(uuid) instanceof Xenomorph defender) || !defender.isAlive()) {
                continue;
            }

            // ⚠ INSIDE THE HIVE ONLY. A defender out on a party, a convoy or a hunt is doing its job somewhere else
            // and is not this task's to bank.
            if (
                !location.withinSlab(defender.blockPosition().getY())
                    || !location.claimedChunks().contains(new net.minecraft.world.level.ChunkPos(defender.blockPosition()))
            ) {
                continue;
            }

            if (defender.hasCustomName() || defender.isPassenger() || defender.isLeashed()) {
                continue;
            }

            // !!! A PARTY MEMBER IS ON A JOB, EVEN STANDING STILL INSIDE THE HIVE. Every other system that removes or
            // re-tasks a member checks this - CarveWorkers, HiveBreachRepair, StrandedMemberRecovery, BroodBankTask,
            // the aggro sweep - and the recall did not. An attack party mustering in its own throne room has no
            // target yet, so without this it would have been banked out from under the raid that was about to leave.
            //
            // ⚠ Same for a worker heading out to fold itself into the bank: it is already leaving, and banking it
            // here would double-count it.
            if (defender.partyMembership() != null || defender.isMarkedForReserveReturn()) {
                continue;
            }

            // 🚨 A TARGET IT CANNOT REACH IS NOT A FIGHT. This used to keep any defender with a target forever, so one
            // zombie behind a wall - or a player who walked off through solid rock - pinned defenders permanently and
            // the pool only ever grew. A live hive stood down ONCE across thirty-seven emergence waves.
            //
            // ⭐ [stated] "also do the unreachable thing". A defender that has made no progress toward its target for
            // UNREACHABLE_TARGET_TICKS gives up and banks itself, exactly as if the intruder had gone.
            if (isChasingSomethingReachable(defender, now)) {
                continue; // still fighting something it can actually get to
            }

            bank(location, defender);
            recalled++;
        }

        if (recalled > 0) {
            com.alien.Alien.LOGGER.info(
                "Hive at {}: stood down {} vent defender(s) - the intruders are gone, banked back into reserves.",
                location.centerPos(),
                recalled
            );
        }
    }

    /** Back into the reserve it came from, and out of the world. */
    private static void bank(HiveLocation location, Xenomorph defender) {
        if (!location.localReserves().addReturningMember(defender.getType(), 1)) {
            location.localReserves().addBrood(defender.getType(), 1);
        }
        defender.discard();
    }

    /**
     * 🚨🚨 Oct 2 - THE RESERVE DRAIN. [stated] "defenders would keep spawning after an enemy they couldnt find or reach
     * and it just emptied the entire reserves for a hive causing critical entity lag. which is what the reserves were
     * specifically designed to stop."
     * <p>
     * HOW IT HAPPENED. A wave was sized by {@code target - engaged}, and "engaged" counted only defenders currently
     * TARGETING the intruder. A defender that could not reach it gave up its target after 30 seconds of no progress -
     * and from then on it no longer counted, so the next wave replaced it. The per-intruder cap (8) also reset every
     * 30-second window. Against an intruder nobody could reach (on a pillar, across water, behind bedrock) that was
     * eight more live defenders every half-minute, none of them ever banked back, because the stand-down only ran once
     * the territory had been quiet for a minute - which it never was. The whole reserve walked out into the world.
     * </p>
     * <p>
     * THREE FIXES, ALL HERE:
     * </p>
     * <ul>
     * <li><b>Hive-wide field cap.</b> At most {@link #FIELD_CAP} vent defenders alive at once, however many intruders
     * there are. Defenders that die free their slot, so a real fight still gets replacements.</li>
     * <li><b>Stand-down DURING an incident.</b> A fielded defender older than {@link #STAND_DOWN_AGE_TICKS} that is not
     * chasing anything it can reach is banked straight away, not after the incident.</li>
     * <li><b>Written-off intruders.</b> When defenders are stood down for failing to reach an intruder, the hive stops
     * sending more at it until it moves {@link #WRITE_OFF_RESET_BLOCKS} blocks from where they gave up.</li>
     * </ul>
     */
    private static final int FIELD_CAP = DEFENDER_TARGET * 2;

    private static final long STAND_DOWN_AGE_TICKS = 20L * 30L;

    private static final int WRITE_OFF_RESET_BLOCKS = 8;

    private static final Map<HiveLocation, Map<UUID, BlockPos>> WRITTEN_OFF =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** Live vent defenders this hive has fielded; drops the dead and the unloaded from the record as it counts. */
    private static int fieldedAlive(ServerLevel level, HiveLocation location) {
        var fielded = RECALLABLE.get(location);
        if (fielded == null) {
            return 0;
        }
        fielded.removeIf(id -> !(level.getEntity(id) instanceof Xenomorph defender) || !defender.isAlive());
        return fielded.size();
    }

    /** Bank fielded defenders that have been out long enough and are chasing nothing they can reach. */
    private static void standDownStranded(ServerLevel level, HiveLocation location, long now) {
        var fielded = RECALLABLE.get(location);
        if (fielded == null || fielded.isEmpty()) {
            return;
        }
        var writtenOff = WRITTEN_OFF.computeIfAbsent(location, $ -> new java.util.concurrent.ConcurrentHashMap<>());
        var stood = 0;
        for (var id : new java.util.ArrayList<>(fielded)) {
            if (!(level.getEntity(id) instanceof Xenomorph defender) || !defender.isAlive()) {
                fielded.remove(id);
                continue;
            }
            if (defender.tickCount < STAND_DOWN_AGE_TICKS || defender.hasCustomName() || defender.isPassenger() || defender.isLeashed()) {
                continue;
            }
            var quarry = defender.getHiveIntruderTargetOrNull() != null ? defender.getHiveIntruderTargetOrNull() : defender.getTarget();
            if (isChasingSomethingReachable(defender, now)) {
                continue;
            }
            if (quarry != null) {
                writtenOff.put(quarry.getUUID(), quarry.blockPosition());
            }
            fielded.remove(id);
            bank(location, defender);
            stood++;
        }
        if (stood > 0) {
            com.alien.Alien.LOGGER.info(
                "Hive at {}: banked {} vent defender(s) that could not reach the intruders.",
                location.centerPos(),
                stood
            );
        }
    }

    /** True while this intruder is written off and has not moved far from where the defenders gave up on it. */
    private static boolean isWrittenOff(HiveLocation location, LivingEntity intruder) {
        var writtenOff = WRITTEN_OFF.get(location);
        if (writtenOff == null) {
            return false;
        }
        var at = writtenOff.get(intruder.getUUID());
        if (at == null) {
            return false;
        }
        if (at.distSqr(intruder.blockPosition()) > WRITE_OFF_RESET_BLOCKS * WRITE_OFF_RESET_BLOCKS) {
            writtenOff.remove(intruder.getUUID());
            return false;
        }
        return true;
    }

    /**
     * ⭐⭐ Oct 2 - A HIVE THAT CANNOT GET ITS BANK OUT IS RESCUED, NEVER KILLED.
     * <p>
     * [stated] "Option c or if its part of a empress network have the member join the lowest populations hive reserves
     * as adopted bonus." When the hive has wanted defenders and fielded none for {@link #RESCUE_AFTER_TICKS}:
     * </p>
     * <ol>
     * <li><b>Empress network, nothing of its own loaded:</b> the whole bank is adopted into the network hive with the
     * smallest population (same strain only). The members carry on serving the empress; the emptied hive then decays
     * through the ordinary zero-population rule, with nobody lost.</li>
     * <li><b>Otherwise:</b> a fresh STRUCTURE vent is stamped inside the claim beside the first intruder, so the next
     * wave has a mouth to come out of - the cause the dormancy comment described ("no reachable vent, or every mouth
     * blocked").</li>
     * </ol>
     * <p>
     * ⚠ One attempt per hive per {@link #RESCUE_AFTER_TICKS}. If the bank holds nothing that can fight, a new vent will
     * not help - the hive simply waits, which is harmless; it is no longer counted as dead for it.
     * </p>
     */
    private static void rescueStrandedBank(ServerLevel level, HiveLocation location, List<LivingEntity> intruders, long now) {
        var last = LAST_RESCUE.get(location);
        if (last != null && now - last < RESCUE_AFTER_TICKS) {
            return;
        }
        LAST_RESCUE.put(location, now);

        if (loadedXenomorphs(location) == 0 && adoptIntoNetwork(location) != null) {
            FAILED_TO_FIELD_SINCE.remove(location);
            return;
        }

        // Oct 6 - NO NEW VENTS. This used to stamp a fresh vent beside the intruder; [stated] "why are xenomorphs
        // making vents in the hive? they should not be" / "each room has about 2-4 vents there should be no need for
        // one to suddenly appear". The bank keeps trying the hive's own vents every wave; the stuck flag above stays
        // set, so the dormancy task can still settle a hive whose bank truly cannot get out.
    }

    private static int loadedXenomorphs(HiveLocation location) {
        var total = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                total += entry.getValue().size();
            }
        }
        return total;
    }

    /** Moves the whole bank to the smallest same-strain hive in this hive's empress network. Null if not networked. */
    private static @Nullable HiveLocation adoptIntoNetwork(HiveLocation location) {
        var empressId = empressOf(location);
        if (empressId == null) {
            return null;
        }
        HiveLocation smallest = null;
        var smallestPopulation = Integer.MAX_VALUE;
        for (var other : HiveLocationRegistry.INSTANCE.all()) {
            if (other == location || !other.isAlive() || !empressId.equals(empressOf(other))) {
                continue;
            }
            if (!java.util.Objects.equals(other.lineageVariantOrNull(), location.lineageVariantOrNull())) {
                continue; // a reserve only accepts its own strain
            }
            var population = com.alien.common.gameplay.hive.economy.CastePopulation.totalReliableXenomorphPopulation(other);
            if (population < smallestPopulation) {
                smallestPopulation = population;
                smallest = other;
            }
        }
        if (smallest == null) {
            return null;
        }

        var from = location.localReserves();
        var to = smallest.localReserves();
        var moved = 0;
        for (var type : from.identity().getAvailableEntityTypes()) {
            for (var entry = from.removeIdentity(type); entry != null; entry = from.removeIdentity(type)) {
                to.restoreIdentity(entry);
                moved++;
            }
        }
        for (var type : from.getAvailableEntityTypes()) {
            while (from.getCount(type) > 0 && from.trySpawn(type)) {
                if (to.addReturningMember(type, 1)) {
                    moved++;
                } else {
                    from.addReturningMember(type, 1); // refused after all - put it back and stop
                    break;
                }
            }
        }
        if (moved == 0) {
            return null;
        }
        markLineageDirty(location);
        markLineageDirty(smallest);
        Alien.LOGGER.info(
            "Hive at {}: could not deploy its bank, so {} member(s) were adopted by the empress network hive at {}.",
            location.centerPos(),
            moved,
            smallest.centerPos()
        );
        return smallest;
    }

    private static @Nullable UUID empressOf(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        return faction != null && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage
            ? lineage.empressId()
            : null;
    }

    /** These mutations run off the defence task, so the lineages must be marked or BLib will not save them. */
    private static void markLineageDirty(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction != null && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage) {
            lineage.markDirty();
        }
    }

    /** How long a hive must fail to field anyone before it is rescued. Two minutes. */
    private static final long RESCUE_AFTER_TICKS = 20L * 120L;

    private static final Map<HiveLocation, Long> LAST_RESCUE = Collections.synchronizedMap(new WeakHashMap<>());

    private VentDefenseTask() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        var intruders = HiveTerritoryAggroTask.intrudersInTerritory(level, location);
        if (intruders.isEmpty()) {
            EMERGED.remove(location); // incident over - forget the per-intruder tallies
            WRITTEN_OFF.remove(location);
            recallDefenders(level, location);
            return;
        }

        // Something hostile is here, so the quiet clock restarts.
        QUIET_SINCE.remove(location);

        long now = level.getGameTime();
        Long lastWave = LAST_WAVE.get(location);
        if (lastWave != null && now - lastWave < WAVE_COOLDOWN_TICKS) {
            return;
        }

        var emerged = EMERGED.computeIfAbsent(location, $ -> new HashMap<>());
        var presentIds = new HashSet<UUID>();
        for (var intruder : intruders) {
            presentIds.add(intruder.getUUID());
        }
        emerged.keySet().retainAll(presentIds); // an intruder who left/died resets their tally

        withdrawWorkersNearIntruders(level, location, intruders);

        standDownStranded(level, location, now);
        int budget = FIELD_CAP - fieldedAlive(level, location);

        int spawnedThisWave = 0;
        int wantedThisWave = 0;
        for (var intruder : intruders) {
            int target = defenderTarget(level, location, intruder);
            int engaged = countDefendersEngaged(level, location, intruder);
            int already = alreadyEmerged(emerged, intruder.getUUID(), now);
            int need = Math.min(target - engaged, INCIDENT_EMERGE_CAP - already);
            need = Math.min(need, budget - spawnedThisWave); // hive-wide: never more than FIELD_CAP out at once
            if (need <= 0 || isWrittenOff(location, intruder)) {
                continue;
            }
            wantedThisWave += need;

            // STRUCTURE or SURFACE only. A defender emerging from a FRONTIER vent pops out in a cave, far from the
            // intruder it was summoned for, and strands itself out there.
            var vents = HiveVents.ventsNear(
                location.ventManager(),
                intruder.blockPosition(),
                VENT_RANGE_CHUNKS,
                v -> location.ventManager().isKind(v, VentKind.STRUCTURE, VentKind.SURFACE)
            );
            if (vents.isEmpty()) {
                continue; // no duct mouth near this intruder - the loaded defenders will have to walk
            }

            int ventIndex = 0;
            int elitesSent = 0; // the guard answers, but does not empty itself for one intruder
            for (int i = 0; i < need; i++) {
                var type = pickReserveType(location, elitesSent);
                if (type == null) {
                    break; // reserves hold no combat-capable adults
                }
                BlockPos emergence = null;
                for (int attempt = 0; attempt < vents.size() && emergence == null; attempt++) {
                    emergence = HiveVents.emergencePosNear(level, vents.get(ventIndex++ % vents.size()));
                }
                if (emergence == null) {
                    break; // every nearby vent mouth is blocked
                }
                var defender = HiveLoadedSpawner.trySpawnFromReserves(level, location, type, emergence);
                if (defender == null) {
                    continue;
                }
                // Point it at what it was called up for. Without this it emerges and looks around: the aggro sweep
                // runs on its own 20-tick cadence and only reaches members the location already has on its roster.
                if (
                    defender instanceof Xenomorph freshDefender
                        && AlienPredicates.canAcquireTarget(freshDefender, intruder)
                ) {
                    freshDefender.setHiveIntruderTarget(intruder);
                }

                // ⭐ REMEMBER WHO WE CALLED UP, so they can be sent back when the incident ends. Only these are ever
                // recalled - a defender is a body the hive SPENT from its bank, and it must go back in it.
                RECALLABLE
                    .computeIfAbsent(location, $ -> Collections.synchronizedSet(new HashSet<>()))
                    .add(defender.getUUID());
                if (isElite(type)) {
                    elitesSent++;
                }
                level.playSound(null, emergence, AlienSoundEvents.BLOCK_RESIN_SPREAD.get(), SoundSource.HOSTILE, 1.0F, 0.8F);
                recordEmergence(emerged, intruder.getUUID(), now);
                spawnedThisWave++;
                // ⭐ It can still deploy, so it is not stranded. Cleared on the FIRST success rather than at
                // the end of the wave, so even one defender getting out resets it.
                FAILED_TO_FIELD_SINCE.remove(location);
            }
        }

        // ⚠ THE SILENT FAILURE THAT HID THIS BUG FOR SO LONG: the old loop only ever logged on SUCCESS, so a hive
        // that wanted defenders and produced none said nothing at all in the log. Throttled to the wave cooldown by
        // the guard above, so it cannot become spam.
        // ⭐ Oct 6 - AN EMPTY BANK IS NOT A FAILURE TO FIELD. Both of his logs show it: "wanted 48 defender(s) ... could
        // field NONE (bank 0 ...)" every 10 seconds for minutes, after which the rescue opened a NEW VENT in the middle
        // of the hive - for a bank with nobody in it. [stated] "each room has about 2-4 vents there should be no need
        // for one to suddenly appear." With no combat-capable adult banked there is nothing to get out, so nothing is
        // flagged, nothing is rescued and nothing is logged; the wave cooldown is still stamped so this branch is not
        // re-entered every tick.
        if (spawnedThisWave == 0 && wantedThisWave > 0 && pickReserveType(location, 0) == null) {
            FAILED_TO_FIELD_SINCE.remove(location);
            LAST_WAVE.put(location, now);
        } else if (spawnedThisWave == 0 && wantedThisWave > 0) {
            // Oct 2 - the flag was declared, read by the dormancy task and cleared here, but NEVER SET. It is now.
            FAILED_TO_FIELD_SINCE.putIfAbsent(location, now);
            if (hasFailedToFieldSince(location, now, RESCUE_AFTER_TICKS)) {
                rescueStrandedBank(level, location, intruders, now);
            }
            // \u26a0\u26a0 STAMP THE COOLDOWN ON FAILURE TOO. My own comment above claimed this was "throttled to the
            // wave cooldown by the guard above" - it was not. LAST_WAVE was only written when defenders actually
            // spawned, so on the failing path the cooldown never started and this logged EVERY TICK: a live log had
            // it firing three times a second for half a minute straight. The throttle only works if the failing path
            // records that it ran.
            LAST_WAVE.put(location, now);
            Alien.LOGGER.info(
                // ⚠⚠ THIS MESSAGE NAMED THREE CAUSES AND HID A FOURTH. A hive reported "bank 1, no reachable vent,
                // or every mouth blocked" while nothing was blocked and every vent was fine - the real cause was
                // canSpawn disagreeing with pickReserveType about whether the identity bank counts. Guessing at
                // causes in a log line is worse than reporting the count and letting the reader look.
                "Hive at {}: wanted {} defender(s) against intruders and could field NONE"
                    + " (bank {} - check reserve contents, vent kinds, and mouth clearance).",
                location.centerPos(),
                wantedThisWave,
                location.localReserves().getReliableCount()
            );
        }

        if (spawnedThisWave > 0) {
            LAST_WAVE.put(location, now);
            Alien.LOGGER.info(
                "Hive at {}: {} defender(s) emerged from the vents against intruders.",
                location.centerPos(),
                spawnedThisWave
            );
        }
    }

    /**
     * Full strength for interior intruders; 2/3 strength for surface intruders UNLESS they've escalated into an active
     * retribution campaign - an escalated enemy gets the full response wherever they stand.
     */
    private static int defenderTarget(ServerLevel level, HiveLocation location, LivingEntity intruder) {
        int band = HiveLocationRegistry.INSTANCE.config().surfacePartySurfaceBandBlocks();
        int surfaceY = level.getHeight(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            intruder.getBlockX(),
            intruder.getBlockZ()
        );
        boolean onSurface = intruder.getY() >= surfaceY - band;
        boolean escalated = intruder instanceof Player player
            && location.attackCampaigns().containsKey(player.getUUID());
        if (onSurface && !escalated) {
            return (DEFENDER_TARGET * 2 + 2) / 3; // 2/3 strength, rounded up
        }
        return DEFENDER_TARGET;
    }

    /**
     * ⭐⭐ DEFENDERS ACTUALLY FIGHTING THIS INTRUDER - not bodies standing near it.
     * <p>
     * ⚠⚠ THIS IS THE OTHER HALF OF WHY THE HIVE LOOKED DEAD. The old test counted every loaded xenomorph member within
     * 32 blocks, which inside a hive is every drone hauling an egg, every builder trailing resin and every idler in the
     * corridor. Four of those is the resting state of an occupied hive, so {@code target - nearby} came out at zero or
     * below and no wave was ever considered - regardless of what the bank held, and regardless of whether a single one
     * of them had so much as looked at the intruder.
     * </p>
     * <p>
     * BOTH TARGET FIELDS ARE READ. {@code getTarget} is ordinary combat; {@code getHiveIntruderTargetOrNull} is the
     * territory-aggro assignment, which drives its own navigator and is what a member sent by
     * {@link HiveTerritoryAggroTask} is carrying. A defender counts if either one names this intruder.
     * </p>
     * <p>
     * No distance term on purpose: a defender that has committed to the intruder is committed wherever it currently is,
     * and one still running down a corridor should not be replaced by a fresh spawn.
     * </p>
     */
    /**
     * Whether this defender is in a fight it can actually finish.
     * <p>
     * ⚠ "Reachable" is measured as PROGRESS, not line of sight: a defender that has been unable to close the distance
     * for UNREACHABLE_TARGET_TICKS is treated as having no target, however visible that target is. Distance alone would
     * strand anything chasing across a room; line of sight alone would strand anything behind glass.
     * </p>
     */
    private static boolean isChasingSomethingReachable(Xenomorph defender, long now) {
        var target = defender.getTarget();

        if (target == null && defender.getHiveIntruderTargetOrNull() == null) {
            return false;
        }

        if (target == null) {
            return true; // Hive intruder duty without a live target - leave that to its own bookkeeping.
        }

        var distanceSqr = defender.distanceToSqr(target);
        var progress = CHASE_PROGRESS.get(defender.getUUID());

        if (progress == null || distanceSqr < progress.bestDistanceSqr() - CHASE_PROGRESS_EPSILON) {
            CHASE_PROGRESS.put(defender.getUUID(), new ChaseProgress(distanceSqr, now));
            return true;
        }

        if (now - progress.sinceTick() < UNREACHABLE_TARGET_TICKS) {
            return true;
        }

        CHASE_PROGRESS.remove(defender.getUUID());
        defender.setTarget(null);

        return false;
    }

    /** Closest approach a defender has managed to its target, and when it managed it. */
    private record ChaseProgress(
        double bestDistanceSqr,
        long sinceTick
    ) {}

    /** Per-defender chase progress. Weakly held - a dead defender takes its entry with it. */
    private static final java.util.Map<java.util.UUID, ChaseProgress> CHASE_PROGRESS =
        new java.util.WeakHashMap<>();

    /** How long a defender may fail to close on its target before it gives up and banks itself. */
    private static final long UNREACHABLE_TARGET_TICKS = 20L * 30L;

    /** Squared-distance improvement that counts as real progress rather than jitter. */
    private static final double CHASE_PROGRESS_EPSILON = 1.0D;

    /**
     * Workers near an intruder down tools and go into the vents; workers elsewhere carry on.
     * <p>
     * ⭐⭐ [stated] "workers should retreat into the vents and be banked. only if the defenders get low do the workers
     * join the fight. This way workers not near the player keep working and workers near the player get out of the way
     * and stop working. So it still feels like a defensive hive."
     * </p>
     * <p>
     * ⚠ PROXIMITY IS THE WHOLE POINT. A drone carving a hallway on the far side of the hive is in no danger and keeps
     * carving - the hive does not down tools everywhere because one zombie wandered in. Only the ones actually in
     * harm's way clear the floor, which is what leaves the fighting castes room to work.
     * </p>
     * <p>
     * ⚠ NOT WHEN THE HIVE IS DESPERATE. Below MIN_DEFENDERS_BEFORE_CONSCRIPTION fighting members, nobody retreats - the
     * workers are the defence at that point, and pulling them out would leave the hive empty.
     * </p>
     */
    private static void withdrawWorkersNearIntruders(
        ServerLevel level,
        HiveLocation location,
        List<LivingEntity> intruders
    ) {
        if (intruders.isEmpty() || countFreeDefenders(level, location) < MIN_DEFENDERS_BEFORE_CONSCRIPTION) {
            return;
        }

        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.XENOMORPHS) || isDefenderCaste(entry.getKey())) {
                continue;
            }

            for (var uuid : new java.util.ArrayList<>(entry.getValue())) {
                if (
                    !(level.getEntity(uuid) instanceof Xenomorph worker)
                        || !worker.isAlive()
                        || worker.isMarkedForReserveReturn()
                        || worker.partyMembership() != null
                ) {
                    continue;
                }

                if (!isNearAnyIntruder(worker, intruders)) {
                    continue; // Far from the trouble - keep working.
                }

                var vent = com.alien.common.gameplay.hive.economy.BroodBankTask
                    .nearestVent(location, worker.blockPosition());

                if (vent == null) {
                    continue;
                }

                worker.setTarget(null);
                worker.markForReserveReturn(level.getGameTime());
                worker.getNavigation().moveTo(vent.getX() + 0.5, vent.getY(), vent.getZ() + 0.5, 1.0);
            }
        }
    }

    private static boolean isNearAnyIntruder(Xenomorph worker, List<LivingEntity> intruders) {
        for (var intruder : intruders) {
            if (worker.distanceToSqr(intruder) <= WORKER_WITHDRAW_RANGE_SQUARED) {
                return true;
            }
        }

        return false;
    }

    /** Fighting-caste members of this hive that are loaded and alive. */
    private static int countFreeDefenders(ServerLevel level, HiveLocation location) {
        var count = 0;

        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!isDefenderCaste(entry.getKey())) {
                continue;
            }

            for (var uuid : entry.getValue()) {
                if (level.getEntity(uuid) instanceof Xenomorph member && member.isAlive()) {
                    count++;
                }
            }
        }

        return count;
    }

    /** How close an intruder must be before a worker abandons its job and heads for a vent. */
    private static final double WORKER_WITHDRAW_RANGE_SQUARED = 16.0D * 16.0D;

    /** Below this many live fighting castes, workers stay out and fight instead of withdrawing. */
    public static final int MIN_DEFENDERS_BEFORE_CONSCRIPTION = 3;

    private static int countDefendersEngaged(ServerLevel level, HiveLocation location, LivingEntity intruder) {
        int count = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                if (!(level.getEntity(uuid) instanceof net.minecraft.world.entity.Mob member) || !member.isAlive()) {
                    continue;
                }
                if (member.getTarget() == intruder) {
                    count++;
                    continue;
                }
                if (
                    member instanceof Xenomorph xenomorph
                        && xenomorph.getHiveIntruderTargetOrNull() == intruder
                ) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Defenders this intruder has already drawn inside the CURRENT budget window; a stale window reads as zero. */
    private static int alreadyEmerged(Map<UUID, EmergeTally> emerged, UUID intruderId, long now) {
        var tally = emerged.get(intruderId);
        if (tally == null || now - tally.windowStartTick() >= INCIDENT_WINDOW_TICKS) {
            return 0;
        }
        return tally.count();
    }

    /** Charges one defender to this intruder's budget, opening a fresh window if the last one has run out. */
    private static void recordEmergence(Map<UUID, EmergeTally> emerged, UUID intruderId, long now) {
        var tally = emerged.get(intruderId);
        if (tally == null || now - tally.windowStartTick() >= INCIDENT_WINDOW_TICKS) {
            emerged.put(intruderId, new EmergeTally(1, now));
            return;
        }
        emerged.put(intruderId, new EmergeTally(tally.count() + 1, tally.windowStartTick()));
    }

    /**
     * The best combat-capable reserve type the hive can field: the queen's guard first, then the line, then workers.
     * {@code elitesAlreadySent} caps how much of the guard any single incident can consume.
     */
    @Nullable
    private static EntityType<?> pickReserveType(HiveLocation location, int elitesAlreadySent) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return null;
        }
        for (var caste : DRAW_ORDER) {
            // Praetorians and crushers are rare and expensive - a couple answer the alarm, not the whole guard.
            if (ELITE_CASTES.contains(caste) && elitesAlreadySent >= ELITE_DEFENDER_CAP) {
                continue;
            }
            var type = CasteResolver.entityTypeForCaste(variant, caste);
            // getReliableCount, NOT getCount: getCount is the ABSTRACT bank only, so a hive whose whole population
            // had unloaded into the IDENTITY list read as empty and picked nothing.
            if (type != null && location.localReserves().getReliableCount(type) > 0) {
                return type;
            }
        }
        return null;
    }

    /** Is this entity type one of the queen's guard? */
    private static boolean isElite(EntityType<?> type) {
        for (var caste : ELITE_CASTES) {
            if (type.is(caste)) {
                return true;
            }
        }
        return false;
    }
}
