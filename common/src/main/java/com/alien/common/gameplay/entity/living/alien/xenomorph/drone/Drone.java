package com.alien.common.gameplay.entity.living.alien.xenomorph.drone;

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
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.ai.DroneGOAP;
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

public class Drone extends Xenomorph implements EggCarrier, GOAPUser<Drone>, VentBuilder {

    public static final AttackType CLAW = AttackType.builder("drone_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("drone_bite")
        .requiresHead()
        .defaultDurationInTicks(8)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("drone_tail")
        .requiresTail()
        .defaultDurationInTicks(12)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static AttributeSupplier.Builder createDroneAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 4.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.25F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.3f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 2F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1F);
    }

    private final DroneAnimationDispatcher animationDispatcher;

    private final EggPickupManager eggPickupManager;

    private final VentData ventData;

    public Drone(EntityType<? extends Drone> entityType, Level level) {
        super(
            entityType,
            level,
            XenomorphConfig.builder(XenomorphPathConfig.MEDIUM_DOOR, Drone::getType)
                .attackConfig(
                    XenomorphAttackConfig.builder()
                        .addRegular(CLAW)
                        .addRegular(BITE)
                        .addRegular(TAIL)
                        .build()
                )
                .build()
        );
        this.animationDispatcher = new DroneAnimationDispatcher(this);
        this.eggPickupManager = new EggPickupManager(this);
        this.ventData = new VentData();
    }

    @Override
    public Agent.Builder<Drone> blib$applyGOAPAgentProperties(Agent.Builder<Drone> agentBuilder) {
        return DroneGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Drone> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(DroneGOAP.GRAPH);
    }

    @Override
    public void tick() {
        super.tick();
        eggPickupManager.tick();
    }

    /**
     * A drone carries eggs AND captured hosts.
     * <p>
     * HOSTS is not optional. {@code Alien.tick()} evicts every passenger that fails this check, and
     * {@code HostCaptureTask.capture} mounts the host with {@code startRiding(captor, true)} - force bypasses
     * canAddPassenger at mount time, but NOT the eviction sweep one tick later. Leaving HOSTS out means the drone grabs
     * its captive and throws it off again every single tick: the host is dismounted to the top of the drone's bounding
     * box, falls, is re-grabbed, and never survives long enough as a passenger to receive LivingEntity#rideTick - which
     * is what resets fallDistance. The captive accumulates fall damage until it lands and dies. The planner also sees
     * IS_CARRYING_HOST flicker false every tick and never commits to the delivery, and a captured PLAYER is thrown
     * clear before the struggle bar can survive a tick.
     */
    @Override
    protected boolean canEntityRideAlien(@NotNull Entity passenger) {
        return super.canEntityRideAlien(passenger)
            || passenger.getType().is(AlienEntityTypeTags.OVOMORPHS)
            || passenger.getType().is(AlienEntityTypeTags.HOSTS);
    }

    /** How far up the drone's body its "chest" sits, as a fraction of its height. */
    private static final double CHEST_HEIGHT_FRACTION = 0.55;

    /** Clearance in front of the drone's own body, before the captive's width is added on top. */
    private static final double CARRY_CLEARANCE = 0.35;

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        // An egg rides on the BACK (negative Z is behind).
        if (passenger.getType().is(AlienEntityTypeTags.OVOMORPHS)) {
            var relativePos = EntityUtil.getRelativePosition(this, 0, 0.8, -1);
            callback.accept(passenger, relativePos.x, relativePos.y, relativePos.z);
            return;
        }

        // A captured host is CLUTCHED IN FRONT OF THE CHEST, not balanced on the drone's head - which is where the
        // default passenger attachment (the top of the hitbox) was putting it.
        //
        // Both offsets are derived from the two bodies rather than hardcoded, because a captive can be anything from a
        // wolf to a villager to a marine. A passenger's position is its FEET, so to centre a body of any height on the
        // drone's chest we drop the feet by half that body's height; and we push it forward far enough to clear its own
        // width, so a cow does not end up embedded in the drone's ribs.
        if (passenger.getType().is(AlienEntityTypeTags.HOSTS)) {
            var chestHeight = getBbHeight() * CHEST_HEIGHT_FRACTION;
            var feetOffset = chestHeight - passenger.getBbHeight() * 0.5;
            var forward = CARRY_CLEARANCE + passenger.getBbWidth() * 0.5;

            var relativePos = EntityUtil.getRelativePosition(this, 0, feetOffset, forward);
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

    public DroneAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.DRONE.get();
            case NETHER -> AlienEntityTypes.NETHER_DRONE.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_DRONE.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_DRONE.get();
        };
    }
}
