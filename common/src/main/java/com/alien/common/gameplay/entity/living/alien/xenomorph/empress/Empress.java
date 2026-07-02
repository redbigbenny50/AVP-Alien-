package com.alien.common.gameplay.entity.living.alien.xenomorph.empress;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.ai.EmpressGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class Empress extends Xenomorph implements GOAPUser<Empress>, EggLayer {

    public static final AttackType SWIPE_DOWN = AttackType.builder("empress_swipe_down")
        .requiresAnyArm()
        .defaultDurationInTicks(18)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BACKHAND = AttackType.builder("empress_backhand")
        .requiresAnyArm()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL_STRIKE = AttackType.builder("empress_tail_strike")
        .requiresTail()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.WIDE_TALL, Empress::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(SWIPE_DOWN)
                .addRegular(BACKHAND)
                .addRegular(TAIL_STRIKE)
                .build()
        )
        .parallelDigCount(4)
        .pushedByFluid(false)
        .build();

    public static AttributeSupplier.Builder createEmpressAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 16.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 16.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 2.5F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 10F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 0.9F);
    }

    private final EmpressAnimationDispatcher animationDispatcher;

    private final EmpressOvipositorManager empressOvipositorManager;

    private final EmpressData empressData;

    public Empress(EntityType<? extends Empress> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new EmpressAnimationDispatcher(this);
        this.empressOvipositorManager = new EmpressOvipositorManager(this);
        this.empressData = new EmpressData();
    }

    @Override
    public Agent.Builder<Empress> blib$applyGOAPAgentProperties(Agent.Builder<Empress> agentBuilder) {
        return EmpressGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Empress> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(isOnOvipositor() ? EmpressGOAP.OVIPOSITOR_GRAPH : EmpressGOAP.GRAPH);
    }

    private boolean isOnOvipositor() {
        return empressOvipositorManager != null && empressOvipositorManager.hasOvipositor();
    }

    @Override
    public void tick() {
        super.tick();
        empressOvipositorManager.tick();
        empressData.tick();
    }

    @Override
    protected boolean canEntityRideAlien(@NotNull Entity passenger) {
        return Objects.equals(passenger.getType(), AlienEntityTypes.OVIPOSITOR.get());
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (passenger.getType() == AlienEntityTypes.OVIPOSITOR.get()) {
            var relativePos = com.blib.api.common.entity.v1.EntityUtil.getRelativePosition(this, 3, 0.01, 5.25);
            callback.accept(passenger, relativePos.x, relativePos.y, relativePos.z);
            return;
        }

        super.positionRider(passenger, callback);
    }

    @Override
    public float maxUpStep() {
        return 2.5F;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return AlienSoundEvents.ENTITY_EMPRESS_IDLE.get();
    }

    @Override
    protected @NotNull SoundEvent getDeathSound() {
        return AlienSoundEvents.ENTITY_EMPRESS_DEATH.get();
    }

    @Override
    protected @NotNull SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return AlienSoundEvents.ENTITY_EMPRESS_HURT.get();
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        if (
            !empressOvipositorManager.hasOvipositor()
                || !entity.getType().is(AlienEntityTypeTags.ALIENS)
        ) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    public EmpressAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public EmpressOvipositorManager getEmpressOvipositorManager() {
        return empressOvipositorManager;
    }

    public EmpressData getEmpressData() {
        return empressData;
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float amount) {
        var wasHurt = super.hurt(damageSource, amount);
        if (wasHurt && !level().isClientSide) {
            empressOvipositorManager.abandonOvipositor();
        }
        return wasHurt;
    }

    @Override
    public void remove(@NotNull RemovalReason removalReason) {
        if (!level().isClientSide) {
            empressOvipositorManager.abandonOvipositor();
        }
        super.remove(removalReason);
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public boolean isEggLayCooldownReady() {
        return empressData.isEggLayCooldownReady();
    }

    @Override
    public void resetEggLayCooldown() {
        empressData.resetEggLayCooldown();
    }

    @Override
    public boolean hasOvipositor() {
        return empressOvipositorManager.hasOvipositor();
    }

    @Override
    public Vec3 getEggLayingPosition() {
        return empressOvipositorManager.getEggLayingPosition();
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        empressOvipositorManager.load(compoundTag);
        empressData.load(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        empressOvipositorManager.save(compoundTag);
        empressData.save(compoundTag);
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.EMPRESS.get();
            case NETHER -> AlienEntityTypes.NETHER_EMPRESS.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_EMPRESS.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_EMPRESS.get();
        };
    }
}
