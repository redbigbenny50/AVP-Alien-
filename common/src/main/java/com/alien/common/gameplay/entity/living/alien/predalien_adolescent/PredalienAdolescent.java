package com.alien.common.gameplay.entity.living.alien.predalien_adolescent;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.adolescent.ai.CorneredTracker;
import com.alien.common.gameplay.entity.living.alien.adolescent.ai.HuntPreyGoal;
import com.alien.common.gameplay.entity.living.alien.adolescent.ai.JuvenileFeeding;
import com.alien.common.gameplay.entity.living.alien.adolescent.ai.JuvenileMeleeGoal;
import com.alien.common.gameplay.entity.living.alien.adolescent.ai.JuvenilePrey;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.juvenile.JuvenileGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.entity.v1.BLibEntityPredicates;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ⭐⭐ A XENOMORPH, for the same single reason the normal {@code Adolescent} is one: {@code GrowthManager} routes a
 * growth into the COCOON pipeline only for xenomorphs, so while this extended {@link Alien} it popped straight into a
 * predalien and its authored {@code molt.predalien.*} clips never played.
 * <p>
 * Everything said on {@code Adolescent} applies here unchanged: no attack config, goals untouched, out of the
 * {@code avp_alien:xenomorphs} tag, and {@link #setTarget} refuses anything the hive tries to point it at.
 * </p>
 */
public class PredalienAdolescent extends Xenomorph implements GOAPUser<PredalienAdolescent> {

    private static final Graph<PredalienAdolescent> GOAP_GRAPH = JuvenileGOAP.buildGraph();

    public static final AttackType BITE = AttackType.builder("predalien_adolescent_bite")
        .requiresHead()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType SWIPE = AttackType.builder("predalien_adolescent_swipe")
        .requiresAnyArm()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /** Mirrors {@code Adolescent.CONFIG} - see there for why each of these is what it is. */
    private static final XenomorphConfig CONFIG = XenomorphConfig
        .builder(XenomorphPathConfig.SMALL_DOOR, PredalienAdolescent::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(BITE)
                .addRegular(SWIPE)
                .build()
        )
        .healthRegenPerSecond(0.5F)
        .canCrawl(false)
        .canCrawlAfterLegLoss(false)
        .build();

    private final CorneredTracker corneredTracker = new CorneredTracker();

    public static AttributeSupplier.Builder createPredalienAdolescentAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 0f)
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.1F)
            .add(Attributes.FOLLOW_RANGE, 16F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0F)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 0.5F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.025F)
            .add(Attributes.SCALE, 0.7F);
    }

    private final PredalienAdolescentAnimationDispatcher animationDispatcher;

    public PredalienAdolescent(EntityType<? extends PredalienAdolescent> entityType, Level level) {
        super(entityType, level, CONFIG);

        this.animationDispatcher = new PredalienAdolescentAnimationDispatcher(this);

        // ⚠⚠ THE INHERITED GrowthManager, NOT A SECOND ONE - Xenomorph already builds, ticks, saves and loads it.
        // Keeping the old private field would have grown this entity at DOUBLE SPEED and written its NBT twice.
        getGrowthManager().setGrowOverTime(true);
    }

    /** Prey, or the thing that cornered it. Nothing else - see {@code Adolescent.setTarget} for the reasoning. */
    @Override
    public void setTarget(@Nullable LivingEntity livingEntity) {
        if (livingEntity != null && !mayTarget(livingEntity)) {
            return;
        }

        super.setTarget(livingEntity);
    }

    private boolean mayTarget(LivingEntity candidate) {
        if (JuvenilePrey.isPrey(candidate)) {
            return true;
        }

        return corneredTracker.isCornered(this) && candidate == getLastHurtByMob();
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            return;
        }

        if (!corneredTracker.tick(this)) {
            return;
        }

        var threat = CorneredTracker.threatOf(this);

        if (threat != null && getTarget() != threat) {
            setTarget(threat);
        }
    }

    @Override
    public @Nullable EntityType<? extends Alien> getTypeForVariant(AlienVariant alienVariant) {
        return getType(alienVariant);
    }

    @Override
    protected void registerGoals() {
        // Melee ABOVE avoid - see Adolescent.registerGoals. A target only exists if it is prey or has it cornered.
        goalSelector.addGoal(2, new JuvenileMeleeGoal(this, 1.1D));
        this.goalSelector.addGoal(
            3,
            new AvoidEntityGoal<>(
                this,
                LivingEntity.class,
                8,
                1,
                1.2,
                entity -> entity instanceof Alien alien
                    ? AlienPredicates.areAliensEnemies(this, alien)
                    : !BLibEntityPredicates.isInvulnerable(entity) && !JuvenilePrey.isPrey(entity)
            )
        );
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5));
        targetSelector.addGoal(1, new HuntPreyGoal(this));
    }

    /**
     * ⭐⭐ A KILL IS A MEAL. [stated] "if the adol eats anything it jumps ahead its growth time."
     * <p>
     * This is the payoff for the whole hunting behaviour: a juvenile that works its way through the chickens grows up
     * measurably sooner than one that stands in a corridor. The size of the jump is a fraction of the stage rather than
     * a flat number of ticks - see {@code GrowthManager.feedOnMeal}.
     * </p>
     * <p>
     * ⚠ NOT RESTRICTED TO PREY, DELIBERATELY. A cornered child that kills the thing attacking it has still got a corpse
     * in front of it, and refusing to feed it there would punish the one fight it is allowed to have. What it CANNOT do
     * is go looking for that fight - {@code setTarget} still admits only prey and whatever has it trapped.
     * </p>
     */
    @Override
    public boolean killedEntity(@NotNull ServerLevel level, @NotNull LivingEntity killed) {
        var result = super.killedEntity(level, killed);

        if (result && getGrowthManager().feedOnMeal()) {
            JuvenileFeeding.playEatFeedback(this, killed);
        }

        return result;
    }

    @Override
    protected float getHealthRegenPerSecond() {
        return 0.5F;
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
    }

    public PredalienAdolescentAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static @Nullable EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.PREDALIEN_ADOLESCENT.get();
            case NETHER -> AlienEntityTypes.NETHER_PREDALIEN_ADOLESCENT.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_PREDALIEN_ADOLESCENT.get();
            case IRRADIATED -> null;
        };
    }

    /**
     * ⭐ THE JUVENILE IS A GOAP AGENT — see {@link JuvenileGOAP} for why this is load-bearing rather than cosmetic.
     * <p>
     * {@code getActiveGOAPGraph} is the inherited swap every adult caste uses: it hands back {@code CocoonGOAP.GRAPH}
     * while {@code cocoonManager.shouldRunCocoonAction()} is true, and the juvenile's own graph otherwise. Without an
     * agent on this entity that swap had nothing to run, so a prepared cocoon sat in PENDING forever and the juvenile
     * froze mid-molt.
     * </p>
     */
    @Override
    public Agent.Builder<PredalienAdolescent> blib$applyGOAPAgentProperties(Agent.Builder<PredalienAdolescent> agentBuilder) {
        return JuvenileGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<PredalienAdolescent> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(GOAP_GRAPH);
    }
}
