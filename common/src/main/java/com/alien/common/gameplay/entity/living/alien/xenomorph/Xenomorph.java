package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.AlienResources;
import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import com.alien.common.gameplay.entity.CrawlingManager;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.GrowthManager;
import com.alien.common.gameplay.entity.living.alien.ResinManager;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.cocoon.CocoonGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.convoy.ConvoyMemberTracker;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.model.resin.ResinProducer;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienBlockTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.block.v1.BlockBreakProgressManager;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;
import com.blib.api.common.entity.v1.EntitySenseCache;
import com.blib.api.common.entity.v1.EntitySenseCacheUser;
import com.blib.api.common.goap.v1.GOAPUser;
import com.blib.api.common.pathfinding.v1.cache.TerrainCacheRegistry;
import com.blib.api.common.pathfinding.v1.evaluator.PathBlockBreakingConfig;
import com.blib.api.common.pathfinding.v1.evaluator.PathCrawlConfig;
import com.blib.api.common.pathfinding.v1.evaluator.PathWaterConfig;
import com.blib.api.common.pathfinding.v1.evaluator.TerrainEvaluatorConfig;
import com.blib.api.common.pathfinding.v1.feature.PathfindingProfile;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigator;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigatorConfig;
import com.blib.api.common.pathfinding.v1.navigator.PathNavigatorUser;
import com.blib.api.common.pathfinding.v1.search.SearchConfig;
import com.blib.api.common.pathfinding.v1.terrain.TerrainClassifiers;
import com.blib.api.common.pathfinding.v1.terrain.TerrainType;
import com.just.ai.goap.graph.Graph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

public abstract class Xenomorph extends Alien implements ResinProducer, EntitySenseCacheUser, PathNavigatorUser {

    private static final float HIVE_INTRUDER_PATH_SEARCH_RANGE = 256.0F;

    private static final int HIVE_INTRUDER_MAX_PATH_LENGTH = 512;

    private static final int HIVE_INTRUDER_TARGET_MEMORY_TICKS = 3 * 20;

    private static final float UNDERWATER_HEIGHT_SCALE = 0.4f;

    private static final float PATH_BLOCK_BREAK_MAX_HARDNESS = 6.0f;

    private static final float PATH_BLOCK_BREAK_DAMAGE_PER_TICK = 50.0f;

    private static final int FRENZIED_RAID_BREAKOUT_INTERVAL_TICKS = 4;

    private static final int RAID_CONTAINMENT_BREAKOUT_INTERVAL_TICKS = 10;

    private static final double RAID_CONTAINMENT_TARGET_RADIUS_BLOCKS = 24.0D;

    private static final double RAID_CONTAINMENT_BREAK_RANGE_BLOCKS = 3.0D;

    private static final double RAID_CONTAINMENT_NAVIGATION_SPEED = 1.25D;

    private static final float RAID_BREAKOUT_BLOCK_DAMAGE_PER_ATTEMPT = 50.0F;

    private static final int RAID_EGG_MAX_ATTEMPT_TICKS = 8 * 20;

    private static final int RAID_EGG_NO_PROGRESS_TICKS = 3 * 20;

    private static final int RAID_EGG_FAILED_COOLDOWN_TICKS = 30 * 20;

    private static final int RAID_EGG_MAX_ATTACKERS = 2;

    private static final double RAID_EGG_PROGRESS_EPSILON_SQR = 0.35D * 0.35D;

    private static final ResourceLocation LOST_LIMB_MAX_HEALTH_MODIFIER = AlienResources.location("lost_limb_max_health");

    private static final double MAX_HEALTH_REDUCTION_PER_LOST_LIMB = 0.1D;

    private static final PathBlockBreakingConfig PATH_BLOCK_BREAKING_CONFIG = new PathBlockBreakingConfig(
        true,
        2,
        PATH_BLOCK_BREAK_MAX_HARDNESS,
        4.0f,
        8.0f,
        PATH_BLOCK_BREAK_DAMAGE_PER_TICK,
        Xenomorph::canPathBreakBlock
    );

    public final DataAccessor<Integer> attackDurationInTicks;

    public final DataAccessor<Integer> attackId;

    public final DataAccessor<AttackType> attackType;

    public final DataAccessor<Boolean> isLunging;

    public final DataAccessor<Boolean> isCrawling;

    public final DataAccessor<CocoonState> cocoonState;

    public final DataAccessor<CocoonSourceForm> cocoonSourceForm;

    /** Queen-only in use: true while she is hibernating (front-end Stage 3); drives the client hibernate pose. */
    public final DataAccessor<Boolean> isHibernating;

    /** Queen-only in use: true while she is clip-digging to her anchor; drives the client dig animation. */
    public final DataAccessor<Boolean> isDiggingSynced;

    public final DataAccessor<Integer> cocoonAnimationId;

    protected final CrawlingManager crawlingManager;

    private final CocoonManager cocoonManager;

    private final GrowthManager growthManager;

    private final ResinManager resinManager;

    private final XenomorphData xenomorphData;

    private final EntitySenseCache entitySenseCache;

    private final PathNavigator pathNavigator;

