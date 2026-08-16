package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HostChamberSlots;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * ⭐⭐ SOMETHING HOSTABLE WANDERED IN AND NOBODY CAME FOR IT.
 * <p>
 * [stated] "people are summoning villagers into the hive and they arent being grabbed for hosting or targeted oddly
 * which is fine. i think if a level 1 threat or no threat tier hostable mob wanders into the hive a internal host party
 * should be sent from any available drones. this is a seperate party from the surface host parties. and it can be as
 * small as 1 drone to the closest vent of the intruder."
 * </p>
 * <p>
 * ⚠ THIS IS NOT A TARGETING CHANGE, AND DELIBERATELY SO. {@code AlienPredicates.isTargetThreatAllowed} treats
 * THREAT_1_PASSIVE as "never hunted", which is exactly why a villager can stand in a corridor unmolested — and he
 * signed that off ("which is fine"). Loosening the tier would make every drone in the world chase villagers. So this is
 * a separate CAPTURE path that reads the same tags the facehuggers do, and leaves combat targeting untouched.
 * </p>
 * <h2>Why this reuses {@link HiveParty.HostHunt} instead of adding a party type</h2> {@code HiveParty} is a SEALED
 * interface, so a new variant means a new class, new NBT round-trip, a new lifecycle task and a new tick hook — for
 * behaviour that is identical once the party exists. {@code HostHuntDuty} already drags a captive to a host chamber and
 * {@code HostHuntPartyLifecycleTask} already resolves the party and teleports survivors home through the vents. **Only
 * the DISPATCH rules differ**, and those all live here:
 * <ul>
 * <li>no party cooldown — a wandering host is an opportunity, not a scheduled event;</li>
 * <li>no surface-vent requirement, and no {@code plantVentOnHostTier} — the intruder is already indoors, so
 * {@code findPartyVents} is the right pool and the nearest one wins;</li>
 * <li>a floor of ONE drone, per his "as small as 1 drone";</li>
 * <li>and the size is fixed rather than scaled by claims, because this answers a single intruder.</li>
 * </ul>
 * <p>
 * ⚠ ON "FREE": [stated] "free already home". Nothing is consumed either way — {@code drainComposition} WITHDRAWS from
 * the reserve pool and survivors are folded back in by {@code BroodBankTask}, so a party is only ever a temporary
 * availability cost. What "free" removes here is the gating: no cooldown, no claim-scaled budget, no surface
 * prerequisite. The drone still has to exist in reserves to be dispatched at all.
 * </p>
 */
public final class InternalHostPartyDispatch {

    /**
     * ⭐ How far from a hive vent an intruder counts as "inside". Generous on purpose: the hive is a warren and the
     * intruder is likely to be a corridor or two from the nearest mouth.
     */
    private static final double INTRUDER_SEARCH_RADIUS_BLOCKS = 48.0D;

    /** A single drone answers a single wanderer. */
    private static final int PARTY_SIZE = 1;

    private InternalHostPartyDispatch() {}

    public static void tryRun(MinecraftServer server, HiveLocation location, HiveConfig config) {
        var level = server.getLevel(location.dimension());
        if (level == null) {
            return;
        }

        // One host operation at a time per hive - the surface hunt and this share HiveParty.HostHunt, and two crews
        // fighting over one host chamber spot is worse than waiting a cycle.
        for (var party : location.parties()) {
            if (party instanceof HiveParty.HostHunt) {
                return;
            }
        }

        // Nowhere to put it, no point fetching it. Same gate the surface hunt uses.
        if (HostChamberSlots.firstFreeSpot(level, location) == null) {
            return;
        }

        var vents = PartyVentUtil.findPartyVents(level, location);
        if (vents.isEmpty()) {
            return;
        }

        var intruder = findHostableIntruder(level, location, vents);
        if (intruder == null) {
            return;
        }

        // [stated] "to the closest vent of the intruder" - and they EMERGE from it rather than walking to it.
        var spawnPos = nearestVent(vents, intruder.blockPosition());
        if (spawnPos == null) {
            return;
        }

        var composition = drainDrones(location);
        if (composition.getCount() <= 0) {
            return;
        }

        var party = new HiveParty.HostHunt(
            HivePartyId.fresh(),
            location.id(),
            location.dimension(),
            composition,
            level.getGameTime()
        );

        var spawned = materialize(level, party, spawnPos);
        if (spawned <= 0) {
            refund(location, composition);
            return;
        }

        location.parties().add(party);

        Alien.LOGGER.info(
            "Hive: dispatched internal host party for location {} — {} member(s) from vent at {} for {} at {}",
            location.id(),
            spawned,
            spawnPos,
            intruder.getType().getDescriptionId(),
            intruder.blockPosition()
        );
    }

