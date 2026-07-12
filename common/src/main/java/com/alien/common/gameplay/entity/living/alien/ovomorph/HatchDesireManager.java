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

    /** A free host within reach of this egg - e.g. one webbed into a host-chamber wall right in front of it. */
    private boolean hasAdjacentFreeHost() {
        var box = ovomorph.getBoundingBox().inflate(ADJACENT_HOST_RADIUS);
        for (var candidate : ovomorph.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (AlienPredicates.isFreeHost(ovomorph, candidate)) {
                return true;
            }
        }
        return false;
    }

    public boolean wantsToHatch() {
        return desireToHatch.get() == MAXIMUM_DESIRE_TO_HATCH;
    }

    private void addDesire(int desireAmount) {
        desireToHatch.set(Math.clamp(desireToHatch.get() + desireAmount, 0, MAXIMUM_DESIRE_TO_HATCH));
    }
}
