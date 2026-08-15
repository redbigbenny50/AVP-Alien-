package com.alien.common.gameplay.entity.living.alien.adolescent;

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
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.data_sync.v1.DataAccessor;
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
 * ⭐⭐ THE ADOLESCENT IS A XENOMORPH. It used to extend {@link Alien} directly, and that one word was quietly
 * load-bearing: {@code GrowthManager} routes a growth into the COCOON pipeline only for xenomorphs, so an adolescent
 * simply popped into its adult form with no molt at all. Its own authored molt clips never played, and neither did the
 * emerge clip on whatever it became - the spitter's {@code molt.emerge} was unreachable art.
 * <p>
 * [stated] "cant we just make the adolesent use xenomorph instead of alien? its supposed to change into them".
 * </p>
 * <p>
 * ⚠ WHAT THIS DELIBERATELY DOES <em>NOT</em> BUY. It is still a coward: no attack config, so
 * {@code runAttackAnimations} returns immediately, and its goals are unchanged ({@link AvoidEntityGoal} plus a stroll).
 * It is also kept OUT of the {@code avp_alien:xenomorphs} entity tag, which is what most hive logic actually filters
 * on, and {@link #setTarget} refuses hive-assigned targets outright - see there.
 * </p>
 * <p>
 * ⏭ NOT BUILT YET, [stated]: "its only meant to attack small passive mobs for food or defend itself if it cant escape."
 * Both need an attack config and their own sensors, and neither exists. When they are built they must go through this
 * entity's OWN goals, not through the hive assigning it a target.
 * </p>
 */
public class Adolescent extends Xenomorph implements GOAPUser<Adolescent> {

    private static final Graph<Adolescent> GOAP_GRAPH = JuvenileGOAP.buildGraph();

    /**
     * ⭐ THE CHILD'S TWO ATTACKS. Small, slow and short - it is not a caste, it is a thing that eats chickens.
     * <p>
     * ⚠ THE LIMB REQUIREMENTS ARE REAL. A juvenile with its head torn off cannot bite and one with no arms cannot
     * swipe; with neither it cannot attack at all, which is the correct answer rather than a bug.
     * </p>
     */
    public static final AttackType BITE = AttackType.builder("adolescent_bite")
        .requiresHead()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType SWIPE = AttackType.builder("adolescent_swipe")
        .requiresAnyArm()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    /**
     * ⚠ HAVING AN ATTACK CONFIG DOES NOT MAKE IT A COMBATANT. The config only says what a swing LOOKS like; whether a
     * swing ever happens is decided by {@link #setTarget}, which admits prey and a cornered child's attacker and
     * nothing else. The hive still cannot point one at anything.
     * <p>
     * Crawling is off: at 0.7 x 0.7 it already fits anywhere a crawl would help, it has no limb definitions registered
     * so it can never lose a leg, and its animator has no crawl branch - so an enabled crawl would shrink the hitbox
     * with nothing to show for it.
     * </p>
     */
    private static final XenomorphConfig CONFIG = XenomorphConfig
        .builder(XenomorphPathConfig.SMALL_DOOR, alienVariant -> getType(alienVariant, false))
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

    public static AttributeSupplier.Builder createAdolescentAttributes() {
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

    private static final String NBT_HAS_DORSAL_TUBES = "hasDorsalTubes";

    public final DataAccessor<Boolean> hasDorsalTubes;

    private final AdolescentAnimationDispatcher animationDispatcher;

    public Adolescent(EntityType<? extends Adolescent> entityType, Level level) {
        super(entityType, level, CONFIG);

        this.hasDorsalTubes = new DataAccessor<>(this, AlienDataSyncKeys.ADOLESCENT_HAS_DORSAL_TUBES.get());

        this.animationDispatcher = new AdolescentAnimationDispatcher(this);

        // ⚠⚠ THE INHERITED GrowthManager, NOT A SECOND ONE. Xenomorph already builds, ticks, saves and loads one
        // (with growOverTime FALSE, because adult castes molt on triggers rather than on a clock). Keeping the old
        // private field here would have run growth TWICE PER TICK and written its NBT twice - adolescents would
        // have matured at double speed. Only the flag differs, so only the flag is set.
        getGrowthManager().setGrowOverTime(true);
    }

    @Override
    public @Nullable EntityType<? extends Alien> getTypeForVariant(AlienVariant alienVariant) {
        return getType(alienVariant, isRoyal());
    }

    @Override
    protected void registerGoals() {
        // ⚠ PRIORITY ORDER IS THE BEHAVIOUR. The melee goal sits ABOVE the avoid goal, so once a target exists - and
        // one only exists if it is prey or the thing that cornered it - fighting outranks fleeing. Below it, avoid
        // still runs against everything else, which is the coward default.
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
                    // ⚠ Prey is EXCLUDED from the flee list, or the child would run from the chicken it is chasing:
                    // the avoid goal and the hunt goal would fight over the same entity every tick.
                    : !BLibEntityPredicates.isInvulnerable(entity) && !JuvenilePrey.isPrey(entity)
            )
        );
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5));
        targetSelector.addGoal(1, new HuntPreyGoal(this));
    }

    @Override
    public void tick() {
        // ⚠ NO growthManager.tick() HERE - Xenomorph.tick already ticks the inherited one.
        super.tick();
        // Eat a spent egg / spent facehugger lying next to us to skip a molt phase (and clear the litter).
        com.alien.common.gameplay.entity.living.alien.MoltFeeding.tickFeeding(this);

        if (!level().isClientSide) {
            getHostType().ifSome(hostType -> hasDorsalTubes.set(!hostType.is(AlienEntityTypeTags.RUNNER_HOSTS)));
            updateSelfDefence();
        }
    }

    /**
     * ⭐⭐ THE ONLY TWO TARGETS A CHILD MAY HAVE, and everything else is still refused at the door.
     * <p>
     * [stated] "coward so not targets assigned to it ... its only meant to attack small passive mobs for food or defend
     * itself if it cant escape." Those are the two openings, and they are checked HERE rather than trusted to the goals
     * because roughly a dozen hive systems call {@code setTarget} directly - convoy materialisation, war offensives,
     * the dormant-queen purge, cry-for-help retargeting, attack parties, the End enderman sweep. A goal cannot stop
     * those; this can, and it cannot go stale when a thirteenth is added.
     * </p>
     * <p>
     * ⚠ SELF-DEFENCE IS NARROWER THAN "SOMETHING HURT ME". It must be the thing that hurt it AND the child must be
     * cornered - [stated] "unless they can no longer escape inwhich they would turn to fight". A juvenile being chased
     * across open ground keeps running and is never handed its pursuer as a target.
     * </p>
     */
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

    /**
     * Keeps the cornered latch current, and lets the child turn on whatever has it trapped.
     * <p>
     * ⚠ THE RETALIATION IS SET HERE, NOT IN A TARGET GOAL. {@code setTarget} refuses a threat unless the tracker
     * already says cornered, so the order matters: tick the tracker first, then offer the attacker. Doing it inside a
     * goal would race the same check and silently do nothing.
     * </p>
     */
    private void updateSelfDefence() {
        if (!corneredTracker.tick(this)) {
            return;
        }

        var threat = CorneredTracker.threatOf(this);

        if (threat != null && getTarget() != threat) {
            setTarget(threat);
        }
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

        if (compoundTag.contains(NBT_HAS_DORSAL_TUBES)) {
            hasDorsalTubes.set(compoundTag.getBoolean(NBT_HAS_DORSAL_TUBES));
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);

        compoundTag.putBoolean(NBT_HAS_DORSAL_TUBES, hasDorsalTubes.get());
    }

    public AdolescentAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static @Nullable EntityType<? extends Alien> getType(AlienVariant alienVariant, boolean isRoyal) {
        if (isRoyal) {
            return switch (alienVariant) {
                case NORMAL -> AlienEntityTypes.ROYAL_ADOLESCENT.get();
                case NETHER -> AlienEntityTypes.ROYAL_NETHER_ADOLESCENT.get();
                case ABERRANT -> AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT.get();
                case IRRADIATED -> null;
            };
        }

        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.ADOLESCENT.get();
            case NETHER -> AlienEntityTypes.NETHER_ADOLESCENT.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_ADOLESCENT.get();
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
    public Agent.Builder<Adolescent> blib$applyGOAPAgentProperties(Agent.Builder<Adolescent> agentBuilder) {
        return JuvenileGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Adolescent> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(GOAP_GRAPH);
    }
}
