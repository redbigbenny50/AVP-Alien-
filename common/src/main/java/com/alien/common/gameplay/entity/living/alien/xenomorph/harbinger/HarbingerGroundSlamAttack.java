package com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger;

import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackExecutor;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.DamageApplicator;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.alien.common.util.AlienPredicates;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The harbinger's two-armed ground slam - a triggered crowd-breaker, not part of the regular swing rotation.
 * <p>
 * Timings are read off {@code attack.groundslam} rather than guessed. The 2.0s clip coils from 0 to 0.84s, raises the
 * arms to full extension by ~1.05s, drives them down, and lands at <b>1.4035s</b> - the frame where the lower body
 * snaps from -12.5 to +67.2 degrees and drops 4 units while the arms bottom out. That is tick 28 of 40. The clip then
 * holds the settle until 1.68s, which is the window the dust ring expands through.
 * <p>
 * She commits: the whole 40 ticks play out whether or not anyone is still standing in the ring afterwards.
 */
public final class HarbingerGroundSlamAttack {

    /** Radius of the shockwave. Everything hostile inside it is hit, regardless of which way she is facing. */
    public static final double SLAM_RADIUS_IN_BLOCKS = 6.0;

    /**
     * A shockwave is wasted on one enemy - she has a claw for that. Below this count the attack simply reports itself
     * unusable and the planner never picks it.
     */
    private static final int MINIMUM_ENEMIES_TO_SLAM = 2;

    /** Vertical reach. This is a ground shockwave, so something on a roof two blocks up is untouched. */
    private static final double SLAM_VERTICAL_REACH_IN_BLOCKS = 3.0;

    private static final int COOLDOWN_IN_TICKS = 20 * 20;

    /** Solid, not a finisher: a bit more than a regular swing, paid for with a 20 second cooldown. */
    private static final float DAMAGE_MULTIPLIER = 1.25F;

    /** Impact frame: 1.4035s of a 2.0s clip. */
    private static final int IMPACT_TICK = 28;

    /** Dust ring expands across the clip's settle window (1.40s to 1.68s). */
    private static final int RING_DURATION_IN_TICKS = 6;

    private static final int RING_POINTS = 40;

    public static final AttackType ATTACK = AttackType.builder("harbinger_ground_slam")
        .requiresBothArms()
        .requiresAllLegs()
        .defaultDurationInTicks(HarbingerAnimationRefs.GROUND_SLAM_DURATION_TICKS)
        .damageThresholdPercent(0F)
        .cooldownInTicks(COOLDOWN_IN_TICKS)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .activationCondition(HarbingerGroundSlamAttack::hasCrowdWorthSlamming)
        .damageApplicator(DamageApplicator.NOOP)
        .executorFactory(Executor::new)
        .build();

    private HarbingerGroundSlamAttack() {
        throw new UnsupportedOperationException();
    }

    /**
     * The trigger condition: two or more things she would actually fight, standing inside the ring. Evaluated only
     * after the triggered-attack sensor has already cleared the cooldown and line-of-sight checks, so the entity query
     * does not run on a harbinger that could not slam anyway.
     */
    private static boolean hasCrowdWorthSlamming(Xenomorph xenomorph) {
        return enemiesInRange(xenomorph).size() >= MINIMUM_ENEMIES_TO_SLAM;
    }

