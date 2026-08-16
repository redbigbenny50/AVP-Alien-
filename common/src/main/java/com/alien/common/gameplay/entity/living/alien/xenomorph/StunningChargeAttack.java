package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * A HARD-HITTING HEADBUTT THAT STUNS.
 * <p>
 * [stated] "attack charge is basically a headbutt its a hard hitting attack that stuns a player for 15 seconds, it has
 * a 40s cool down before it can be used again."
 * </p>
 * <p>
 * ⚠ THE COOLDOWN IS NOT ENFORCED HERE - it is passed to {@code AttackType.cooldownInTicks}, which the existing
 * {@code AttackCooldownTracker} already honours per-entity through {@code XenomorphAttackConfig.selectRegular}. Rolling
 * a private timer would have duplicated a mechanism that works and would not have been consulted by the selector.
 * </p>
 * <p>
 * ⚠ THE STUN IS THE SAME EFFECT THE CRUSHER CHARGE USES ({@code AlienMobEffects.getStunnedHolder}), which zeroes
 * movement speed and, via {@code MixinLivingEntity_Stunned}, blocks AI. Fifteen seconds is a LONG time to hold a player
 * still - see the note in the readme.
 * </p>
 */
public final class StunningChargeAttack {

    private StunningChargeAttack() {}

    /**
     * @param id              attack id, e.g. {@code chrysalis_charge}
     * @param damageFraction  multiplier on the caste's OWN {@code ATTACK_DAMAGE} - above 1.0 for a heavy hit
     * @param stunTicks       how long the victim is held
     * @param cooldownInTicks how long before this attack may be chosen again
     * @param durationInTicks swing length, matched to the authored clip
     */
    public static AttackType create(
        String id,
        float damageFraction,
        int stunTicks,
        int cooldownInTicks,
        int durationInTicks
    ) {
        return AttackType.builder(id)
            .requiresHead()
            .defaultDurationInTicks(durationInTicks)
            .cooldownInTicks(cooldownInTicks)
            .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
            .damageApplicator((xenomorph, target) -> apply(xenomorph, target, damageFraction, stunTicks))
            .build();
    }

    private static void apply(Xenomorph xenomorph, LivingEntity target, float damageFraction, int stunTicks) {
        if (!ScaledDamage.canReach(xenomorph, target)) {
            return;
        }

        xenomorph.swing(InteractionHand.MAIN_HAND);

        // ⚠ NO HIT, NO STUN. Blocked, immune or still in i-frames must not hand out a 15-second hold - otherwise a
        // player who correctly raised a shield would be punished exactly as hard as one who did not.
        if (!ScaledDamage.hurtScaled(xenomorph, target, damageFraction)) {
            return;
        }

        target.addEffect(
            new MobEffectInstance(
                AlienMobEffects.getStunnedHolder(),
                stunTicks,
                0,
                false,
                false,
                true
            )
        );
    }
}
