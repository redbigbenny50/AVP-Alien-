package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The kick - her hardest single blow.
 * <p>
 * [stated] "a hard hitting knockback a bit more damage that slam and knockback about the same. has a 30s cooldown." So:
 * more damage than the ground slam, the same far throw, and a cooldown half again as long.
 * <p>
 * Deliberately SINGLE TARGET, unlike the slam. The slam is the crowd answer and is radial; a kick is one boot going one
 * direction, so it punts the thing she is aimed at and drives it along her facing rather than radially outward. That
 * also keeps the two specials distinct in play - if the kick swept a cone it would just be a cheaper slam.
 * <p>
 * Timing from {@code attack.kick} (0.7917s = 16 ticks): the knee chambers by 0.33s, the leg drives out through
 * 0.42-0.58s, and the root twists through the strike at 0.46s. Contact lands at tick 10.
 */
public final class HarbingerKickAttack {

    private static final int COOLDOWN_IN_TICKS = 30 * 20;

    /** A bit more than the slam's 1.25x, as asked. */
    private static final float DAMAGE_MULTIPLIER = 1.6F;

    /** Contact frame: 0.5s of a 0.7917s clip. */
    private static final float DAMAGE_POINT_PERCENT = 10F / HarbingerAnimationRefs.KICK_DURATION_TICKS;

    /** She has to still be able to reach it when the boot arrives. */
    private static final double KICK_REACH_IN_BLOCKS = 4.5;

    public static final AttackType ATTACK = AttackType.builder("harbinger_kick")
        .requiresAllLegs()
        .defaultDurationInTicks(HarbingerAnimationRefs.KICK_DURATION_TICKS)
        .damageThresholdPercent(DAMAGE_POINT_PERCENT)
        .cooldownInTicks(COOLDOWN_IN_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .damageApplicator(HarbingerKickAttack::punt)
        .build();

    private HarbingerKickAttack() {
        throw new UnsupportedOperationException();
    }

    private static void punt(Xenomorph xenomorph, LivingEntity target) {
        if (xenomorph.distanceToSqr(target) > KICK_REACH_IN_BLOCKS * KICK_REACH_IN_BLOCKS) {
            return;
        }

        var damageSource = xenomorph.damageSources().source(AlienDamageTypeKeys.HARBINGER_KICK, xenomorph);

        if (target.isInvulnerableTo(damageSource)) {
            return;
        }

        target.hurt(damageSource, (float) xenomorph.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER);

        // Forward, along her facing - a boot drives things the way it is pointing.
        HarbingerKnockbackUtil.throwForward(
            xenomorph,
            target,
            HarbingerKnockbackUtil.FAR_THROW_HORIZONTAL,
            HarbingerKnockbackUtil.FAR_THROW_VERTICAL
        );

        if (xenomorph.level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                SoundEvents.WARDEN_ATTACK_IMPACT,
                SoundSource.HOSTILE,
                1.6F,
                0.7F
            );
        }
    }
}
