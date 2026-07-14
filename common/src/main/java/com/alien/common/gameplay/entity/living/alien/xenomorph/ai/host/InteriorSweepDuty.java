package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.party.EggDutyGuard;
import com.alien.common.gameplay.hive.structure.HostChamberSlots;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * A host loose INSIDE the hive is a chore, not an expedition.
 * <p>
 * Livestock wanders in through the vents; a player cuts a captive out of the webbing and it walks off down a corridor.
 * Nothing used to deal with either - {@link HostSensors} only ever let a dispatched {@code HostHunt} party member
 * target a host, so the hive's own workers strolled straight past a cow standing in the nursery.
 * <p>
 * This is deliberately NOT a seventh party type. There is no dispatch, no reserve drain, no member cap: any drone that
 * happens to be near a loose host simply picks it up, the way workers already react to a loose egg. Nobody needs to be
 * <i>sent</i> to deal with a sheep that wandered into the hive.
 * <h2>The guardrails, and why each one is there</h2>
 * <ul>
 * <li><b>Built structure only, not the whole claim.</b> The load-bearing one. On a flat world a claim is enormous and
 * full of surface animals; if the sweep could see the whole claim, every drone in the hive would abandon its chores to
 * fetch sheep out of a field. This is the same failure the worker leash was added to fix.</li>
 * <li><b>Only if a chamber spot is free.</b> Otherwise a drone grabs a cow and stands holding it forever. Same rule the
 * host-hunt dispatch already applies. With the larder full, loose livestock is simply left to mill about.</li>
 * <li><b>Drones only.</b> {@code canEntityRideAlien} permits HOSTS on {@code Drone} and nowhere else - a runner
 * physically cannot carry one, so letting it target one would strand it.</li>
 * <li><b>Never while hauling an egg.</b> A carrier mid-run would drop its egg for a cow.</li>
 * </ul>
 */
public final class InteriorSweepDuty {

    private InteriorSweepDuty() {}

    /** Can this xenomorph take a host it finds lying around inside the hive? */
    public static boolean isSweeper(Xenomorph xenomorph) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        // Only a drone can physically carry a host home.
        if (!xenomorph.getType().is(AlienEntityTypeTags.DRONES)) {
            return false;
        }

        // Egg first. A hauler mid-run does not drop its cargo for a cow.
        if (EggDutyGuard.isOnEggDuty(xenomorph)) {
            return false;
        }

        var location = HiveMemberLocationResolver.reserveReturnLocation(xenomorph);
        if (location == null || !location.isAlive()) {
            return false;
        }

        // Nowhere to put it = do not pick it up.
        if (HostChamberSlots.firstFreeSpot(serverLevel, location) == null) {
            return false;
        }

        // And we must be inside the hive proper ourselves - this is a chore, not a patrol.
        return isInsideHive(location, xenomorph);
    }

    /** The hive this xenomorph belongs to, or null. */
    public static @Nullable HiveLocation hiveOf(Xenomorph xenomorph) {
        var location = HiveMemberLocationResolver.reserveReturnLocation(xenomorph);
        return location != null && location.isAlive() ? location : null;
    }

    /**
     * Is this entity inside the hive's built interior?
     * <p>
     * Deliberately the BUILT STRUCTURE ({@code structurePieceByChunk} + {@code withinSlab}), not the claim. The claim
     * is territory; the structure is the building. A sheep in a field the hive happens to own is a host-hunt's problem,
     * not a chore.
     * <p>
     * Asked of two different things: of a HOST, to decide whether it is loose in the corridors and should be swept up;
     * and of a CARRIER, to decide whether it can walk its captive straight to the chamber instead of hauling it out to
     * a surface vent.
     */
    public static boolean isInsideHive(HiveLocation location, LivingEntity entity) {
        var pos = entity.blockPosition();
        return location.structurePieceByChunk().containsKey(new ChunkPos(pos))
            && location.withinSlab(pos.getY());
    }
}
