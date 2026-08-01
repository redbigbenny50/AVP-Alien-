package com.alien.common.gameplay.entity.living.alien.ovomorph;

import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.data_sync.v1.DataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gameevent.vibrations.VibrationInfo;

import java.util.Objects;

public class HatchDesireManager {

    private static final int MAXIMUM_DESIRE_TO_HATCH = 100;

    private static final int HOST_VIBRATION_DESIRE_MULTIPLIER = 4;

    // A host PARKED in a host chamber is frozen (setNoAi + removeFreedom), so it emits NO vibrations - the
    // vibration-driven desire below can never fire for it, and an egg delivered in front of it just decays to zero
    // and never opens. A free host sitting still right next to the egg is the entire point of the host chamber, so
    // desire also builds on PROXIMITY.
    private static final double ADJACENT_HOST_RADIUS = 2.5;

    /** Desire per 20-tick cycle from an adjacent free host: maximum after ~5s beside one. */
    private static final int ADJACENT_HOST_DESIRE = 20;

    private final Ovomorph ovomorph;

    private final DataAccessor<Integer> desireToHatch;

    private VibrationInfo lastVibrationInfo;

    public HatchDesireManager(Ovomorph ovomorph) {
        this.ovomorph = ovomorph;
        this.desireToHatch = new DataAccessor<>(ovomorph, AlienDataSyncKeys.OVOMORPH_DESIRE_TO_HATCH.get());
    }

    public void tick() {
        handleVibration();

        if (ovomorph.tickCount % 20 == 0) {
            if (hasAdjacentFreeHost()) {
                addDesire(ADJACENT_HOST_DESIRE);
            } else {
                addDesire(-1);
            }
        }

        if (ovomorph.getHatchManager().isHatched()) {
            desireToHatch.reset();
        }
    }

    private void handleVibration() {
        var vibrationSystemManager = ovomorph.getVibrationSystemManager();
        var vibrationInfo = vibrationSystemManager.getVibrationData()
            .getCurrentVibration();

        if (vibrationInfo != null && !Objects.equals(vibrationInfo, lastVibrationInfo)) {
            var radius = vibrationSystemManager.getVibrationUser()
                .getListenerRadius();
            var sourceEntity = vibrationInfo.entity();

            if (
                sourceEntity != null && AlienPredicates.isFreeHost(ovomorph, sourceEntity) && ovomorph.getSensing()
                    .hasLineOfSight(sourceEntity)
            ) {
                var baseDesire = (int) Math.abs(radius - vibrationInfo.distance()) * HOST_VIBRATION_DESIRE_MULTIPLIER;

                var level = ovomorph.level();
                var blockPos = ovomorph.blockPosition();
                var brightness = level.getRawBrightness(blockPos, 0);
                var bonusFactor = 5.0;
                var clampedBrightness = Math.clamp(brightness, bonusFactor, 15);
                // At full brightness, the bonus is 3x for the egg being more likely to hatch.
                var brightnessBonus = (int) (clampedBrightness / bonusFactor);

                addDesire(baseDesire * brightnessBonus);
            }

            this.lastVibrationInfo = vibrationInfo;
        }
    }

    /**
     * A host within reach of this egg - e.g. one webbed into a host-chamber wall right in front of it, which is the
     * entire point of the host chamber and must keep working.
     * <p>
     * A host BEING CARRIED HOME is excluded. {@code isFreeHost} does not look at whether the hive has already claimed a
     * host, so a capture party hauling one back through its own corridors was setting off every egg it passed within
     * {@code ADJACENT_HOST_RADIUS} - desire builds at {@code ADJACENT_HOST_DESIRE} per 20-tick cycle, so roughly five
     * seconds of proximity is enough, and a drone walking a captive down a hall clears that beside egg after egg. The
     * result is huggers hatching the length of the hive for a host that was never theirs to take: it is already spoken
     * for, and it is going to be webbed in front of an egg of its own on arrival.
     * <p>
     * LINE OF SIGHT IS REQUIRED, matching the vibration path. Without it this test was purely a distance sphere, so a
     * captive webbed in a host chamber primed every egg within 2.5 blocks THROUGH THE WALLS - and egg chambers are
     * built around host chambers, so eggs one room over hatched on their own with no host anywhere they could reach.
     * The asymmetry made it worse: desire climbs 20 per cycle and decays only 1, so five seconds of a neighbour through
     * a wall beats a hundred seconds of quiet.
     */
    private boolean hasAdjacentFreeHost() {
        var box = ovomorph.getBoundingBox().inflate(ADJACENT_HOST_RADIUS);
        for (var candidate : ovomorph.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (isBeingCarriedHome(candidate)) {
                continue;
            }
            if (!ovomorph.getSensing().hasLineOfSight(candidate)) {
                continue;
            }
            if (AlienPredicates.isFreeHost(ovomorph, candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Riding the captor that caught it. Mirrors the carried-host half of {@code AlienPredicates.isCapturedHost}. */
    private static boolean isBeingCarriedHome(LivingEntity candidate) {
        return candidate.getVehicle() instanceof com.alien.common.gameplay.entity.living.alien.Alien captor
            && com.alien.common.gameplay.hive.party.HostCaptureTask.carriedHost(captor) == candidate;
    }

    public boolean wantsToHatch() {
        return desireToHatch.get() == MAXIMUM_DESIRE_TO_HATCH;
    }

    private void addDesire(int desireAmount) {
        desireToHatch.set(Math.clamp(desireToHatch.get() + desireAmount, 0, MAXIMUM_DESIRE_TO_HATCH));
    }
}