    /**
     * The nearest hostable, low-threat mob standing in the hive's own structure.
     * <p>
     * ⚠ THE TAGS ARE THE AUTHORITY, NOT A HAND-WRITTEN LIST. {@code #avp_alien:hosts} already contains
     * {@code #avp_alien:runner_hosts}, so testing the one tag covers both ladders and every cross-mod entry that has
     * been added to them — including the marine dog, once its tag lands.
     * </p>
     * <p>
     * ⚠ AND THE THREAT GATE IS INVERTED FROM EVERYWHERE ELSE IN THE MOD: this wants the mobs the hive does NOT consider
     * dangerous. Anything in tier 2 or 3 is a fight, and a fight is the combat system's business, not a capture crew's.
     * </p>
     */
    private static @Nullable LivingEntity findHostableIntruder(
        ServerLevel level,
        HiveLocation location,
        java.util.List<BlockPos> vents
    ) {
        var anchor = location.centerPos();
        var box = new AABB(anchor).inflate(INTRUDER_SEARCH_RADIUS_BLOCKS);

        LivingEntity best = null;
        var bestDistance = Double.MAX_VALUE;

        for (var candidate : level.getEntitiesOfClass(LivingEntity.class, box, InternalHostPartyDispatch::isHostable)) {
            // Inside the BUILT hive, not merely inside the claim - a claim is enormous and a mob wandering the
            // territory is not an intruder, it is scenery.
            if (!location.structurePieceByChunk().containsKey(new ChunkPos(candidate.blockPosition()))) {
                continue;
            }
            if (!location.withinSlab(candidate.blockPosition().getY())) {
                continue;
            }

            var distance = candidate.distanceToSqr(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }

        return best;
    }

    private static boolean isHostable(LivingEntity candidate) {
        if (!candidate.isAlive() || candidate.isPassenger()) {
            return false; // already carried - somebody got there first
        }
        if (!candidate.getType().is(AlienEntityTypeTags.HOSTS)) {
            return false;
        }
        // Tier 2 and 3 are threats the hive fights on its own terms; only the harmless get collected.
        return !candidate.getType().is(AlienEntityTypeTags.XENOMORPH_THREAT_2_LOW_DANGER)
            && !candidate.getType().is(AlienEntityTypeTags.XENOMORPH_THREAT_3_HIGH_DANGER);
    }

    private static @Nullable BlockPos nearestVent(java.util.List<BlockPos> vents, BlockPos target) {
        BlockPos best = null;
        var bestDistance = Double.MAX_VALUE;

        for (var vent : vents) {
            var distance = vent.distSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = vent;
            }
        }

        return best;
    }

    /** Drones only, exactly like the surface host hunt: they carry hosts, they do not fight for sport. */
    private static EntityReserves drainDrones(HiveLocation location) {
        var reserves = location.localReserves();
        var composition = new EntityReserves();

        for (var type : new ArrayList<>(reserves.getAvailableEntityTypes())) {
            if (!type.is(AlienEntityTypeTags.DRONES)) {
                continue;
            }
            if (reserves.trySpawn(type)) {
                composition.add(type, 1);
                if (composition.getCount() >= PARTY_SIZE) {
                    break;
                }
            }
        }

        return composition;
    }

    private static void refund(HiveLocation location, EntityReserves composition) {
        for (var type : new ArrayList<>(composition.getAvailableEntityTypes())) {
            var count = composition.getCount(type);
            if (count <= 0) {
                continue;
            }
            location.localReserves().addReturningMember(type, count);
            composition.add(type, -count);
        }
    }

    private static int materialize(ServerLevel level, HiveParty.HostHunt party, BlockPos spawnPos) {
        var spawned = 0;

        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            for (var i = 0; i < party.composition().getCount(type); i++) {
                var entity = type.create(level);
                if (!(entity instanceof net.minecraft.world.entity.Mob mob)) {
                    continue;
                }

                // Emerge AT the vent. Unlike the surface hunt there is no need to find sky - this vent is indoors and
                // the drone belongs on the other side of it.
                mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360F, 0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);

                if (!level.addFreshEntity(mob)) {
                    continue;
                }

                party.trackMaterializedMember(mob.getUUID(), type);
                spawned++;
            }
        }

        return spawned;
    }
}
