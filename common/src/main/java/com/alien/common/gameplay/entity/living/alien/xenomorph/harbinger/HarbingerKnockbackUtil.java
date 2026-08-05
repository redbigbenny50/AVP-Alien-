package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Shared throw maths for the harbinger's three knockback attacks, so the slam, the backhand and the kick can never
 * drift apart in how they launch things.
 * <p>
 * The impulse pairs below were derived by simulating Minecraft's own motion rather than tuned by feel: horizontal
 * velocity decays 0.91 per tick, vertical is gravity 0.08 with 0.98 drag. Simulating to the tick the victim returns to
 * its starting height gives the landing distance directly.
 * <ul>
 * <li>1.30 / 0.70 - about 11.8 blocks over 17 airborne ticks (slam and kick: the "thrown across the room" hit)</li>
 * <li>0.95 / 0.45 - about 7.2 blocks over 11 airborne ticks (backhand: a flatter, faster swat)</li>
 * </ul>
 * Change these only against a fresh simulation - eyeballing a velocity gets the distance wrong by a lot, because the
 * drag makes distance strongly non-linear in the initial impulse.
 */
public final class HarbingerKnockbackUtil {

    /** ~11.8 blocks. */
    public static final double FAR_THROW_HORIZONTAL = 1.3;

    public static final double FAR_THROW_VERTICAL = 0.7;

    /** ~7.2 blocks. */
    public static final double SWAT_HORIZONTAL = 0.95;

    public static final double SWAT_VERTICAL = 0.45;

    private HarbingerKnockbackUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Launches {@code victim} directly away from {@code source}, horizontally outward with an upward arc.
     * <p>
     * Call this AFTER {@code hurt} - vanilla applies its own small knockback inside the damage path, and this
     * overwrites the motion outright so the distance is the tuned one rather than the sum of the two.
     */
    public static void throwOutward(LivingEntity source, LivingEntity victim, double horizontal, double vertical) {
        var resistance = Mth.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        var scale = 1.0 - resistance;

        if (scale <= 0.0) {
            return;
        }

        var deltaX = victim.getX() - source.getX();
        var deltaZ = victim.getZ() - source.getZ();
        var horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        // Standing exactly on top of the source: no outward direction exists, so pick one instead of dividing by zero.
        if (horizontalDistance < 1.0E-4) {
            var angle = source.getRandom().nextDouble() * Math.PI * 2;
            deltaX = Math.cos(angle);
            deltaZ = Math.sin(angle);
            horizontalDistance = 1.0;
        }

        launch(victim, deltaX / horizontalDistance, deltaZ / horizontalDistance, horizontal, vertical, scale);
    }

    /**
     * Launches {@code victim} along the direction {@code source} is facing rather than radially - the right shape for a
     * strike that drives a single target away from the attacker's front.
     */
    public static void throwForward(LivingEntity source, LivingEntity victim, double horizontal, double vertical) {
        var resistance = Mth.clamp(victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
        var scale = 1.0 - resistance;

        if (scale <= 0.0) {
            return;
        }

        var facing = source.getViewVector(1.0F);
        var facingX = facing.x;
        var facingZ = facing.z;
        var facingLength = Math.sqrt(facingX * facingX + facingZ * facingZ);

        // Looking straight up or down leaves no horizontal facing; fall back to driving them radially outward.
        if (facingLength < 1.0E-4) {
            throwOutward(source, victim, horizontal, vertical);
            return;
        }

        launch(victim, facingX / facingLength, facingZ / facingLength, horizontal, vertical, scale);
    }

    private static void launch(
        LivingEntity victim,
        double directionX,
        double directionZ,
        double horizontal,
        double vertical,
        double scale
    ) {
        victim.setDeltaMovement(
            directionX * horizontal * scale,
            vertical * scale,
            directionZ * horizontal * scale
        );

        // Without this the server never sends the new motion to a player - they would take the hit and stand still.
        victim.hurtMarked = true;
    }
}
