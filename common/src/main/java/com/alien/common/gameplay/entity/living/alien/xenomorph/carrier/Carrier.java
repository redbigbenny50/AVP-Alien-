package com.alien.common.gameplay.entity.living.alien.xenomorph.carrier;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
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
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("carrier_bite")
        .requiresHead()
        .defaultDurationInTicks(14)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("carrier_tail")
        .requiresTail()
        .defaultDurationInTicks(19)
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

    /**
     * The reserve to debit an egg from, or null when this carrier arms for free.
     * <p>
     * Only an irradiated carrier pays: its eggs are finite ordnance rather than a renewable nursery stock, so the bank
     * has to actually shrink. Returns null - meaning "arm for free" - for every other strain, and also when the carrier
     * has no hive to draw from, since a stray should not be left permanently unarmed.
     */
    private @Nullable com.alien.common.gameplay.hive.location.HiveLocationReserves irradiatedEggBankOrNull() {
        if (getVariant() != com.alien.common.model.alien.variant.AlienVariant.IRRADIATED) {
            return null;
        }

        var location = com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver.reserveReturnLocation(this);
        return location == null ? null : location.localReserves();
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

        // An IRRADIATED carrier arms itself from the hive's leftover egg bank, one egg per hugger, and that bank
        // never restocks. [stated] "partial uses whatevers left - if theres only 4 then it empties the bank." The
        // loop below already handles that: it simply stops early. Released huggers never return to the spine, so a
        // raid permanently spends up to six eggs.
        //
        // Every other strain keeps the free top-up it always had - their nurseries refill.
        var eggBank = irradiatedEggBankOrNull();

        while (getRidingFacehuggerCount() < CarrierSpine.COUNT) {
            if (eggBank != null && !eggBank.trySpawn(Ovomorph.getType(getVariant(), false))) {
                break;
            }

            var facehugger = facehuggerType.create(level());
            if (facehugger == null) {
                return;
            }

            facehugger.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
            // NO setPersistenceRequired - deliberately removed (Aug 1). While riding the spine a hugger is already
            // despawn-proof (vanilla Mob.requiresCustomPersistence() is literally isPassenger()), and one attached
            // to a host is protected by Facehugger.isPersistenceRequired's own override. The flag's only real
            // effect was on RELEASED huggers, which it made immortal - every scatter permanently added up to six
            // never-despawning facehuggers, the accumulation behind the tester-reported overpop. Released strays
            // are now ordinary mobs: they hunt while a player is near and despawn like anything else once the
            // fight moves on. "Released huggers are gone for good" still holds - they never return to the spine.
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
