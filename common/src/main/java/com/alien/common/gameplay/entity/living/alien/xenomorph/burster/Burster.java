package com.alien.common.gameplay.entity.living.alien.xenomorph.burster;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.EggCarrier;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CrawlAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.EggPickupManager;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ExplosiveXenomorphUtil;
import com.alien.common.gameplay.entity.living.alien.xenomorph.VentBuilder;
import com.alien.common.gameplay.entity.living.alien.xenomorph.VentData;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.burster.ai.BursterGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.dismemberment.v1.LimbDismemberer;
import com.blib.api.common.entity.v1.EntityUtil;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class Burster extends Xenomorph implements EggCarrier, GOAPUser<Burster>, VentBuilder {

    private static final float CRITICAL_HEALTH_THRESHOLD = 0.1F;

    private static final float EXPLOSION_RADIUS = 2F;

    private static final int ACID_AMOUNT = 3;

    private static final double LIMB_HORIZONTAL_VELOCITY = 0.18D;

    private static final double LIMB_VERTICAL_VELOCITY = 0.14D;

    /**
     * ⭐⭐ THE PRONE ATTACKS. [stated] "the spitter loses one leg and still stands to attack with arms and tail attack it
     * should only resort to crawl attacks."
     * <p>
     * ⚠⚠ THE CLIPS AND THE DISPATCHER METHODS ALREADY EXISTED - what was missing was the ATTACK TYPES. The crawl
     * preference in {@code XenomorphAttackConfig} restricts a crawling caste to crawl attacks ONLY IF it has any; with
     * none registered there was nothing to restrict to and it fell straight through to the standing set. A one-legged
     * burster stood up to swing because there was literally nothing prone to pick.
     * </p>
     */
    /** ⚠ Slightly softer than a standing swing, matching the predalien's existing crawl claw. */
    private static final float CRAWL_DAMAGE_FRACTION = 0.8F;

    public static final AttackType CRAWL_CLAW = CrawlAttack.create(
        "burster_crawl_claw",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.ARM,
        16
    );

    /** ⚠ HEAD, NOT ARM - so a crawling burster that has also lost both arms still has a bite. */
    public static final AttackType CRAWL_BITE = CrawlAttack.create(
        "burster_crawl_bite",
        CRAWL_DAMAGE_FRACTION,
        CrawlAttack.Limb.HEAD,
        14
    );

    public static final AttackType CLAW = AttackType.builder("burster_claw")
        .requiresAnyArm()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BITE = AttackType.builder("burster_bite")
        .requiresHead()
        .defaultDurationInTicks(10)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL = AttackType.builder("burster_tail")
        .requiresTail()
        .defaultDurationInTicks(17)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static AttributeSupplier.Builder createBursterAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 0.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.3F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.3f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 3F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.2F);
    }

    private final BursterAnimationDispatcher animationDispatcher;

    private final EggPickupManager eggPickupManager;

    private final VentData ventData;

    private boolean hasExploded;

    public Burster(EntityType<? extends Burster> entityType, Level level) {
        super(
            entityType,
            level,
            XenomorphConfig.builder(XenomorphPathConfig.SMALL_DOOR, Burster::getType)
                .attackConfig(
                    XenomorphAttackConfig.builder()
                        .addRegular(CLAW)
                        .addRegular(BITE)
                        .addRegular(CRAWL_CLAW)
                        .addRegular(CRAWL_BITE)
                        .addRegular(TAIL)
                        .build()
                )
                .build()
        );
        this.animationDispatcher = new BursterAnimationDispatcher(this);
        this.eggPickupManager = new EggPickupManager(this);
        this.ventData = new VentData();
    }

    @Override
    public Agent.Builder<Burster> blib$applyGOAPAgentProperties(Agent.Builder<Burster> agentBuilder) {
        return BursterGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Burster> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(BursterGOAP.GRAPH);
    }

    @Override
    public void tick() {
        super.tick();
        eggPickupManager.tick();

        if (!level().isClientSide && isAlive() && getHealth() <= getMaxHealth() * CRITICAL_HEALTH_THRESHOLD) {
            explodeAndDiscard();
        }
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
    public void die(@NotNull DamageSource damageSource) {
        explode();
        super.die(damageSource);
    }

    private void explodeAndDiscard() {
        explode();
        triggerOnDeathMobEffects(RemovalReason.KILLED);
        discard();
    }

    private void explode() {
        if (hasExploded || level().isClientSide) {
            return;
        }

        hasExploded = true;
        detachAllLimbs();
        ExplosiveXenomorphUtil.explodeWithAcid(this, EXPLOSION_RADIUS, ACID_AMOUNT);
    }

    private void detachAllLimbs() {
        var randomSource = getRandom();
        var bodyCenter = position().add(0.0D, getBbHeight() * 0.5D, 0.0D);

        for (var definition : LimbDismemberer.getRemainingDefinitions(this)) {
            var angle = randomSource.nextDouble() * Math.TAU;
            var horizontalVelocity = LIMB_HORIZONTAL_VELOCITY * (0.65D + randomSource.nextDouble() * 0.7D);
            var verticalVelocity = LIMB_VERTICAL_VELOCITY * (0.65D + randomSource.nextDouble() * 0.7D);
            var velocity = new Vec3(
                Math.cos(angle) * horizontalVelocity,
                verticalVelocity,
                Math.sin(angle) * horizontalVelocity
            );

            LimbDismemberer.detach(this, definition.id(), limb -> {
                limb.moveTo(bodyCenter, getYRot(), getXRot());
                limb.launch(velocity);
            });
        }
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

    public BursterAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.BURSTER.get();
            case NETHER -> AlienEntityTypes.NETHER_BURSTER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_BURSTER.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_BURSTER.get();
        };
    }
}
