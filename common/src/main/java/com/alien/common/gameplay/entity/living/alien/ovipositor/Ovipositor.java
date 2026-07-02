package com.alien.common.gameplay.entity.living.alien.ovipositor;

import com.alien.common.registry.tag.AlienDamageTypesTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class Ovipositor extends Mob {

    public static AttributeSupplier.Builder createOvipositorAttributes() {
        return createMobAttributes()
            .add(Attributes.MAX_HEALTH, 100)
            .add(Attributes.MOVEMENT_SPEED, 0);
    }

    public Ovipositor(EntityType<? extends Ovipositor> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float amount) {
        if (damageSource.is(AlienDamageTypesTags.DOES_NOT_HURT_ALIENS)) {
            return false;
        }

        return super.hurt(damageSource, amount);
    }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    protected final boolean canRide(@NotNull Entity vehicle) {
        return super.canRide(vehicle) && vehicle.getType().is(AlienEntityTypeTags.QUEENS);
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    protected final boolean canAddPassenger(@NotNull Entity passenger) {
        return false;
    }

    @Override
    protected void doPush(@NotNull Entity entity) {}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public int getAirSupply() {
        return Integer.MAX_VALUE;
    }

    /**
     * True when this is a captive queen's chained eggsack (grown while inhibited + fully bound), false for a normal
     * founding ovipositor. Lets {@code OvipositorManager} tell the two apart so a captured queen drops her founding
     * eggsack and swaps to the chained one. Persisted so the distinction survives reload.
     */
    private boolean chainedEggsack = false;

    public boolean isChainedEggsack() {
        return chainedEggsack;
    }

    public void setChainedEggsack(boolean value) {
        this.chainedEggsack = value;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putBoolean("ChainedEggsack", chainedEggsack);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        this.chainedEggsack = compoundTag.getBoolean("ChainedEggsack");
    }
}
