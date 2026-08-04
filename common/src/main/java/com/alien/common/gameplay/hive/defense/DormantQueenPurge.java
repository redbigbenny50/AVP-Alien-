package com.alien.common.gameplay.hive.defense;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.economy.CasteResolver;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.party.EggDutyGuard;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The execution of a sleeping rival-strain queen found inside the hive's own territory.
 * <p>
 * [stated] "if its a different strain that discovers her they would kill her outright... by discover her its if their
 * territory claim overlaps her the hive will 'sense' her and either dig to her and kill her or teleport to her location
 * kill her and dig back to the hive/vent." The claim IS the sense organ: the moment a chunk the hive owns contains a
 * dormant queen of another strain, a kill squad is dispatched, and when she is dead the survivors duct home.
 * <p>
 * SAME-STRAIN SLEEPERS ARE NEVER TOUCHED. Her own strain either adopts her as a daughter queen or - when the lineage is
 * at its hive cap - wakes her to relocate; both of those live on her side in {@code QueenLifecyclePhaseManager}. Only a
 * foreign strain kills.
 * <p>
 * THE SQUAD TRAVELS BY DUCT, NOT BY PICKAXE. A dormant queen has sealed herself into a carved pocket with no opening -
 * there is no path to walk, so a walking squad would grind against stone until it timed out. The teleport is the "dig
 * to her" of the fiction: they arrive in her chamber, and the survivors are pulled back to the hive floor afterwards.
 */
public final class DormantQueenPurge {

    /**
     * Check cadence while the hive ticks. Thirty seconds, not ten: the scan below is the expensive part of this class
     * and a sleeping queen is not going anywhere. Nothing about the outcome changes at this resolution.
     */
    private static final long INTERVAL_TICKS = 600L;

    /**
     * How far above and below the hive floor the sweep looks. Full world height turned this into a scan of every
     * section over the whole claimed footprint - a few hundred chunk columns times twenty-four sections, every ten
     * seconds, per hive, almost always finding nothing. Both parties here are underground hives in the same dig band,
     * so a window around our own floor covers every case that can realistically occur.
     */
    private static final int SWEEP_HALF_HEIGHT = 96;

    /** How many killers go. She is asleep and alone; this is an execution, not a siege. */
    private static final int SQUAD_SIZE = 3;

    /** A job this old is abandoned - the squad goes home and the claim will re-sense her next cycle. */
    private static final long JOB_TIMEOUT_TICKS = 2400L;

    /** Ring radius around her for arrival spots, in blocks. */
    private static final double ARRIVAL_SPREAD = 2.0;

    /**
     * Who the hive sends, heaviest first. The queen's guard leads because this is exactly the work they exist for - and
     * unlike a vent-defense incident there is no cap on the guard here: a rival queen asleep in your ground is worth
     * the praetorian.
     */
    private static final List<net.minecraft.tags.TagKey<EntityType<?>>> DRAW_ORDER = List.of(
        AlienEntityTypeTags.PRAETORIANS,
        AlienEntityTypeTags.CRUSHERS,
        AlienEntityTypeTags.PREDALIENS,
        AlienEntityTypeTags.WARRIORS,
        AlienEntityTypeTags.PROWLERS,
        AlienEntityTypeTags.RUNNERS,
        AlienEntityTypeTags.DRONES
    );

    /** Live jobs, one per hive. Transient by design: a reload just re-senses her and sends a fresh squad. */
    private static final Map<HiveLocation, Job> JOBS = Collections.synchronizedMap(new WeakHashMap<>());

    private DormantQueenPurge() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        if (!location.isAlive()) {
            JOBS.remove(location);
            return;
        }

        var job = JOBS.get(location);
        if (job != null) {
            tickJob(level, location, job);
            return;
        }

        var quarry = findDormantRivalQueen(level, location);
        if (quarry == null) {
            return;
        }

        var squad = musterSquad(level, location, quarry);
        if (squad.isEmpty()) {
            return; // nobody free and nothing in the bank - she sleeps on, and we look again next cycle
        }

