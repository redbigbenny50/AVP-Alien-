package com.alien.common.gameplay.hive.defense;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.gameplay.hive.tick.HiveTerritoryAggroTask;
import com.alien.common.gameplay.hive.vent.HiveVents;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

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

    private static final int DEFENDER_TARGET = 4; // defenders wanted near an interior intruder

    private static final int DEFENDER_RADIUS = 32; // "near" the intruder, in blocks

    private static final int VENT_RANGE_CHUNKS = 3; // vents this close to the intruder can be used

    private static final long WAVE_COOLDOWN_TICKS = 200L; // min ticks between emergence waves per hive

    private static final int INCIDENT_EMERGE_CAP = 8; // max defenders emerged per intruder incident

    /** Combat castes first when drawing defenders from the reserves. */
    private static final List<net.minecraft.tags.TagKey<EntityType<?>>> DRAW_ORDER = List.of(
        AlienEntityTypeTags.WARRIORS,
        AlienEntityTypeTags.PROWLERS,
        AlienEntityTypeTags.RUNNERS,
        AlienEntityTypeTags.DRONES
    );

    private static final Map<HiveLocation, Long> LAST_WAVE =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Defenders emerged per intruder (by UUID) for the current incident; cleared when the intruder is gone. */
    private static final Map<HiveLocation, Map<UUID, Integer>> EMERGED =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private VentDefenseTask() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        var intruders = HiveTerritoryAggroTask.intrudersInTerritory(level, location);
        if (intruders.isEmpty()) {
            EMERGED.remove(location); // incident over - forget the per-intruder tallies
            return;
        }

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

        int spawnedThisWave = 0;
        for (var intruder : intruders) {
            int target = defenderTarget(level, location, intruder);
            int nearby = countDefendersNear(level, location, intruder);
            int already = emerged.getOrDefault(intruder.getUUID(), 0);
            int need = Math.min(target - nearby, INCIDENT_EMERGE_CAP - already);
            if (need <= 0) {
                continue;
            }

            var vents = HiveVents.ventsNear(location.ventManager(), intruder.blockPosition(), VENT_RANGE_CHUNKS, null);
            if (vents.isEmpty()) {
                continue; // no duct mouth near this intruder - the loaded defenders will have to walk
            }

            int ventIndex = 0;
            for (int i = 0; i < need; i++) {
                var type = pickReserveType(location);
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
                var defender = HiveLoadedSpawner.trySpawnIdentityReserve(level, location, type, emergence);
                if (defender == null) {
                    continue;
                }
                ReserveSpawnUtil.markSpawnedFromReserves(defender);
                level.playSound(null, emergence, AlienSoundEvents.BLOCK_RESIN_SPREAD.get(), SoundSource.HOSTILE, 1.0F, 0.8F);
                emerged.merge(intruder.getUUID(), 1, Integer::sum);
                spawnedThisWave++;
            }
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

    /** Loaded, living xenomorph members within {@link #DEFENDER_RADIUS} blocks of the intruder. */
    private static int countDefendersNear(ServerLevel level, HiveLocation location, LivingEntity intruder) {
        double radiusSq = (double) DEFENDER_RADIUS * DEFENDER_RADIUS;
        int count = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                var member = level.getEntity(uuid);
                if (member != null && member.isAlive() && member.distanceToSqr(intruder) <= radiusSq) {
                    count++;
                }
            }
        }
        return count;
    }

    /** The best combat-capable reserve type the hive can field, warriors first. */
    @Nullable
    private static EntityType<?> pickReserveType(HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return null;
        }
        for (var caste : DRAW_ORDER) {
            var type = CasteResolver.entityTypeForCaste(variant, caste);
            if (type != null && location.localReserves().getCount(type) > 0) {
                return type;
            }
        }
        return null;
    }
}
