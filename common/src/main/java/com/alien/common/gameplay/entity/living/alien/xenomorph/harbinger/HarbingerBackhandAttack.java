package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The backhand - an ordinary swing that happens to throw whatever it lands on.
 * <p>
 * [stated] "like a normal attack but it has knockback not like the slam though its a weaker knockback and send them
 * about 7 blocks away. if a player or mob is up in its face with melee it would use that accosionally."
 * <p>
 * So it is a REGULAR attack, not a triggered one: it sits in the weighted swing rotation alongside the claw, bite and
 * tail. "Occasionally" comes from a short cooldown rather than a low weight - regular selection already skips attacks
 * whose cooldown is not ready, and dropping its weight would have meant re-weighting the three existing swings to
 * compensate. "Up in its face" is the activation condition: it only offers itself when something is inside arm's reach,
 * which is also where a knockback is worth spending.
 * <p>
 * Timing from {@code attack.backhand} (1.0s = 20 ticks): the right shoulder cocks across the body by 0.25s, then sweeps
 * out to full extension at 0.5s. Contact is that sweep, so the damage lands at tick 10.
 */
public final class HarbingerBackhandAttack {

    /** How close counts as "up in its face". Roughly her own reach - a target further out gets a normal swing. */
    private static final double IN_YOUR_FACE_RANGE_IN_BLOCKS = 4.0;

    /** Not a special, just paced - about one backhand every 6 seconds at most. */
    private static final int COOLDOWN_IN_TICKS = 6 * 20;

    /** Weaker than the slam, as asked - a shove rather than a crushing blow. */
    private static final float DAMAGE_MULTIPLIER = 0.75F;

    public static final AttackType ATTACK = AttackType.builder("harbinger_backhand")
        .requiresAnyArm()
        .defaultDurationInTicks(HarbingerAnimationRefs.BACKHAND_DURATION_TICKS)
        .damageThresholdPercent(0.5F)
        .cooldownInTicks(COOLDOWN_IN_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .activationCondition(HarbingerBackhandAttack::isTargetInItsFace)
        .damageApplicator(HarbingerBackhandAttack::swat)
        .build();

    private HarbingerBackhandAttack() {
        throw new UnsupportedOperationException();
    }

    private static boolean isTargetInItsFace(Xenomorph xenomorph) {
        var target = xenomorph.getTarget();

        return target != null
            && target.isAlive()
            && xenomorph.distanceToSqr(target) <= IN_YOUR_FACE_RANGE_IN_BLOCKS * IN_YOUR_FACE_RANGE_IN_BLOCKS;
    }

    private static void swat(Xenomorph xenomorph, LivingEntity target) {
        // Re-checked at the contact frame rather than trusted from the wind-up: half a second is plenty of time for
        // the target to have walked out of reach, and a backhand that connects with empty air should not throw.
        if (xenomorph.distanceToSqr(target) > IN_YOUR_FACE_RANGE_IN_BLOCKS * IN_YOUR_FACE_RANGE_IN_BLOCKS) {
            return;
        }

        var damageSource = xenomorph.damageSources().source(AlienDamageTypeKeys.HARBINGER_BACKHAND, xenomorph);

        if (target.isInvulnerableTo(damageSource)) {
            return;
        }

        target.hurt(damageSource, (float) xenomorph.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER);

        // Swept away from her, not along her facing: the whole point of a backhand is that it clears whatever has
        // crowded in, wherever it crowded in from.
        HarbingerKnockbackUtil.throwOutward(
            xenomorph,
            target,
            HarbingerKnockbackUtil.SWAT_HORIZONTAL,
            HarbingerKnockbackUtil.SWAT_VERTICAL
        );
    }
}
