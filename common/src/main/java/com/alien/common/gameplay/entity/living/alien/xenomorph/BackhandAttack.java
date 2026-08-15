package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * The BACKHAND - a shoving attack that trades damage for distance.
 * <p>
 * [stated] "the backhand attack is supposed to be a medium attack with a far knockback its meant to push an attacker
 * away by about 6-7 blocks... a praetorians back hand is light-medium damage with a 4-5 block knock back while a
 * predaliens is medium damage with a 6-7 block knock back."
 * </p>
 * <p>
 * Shared because the two castes differ ONLY in two numbers. Each passes its own damage fraction and knockback, so the
 * relative tiers stay visible in one place instead of drifting apart in two entity classes.
 * </p>
 */
public final class BackhandAttack {

    /**
     * Vertical lift added on top of the horizontal shove.
     * <p>
     * ⚠ THIS IS WHAT MAKES THE DISTANCE REACHABLE. Horizontal knockback bleeds off almost immediately once the target
     * is walking on the ground (ground friction ~0.6 per tick); airborne it only decays by the 0.91 air-drag factor. A
     * small lift buys the airtime the 4-7 block figures assume. Raise it and they sail; drop it to zero and even a big
     * horizontal impulse dies in about a block.
     * </p>
     */
    private static final double KNOCKBACK_VERTICAL_BOOST = 0.32;

    private BackhandAttack() {}

    /**
     * Builds a caste's backhand.
     *
     * @param id                attack id, e.g. {@code praetorian_backhand}
     * @param damageFraction    multiplier on the caste's OWN {@code ATTACK_DAMAGE} - the tier is relative to that
     *                          caste, so "light-medium" and "medium" mean different absolute numbers for a praetorian
     *                          (base 9) and a predalien (base 15)
     * @param knockbackStrength horizontal impulse in blocks/tick; roughly {@code strength x 11} blocks of travel with
     *                          the lift above
     * @param durationInTicks   swing length, matched to the authored clip
     */
    public static AttackType create(String id, float damageFraction, double knockbackStrength, int durationInTicks) {
        return AttackType.builder(id)
            .requiresAnyArm()
            .defaultDurationInTicks(durationInTicks)
            .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
            .damageApplicator((xenomorph, target) -> apply(xenomorph, target, damageFraction, knockbackStrength))
            .build();
    }

    private static void apply(Xenomorph xenomorph, LivingEntity target, float damageFraction, double knockback) {
        if (!ScaledDamage.canReach(xenomorph, target)) {
            return;
        }

        xenomorph.swing(InteractionHand.MAIN_HAND);

        if (!ScaledDamage.hurtScaled(xenomorph, target, damageFraction)) {
            return; // blocked, immune, or still in i-frames - no shove either
        }

        applyKnockback(xenomorph, target, knockback);
    }

    /**
     * Shoves the target directly away from the attacker.
     * <p>
     * ⚠ THE NEGATED VECTOR IS DELIBERATE. {@code LivingEntity.knockback} SUBTRACTS the direction it is given, so
     * passing the away-vector negated is what pushes the target away rather than dragging it in. Same convention as
     * {@code RazorClawSweepAttack.applyRadialKnockback}.
     * </p>
     */
    private static void applyKnockback(Xenomorph xenomorph, LivingEntity target, double knockbackStrength) {
        var dx = target.getX() - xenomorph.getX();
        var dz = target.getZ() - xenomorph.getZ();
        var length = Math.sqrt(dx * dx + dz * dz);

        if (length < 1.0E-4) {
            // Exactly overlapping - shove along the attacker's facing instead of dividing by zero.
            var yawRadians = xenomorph.getYRot() * Mth.DEG_TO_RAD;
            dx = -Mth.sin(yawRadians);
            dz = Mth.cos(yawRadians);
            length = 1.0;
        }

        target.knockback(knockbackStrength, -(dx / length), -(dz / length));
        target.setDeltaMovement(target.getDeltaMovement().add(0.0, KNOCKBACK_VERTICAL_BOOST, 0.0));
        target.hurtMarked = true; // the shove is server-side; without this the client never sees the player move
    }
}