    private static List<LivingEntity> enemiesInRange(Xenomorph xenomorph) {
        var searchBox = xenomorph.getBoundingBox()
            .inflate(SLAM_RADIUS_IN_BLOCKS, SLAM_VERTICAL_REACH_IN_BLOCKS, SLAM_RADIUS_IN_BLOCKS);

        return xenomorph.level()
            .getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                candidate -> candidate != xenomorph
                    && candidate.isAlive()
                    && isWithinShockwave(xenomorph, candidate)
                    && AlienPredicates.canTarget(xenomorph, candidate)
            );
    }

    /**
     * A flat disc rather than a sphere - the force travels along the ground, so reach is measured horizontally and
     * height is only a cutoff.
     */
    private static boolean isWithinShockwave(Xenomorph xenomorph, LivingEntity candidate) {
        var deltaX = candidate.getX() - xenomorph.getX();
        var deltaZ = candidate.getZ() - xenomorph.getZ();
        var reach = SLAM_RADIUS_IN_BLOCKS + candidate.getBbWidth() * 0.5;

        if (deltaX * deltaX + deltaZ * deltaZ > reach * reach) {
            return false;
        }

        return Math.abs(candidate.getY() - xenomorph.getY()) <= SLAM_VERTICAL_REACH_IN_BLOCKS;
    }

    private static void slam(Xenomorph xenomorph) {
        var damageSource = xenomorph.damageSources().source(AlienDamageTypeKeys.HARBINGER_SLAM, xenomorph);
        var damage = (float) xenomorph.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER;

        for (var victim : enemiesInRange(xenomorph)) {
            if (victim.isInvulnerableTo(damageSource)) {
                continue;
            }

            victim.hurt(damageSource, damage);

            // Radially outward - a shockwave scatters the ring, it does not punch everyone the same way she faces.
            HarbingerKnockbackUtil.throwOutward(
                xenomorph,
                victim,
                HarbingerKnockbackUtil.FAR_THROW_HORIZONTAL,
                HarbingerKnockbackUtil.FAR_THROW_VERTICAL
            );
        }
    }

    private static void playImpactEffects(ServerLevel level, Xenomorph xenomorph) {
        var x = xenomorph.getX();
        var y = xenomorph.getY();
        var z = xenomorph.getZ();

        level.playSound(null, x, y, z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.HOSTILE, 2.5F, 0.5F);
        level.playSound(null, x, y, z, SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 2.0F, 0.6F);

        // Debris torn out of whatever she actually slammed - stone, sand, resin - so the burst matches the floor.
        var groundState = level.getBlockState(xenomorph.blockPosition().below());

        if (!groundState.isAir()) {
            level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, groundState),
                x,
                y + 0.1,
                z,
                140,
                SLAM_RADIUS_IN_BLOCKS * 0.35,
                0.1,
                SLAM_RADIUS_IN_BLOCKS * 0.35,
                0.4
            );
        }

        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.4, z, 3, 0.6, 0.1, 0.6, 0.0);
    }

    /**
     * Expanding dust ring. Drawn once per tick across the settle window so the wave visibly travels outward to the edge
     * of the damage radius instead of appearing all at once - it doubles as an honest tell of how far the shockwave
     * actually reached.
     */
    private static void drawExpandingRing(ServerLevel level, Xenomorph xenomorph, int ticksSinceImpact) {
        var progress = (ticksSinceImpact + 1) / (double) RING_DURATION_IN_TICKS;
        var radius = SLAM_RADIUS_IN_BLOCKS * progress;
        var y = xenomorph.getY() + 0.15;

        for (var point = 0; point < RING_POINTS; point++) {
            var angle = Math.PI * 2 * point / RING_POINTS;

            level.sendParticles(
                ParticleTypes.CLOUD,
                xenomorph.getX() + Math.cos(angle) * radius,
                y,
                xenomorph.getZ() + Math.sin(angle) * radius,
                1,
                0.0,
                0.0,
                0.0,
                0.02
            );
        }
    }

    /**
     * Single-stage executor. The clip carries its own windup, so unlike the ravager's cleave there is no separate
     * windup {@link AttackType} to transition into - this just counts ticks, fires the shockwave on the impact frame,
     * and trails the ring behind it.
     */
    public static class Executor implements AttackExecutor {

        private int elapsedTicks;

        private boolean impactDealt;

        @Override
        public int totalDurationInTicks(AttackType attack) {
            return HarbingerAnimationRefs.GROUND_SLAM_DURATION_TICKS;
        }

        @Override
        public void onStart(Xenomorph entity, AttackType attack, @Nullable LivingEntity target) {
            this.elapsedTicks = 0;
            this.impactDealt = false;
        }

        @Override
        public boolean onTick(Xenomorph entity, AttackType attack) {
            // She plants both feet for the wind-up; letting the navigation drag her forward would slide the whole
            // slam off the crowd it was aimed at.
            entity.getNavigation().stop();

            if (!impactDealt && elapsedTicks >= IMPACT_TICK) {
                slam(entity);
                impactDealt = true;

                if (entity.level() instanceof ServerLevel serverLevel) {
                    playImpactEffects(serverLevel, entity);
                }
            }

            if (impactDealt && entity.level() instanceof ServerLevel serverLevel) {
                var ticksSinceImpact = elapsedTicks - IMPACT_TICK;

                if (ticksSinceImpact < RING_DURATION_IN_TICKS) {
                    drawExpandingRing(serverLevel, entity, ticksSinceImpact);
                }
            }

            elapsedTicks++;

            return elapsedTicks < HarbingerAnimationRefs.GROUND_SLAM_DURATION_TICKS;
        }
    }
}
