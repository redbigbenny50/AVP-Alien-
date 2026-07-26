package com.alien.common.gameplay.entity.living.alien.xenomorph.runner;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.EggCarrier;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.EggPickupManager;
import com.alien.common.gameplay.entity.living.alien.xenomorph.VentBuilder;
import com.alien.common.gameplay.entity.living.alien.xenomorph.VentData;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.runner.ai.RunnerGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityUtil;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class Runner extends Xenomorph implements EggCarrier, GOAPUser<Runner>, VentBuilder, com.alien.common.gameplay.hive.structure.carve.CarveWorker {

    public static final AttackType CLAW = AttackType.builder("runner_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("runner_bite")
        .requiresHead()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL_QUAD = AttackType.builder("runner_tail_quad")
        .requiresTail()
        .defaultDurationInTicks(17)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static AttributeSupplier.Builder createRunnerAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 4.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.25F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.3f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 2F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.1F);
    }

    /** Synced crew gait, same contract as the drone's: 0 idle, 1 digging, 2 placing. */
    public final com.blib.api.common.data_sync.v1.DataAccessor<Integer> carveDigMode;

    private final RunnerAnimationDispatcher animationDispatcher;

    private final EggPickupManager eggPickupManager;

    private final VentData ventData;

    public Runner(EntityType<? extends Runner> entityType, Level level) {
        super(
            entityType,
            level,
            XenomorphConfig.builder(XenomorphPathConfig.SMALL_DOOR, Runner::getType)
                .attackConfig(
                    XenomorphAttackConfig.builder()
                        .addRegular(CLAW)
                        .addRegular(BITE)
                        .addRegular(TAIL_QUAD)
                        .build()
                )
                .build()
        );
        this.animationDispatcher = new RunnerAnimationDispatcher(this);
        this.carveDigMode = new com.blib.api.common.data_sync.v1.DataAccessor<>(
            this,
            com.alien.common.registry.init.AlienDataSyncKeys.RUNNER_CARVE_DIG_MODE.get()
        );
        this.eggPickupManager = new EggPickupManager(this);
        this.ventData = new VentData();
    }

    @Override
    public Agent.Builder<Runner> blib$applyGOAPAgentProperties(Agent.Builder<Runner> agentBuilder) {
        return RunnerGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Runner> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(RunnerGOAP.GRAPH);
    }

    @Override
    public void tick() {
        super.tick();
        eggPickupManager.tick();
    }

    @Override
    protected boolean canEntityRideAlien(@NotNull Entity passenger) {
        return super.canEntityRideAlien(passenger)
            || passenger.getType().is(AlienEntityTypeTags.OVOMORPHS);
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (passenger.getType().is(AlienEntityTypeTags.OVOMORPHS)) {
            var relativePos = EntityUtil.getRelativePosition(this, 0, 0.8, -1);
            callback.accept(passenger, relativePos.x, relativePos.y, relativePos.z);
            return;
        }

        super.positionRider(passenger, callback);
    }

    @Override
    public void updateDynamicGameEventListener(@NotNull BiConsumer<DynamicGameEventListener<?>, ServerLevel> biConsumer) {
        super.updateDynamicGameEventListener(biConsumer);
        eggPickupManager.updateDynamicGameEventListener(biConsumer);
    }

    @Override
    public EggPickupManager getEggPickupManager() {
        return eggPickupManager;
    }

    @Override
    public VentData getVentData() {
        return ventData;
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        ventData.load(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        ventData.save(compoundTag);
    }

    @Override
    public com.blib.api.common.data_sync.v1.DataAccessor<Integer> carveDigMode() {
        return carveDigMode;
    }

    public RunnerAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.RUNNER.get();
            case NETHER -> AlienEntityTypes.NETHER_RUNNER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_RUNNER.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_RUNNER.get();
        };
    }
}
