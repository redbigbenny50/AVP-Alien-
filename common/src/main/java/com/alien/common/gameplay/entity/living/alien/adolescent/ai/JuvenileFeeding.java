package com.alien.common.gameplay.entity.living.alien.adolescent.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The sound and mess of a juvenile eating something it killed.
 * <p>
 * Deliberately the SAME feedback {@code MoltFeeding.consume} plays when one eats a spent egg, because from the player's
 * side they are the same event - a young alien got a meal and grew a little. Two different eat noises for the same idea
 * would just read as two unrelated systems.
 * </p>
 * <p>
 * ⚠ NO {@code discard()} HERE, unlike the spent-remains path. That one removes the thing it ate; this one fires on a
 * KILL, where the corpse is already handled by the death pipeline and its drops belong to whoever else might want them.
 * Deleting the body would also swallow the loot silently.
 * </p>
 */
public final class JuvenileFeeding {

    private JuvenileFeeding() {}

    public static void playEatFeedback(LivingEntity feeder, Entity meal) {
        var level = feeder.level();

        level.playSound(
            null,
            feeder.getX(),
            feeder.getY(),
            feeder.getZ(),
            SoundEvents.GENERIC_EAT,
            SoundSource.HOSTILE,
            1.0F,
            0.7F
        );

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                ParticleTypes.ITEM_SLIME,
                meal.getX(),
                meal.getY() + 0.4,
                meal.getZ(),
                12,
                0.2,
                0.2,
                0.2,
                0.01
            );
        }
    }
}
