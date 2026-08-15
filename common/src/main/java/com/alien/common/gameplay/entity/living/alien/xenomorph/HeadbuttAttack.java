package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * The crusher's HEADBUTT - medium damage and a throw.
 * <p>
 * [stated] "the headbutt attack is meant to be a high knockback attack. it does medium damage and it throws the player
 * back and up."
 * </p>
 * <p>
 * Kept separate from {@link BackhandAttack} rather than folded into it because the two want opposite things. A backhand
 * SHOVES: mostly horizontal, with just enough lift to stop ground friction eating the impulse. A headbutt THROWS: the
 * vertical component is the whole character of the move, and the caller sets it explicitly.
 * </p>
 * <p>
 * ⚠ THE LIFT IS NOT A TUNING DETAIL, IT IS THE ATTACK. Horizontal knockback dies in about a block once a target is
 * walking on the ground; almost all of the distance in a headbutt comes from the airtime the lift buys.
 * </p>
 */
public final class HeadbuttAttack {

    private HeadbuttAttack() {}

    /**
     * @param id                attack id, e.g. {@code crusher_headbutt}
     * @param damageFraction    multiplier on the caste's OWN {@code ATTACK_DAMAGE}
     * @param knockbackStrength horizontal impulse in blocks/tick
     * @param verticalBoost     upward impulse - the throw
     * @param durationInTicks   swing length, matched to the authored clip
     */
    public static AttackType create(
        String id,
        float damageFraction,
        double knockbackStrength,
        double verticalBoost,
        int durationInTicks
    ) {
        return AttackType.builder(id)
            .requiresHead()
            .defaultDurationInTicks(durationInTicks)
            .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
            .damageApplicator(
                (xenomorph, target) -> apply(xenomorph, target, damageFraction, knockbackStrength, verticalBoost)
            )
            .build();
    }

    private static void apply(
        Xenomorph xenomorph,
        LivingEntity target,
        float damageFraction,
        double knockback,
        double verticalBoost
    ) {
        if (!ScaledDamage.canReach(xenomorph, target)) {
            return;
        }

        xenomorph.swing(InteractionHand.MAIN_HAND);

        if (!ScaledDamage.hurtScaled(xenomorph, target, damageFraction)) {
            return; // blocked, immune, or still in i-frames - no throw either
        }

        var dx = target.getX() - xenomorph.getX();
        var dz = target.getZ() - xenomorph.getZ();
        var length = Math.sqrt(dx * dx + dz * dz);

        if (length < 1.0E-4) {
            // Exactly overlapping - throw along the crusher's facing instead of dividing by zero.
            var yawRadians = xenomorph.getYRot() * Mth.DEG_TO_RAD;
            dx = -Mth.sin(yawRadians);
            dz = Mth.cos(yawRadians);
            length = 1.0;
        }

        // ⚠ NEGATED DELIBERATELY: LivingEntity.knockback SUBTRACTS the direction it is given, so the away-vector has
        // to be negated to throw the target away rather than drag it in. Same convention as RazorClawSweepAttack.
        target.knockback(knockback, -(dx / length), -(dz / length));

        // ⚠ SET, not add, on the Y axis. A target already falling would otherwise have its downward momentum merely
        // reduced and barely leave the ground - the throw has to overwrite what it was doing.
        var motion = target.getDeltaMovement();
        target.setDeltaMovement(motion.x, Math.max(motion.y, 0.0) + verticalBoost, motion.z);
        target.hurtMarked = true; // server-side shove; without this the client never sees the player move
    }
}
