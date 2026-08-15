package com.alien.common.gameplay.entity.living.alien;

import com.alien.common.gameplay.effect.RadiationSicknessStatusEffect;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_human.RadiationCompat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * How irradiated eggs and huggers die: loudly.
 * <p>
 * The irradiated strain does not breed through hosts - it has no chestburster and no adolescent - so its egg and its
 * hugger are ORDNANCE rather than a nursery. The egg detonates when something damages it; the hugger detonates on the
 * face it reaches, instead of implanting. Either way the answer is the same blast, so it lives in one place.
 * <p>
 * Modelled on AVP: Human's irradiated grenade, with one deliberate difference: {@code ExplosionInteraction.NONE} rather
 * than {@code BLOCK}. A hive that cratered its own corridors every time an egg was disturbed would demolish itself, and
 * a weapon that rearranges the terrain is a very different thing from one that poisons it.
 * <p>
 * Dosing goes through the usual bridge - AVP: Human's shared exposure counter when that mod is present, our own
 * {@link RadiationSicknessStatusEffect} when it is not - so the blast means the same thing either way. Aliens are
 * skipped by the same rule claws and talons use, so a detonating egg does not gas the hive that laid it. Aberrants are
 * NOT skipped: they are the one strain radiation can touch.
 */
public final class IrradiatedDetonation {

    /** Matches the grenade. Large enough to clear a chamber, small enough not to reach the next one. */
    private static final float EXPLOSION_RADIUS = 3.0F;

    /** The lingering hazard the blast leaves behind, in blocks. */
    private static final float CLOUD_RADIUS = 5.0F;

    /** How long the cloud sits before it has shrunk to nothing. */
    private static final int CLOUD_DURATION_TICKS = 20 * 15;

    private IrradiatedDetonation() {
        throw new UnsupportedOperationException();
    }

    /** Blast, dose, and leave a cloud. Server side only; the caller is responsible for removing the entity. */
    public static void detonate(Alien source) {
        if (!(source.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        serverLevel.explode(
            source,
            source.getX(),
            source.getY(),
            source.getZ(),
            EXPLOSION_RADIUS,
            false,
            Level.ExplosionInteraction.NONE
        );

        doseEverythingCaught(source, serverLevel);
        leaveCloud(source, serverLevel);
    }

    /**
     * The explosion alone would only bruise things. The radiation is the actual payload, so it is applied directly to
     * everything in the blast rather than left to the cloud - something that walks out immediately should still have
     * been dosed.
     */
    private static void doseEverythingCaught(Alien source, net.minecraft.server.level.ServerLevel serverLevel) {
        var box = source.getBoundingBox().inflate(EXPLOSION_RADIUS);

        for (var caught : serverLevel.getEntitiesOfClass(LivingEntity.class, box, candidate -> candidate != source)) {
            irradiate(caught);
        }
    }

    /**
     * One dose, through whichever radiation system is actually present.
     * <p>
     * Public because the cloud needs the same decision, and because anything else the irradiated strain grows into a
     * weapon should poison the same way rather than inventing its own.
     */
    public static void irradiate(LivingEntity victim) {
        if (!canBeIrradiated(victim)) {
            return;
        }

        if (AVPHuman.MOD.isLoaded()) {
            RadiationCompat.addExposure(victim, RadiationCompat.EXPOSURE_PER_SICKNESS_LEVEL);
            return;
        }

        victim.addEffect(
            new MobEffectInstance(
                AlienMobEffects.getRadiationSicknessHolder(),
                RadiationSicknessStatusEffect.JELLY_DOSE_DURATION_TICKS,
                0
            )
        );
    }

    /**
     * The species is radiation-immune by decree - EXCEPT aberrants, the weak line, which is exactly why they cannot
     * convert to irradiated and why a blast like this kills them instead.
     */
    private static boolean canBeIrradiated(Entity victim) {
        return !(victim instanceof Alien alien)
            || alien.getVariant() == com.alien.common.model.alien.variant.AlienVariant.ABERRANT;
    }

    /** An ash cloud that shrinks to nothing over its life, so the ground stays hot for a while after the bang. */
    private static void leaveCloud(Alien source, net.minecraft.server.level.ServerLevel serverLevel) {
        var cloud = new AreaEffectCloud(serverLevel, source.getX(), source.getY(), source.getZ());
        cloud.setRadius(CLOUD_RADIUS);
        cloud.setDuration(CLOUD_DURATION_TICKS);
        cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
        cloud.setParticle(ParticleTypes.ASH);
        cloud.addEffect(
            new MobEffectInstance(
                AlienMobEffects.getRadiationSicknessHolder(),
                RadiationSicknessStatusEffect.JELLY_DOSE_DURATION_TICKS,
                0
            )
        );

        serverLevel.addFreshEntity(cloud);
    }
}