        JOBS.put(location, new Job(quarry.getUUID(), squad, level.getGameTime()));
        Alien.LOGGER.info(
            "Hive at {}: sensed a dormant {} queen in its territory at {} — sending {} to kill her.",
            location.centerPos(),
            quarry.getVariant(),
            quarry.blockPosition(),
            squad.size()
        );
    }

    /** Drives a running job: keep the squad on her, then send the survivors home when she is dead or time is up. */
    private static void tickJob(ServerLevel level, HiveLocation location, Job job) {
        var quarry = level.getEntity(job.quarryId) instanceof Queen queen && queen.isAlive() ? queen : null;
        boolean expired = level.getGameTime() - job.startedTick > JOB_TIMEOUT_TICKS;

        if (quarry == null || expired) {
            for (var memberId : job.squad) {
                if (level.getEntity(memberId) instanceof Mob member && member.isAlive()) {
                    member.setTarget(null);
                    ductHome(level, location, member);
                }
            }
            JOBS.remove(location);
            Alien.LOGGER.info(
                "Hive at {}: dormant-queen purge {} — squad returning to the hive.",
                location.centerPos(),
                quarry == null ? "complete" : "abandoned (she outlasted them)"
            );
            return;
        }

        // She is still breathing. Re-assert the target every cycle: a killer that lost aggro while she was asleep
        // (she fights back only once roused) would otherwise stand in her chamber doing nothing.
        for (var memberId : job.squad) {
            if (level.getEntity(memberId) instanceof Mob member && member.isAlive() && member.getTarget() != quarry) {
                member.setTarget(quarry);
            }
        }
    }

    /**
     * A living, dormant queen of ANOTHER strain standing in a chunk this hive claims. Scans the box around the hive's
     * claimed footprint rather than every loaded entity, and re-checks the claim per candidate so a queen sleeping just
     * outside the border is left alone.
     */
    @Nullable
    private static Queen findDormantRivalQueen(ServerLevel level, HiveLocation location) {
        var ourVariant = location.lineageVariantOrNull();
        if (ourVariant == null || location.claimedChunks().isEmpty()) {
            return null;
        }

        int reach = 0;
        var centre = new ChunkPos(location.centerPos());
        for (var chunk : location.claimedChunks()) {
            reach = Math.max(reach, Math.max(Math.abs(chunk.x - centre.x), Math.abs(chunk.z - centre.z)));
        }

        double span = (reach + 1) * 16.0;
        var box = new AABB(
            centre.getMiddleBlockX() - span,
            Math.max(level.getMinBuildHeight(), location.hiveFloorY() - SWEEP_HALF_HEIGHT),
            centre.getMiddleBlockZ() - span,
            centre.getMiddleBlockX() + span,
            Math.min(level.getMaxBuildHeight(), location.hiveFloorY() + SWEEP_HALF_HEIGHT),
            centre.getMiddleBlockZ() + span
        );

        for (var candidate : level.getEntitiesOfClass(Queen.class, box)) {
            if (!candidate.isAlive() || !Boolean.TRUE.equals(candidate.isHibernating.get())) {
                continue;
            }
            if (java.util.Objects.equals(candidate.getVariant(), ourVariant)) {
                continue; // her own strain adopts her or moves her along - never this
            }
            if (!location.claimedChunks().contains(candidate.chunkPosition())) {
                continue; // sleeping near the border is not sleeping in our ground
            }
            return candidate;
        }

        return null;
    }

    /**
     * Assembles the killers: free loaded members first (they are already paid for), topped up from the reserve bank.
     * Everyone selected is teleported into her chamber - the duct arrival that stands in for tunnelling to her.
     */
    private static List<UUID> musterSquad(ServerLevel level, HiveLocation location, Queen quarry) {
        var squad = new ArrayList<UUID>(SQUAD_SIZE);

        for (var entry : location.loadedMembersByType().entrySet()) {
            if (squad.size() >= SQUAD_SIZE || !entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                if (squad.size() >= SQUAD_SIZE) {
                    break;
                }
                if (level.getEntity(uuid) instanceof Mob member && isFree(member)) {
                    deploy(level, member, quarry, squad.size());
                    squad.add(uuid);
                }
            }
        }

        while (squad.size() < SQUAD_SIZE) {
            var type = pickReserveType(location);
            if (type == null) {
                break; // the bank is empty of anything that can fight
            }
            var arrival = arrivalPos(level, quarry, squad.size());
            var killer = HiveLoadedSpawner.trySpawnIdentityReserve(level, location, type, arrival);
            if (!(killer instanceof Mob mob)) {
                break;
            }
            ReserveSpawnUtil.markSpawnedFromReserves(mob);
            mob.setTarget(quarry);
            squad.add(mob.getUUID());
        }

        if (!squad.isEmpty()) {
            level.playSound(
                null,
                quarry.blockPosition(),
                AlienSoundEvents.BLOCK_RESIN_SPREAD.get(),
                SoundSource.HOSTILE,
                1.0F,
                0.7F
            );
        }
        return squad;
    }

    /** Free = not fighting, not hauling an egg or a host, not riding or carrying anything. */
    private static boolean isFree(Mob member) {
        return member.isAlive()
            && member.getTarget() == null
            && !member.isPassenger()
            && member.getPassengers().isEmpty()
            && !EggDutyGuard.isOnEggDuty(member);
    }

    /** Puts a loaded member in her chamber and sets her as its target. */
    private static void deploy(ServerLevel level, Mob member, Queen quarry, int index) {
        var arrival = arrivalPos(level, quarry, index);
        member.teleportTo(arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5);
        member.setTarget(quarry);
    }

    /** A spot beside her, spread around the ring so three killers do not stack in one block. */
    private static BlockPos arrivalPos(ServerLevel level, Queen quarry, int index) {
        double angle = (Math.PI * 2.0 / SQUAD_SIZE) * index;
        var spot = BlockPos.containing(
            quarry.getX() + Math.cos(angle) * ARRIVAL_SPREAD,
            quarry.getY(),
            quarry.getZ() + Math.sin(angle) * ARRIVAL_SPREAD
        );
        // Her pocket is small and carved; if the ring lands in stone, arrive on top of her instead of inside a wall.
        return level.getBlockState(spot).isAir() ? spot : quarry.blockPosition();
    }

    /** The return leg: back to the hive floor at its centre, the "dig back to the hive/vent" half of the order. */
    private static void ductHome(ServerLevel level, HiveLocation location, LivingEntity member) {
        var home = location.centerPos();
        member.teleportTo(home.getX() + 0.5, location.hiveFloorY() + 1, home.getZ() + 0.5);
        level.playSound(null, home, AlienSoundEvents.BLOCK_RESIN_SPREAD.get(), SoundSource.HOSTILE, 0.8F, 0.9F);
    }

    /** The heaviest combat-capable caste the bank can still field. */
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

    /** One hive's running execution: who they are after, who went, and when they left. */
    private record Job(
        UUID quarryId,
        List<UUID> squad,
        long startedTick
    ) {}
}
