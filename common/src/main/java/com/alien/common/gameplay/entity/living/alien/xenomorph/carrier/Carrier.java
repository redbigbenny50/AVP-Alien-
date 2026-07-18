package com.alien.common.gameplay.entity.living.alien.xenomorph.carrier;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.parasite.facehugger.Facehugger;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.ai.CarrierGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Carrier extends Xenomorph implements GOAPUser<Carrier> {

    public static final AttackType CLAW = AttackType.builder("carrier_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("carrier_bite")
        .requiresHead()
        .defaultDurationInTicks(8)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("carrier_tail")
        .requiresTail()
        .defaultDurationInTicks(12)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType THROW = AttackType.builder("carrier_throw")
        .requiresAnyArm()
        .defaultDurationInTicks(20)
        .build();

    public static final AttackType SCREAM = AttackType.builder("carrier_scream")
        .requiresHead()
        .defaultDurationInTicks(35)
        .build();

    public static AttributeSupplier.Builder createCarrierAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 8.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 12.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.4F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.7f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 4F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    private final CarrierAnimationDispatcher animationDispatcher;

    private final CarrierData carrierData;

    public Carrier(EntityType<? extends Carrier> entityType, Level level) {
        super(
            entityType,
            level,
            XenomorphConfig.builder(XenomorphPathConfig.LARGE, Carrier::getType)
                .attackConfig(
                    XenomorphAttackConfig.builder()
                        .addRegular(CLAW)
                        .addRegular(BITE)
                        .addRegular(TAIL)
                        .build()
                )
                .parallelDigCount(2)
                .pushedByFluid(false)
                .build()
        );
        this.animationDispatcher = new CarrierAnimationDispatcher(this);
        this.carrierData = new CarrierData();
    }

    @Override
    public Agent.Builder<Carrier> blib$applyGOAPAgentProperties(Agent.Builder<Carrier> agentBuilder) {
        return CarrierGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Carrier> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(CarrierGOAP.GRAPH);
    }

    @Override
    public void tick() {
        super.tick();
        carrierData.tick();

        if (!level().isClientSide) {
            fillReserveFacehuggerPayloadIfPending();
        }
    }

    @Override
    protected int getMaxPassengerCount() {
        return CarrierSpine.COUNT;
    }

    @Override
    protected boolean canEntityRideAlien(@NotNull Entity passenger) {
        if (passenger.getType().is(AlienEntityTypeTags.FACEHUGGERS)) {
            if (passenger.getVehicle() == this) {
                return true;
            }
            return getRidingFacehuggerCount() < CarrierSpine.COUNT;
        }
        return super.canEntityRideAlien(passenger);
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (passenger.getType().is(AlienEntityTypeTags.FACEHUGGERS)) {
            callback.accept(passenger, getX(), getY(), getZ());
            return;
        }
        super.positionRider(passenger, callback);
    }

    @Override
    public void die(@NotNull DamageSource damageSource) {
        releaseAllFacehuggers();
        super.die(damageSource);
    }

    public void releaseAllFacehuggers() {
        var facehuggers = getPassengers().stream()
            .filter(p -> p.getType().is(AlienEntityTypeTags.FACEHUGGERS))
            .toList();

        var count = facehuggers.size();
        for (int i = 0; i < count; i++) {
            var facehugger = facehuggers.get(i);
            facehugger.stopRiding();

            var angle = ((float) i / count) * (float) (Math.PI * 2) + (random.nextFloat() - 0.5F) * 0.5F;
            var horizontalSpeed = 0.5 + random.nextFloat() * 0.5;
            var verticalSpeed = 0.3 + random.nextFloat() * 0.4;

            facehugger.setDeltaMovement(
                Math.cos(angle) * horizontalSpeed,
                verticalSpeed,
                Math.sin(angle) * horizontalSpeed
            );
        }
    }

    public void throwFacehugger() {
        startAttack(THROW, null);
    }

    public void screamReleaseFacehuggers() {
        startAttack(SCREAM, null);
    }

    public int getRidingFacehuggerCount() {
        return (int) getPassengers().stream()
            .filter(p -> p.getType().is(AlienEntityTypeTags.FACEHUGGERS))
            .count();
    }

    public void queueReserveFacehuggerPayload() {
        carrierData.setReserveFacehuggerPayloadPending(true);
    }

    private void fillReserveFacehuggerPayloadIfPending() {
        if (!carrierData.isReserveFacehuggerPayloadPending()) {
            return;
        }

        var facehuggerType = Facehugger.getType(getVariant(), false);
        if (facehuggerType == null) {
            carrierData.setReserveFacehuggerPayloadPending(false);
            return;
        }

        while (getRidingFacehuggerCount() < CarrierSpine.COUNT) {
            var facehugger = facehuggerType.create(level());
            if (facehugger == null) {
                return;
            }

            facehugger.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
            facehugger.setPersistenceRequired();
            level().addFreshEntity(facehugger);

            if (!facehugger.startRiding(this, true)) {
                facehugger.discard();
                return;
            }
        }

        carrierData.setReserveFacehuggerPayloadPending(false);
    }

    public CarrierData getCarrierData() {
        return carrierData;
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        carrierData.load(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        carrierData.save(compoundTag);
    }

    @Override
    protected boolean isImmobile() {
        return isDeadOrDying();
    }

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }

    public CarrierAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.CARRIER.get();
            case NETHER -> AlienEntityTypes.NETHER_CARRIER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_CARRIER.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_CARRIER.get();
        };
    }
}
