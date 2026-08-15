package com.alien.common.gameplay.entity.projectile;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienParticleTypes;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;

public class AcidSpit extends ThrowableProjectile {

    /**
     * Acid does not burn - matching {@code Acid}, which overrides this the same way.
     * <p>
     * The last entity in the mod that did not extend {@code Alien} and so inherited none of its fire immunity. A thrown
     * projectile is unlikely to meet fire in its short life, but a nether spitter firing across lava is exactly the
     * case where it would, and there is no reason for the shot to be more flammable than the thing that spat it.
     */
    @Override
    public boolean fireImmune() {
        return true;
    }

    private static final int MAX_LIFETIME_IN_TICKS = 60;

    private static final float DAMAGE = 6.0F;

    private static final int ACID_MULTIPLIER_ON_IMPACT = 2;

    private static final EntityDataAccessor<Boolean> IS_NETHER_AFFLICTED = SynchedEntityData.defineId(
        AcidSpit.class,
        EntityDataSerializers.BOOLEAN
    );

    private static final EntityDataAccessor<Boolean> IS_IRRADIATED = SynchedEntityData.defineId(
        AcidSpit.class,
        EntityDataSerializers.BOOLEAN
    );

    public AcidSpit(EntityType<? extends AcidSpit> entityType, Level level) {
        super(entityType, level);
    }

    public AcidSpit(LivingEntity owner, Level level) {
        super(AlienEntityTypes.ACID_SPIT.get(), owner, level);

        if (owner instanceof Alien alien) {
            setVariant(alien.getVariant());
        }
    }

    public AcidSpit(LivingEntity owner, Level level, AlienVariant variant) {
        this(owner, level);
        setVariant(variant);
    }

    public void setVariant(AlienVariant variant) {
        entityData.set(IS_NETHER_AFFLICTED, variant == AlienVariant.NETHER);
        entityData.set(IS_IRRADIATED, variant == AlienVariant.IRRADIATED);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(IS_NETHER_AFFLICTED, false);
        builder.define(IS_IRRADIATED, false);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            spawnTrailParticles();
        }

        if (!level().isClientSide && tickCount > MAX_LIFETIME_IN_TICKS) {
            discard();
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);

        if (level().isClientSide) {
            return;
        }

        var target = result.getEntity();
        var registry = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        var damageSource = new net.minecraft.world.damagesource.DamageSource(
            registry.getHolderOrThrow(AlienDamageTypeKeys.ACID_SPIT),
            this,
            getOwnerAsLiving()
        );

        target.hurt(damageSource, DAMAGE);
        spawnAcidAtPosition(target.position());
        discard();
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);

        if (level().isClientSide) {
            return;
        }

        spawnAcidAtPosition(result.getLocation());
        discard();
    }

    private void spawnAcidAtPosition(net.minecraft.world.phys.Vec3 position) {
        var acidEntityType = AlienEntityTypes.ACID.get();
        var acid = acidEntityType.create(level());

        if (acid == null) {
            return;
        }

        acid.setNetherAfflicted(entityData.get(IS_NETHER_AFFLICTED));
        acid.setIrradiated(entityData.get(IS_IRRADIATED));
        acid.moveTo(position.x, position.y, position.z, 0, 0);
        acid.setMultiplier(ACID_MULTIPLIER_ON_IMPACT);
        level().addFreshEntity(acid);
    }

    private static final int TRAIL_PARTICLE_COUNT = 8;

    private static final double PARTICLE_SPREAD = 0.15;

    private static final double PARTICLE_MOTION_SCALE = 0.02;

    private void spawnTrailParticles() {
        var particleType = getAcidParticleType();
        var motionX = getDeltaMovement().x * PARTICLE_MOTION_SCALE;
        var motionY = getDeltaMovement().y * PARTICLE_MOTION_SCALE;
        var motionZ = getDeltaMovement().z * PARTICLE_MOTION_SCALE;

        var prevX = xOld;
        var prevY = yOld;
        var prevZ = zOld;

        for (var i = 0; i < TRAIL_PARTICLE_COUNT; i++) {
            var fraction = (double) i / TRAIL_PARTICLE_COUNT;
            var interpX = prevX + (getX() - prevX) * fraction;
            var interpY = prevY + (getY() - prevY) * fraction;
            var interpZ = prevZ + (getZ() - prevZ) * fraction;

            level().addParticle(
                particleType,
                interpX + (random.nextDouble() - 0.5) * PARTICLE_SPREAD,
                interpY + (random.nextDouble() - 0.5) * PARTICLE_SPREAD,
                interpZ + (random.nextDouble() - 0.5) * PARTICLE_SPREAD,
                motionX,
                motionY,
                motionZ
            );
        }
    }

    private SimpleParticleType getAcidParticleType() {
        if (entityData.get(IS_IRRADIATED)) {
            return AlienParticleTypes.IRRADIATED_ACID.get();
        }

        if (entityData.get(IS_NETHER_AFFLICTED)) {
            return AlienParticleTypes.BLUE_ACID.get();
        }

        return AlienParticleTypes.ACID.get();
    }

    private LivingEntity getOwnerAsLiving() {
        var owner = getOwner();
        return owner instanceof LivingEntity living ? living : null;
    }
}
