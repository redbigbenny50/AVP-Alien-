package com.alien.common.gameplay.entity.acid;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.data_sync.v1.model.DataUser;
import com.blib.api.common.physics.v1.GravityUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class Acid extends Entity implements DataUser {

    public static final int MAX_MULTIPLIER = 5;

    private static final int DEFAULT_MAX_LIFE_IN_TICKS = 20 * 20; // 20 seconds.

    private static final int MIN_TICKS_UNTIL_PARTICLES = 5;

    private static final String IS_NETHER_AFFLICTED_KEY = "isNetherAfflicted";

    private static final String IS_IRRADIATED_KEY = "isIrradiated";

    private static final EntityDataAccessor<Boolean> IS_NETHER_AFFLICTED = SynchedEntityData.defineId(
        Acid.class,
        EntityDataSerializers.BOOLEAN
    );

    public static final EntityDataAccessor<Boolean> IS_IRRADIATED = SynchedEntityData.defineId(
        Acid.class,
        EntityDataSerializers.BOOLEAN
    );

    public final DataAccessor<Integer> multiplier;

    public final DataAccessor<Integer> tickCountForCurrentMultiplier;

    private int particleTickCounter = 0;

    public Acid(EntityType<? extends Entity> entityType, Level level) {
        super(entityType, level);

        this.multiplier = new DataAccessor<>(this, AlienDataSyncKeys.ACID_MULTIPLIER.get());
        this.tickCountForCurrentMultiplier = new DataAccessor<>(this, AlienDataSyncKeys.ACID_TICK_COUNT_FOR_MULTIPLIER.get());

        setNoGravity(false);
        refreshDimensions();

        multiplier.onChange($ -> refreshDimensions());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(IS_NETHER_AFFLICTED, false);
        builder.define(IS_IRRADIATED, false);
    }

    @Override
    public void tick() {
        super.tick();

        var level = level();

        // Oct 6 - profiler v3 laps; free while no session runs.
        var perfLap = com.blib.api.common.perf.v1.BLibPerf.start();

        if (!level.isClientSide && !horizontalCollision && !isRestingOnSupport(level)) {
            GravityUtil.apply(this);
        }

        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "acid.gravity", perfLap);
        AcidBlockDamageUtil.damageBlocks(this);
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "acid.blocks", perfLap);
        AcidEntityDamageUtil.damageEntities(this);
        com.blib.api.common.perf.v1.BLibPerf.lap(this, "acid.entities+merge", perfLap);

        particleTickCounter += getMultiplier();

        createParticlesAndSounds(level);

        if (!level.isClientSide) {
            // Acid disappears twice as fast when in water.
            var increment = getMultiplier() * (isInWater() ? 2 : 1);
            tickCountForCurrentMultiplier.set(tickCountForCurrentMultiplier.get() + increment);

            if (tickCountForCurrentMultiplier.get() > DEFAULT_MAX_LIFE_IN_TICKS) {
                decreaseMultiplier();
                tickCountForCurrentMultiplier.reset();
            }

            if (getMultiplier() == 0) {
                kill();
            }
        }
    }

    /**
     * ⭐ Oct 6 - A POOL LYING ON THE GROUND DOES NOT RE-RUN ITS FALL. GravityUtil moves the entity down every tick and
     * lets collision put it back - a full collision sweep each tick for a pool that has not moved since it landed. That
     * was 7.4 us per pool per tick by /blib perf (0.2 ms with 147 pools in a fight). Resting means: on the ground, no
     * upward motion, and something non-air directly under its centre - checked every tick with one block read, so when
     * the acid eats through the block it sits on (or anything else removes it) the next tick finds air and it falls.
     */
    private boolean isRestingOnSupport(Level level) {
        if (!onGround() || getDeltaMovement().y > 0.0) {
            return false;
        }

        var below = net.minecraft.core.BlockPos.containing(getX(), getY() - 0.05, getZ());

        // Collision, not "not air": grass, flowers and fluids are not air but hold nothing up.
        return !isInWater() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    public boolean isNetherAfflicted() {
        return entityData.get(IS_NETHER_AFFLICTED);
    }

    public void setNetherAfflicted(boolean isNetherAfflicted) {
        entityData.set(IS_NETHER_AFFLICTED, isNetherAfflicted);
    }

    public boolean isIrradiated() {
        return entityData.get(IS_IRRADIATED);
    }

    public void setIrradiated(boolean isIrradiated) {
        entityData.set(IS_IRRADIATED, isIrradiated);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        setNetherAfflicted(compoundTag.getBoolean(IS_NETHER_AFFLICTED_KEY));
        setIrradiated(compoundTag.getBoolean(IS_IRRADIATED_KEY));
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        compoundTag.putBoolean(IS_NETHER_AFFLICTED_KEY, isNetherAfflicted());
        compoundTag.putBoolean(IS_IRRADIATED_KEY, isIrradiated());
    }

    public void decreaseMultiplier() {
        this.setMultiplier(this.getMultiplier() - 1);
    }

    private void createParticlesAndSounds(Level level) {
        // Both particles and fizzing sounds should only play client-side.
        if (!level.isClientSide)
            return;

        if (particleTickCounter < MIN_TICKS_UNTIL_PARTICLES) {
            return;
        }

        particleTickCounter = 0;

        var alienVariantType = getVariantType();

        for (int i = 0; i < getMultiplier(); i++) {
            if (isInWater()) {
                level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_COLUMN_UP, getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0, 0);
            }

            level.addAlwaysVisibleParticle(
                alienVariantType.acidParticleType().get(),
                getRandomX(0.5),
                getRandomY(),
                getRandomZ(0.5),
                0,
                0,
                0
            );
        }
    }

    /**
     * The strain this acid came from, for its particles. Irradiated and nether acid carry their own flags; anything
     * else (including aberrant blood, which has no acid particle of its own) is drawn as normal acid. This was the
     * deprecated {@code AlienVariantTypes.getFor(Acid)} - moved here, its only caller, and the overload removed.
     */
    public com.alien.common.model.alien.variant.AlienVariantType getVariantType() {
        if (isIrradiated()) {
            return AlienVariantTypes.IRRADIATED;
        } else if (isNetherAfflicted()) {
            return AlienVariantTypes.NETHER;
        }

        return AlienVariantTypes.NORMAL;
    }

    public int getMultiplier() {
        return multiplier.get();
    }

    public void setMultiplier(int multiplier) {
        this.multiplier.set(Mth.clamp(multiplier, 0, MAX_MULTIPLIER));
        tickCountForCurrentMultiplier.reset();
        refreshDimensions();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        var originalDimensions = super.getDimensions(pose);
        var originalWidth = originalDimensions.width();
        var maxScale = 1F / originalWidth;
        var scaleStep = Mth.map(getMultiplier(), 0, MAX_MULTIPLIER, 1F, maxScale);
        return originalDimensions.scale(scaleStep, 1);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    public void age() {
        tickCountForCurrentMultiplier.set(tickCountForCurrentMultiplier.get() + getMultiplier());
    }
}
