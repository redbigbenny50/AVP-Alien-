package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.swim;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;

public class SwimSensors {

    /**
     * The shallowest water that can ever count as swimmable, whatever the creature.
     * <p>
     * [stated] "it shouldnt be affected by a bucket or 1 block deep of water but it should absolutely try to swim if in
     * a possible water feature." A poured bucket and a one-deep puddle are below this by definition.
     * </p>
     */
    private static final double SHALLOW_WATER_DEPTH = 1.0;

    /**
     * Deep enough that this creature is swimming rather than wading.
     * <p>
     * !!! EVERY SWIM DECISION GOES THROUGH THIS, NOT isInWater(). isInWater() is true for a single wet block, so the
     * whole swim package used to fire on puddles: a xenomorph standing ankle-deep counted as stranded, stopped
     * wandering, and started looking for a shore it was already standing on.
     * </p>
     * <p>
     * SCALED BY THE CREATURE. Half its own height, floored at one block - so a runner needs more than a block, and a
     * praetorian at 3.98 tall needs about two before it stops walking. A tall xenomorph wading a stream is not swimming
     * and should not be handled as though it were.
     * </p>
     */
    public static boolean isSubmerged(Xenomorph xenomorph) {
        if (!xenomorph.isInWater()) {
            return false;
        }

        var depth = xenomorph.getFluidHeight(net.minecraft.tags.FluidTags.WATER);

        return depth > Math.max(SHALLOW_WATER_DEPTH, xenomorph.getBbHeight() * 0.5D);
    }

    public static final Sensor.Mono<Xenomorph, Boolean> NEEDS_WATER_TO_LAND_TRANSITION = Sensors.map(
        StateKey.sensed("needs_water_to_land_transition"),
        xenomorph -> {
            if (!isSubmerged(xenomorph)) {
                return false;
            }

            var target = xenomorph.getTarget();

            return target != null && !target.isInWater();
        }
    );

    /**
     * ⭐⭐ IN WATER WITH NOTHING TO CHASE. The trigger for {@code SwimToShoreAction}.
     * <p>
     * Deliberately the exact complement of {@link #NEEDS_WATER_TO_LAND_TRANSITION}: that one requires a target OUT of
     * the water, this one requires no target at all, so the combat crossing and the idle crossing can never both be
     * eligible and the planner never has to choose between them.
     * </p>
     */
    public static final Sensor.Mono<Xenomorph, Boolean> IS_STRANDED_IN_WATER = Sensors.map(
        StateKey.sensed("is_stranded_in_water"),
        xenomorph -> isSubmerged(xenomorph) && xenomorph.getTarget() == null
    );

    private SwimSensors() {
        throw new UnsupportedOperationException();
    }
}