    private final PathNavigator hiveIntruderPathNavigator;

    private final XenomorphConfig config;

    private final Map<UUID, Integer> failedRaidEggTargets = new HashMap<>();

    private @Nullable UUID raidEggTargetId;

    private int raidEggTargetStartedAtTick;

    private int raidEggNoProgressTicks;

    private double raidEggLastDistanceSqr = Double.MAX_VALUE;

    private final AttackCooldownTracker cooldownTracker;

    private @Nullable AttackType activeAttack;

    private @Nullable AttackExecutor activeExecutor;

    private @Nullable LivingEntity hiveIntruderTarget;

    private int hiveIntruderTargetExpiresAtTick;

    private boolean wasUnderwaterLastTick;

    public Xenomorph(EntityType<? extends Xenomorph> entityType, Level level, XenomorphConfig config) {
        super(entityType, level);

        this.config = config;

        this.attackDurationInTicks = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_ATTACK_DURATION_IN_TICKS.get());
        this.attackId = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_ATTACK_ID.get());
        this.attackType = new DataAccessor<>(this, AlienDataSyncKeys.ATTACK_TYPE.get());
        this.isLunging = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_IS_LUNGING.get());
        this.isCrawling = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_IS_CRAWLING.get());
        this.cocoonState = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_COCOON_STATE.get());
        this.cocoonSourceForm = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_COCOON_SOURCE_FORM.get());
        this.isHibernating = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_IS_HIBERNATING.get());
        this.isDiggingSynced = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_IS_DIGGING.get());
        this.cocoonAnimationId = new DataAccessor<>(this, AlienDataSyncKeys.XENOMORPH_COCOON_ANIMATION_ID.get());

        this.crawlingManager = new CrawlingManager(this, isCrawling, config.canCrawl());
        this.cocoonManager = new CocoonManager(this);
        this.growthManager = new GrowthManager(this)
            .setGrowOverTime(false);
        this.resinManager = new ResinManager(this);
        this.xenomorphData = new XenomorphData(getRandom());
        this.entitySenseCache = EntitySenseCache.builder(this)
            .withScanRadius(40)
            .addTrackedTag(AlienEntityTypeTags.XENOMORPHS)
            .withRefreshPolicy(cache -> {
                var ticksSinceRefresh = cache.getEntity().tickCount - cache.getLastSenseTick();
                var wasRecentlyHurt = getLastHurtByMobTimestamp() > 0
                    && tickCount - getLastHurtByMobTimestamp() < 10;

                return ticksSinceRefresh > 20 || (wasRecentlyHurt && ticksSinceRefresh > 10);
            })
            .build();
        this.pathNavigator = createPathNavigator(level, config.pathConfig());
        this.hiveIntruderPathNavigator = createPathNavigator(level, config.pathConfig(), createHiveIntruderSearchConfig());
        this.cooldownTracker = new AttackCooldownTracker();
        this.wasUnderwaterLastTick = false;

        xenomorphData.setParallelDigCount(config.parallelDigCount());
        isCrawling.onChange($ -> refreshDimensions());
    }

    private PathNavigator createPathNavigator(Level level, XenomorphPathConfig pathConfig) {
        var followRange = (float) getAttributeValue(Attributes.FOLLOW_RANGE);

        return createPathNavigator(
            level,
            pathConfig,
            SearchConfig.fromFollowRange(followRange).withElevationWeight(0.5f)
        );
    }

    private PathNavigator createPathNavigator(Level level, XenomorphPathConfig pathConfig, SearchConfig searchConfig) {
        var crawlConfig = config.canCrawl()
            ? PathCrawlConfig.enabled(pathConfig.crawlHeight())
            : PathCrawlConfig.DISABLED;
        var waterConfig = PathWaterConfig.enabled((int) Math.ceil(pathConfig.entityHeight() * UNDERWATER_HEIGHT_SCALE));
        var evaluatorConfig = TerrainEvaluatorConfig.builder()
            .addTerrain(TerrainType.GROUND, 1.0f)
            .addTerrain(TerrainType.WATER, 1.5f)
            .withTerrainClassifier((reader, pos) -> classifyGroundWaterAvoidingHumanRazorWire(reader, pos, pathConfig))
            .withEntitySize(pathConfig.entityWidth(), pathConfig.entityHeight())
            .withCrawlConfig(crawlConfig)
            .withWaterConfig(waterConfig)
            .withBlockBreakingConfig(PATH_BLOCK_BREAKING_CONFIG)
            .withMaxFallDistance(14)
            .withCanOpenDoors(pathConfig.canOpenDoors())
            .build();

        var navigatorConfig = PathNavigatorConfig.builder(evaluatorConfig)
            .withSearchConfig(searchConfig)
            .withPathfindingProfile(PathfindingProfile.LEGACY_PERMISSIVE)
            .build();

        var classificationCache = TerrainCacheRegistry.getOrCreate(level, evaluatorConfig.getTerrainClassifier());

        return new PathNavigator(level, navigatorConfig, classificationCache);
    }

    private static @Nullable TerrainType classifyGroundWaterAvoidingHumanRazorWire(
        LevelReader level,
        BlockPos pos,
        XenomorphPathConfig pathConfig
    ) {
        if (
            hasBlockInPathVolume(
                level,
                pos,
                pathConfig.entityWidth(),
                pathConfig.entityHeight(),
                AlienBlockTags.HUMAN_RAZOR_WIRE
            )
        ) {
            return null;
        }

        return TerrainClassifiers.GROUND_AND_WATER.classify(level, pos);
    }

    private static boolean hasBlockInPathVolume(
        LevelReader level,
        BlockPos origin,
        int width,
        int height,
        TagKey<Block> blockTag
    ) {
        var radius = Math.max(0, (width - 1) / 2);

        for (var x = origin.getX() - radius; x <= origin.getX() + radius; x++) {
            for (var y = origin.getY(); y < origin.getY() + height; y++) {
                for (var z = origin.getZ() - radius; z <= origin.getZ() + radius; z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(blockTag)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean canPathBreakBlock(LevelReader level, BlockPos pos, BlockState state) {
        if (
            !(level instanceof Level world)
                || !world.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
        ) {
            return false;
        }

        return !state.hasBlockEntity()
            && state.getDestroySpeed(level, pos) >= 0.0f
            && !state.is(AlienBlockTags.XENOMORPH_IMMUNE);
    }

    private SearchConfig createHiveIntruderSearchConfig() {
        var searchConfig = SearchConfig.fromFollowRange(HIVE_INTRUDER_PATH_SEARCH_RANGE);

        return new SearchConfig(
            searchConfig.maxSearchNodes(),
            searchConfig.heuristicWeight(),
            HIVE_INTRUDER_MAX_PATH_LENGTH,
            0.5f
        );
    }

    @Override
    public final PathNavigator getPathNavigator() {
        return getHiveIntruderTargetOrNull() != null ? hiveIntruderPathNavigator : pathNavigator;
    }

    public final boolean hasActiveBLibPath() {
        return isPathActive(pathNavigator) || isPathActive(hiveIntruderPathNavigator);
    }

    private static boolean isPathActive(PathNavigator navigator) {
        var state = navigator.getState();
        return state.isPathPending() || state.isNavigating();
    }

    @Override
    public @Nullable EntityType<? extends Alien> getTypeForVariant(AlienVariant alienVariant) {
        return config.variantResolver().apply(alienVariant);
    }

    @Override
    protected float getHealthRegenPerSecond() {
        return config.healthRegenPerSecond();
    }

    @Override
    public boolean isPushedByFluid() {
        return config.isPushedByFluid();
    }

    public void runAttackAnimations() {
        var attackConfig = config.attackConfig();

        if (attackConfig == null || !attackConfig.hasRegulars()) {
            return;
        }

        var attack = attackConfig.selectRegular(random, cooldownTracker, this);

        if (attack == null) {
            return;
        }

        startAttack(attack, getTarget());
    }

    public void runDigAnimation() {
        runAttackAnimations();
    }

    public boolean isAttacking() {
        return !attackType.get().isNone();
    }

    public boolean isExecutingTriggeredAttack() {
        var attackConfig = config.attackConfig();
        return activeAttack != null && attackConfig != null && attackConfig.triggered().contains(activeAttack);
    }

    public boolean canUseAttack(AttackType attack) {
        return !attack.isNone() && attack.canUse(this);
    }

    protected void resetAttackType() {
        attackType.set(AttackType.NONE);
    }

    public void startAttack(AttackType attack, @Nullable LivingEntity target) {
        if (!canUseAttack(attack)) {
            return;
        }

        if (activeAttack != null) {
            completeActiveAttack();
        }

        var executor = attack.executorFactory().get();
        var totalTicks = executor.totalDurationInTicks(attack);

        activeAttack = attack;
        activeExecutor = executor;

        playAttackSound(attack);
        cooldownTracker.start(attack);

        attackType.set(attack);
        beginAttack(totalTicks);

        executor.onStart(this, attack, target);
    }

    private void completeActiveAttack() {
        if (activeExecutor != null && activeAttack != null) {
            activeExecutor.onComplete(this, activeAttack);
        }

        activeAttack = null;
        activeExecutor = null;
        resetAttackType();
        attackDurationInTicks.set(0);
    }

    private void playAttackSound(AttackType attack) {
        if (attack.sound() == null) {
            return;
        }

        playSound(
            attack.sound().get(),
            getSoundVolume(),
            (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F
        );
    }

    public AttackCooldownTracker getCooldownTracker() {
        return cooldownTracker;
    }

    public XenomorphConfig getConfig() {
        return config;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Xenomorph> Graph<T> getActiveGOAPGraph(Graph<T> defaultGraph) {
        return cocoonManager.shouldRunCocoonAction() ? (Graph<T>) CocoonGOAP.GRAPH : defaultGraph;
    }

    public void beginAttack(int durationInTicks) {
        attackDurationInTicks.set(durationInTicks);
        attackId.set(attackId.get() + 1);
    }

    /**
     * Transition the visible/synced attack-type without spinning up a new executor. Used by executors that want to swap
     * animations mid-flight (e.g. windup → active cleave).
     */
    public void transitionAttack(AttackType newAttackType, int newDurationInTicks) {
        attackType.set(newAttackType);
        beginAttack(newDurationInTicks);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            applyLostLimbMaxHealthPenalty();
            breakIntersectingCobwebs();
            escapeHumanRazorWire();
            breakFrenziedRaidObstructions();
            breakRaidContainmentTargets();
        }

        crawlingManager.tick();
        cocoonManager.maintainLockedState();

        growthManager.tick();
        resinManager.tick();
        xenomorphData.tick();

        updateDimensionsBasedOnWaterState();

        if (!level().isClientSide) {
            cooldownTracker.tick();
            clearExpiredHiveIntruderTarget();
        }

        if (!level().isClientSide && isLunging.get() && onGround()) {
            isLunging.set(false);
        }

        if (!level().isClientSide && activeAttack != null && !canUseAttack(activeAttack)) {
            completeActiveAttack();
        }

        if (!level().isClientSide && activeAttack != null && activeExecutor != null) {
            var stillActive = activeExecutor.onTick(this, activeAttack);

            if (!stillActive) {
                completeActiveAttack();
            }
        }

        if (!level().isClientSide) {
            var target = getTarget();

            if (target != null && !AlienPredicates.canContinueTargeting(this, target)) {
                setTarget(null);
            }

            if (this instanceof GOAPUser<?>) {
                tryAlertNearbyXenomorphs();
            }
        }
    }

    private void applyLostLimbMaxHealthPenalty() {
        var attributeInstance = getAttribute(Attributes.MAX_HEALTH);

        if (attributeInstance == null) {
            return;
        }

        attributeInstance.removeModifier(LOST_LIMB_MAX_HEALTH_MODIFIER);

        var detachedLimbs = countDetachedLimbs();

        if (detachedLimbs <= 0) {
            return;
        }

        attributeInstance.addTransientModifier(
            new AttributeModifier(
                LOST_LIMB_MAX_HEALTH_MODIFIER,
                -detachedLimbs * MAX_HEALTH_REDUCTION_PER_LOST_LIMB,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
        );

        if (getHealth() > getMaxHealth()) {
            setHealth(getMaxHealth());
        }
    }

    private int countDetachedLimbs() {
        if (!(this instanceof Dismemberable dismemberable)) {
            return 0;
        }

        var manager = dismemberable.getDismembermentManager();

        if (manager == null || !manager.hasAnyDetached()) {
            return 0;
        }

        var count = 0;

        for (var definition : LimbDefinitionRegistry.getDefinitions(getType())) {
            if (manager.isDetached(definition)) {
                count++;
            }
        }

        return count;
    }

    private void breakIntersectingCobwebs() {
        var level = level();
        var boundingBox = getBoundingBox();
        var minX = Mth.floor(boundingBox.minX);
        var minY = Mth.floor(boundingBox.minY);
        var minZ = Mth.floor(boundingBox.minZ);
        var maxX = Mth.floor(boundingBox.maxX);
        var maxY = Mth.floor(boundingBox.maxY);
        var maxZ = Mth.floor(boundingBox.maxZ);

        for (var x = minX; x <= maxX; x++) {
            for (var y = minY; y <= maxY; y++) {
                for (var z = minZ; z <= maxZ; z++) {
                    var pos = new BlockPos(x, y, z);

                    if (level.getBlockState(pos).is(Blocks.COBWEB)) {
                        level.destroyBlock(pos, false, this);
                    }
                }
            }
        }
    }

    private void escapeHumanRazorWire() {
        var center = averageIntersectingBlockCenter(AlienBlockTags.HUMAN_RAZOR_WIRE);

        if (center == null) {
            return;
        }

        getNavigation().stop();
        pathNavigator.stop();
        hiveIntruderPathNavigator.stop();

        var away = position().subtract(center.x, getY(), center.z);

        if (away.horizontalDistanceSqr() < 1.0E-4D) {
            away = Vec3.directionFromRotation(0.0F, getYRot());
        }

        var push = away.normalize().scale(0.18D);
        setDeltaMovement(getDeltaMovement().add(push.x, 0.0D, push.z));
    }

    private void breakFrenziedRaidObstructions() {
        if (tickCount % FRENZIED_RAID_BREAKOUT_INTERVAL_TICKS != 0) {
            return;
        }

        if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        if (!hasEffect(AlienMobEffects.getFrenzyHolder())) {
            return;
        }

        var pos = firstIntersectingFrenzyBreakoutBlock();
        if (pos == null) {
            pos = firstLineOfSightFrenzyBreakoutBlock();
        }
        if (pos == null) {
            return;
        }

        getNavigation().stop();
        pathNavigator.stop();
        hiveIntruderPathNavigator.stop();
        damageRaidBreakoutBlock(pos);
    }

    private @Nullable BlockPos firstIntersectingFrenzyBreakoutBlock() {
        var level = level();
        var boundingBox = getBoundingBox().inflate(0.08D);
        var minX = Mth.floor(boundingBox.minX);
        var minY = Mth.floor(boundingBox.minY);
        var minZ = Mth.floor(boundingBox.minZ);
        var maxX = Mth.floor(boundingBox.maxX);
        var maxY = Mth.floor(boundingBox.maxY);
        var maxZ = Mth.floor(boundingBox.maxZ);

        for (var x = minX; x <= maxX; x++) {
            for (var y = minY; y <= maxY; y++) {
                for (var z = minZ; z <= maxZ; z++) {
                    var pos = new BlockPos(x, y, z);
                    if (canFrenziedRaidBreakoutBlock(level.getBlockState(pos), pos)) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private @Nullable BlockPos firstLineOfSightFrenzyBreakoutBlock() {
        var target = getTarget();
        if (target == null || !target.isAlive() || getSensing().hasLineOfSight(target)) {
            return null;
        }

        var clipContext = new ClipContext(
            getEyePosition(),
            target.getEyePosition(),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            this
        );
        var hit = level().clip(clipContext);
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }

        var pos = hit.getBlockPos();
        return canFrenziedRaidBreakoutBlock(level().getBlockState(pos), pos) ? pos : null;
    }

    private boolean canFrenziedRaidBreakoutBlock(BlockState state, BlockPos pos) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(level(), pos) < 0.0F) {
            return false;
        }

        return state.is(AlienBlockTags.XENOMORPH_FRENZY_BREAKABLE);
    }

    private void breakRaidContainmentTargets() {
        if (tickCount % RAID_CONTAINMENT_BREAKOUT_INTERVAL_TICKS != 0) {
            return;
        }
        if (!level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }
        if (!hasEffect(AlienMobEffects.getFrenzyHolder()) || !ConvoyMemberTracker.isRaidMember(this)) {
            return;
        }
        if (this instanceof Queen) {
            return;
        }

        if (tryBreakOutNearbyQueen()) {
            clearRaidEggTarget();
            return;
        }
        if (!tryBreakOutNearbyEgg()) {
            clearRaidEggTarget();
        }
    }

    private boolean tryBreakOutNearbyQueen() {
        var bounds = getBoundingBox().inflate(RAID_CONTAINMENT_TARGET_RADIUS_BLOCKS);
        var queens = level().getEntitiesOfClass(
            Queen.class,
            bounds,
            queen -> queen != this
                && queen.isAlive()
                && !queen.isRemoved()
                && (queen.isContained() || queen.isInhibited())
                && queen.getVariant() == getVariant()
        );

        Queen nearest = null;
        var nearestDistance = Double.MAX_VALUE;
        for (var queen : queens) {
            var distance = distanceToSqr(queen);
            if (distance < nearestDistance) {
                nearest = queen;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return false;
        }

        var anchor = nearest.getBindManager()
            .anchors()
            .stream()
            .filter(pos -> level().getBlockEntity(pos) instanceof AnchorBlockEntity)
            .min((first, second) -> Double.compare(distanceToSqr(first.getCenter()), distanceToSqr(second.getCenter())))
            .orElse(null);
        if (anchor != null) {
            return workOnAnchor(anchor);
        }

        var containment = firstTargetedContainmentBlockAround(nearest);
        if (containment != null) {
            breakOrMoveToContainment(containment);
            return true;
        }
        moveToward(nearest.position());
        return true;
    }

    private boolean workOnAnchor(BlockPos anchorPos) {
        if (distanceToSqr(anchorPos.getCenter()) > RAID_CONTAINMENT_BREAK_RANGE_BLOCKS * RAID_CONTAINMENT_BREAK_RANGE_BLOCKS) {
            moveToward(anchorPos.getCenter());
            return true;
        }

        if (level().getBlockEntity(anchorPos) instanceof AnchorBlockEntity anchor) {
            if (damageRaidBreakoutBlock(anchorPos) == BlockBreakProgressManager.Result.DESTROYED) {
                anchor.release();
            }
            return true;
        }
        return false;
    }

    private boolean tryBreakOutNearbyEgg() {
        clearExpiredRaidEggFailures();

        var bounds = getBoundingBox().inflate(RAID_CONTAINMENT_TARGET_RADIUS_BLOCKS);
        var eggs = level().getEntitiesOfClass(
            Ovomorph.class,
            bounds,
            egg -> egg.isAlive()
                && !egg.isRemoved()
                && egg.getVariant() == getVariant()
                && !egg.getHatchManager().isHatching()
                && !egg.getHatchManager().isHatched()
                && ConvoyMemberTracker.isNearActiveRaidContext(egg, RAID_CONTAINMENT_TARGET_RADIUS_BLOCKS)
                && !isRaidEggFailureCoolingDown(egg.getUUID())
        );

        Ovomorph nearest = null;
        var nearestDistance = Double.MAX_VALUE;
        for (var egg : eggs) {
            if (!canAttemptRaidEgg(egg)) {
                continue;
            }
            var distance = distanceToSqr(egg);
            if (distance < nearestDistance) {
                nearest = egg;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return false;
        }

        updateRaidEggProgress(nearest, nearestDistance);
        if (shouldAbandonRaidEgg(nearest, nearestDistance)) {
            failRaidEggTarget(nearest.getUUID());
            return false;
        }

        var containment = firstTargetedContainmentBlockAround(nearest);
        if (containment != null) {
            breakOrMoveToContainment(containment);
            return true;
        }

        if (distanceToSqr(nearest) <= RAID_CONTAINMENT_BREAK_RANGE_BLOCKS * RAID_CONTAINMENT_BREAK_RANGE_BLOCKS) {
            nearest.tryHatch();
            clearRaidEggTarget();
        } else {
            moveToward(nearest.position());
        }
        return true;
    }

    private boolean canAttemptRaidEgg(Ovomorph egg) {
        if (raidEggHigherPriorityTargetCount(egg) >= RAID_EGG_MAX_ATTACKERS) {
            return false;
        }
        if (firstTargetedContainmentBlockAround(egg) != null) {
            return true;
        }

        var path = getNavigation().createPath(egg, 0);
        return path != null && path.canReach();
    }

    private boolean isCurrentRaidEggTarget(Ovomorph egg) {
        return raidEggTargetId != null && raidEggTargetId.equals(egg.getUUID());
    }

    private int raidEggHigherPriorityTargetCount(Ovomorph egg) {
        var bounds = egg.getBoundingBox().inflate(RAID_CONTAINMENT_TARGET_RADIUS_BLOCKS);
        var count = 0;

        for (
            var xenomorph : level().getEntitiesOfClass(
                Xenomorph.class,
                bounds,
                xenomorph -> xenomorph != this
                    && xenomorph.isAlive()
                    && !xenomorph.isRemoved()
                    && xenomorph.hasEffect(AlienMobEffects.getFrenzyHolder())
                    && ConvoyMemberTracker.isRaidMember(xenomorph)
            )
        ) {
            if (xenomorph.isCurrentRaidEggTarget(egg) && xenomorph.getUUID().compareTo(getUUID()) < 0) {
                count++;
            }
        }

        return count;
    }

    private void updateRaidEggProgress(Ovomorph egg, double distanceSqr) {
        if (!isCurrentRaidEggTarget(egg)) {
            raidEggTargetId = egg.getUUID();
            raidEggTargetStartedAtTick = tickCount;
            raidEggNoProgressTicks = 0;
            raidEggLastDistanceSqr = distanceSqr;
            return;
        }

        if (distanceSqr + RAID_EGG_PROGRESS_EPSILON_SQR < raidEggLastDistanceSqr) {
            raidEggNoProgressTicks = 0;
            raidEggLastDistanceSqr = distanceSqr;
            return;
        }

        raidEggNoProgressTicks += RAID_CONTAINMENT_BREAKOUT_INTERVAL_TICKS;
    }

    private boolean shouldAbandonRaidEgg(Ovomorph egg, double distanceSqr) {
        if (firstTargetedContainmentBlockAround(egg) != null) {
            return false;
        }
        if (tickCount - raidEggTargetStartedAtTick > RAID_EGG_MAX_ATTEMPT_TICKS) {
            return true;
        }
        if (raidEggNoProgressTicks > RAID_EGG_NO_PROGRESS_TICKS) {
            return true;
        }
        if (distanceSqr <= RAID_CONTAINMENT_BREAK_RANGE_BLOCKS * RAID_CONTAINMENT_BREAK_RANGE_BLOCKS) {
            return false;
        }

        var path = getNavigation().createPath(egg, 0);
        return path == null || !path.canReach();
    }

    private boolean isRaidEggFailureCoolingDown(UUID eggId) {
        var expiresAtTick = failedRaidEggTargets.get(eggId);
        if (expiresAtTick == null) {
            return false;
        }
        if (expiresAtTick <= tickCount) {
            failedRaidEggTargets.remove(eggId);
            return false;
        }
        return true;
    }

    private void failRaidEggTarget(UUID eggId) {
        failedRaidEggTargets.put(eggId, tickCount + RAID_EGG_FAILED_COOLDOWN_TICKS);
        clearRaidEggTarget();
        getNavigation().stop();
    }

    private void clearRaidEggTarget() {
        raidEggTargetId = null;
        raidEggTargetStartedAtTick = 0;
        raidEggNoProgressTicks = 0;
        raidEggLastDistanceSqr = Double.MAX_VALUE;
    }

    private void clearExpiredRaidEggFailures() {
        failedRaidEggTargets.entrySet().removeIf(entry -> entry.getValue() <= tickCount);
    }

    private @Nullable BlockPos firstTargetedContainmentBlockAround(Entity target) {
        var base = target.blockPosition();
        var orderedDirections = new Direction[] {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.UP,
            Direction.DOWN
        };

        for (var direction : orderedDirections) {
            var pos = base.relative(direction);
            if (canBreakTargetedContainmentBlock(pos)) {
                return pos;
            }
        }

        var above = base.above(2);
        if (canBreakTargetedContainmentBlock(above)) {
            return above;
        }
        return null;
    }

    private boolean canBreakTargetedContainmentBlock(BlockPos pos) {
        var state = level().getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        if (state.getDestroySpeed(level(), pos) < 0.0F || state.is(AlienBlockTags.XENOMORPH_IMMUNE)) {
            return false;
        }
        return state.is(AlienBlockTags.XENOMORPH_FRENZY_BREAKABLE)
            || state.isSuffocating(level(), pos)
            || !state.getCollisionShape(level(), pos).isEmpty();
    }

    private void breakOrMoveToContainment(BlockPos pos) {
        if (distanceToSqr(pos.getCenter()) > RAID_CONTAINMENT_BREAK_RANGE_BLOCKS * RAID_CONTAINMENT_BREAK_RANGE_BLOCKS) {
            moveToward(pos.getCenter());
            return;
        }

        getNavigation().stop();
        pathNavigator.stop();
        hiveIntruderPathNavigator.stop();
        damageRaidBreakoutBlock(pos);
    }

    private BlockBreakProgressManager.Result damageRaidBreakoutBlock(BlockPos pos) {
        var state = level().getBlockState(pos);
        if (!canDamageRaidBreakoutBlock(state, pos)) {
            return BlockBreakProgressManager.Result.NOT_DAMAGED;
        }
        return BlockBreakProgressManager.damage(level(), pos, RAID_BREAKOUT_BLOCK_DAMAGE_PER_ATTEMPT);
    }

    private boolean canDamageRaidBreakoutBlock(BlockState state, BlockPos pos) {
        if (level().getBlockEntity(pos) instanceof AnchorBlockEntity) {
            return state.getDestroySpeed(level(), pos) >= 0.0F && !state.is(AlienBlockTags.XENOMORPH_IMMUNE);
        }
        return canBreakTargetedContainmentBlock(pos) || canFrenziedRaidBreakoutBlock(state, pos);
    }

    private void moveToward(Vec3 pos) {
        getNavigation().moveTo(pos.x, pos.y, pos.z, RAID_CONTAINMENT_NAVIGATION_SPEED);
    }

    private @Nullable Vec3 averageIntersectingBlockCenter(TagKey<Block> blockTag) {
        var level = level();
        var boundingBox = getBoundingBox();
        var minX = Mth.floor(boundingBox.minX);
        var minY = Mth.floor(boundingBox.minY);
        var minZ = Mth.floor(boundingBox.minZ);
        var maxX = Mth.floor(boundingBox.maxX);
        var maxY = Mth.floor(boundingBox.maxY);
        var maxZ = Mth.floor(boundingBox.maxZ);
        var totalX = 0.0D;
        var totalY = 0.0D;
        var totalZ = 0.0D;
        var count = 0;

        for (var x = minX; x <= maxX; x++) {
            for (var y = minY; y <= maxY; y++) {
                for (var z = minZ; z <= maxZ; z++) {
                    var pos = new BlockPos(x, y, z);

                    if (level.getBlockState(pos).is(blockTag)) {
                        totalX += x + 0.5D;
                        totalY += y + 0.5D;
                        totalZ += z + 0.5D;
                        count++;
                    }
                }
            }
        }

        if (count <= 0) {
            return null;
        }

        return new Vec3(totalX / count, totalY / count, totalZ / count);
    }

    private void updateDimensionsBasedOnWaterState() {
        if (wasUnderwaterLastTick != isUnderWater()) {
            refreshDimensions();
        }

        this.wasUnderwaterLastTick = isUnderWater();
    }

    @Override
    public void travel(@NotNull Vec3 vec3) {
        if (cocoonManager.isLocked()) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        if (isControlledByLocalInstance() && isUnderWater()) {
            moveRelative(0.01F, vec3);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.9));
        } else {
            super.travel(vec3);
        }
    }

    @Override
    public void makeStuckInBlock(@NotNull BlockState blockState, @NotNull Vec3 movementMultiplier) {
        if (blockState.is(Blocks.COBWEB) || blockState.is(AlienBlockTags.HUMAN_RAZOR_WIRE)) {
            return;
        }

        super.makeStuckInBlock(blockState, movementMultiplier);
    }

    @Override
    public boolean startRiding(@NotNull Entity entity, boolean force) {
        if (entity instanceof Boat || entity instanceof Minecart) {
            return false;
        }

        return super.startRiding(entity, force);
    }

    @Override
    public @NotNull EntityDimensions getDefaultDimensions(@NotNull Pose pose) {
        var defaultDimensions = getType().getDimensions();
        var shouldBeSmall = crawlingManager.isCrawling() || isUnderWater();
        return defaultDimensions.scale(1, shouldBeSmall ? UNDERWATER_HEIGHT_SCALE : 1);
    }

    @Override
    public void updateDynamicGameEventListener(@NotNull BiConsumer<DynamicGameEventListener<?>, ServerLevel> biConsumer) {
        super.updateDynamicGameEventListener(biConsumer);
        resinManager.updateDynamicGameEventListener(biConsumer);
    }

    @Override
    public boolean canDisableShield() {
        return true;
    }

    @Override
    public float getWalkTargetValue(@NotNull BlockPos blockPos, @NotNull LevelReader levelReader) {
        return 0.0F;
    }

    @Override
    public boolean updateFluidHeightAndDoFluidPushing(@NotNull TagKey<Fluid> tagKey, double d) {
        var modifier = d;

        if (Objects.equals(tagKey, FluidTags.WATER)) {
            modifier = 0;
        }

        if (isNetherAfflicted() && Objects.equals(tagKey, FluidTags.LAVA)) {
            modifier = 0;
        }

        return super.updateFluidHeightAndDoFluidPushing(tagKey, modifier);
    }

    @Override
    public void setTarget(@Nullable LivingEntity livingEntity) {
        if (cocoonManager.isLocked() && livingEntity != null) {
            return;
        }

        if (livingEntity != null && !livingEntity.equals(getTarget()) && ambientSoundTime > getAmbientSoundInterval()) {
            playSound(
                AlienSoundEvents.ENTITY_XENOMORPH_HISS.get(),
                getSoundVolume(),
                (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F
            );
        }

        super.setTarget(livingEntity);
    }

    public void setHiveIntruderTarget(LivingEntity livingEntity) {
        if (cocoonManager.isLocked()) {
            return;
        }

        var activeTarget = getHiveIntruderTargetOrNull();

        if (activeTarget != livingEntity) {
            hiveIntruderPathNavigator.stop();
        }

        hiveIntruderTarget = livingEntity;
        hiveIntruderTargetExpiresAtTick = tickCount + HIVE_INTRUDER_TARGET_MEMORY_TICKS;
        setTarget(livingEntity);
    }

    public @Nullable LivingEntity getHiveIntruderTargetOrNull() {
        if (hiveIntruderTarget == null || tickCount > hiveIntruderTargetExpiresAtTick) {
            return null;
        }

        if (hiveIntruderTarget.isRemoved() || !AlienPredicates.canContinueTargeting(this, hiveIntruderTarget)) {
            return null;
        }

        return hiveIntruderTarget;
    }

    private void clearExpiredHiveIntruderTarget() {
        if (hiveIntruderTarget != null && getHiveIntruderTargetOrNull() == null) {
            var expiredTarget = hiveIntruderTarget;

            hiveIntruderTarget = null;
            hiveIntruderTargetExpiresAtTick = 0;
            hiveIntruderPathNavigator.stop();

            if (expiredTarget == getTarget() && !AlienPredicates.canContinueTargeting(this, expiredTarget)) {
                setTarget(null);
            }
        }
    }

    private void tryAlertNearbyXenomorphs() {
        var attacker = getLastHurtByMob();
        var hurtTimestamp = getLastHurtByMobTimestamp();

        if (attacker == null || hurtTimestamp == xenomorphData.getLastAlertedHurtTimestamp()) {
            return;
        }

        xenomorphData.setLastAlertedHurtTimestamp(hurtTimestamp);

        var nearbyXenomorphs = entitySenseCache.getByTag(AlienEntityTypeTags.XENOMORPHS);

        for (var entity : nearbyXenomorphs) {
            if (
                entity instanceof Xenomorph xenomorph
                    && xenomorph != this
                    && xenomorph.getTarget() == null
                    && AlienPredicates.canAcquireTarget(xenomorph, attacker)
            ) {
                xenomorph.setTarget(attacker);
            }
        }
    }

    @Override
    public boolean canAttack(@NotNull LivingEntity target) {
        return !cocoonManager.isLocked() && super.canAttack(target) && AlienPredicates.canContinueTargeting(this, target);
    }

    @Override
    protected void doPush(Entity entity) {
        if (
            !entity.getType().is(AlienEntityTypeTags.FACEHUGGERS)
                && !entity.getType().is(AlienEntityTypeTags.CHESTBURSTERS)
                && !entity.getType().is(AlienEntityTypeTags.ADOLESCENTS)
        ) {
            super.doPush(entity);
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return 6 * 20;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return getTarget() != null ? AlienSoundEvents.ENTITY_XENOMORPH_HISS.get() : AlienSoundEvents.ENTITY_XENOMORPH_IDLE.get();
    }

    @Override
    protected @NotNull SoundEvent getDeathSound() {
        return AlienSoundEvents.ENTITY_XENOMORPH_DEATH.get();
    }

    @Override
    protected @NotNull SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return AlienSoundEvents.ENTITY_XENOMORPH_HURT.get();
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        crawlingManager.load(compoundTag);
        cocoonManager.load(compoundTag);
        growthManager.load(compoundTag);
        resinManager.load(compoundTag);
        xenomorphData.load(compoundTag);
        cooldownTracker.load(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        crawlingManager.save(compoundTag);
        cocoonManager.save(compoundTag);
        growthManager.save(compoundTag);
        resinManager.save(compoundTag);
        xenomorphData.save(compoundTag);
        cooldownTracker.save(compoundTag);
    }

    public GrowthManager getGrowthManager() {
        return growthManager;
    }

    public CocoonManager getCocoonManager() {
        return cocoonManager;
    }

    @Override
    public ResinManager getResinManager() {
        return resinManager;
    }

    public XenomorphData getXenomorphData() {
        return xenomorphData;
    }

    @Override
    public EntitySenseCache getEntitySenseCache() {
        return entitySenseCache;
    }

    public CrawlingManager getCrawlingManager() {
        return crawlingManager;
    }
}
