package com.alien.common.gameplay.entity.living.alien.chestburster;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.juvenile.JuvenileGOAP;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.entity.v1.BLibEntityPredicates;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class Chestburster extends Xenomorph implements GOAPUser<Chestburster> {

    /**
     * ⚠⚠ WATER AFFINITY IS THE WHOLE REASON THIS CONFIG IS NOT JUST SMALL_DOOR. [stated] "all xenomorphs can swim
     * except chest bursters just arent very good at it and avoid the water". Before this reparent a chestburster was an
     * {@code Alien} with no BLib navigator at all, so it could not swim by accident; becoming a {@link Xenomorph} would
     * have HANDED it full water pathing, which is the opposite of the spec. AVOID keeps it out.
     */
    private static final XenomorphConfig CONFIG = XenomorphConfig
        .builder(
            XenomorphPathConfig.SMALL_DOOR.withWaterAffinity(XenomorphPathConfig.WaterAffinity.AVOID),
            alienVariant -> getType(alienVariant, false)
        )
        .healthRegenPerSecond(0.5F)
        .canCrawl(false)
        .canCrawlAfterLegLoss(false)
        .build();

    private static final Graph<Chestburster> GOAP_GRAPH = JuvenileGOAP.buildGraph();

    public static AttributeSupplier.Builder createChestbursterAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 0f)
            .add(Attributes.ARMOR_TOUGHNESS, 0f)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 0.1F)
            .add(Attributes.FOLLOW_RANGE, 16F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0F)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 0.25F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.05F)
            .add(Attributes.SCALE, 0.4F);
    }

    private final ChestbursterAnimationDispatcher animationDispatcher;

    public Chestburster(EntityType<? extends Chestburster> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new ChestbursterAnimationDispatcher(this);

        // ⚠⚠ THE INHERITED GrowthManager, NOT A SECOND ONE — the same trap the adolescent reparent hit. Xenomorph
        // already builds, ticks, saves and loads one (with growOverTime FALSE, since adult castes molt on triggers
        // rather than on a clock). Keeping the old private field here would have run growth TWICE PER TICK and
        // written its NBT twice. Only the flag differs, so only the flag is set.
        getGrowthManager().setGrowOverTime(true);
    }

    @Override
    public @Nullable EntityType<? extends Alien> getTypeForVariant(AlienVariant alienVariant) {
        return getType(alienVariant, isRoyal());
    }

    @Override
    protected void registerGoals() {
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
                    : !BLibEntityPredicates.isInvulnerable(entity)
            )
        );
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5));
    }

    @Override
    public void tick() {
        super.tick();
        // growthManager.tick() is NOT called here any more - Xenomorph.tick already ticks the inherited one.
        // Eat a spent egg / spent facehugger lying next to us to skip a molt phase (and clear the litter).
        com.alien.common.gameplay.entity.living.alien.MoltFeeding.tickFeeding(this);
    }

    @Override
    protected float getHealthRegenPerSecond() {
        return 0.5F;
    }

    // readAdditionalSaveData / addAdditionalSaveData no longer override anything here: the growth NBT they used to
    // write is the inherited manager's, and Xenomorph already saves and loads it. Writing it twice would have put two
    // copies of the same key in the tag.

    /**
     * ⭐ A GOAP AGENT, so the COCOON CAN ACTUALLY TICK. {@code CocoonActions.COCOON} is the only caller of
     * {@code performCocoonTick()} anywhere in the mod, and it is a GOAP action — an entity without an agent that enters
     * a cocoon locks in PENDING forever. That is precisely what stranded the adolescents.
     */
    @Override
    public Agent.Builder<Chestburster> blib$applyGOAPAgentProperties(Agent.Builder<Chestburster> agentBuilder) {
        return JuvenileGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Chestburster> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(GOAP_GRAPH);
    }

    public ChestbursterAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public static @Nullable EntityType<? extends Alien> getType(AlienVariant alienVariant, boolean isRoyal) {
        if (isRoyal) {
            return switch (alienVariant) {
                case NORMAL -> AlienEntityTypes.ROYAL_CHESTBURSTER.get();
                case NETHER -> AlienEntityTypes.ROYAL_NETHER_CHESTBURSTER.get();
                case ABERRANT -> AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER.get();
                case IRRADIATED -> null;
            };
        }

        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.CHESTBURSTER.get();
            case NETHER -> AlienEntityTypes.NETHER_CHESTBURSTER.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_CHESTBURSTER.get();
            case IRRADIATED -> null;
        };
    }
}
