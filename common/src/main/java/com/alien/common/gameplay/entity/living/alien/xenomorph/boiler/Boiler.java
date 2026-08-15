package com.alien.common.gameplay.entity.living.alien.xenomorph.boiler;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ExplosiveXenomorphUtil;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.boiler.ai.BoilerGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.dismemberment.v1.LimbDismemberer;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.entity.v1.vibration.VibrationSystemManager;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
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

public class Boiler extends Xenomorph implements GOAPUser<Boiler> {

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.MEDIUM_DOOR, Boiler::getType)
        .parallelDigCount(2)
        .build();

    /**
     * ⭐ Blast strength. [stated] "the explosion should be slight less damaging than a creeper" - a creeper is 3.0F, TNT
     * 4.0F, a charged creeper 6.0F. Explosion power scales with roughly the CUBE of radius, so the old 2.0F was under a
     * third of a creeper rather than slightly under it.
     * <p>
     * ⚠ THIS ALSO SIZES THE ACID FIELD, and not smoothly: ExplosiveXenomorphUtil casts the radius to int for the block
     * box, so 2.6 still floors to 2 and the acid stays 5x5x5 while the blast grows. Going to 3.0F or beyond would jump
     * it to 7x7x7 in one step. Keep that in mind before nudging this again.
     * </p>
     */
    private static final float EXPLOSION_RADIUS = 2.6F;

    private static final int ACID_AMOUNT = 3;

    private static final double TARGET_EXPLOSION_PADDING = 0.75D;

    private static final double LIMB_HORIZONTAL_VELOCITY = 0.18D;

    private static final double LIMB_VERTICAL_VELOCITY = 0.14D;

    public static AttributeSupplier.Builder createBoilerAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 0.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.25F)
            .add(Attributes.FOLLOW_RANGE, 16F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.3f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 2F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1F);
    }

    private final BoilerAnimationDispatcher animationDispatcher;

    private final VibrationSystemManager vibrationSystemManager;

    private final BoilerData boilerData;

    private boolean hasExploded;

    public Boiler(EntityType<? extends Boiler> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new BoilerAnimationDispatcher(this);
        this.vibrationSystemManager = new VibrationSystemManager(this, 2.5F, 32);
        this.boilerData = new BoilerData();
    }

    @Override
    public Agent.Builder<Boiler> blib$applyGOAPAgentProperties(Agent.Builder<Boiler> agentBuilder) {
        return BoilerGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Boiler> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(BoilerGOAP.GRAPH);
    }

    @Override
    public void tick() {
        super.tick();
        vibrationSystemManager.tick();
        boilerData.tick();
        tryExplodeNearTarget();
    }

    @Override
    public void updateDynamicGameEventListener(@NotNull BiConsumer<DynamicGameEventListener<?>, ServerLevel> biConsumer) {
        vibrationSystemManager.updateDynamicGameEventListener(biConsumer);
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity entity) {
        explodeAndDiscard();

        return true;
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float damage) {
        var isHurt = super.hurt(damageSource, damage);

        if (isHurt && damage > 0F) {
            explodeAndDiscard();
        }

        return isHurt;
    }

    private void tryExplodeNearTarget() {
        if (level().isClientSide || hasExploded || isRemoved()) {
            return;
        }

        var target = getTarget();

        if (target == null || !target.isAlive() || !AlienPredicates.canTarget(this, target)) {
            return;
        }

        var explosionRange = getBbWidth() * 0.5D + target.getBbWidth() * 0.5D + TARGET_EXPLOSION_PADDING;

        if (distanceToSqr(target) <= explosionRange * explosionRange) {
            explodeAndDiscard();
        }
    }

    private void explodeAndDiscard() {
        if (level().isClientSide || hasExploded || isRemoved()) {
            return;
        }

        hasExploded = true;
        detachAllLimbs();
        ExplosiveXenomorphUtil.explodeWithAcid(this, EXPLOSION_RADIUS, ACID_AMOUNT);
        triggerOnDeathMobEffects(RemovalReason.KILLED);
        discard();
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

    public BoilerAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public VibrationSystemManager getVibrationSystemManager() {
        return vibrationSystemManager;
    }

    public BoilerData getBoilerData() {
        return boilerData;
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.BOILER.get();
            case NETHER -> AlienEntityTypes.NETHER_BOILER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_BOILER.get();
            case IRRADIATED -> null;
        };
    }
}
