package com.alien.common.gameplay.entity.living.alien;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.xenomorph.RoyalCandidateProgress;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.runner.Runner;
import com.alien.common.gameplay.hive.bootstrap.RoyalBootstrapLeakRecorder;
import com.alien.common.gameplay.hive.convoy.ConvoyId;
import com.alien.common.gameplay.hive.convoy.ConvoyMemberTracker;
import com.alien.common.gameplay.hive.convoy.ConvoyMembership;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.gameplay.level.saveddata.StrainLeakData;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.tag.AlienDamageTypesTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.registry.tag.AlienMobEffectTags;
import com.alien.common.util.AcidBleedUtil;
import com.alien.common.util.AlienTransitionUtil;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_human.GeneManagerProxy;
import com.alien.compatibility.avp_predator.AVPPredator;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.data_sync.v1.model.DataUser;
import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;
import com.blib.api.common.dismemberment.v1.LimbDismemberer;
import com.blib.api.common.entity.v1.MovementAnalyzer;
import com.blib.mod.common.registry.init.BLibDataSyncKeys;
import com.human.common.gameplay.gene.Genes;
import com.human.common.registry.key.HumanBiomeKeys;
import com.just.core.functional.option.Option;
import com.predator.common.registry.init.PredatorEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public abstract class Alien extends Monster implements DataUser {

    private static final String NBT_HOST_TYPE = "hostType";

    private static final String NBT_CONVOY_MEMBERSHIP = "HiveConvoyMembership";

    private static final String NBT_RAID_MEMBERSHIP = "HiveRaidMembership";

    private static final String NBT_LEGACY_CONVOY_MEMBERSHIP = "Hive2ConvoyMembership";

    private static final String NBT_LEGACY_RAID_MEMBERSHIP = "Hive2RaidMembership";

    // Idle pathing uses 0.5x speed and pursuit uses 1.1x; 0.8x splits the two for animation.
    private static final double RUN_ANIMATION_SPEED_THRESHOLD_MULTIPLIER = 0.8D;

    public final DataAccessor<Boolean> hasTarget;

    public final DataAccessor<Boolean> isPoisoned;

    public final DataAccessor<Float> moltAlpha;

    public final DataAccessor<Boolean> isMovingHorizontally;

    public final DataAccessor<Boolean> isMovingQuickly;

    protected final HiveManager hiveManager;

    protected final MovementAnalyzer movementAnalyzer;

    private final MoltingManager moltingManager;

    private Option<EntityType<?>> hostTypeOption;

    private @Nullable ConvoyMembership convoyMembership;

    private int lastHurtTimeInTicks;

    private boolean hasAnimationMovementSample;

    private double lastAnimationMovementSampleX;

    private double lastAnimationMovementSampleZ;

    protected Alien(EntityType<? extends Alien> entityType, Level level) {
        super(entityType, level);

        this.hasTarget = new DataAccessor<>(this, BLibDataSyncKeys.ENTITY_HAS_TARGET.get());
        this.isPoisoned = new DataAccessor<>(this, AlienDataSyncKeys.ALIEN_IS_POISONED.get());
        this.moltAlpha = new DataAccessor<>(this, AlienDataSyncKeys.ALIEN_MOLT_ALPHA.get());
        this.isMovingHorizontally = new DataAccessor<>(this, BLibDataSyncKeys.ENTITY_IS_MOVING_HORIZONTALLY.get());
        this.isMovingQuickly = new DataAccessor<>(this, AlienDataSyncKeys.ALIEN_IS_MOVING_QUICKLY.get());

        this.hiveManager = new HiveManager(this);
        this.movementAnalyzer = new MovementAnalyzer(this);
        this.moltingManager = new MoltingManager(this);

        this.hostTypeOption = Option.ofNullable(getDefaultHostType(entityType));
        this.lastHurtTimeInTicks = 0;
    }

    public abstract @Nullable EntityType<? extends Alien> getTypeForVariant(AlienVariant alienVariant);

    protected abstract float getHealthRegenPerSecond();

    @Override
    public float maxUpStep() {
        return 1.5F;
    }

    @Override
    public void push(Entity entity) {
        if (entity instanceof Alien) {
            super.push(entity);
        }
    }

    private EntityType<? extends Entity> getDefaultHostType(EntityType<? extends Alien> entityType) {
        if (
            entityType.is(AlienEntityTypeTags.RUNNERS)
                || entityType.is(AlienEntityTypeTags.PROWLERS)
                || entityType.is(AlienEntityTypeTags.CRUSHERS)
        ) {
            return EntityType.PIG;
        } else if (entityType.is(AlienEntityTypeTags.SPITTERS)) {
            return EntityType.LLAMA;
        }

        if (AVPPredator.MOD.isLoaded()) {
            if (entityType.is(AlienEntityTypeTags.PREDALIENS)) {
                return PredatorEntityTypes.YAUTJA.get();
            }
        }

        return EntityType.VILLAGER;
    }

    protected boolean canBleedAcid() {
        return true;
    }

    protected boolean canHeal() {
        var requiredTicksAfterHurtToHeal = 10 * 20;
        return this.getTarget() == null && tickCount > getLastHurtByMobTimestamp() + requiredTicksAfterHurtToHeal;
    }

    @Override
    public void setTarget(@Nullable LivingEntity livingEntity) {
        super.setTarget(livingEntity);
        // Hive: the per-location boss bar auto-adds in-range players via HiveLocationBossBar.updateTrackingPlayers
        // every 20 ticks; no manual track-on-target hook needed.
    }

    public AlienVariant getVariant() {
        if (isAberrant()) {
            return AlienVariant.ABERRANT;
        } else if (isIrradiated()) {
            return AlienVariant.IRRADIATED;
        } else if (isNetherAfflicted()) {
            return AlienVariant.NETHER;
        }

        return AlienVariant.NORMAL;
    }

    public boolean isAberrant() {
        return Objects.equals(getType(), getTypeForVariant(AlienVariant.ABERRANT));
    }

    public boolean isIrradiated() {
        return Objects.equals(getType(), getTypeForVariant(AlienVariant.IRRADIATED));
    }

    public boolean isNetherAfflicted() {
        return Objects.equals(getType(), getTypeForVariant(AlienVariant.NETHER));
    }

    private void applyMalusBasedOnVariant() {
        if (isNetherAfflicted()) {
            setPathfindingMalus(PathType.LAVA, 0.0F);
            setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
            setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        } else {
            // Hard-avoid lava/fire for non-Nether aliens. Vanilla LAVA malus is only -1 ("avoid but pass if the target
            // demands it"), which let queens walk into flowing lava while chasing prey and burn to death. A large
            // positive malus makes the pathfinder treat these as effectively impassable, so pursuit routes around them
            // instead of through them.
            setPathfindingMalus(PathType.LAVA, 16.0F);
            setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
            setPathfindingMalus(PathType.DAMAGE_FIRE, 16.0F);
        }
    }

    public boolean isPoisoned() {
        return isPoisoned.get();
    }

    public void setPoisoned(boolean isPoisoned) {
        this.isPoisoned.set(isPoisoned);
    }

    public boolean isRoyal() {
        return getType().is(AlienEntityTypeTags.ROYAL_ALIENS);
    }

    protected boolean canEntityRideAlien(@NotNull Entity passenger) {
        return false;
    }

    @Override
    protected final boolean canAddPassenger(@NotNull Entity passenger) {
        return getPassengers().size() < getMaxPassengerCount() && canEntityRideAlien(passenger);
    }

    protected int getMaxPassengerCount() {
        return 1;
    }

    protected boolean canAlienRideVehicle(@NotNull Entity vehicle) {
        return !(vehicle instanceof Boat) && !(vehicle instanceof Minecart);
    }

    @Override
    protected final boolean canRide(@NotNull Entity vehicle) {
        return super.canRide(vehicle) && canAlienRideVehicle(vehicle);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        @NotNull ServerLevelAccessor level,
        @NotNull DifficultyInstance difficulty,
        @NotNull MobSpawnType spawnType,
        @Nullable SpawnGroupData spawnGroupData
    ) {
        // Hive: variant-faction join is event-driven. finalizeSpawn fires once per fresh-spawned alien
        // (natural, spawn egg, command). Idempotent — see HiveManager.ensureVariantFactionMembership.
        hiveManager.ensureVariantFactionMembership();

        // Hive: if this alien spawned inside a location that has it in its reserves, decrement the reserves and
        // copy genes from the location's leader (preserves the legacy "spawned alien inherits leader's genes"
        // behavior).
        var locationAtPos = HiveLocationRegistry.INSTANCE.getByChunk(
            level.getLevel().dimension(),
            new net.minecraft.world.level.ChunkPos(blockPosition())
        );
        if (locationAtPos != null && locationAtPos.isAlive()) {
            if (locationAtPos.localReserves().getCount(getType()) > 0) {
                if (locationAtPos.localReserves().trySpawn(getType())) {
                    ReserveSpawnUtil.markSpawnedFromReserves(this);
                }
            }

            var leaderId = locationAtPos.leadership().getLeaderIdOrNull();
            if (leaderId != null) {
                var leaderEntity = level.getLevel().getEntity(leaderId);
                if (leaderEntity instanceof Alien leaderAlien) {
                    var leaderGeneContainer = GeneManagerProxy.getOrCreate(leaderAlien);
                    var selfGeneContainer = GeneManagerProxy.getOrCreate(this);
                    leaderGeneContainer.transfer(selfGeneContainer, true);
                }
            }

            // Hive: any xenomorph spawning into a claimed chunk auto-joins both the owning lineage and the location
            // faction. Covers natural spawns, spawn eggs, /summon, and MOB_SUMMONED reinforcements/raid units that
            // funnel through finalizeSpawn. (Note: EntityTransitionUtil.transitionInto does NOT call finalizeSpawn —
            // transitions carry membership over explicitly via FactionMembershipTransfer.) The Phase 9 invariant task
            // evicts variant mismatches, so cross-variant strangers don't stick.
            if (getType().is(AlienEntityTypeTags.XENOMORPHS)) {
                LocationMembership.join(locationAtPos, this);
            }
        }

        return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && ConvoyMemberTracker.discardStaleLoadedMember(this)) {
            discard();
            return;
        }

        super.tick();

        if (!level().isClientSide) {
            movementAnalyzer.tick();
        }

        hiveManager.tick();
        moltingManager.tick();

        if (!level().isClientSide) {
            hasTarget.set(getTarget() != null);
            isMovingHorizontally.set(movementAnalyzer.isMovingHorizontally());
            isMovingQuickly.set(updateMovingQuicklyForAnimation());

            if (getVehicle() != null && !canRide(getVehicle())) {
                stopRiding();
            }

            if (!getPassengers().isEmpty()) {
                var passengersToRemove = getPassengers().stream()
                    .filter(Predicate.not(this::canEntityRideAlien))
                    .toList();

                passengersToRemove.forEach(Entity::stopRiding);
            }

            healPassively();
            applyMalusBasedOnVariant();
            applyDynamicAttributes();
            becomeIrradiated();
        }
    }

    private boolean updateMovingQuicklyForAnimation() {
        var currentX = getX();
        var currentZ = getZ();

        if (!hasAnimationMovementSample) {
            hasAnimationMovementSample = true;
            lastAnimationMovementSampleX = currentX;
            lastAnimationMovementSampleZ = currentZ;
            return false;
        }

        var deltaX = currentX - lastAnimationMovementSampleX;
        var deltaZ = currentZ - lastAnimationMovementSampleZ;

        lastAnimationMovementSampleX = currentX;
        lastAnimationMovementSampleZ = currentZ;

        var speedThreshold = Math.max(
            0.01D,
            getAttributeValue(Attributes.MOVEMENT_SPEED) * RUN_ANIMATION_SPEED_THRESHOLD_MULTIPLIER
        );

        return deltaX * deltaX + deltaZ * deltaZ >= speedThreshold * speedThreshold;
    }

    /**
     * 10% chance when in Nuked Biome to become Irradiated
     */
    private void becomeIrradiated() {
        if (!AVPHuman.MOD.isLoaded()) {
            return;
        }

        if (tickCount % 60 != 0) {
            return;
        }

        if (!level().getBiome(blockPosition()).is(HumanBiomeKeys.NUKED_BIOME)) {
            return;
        }

        if (!isAlive()) {
            return;
        }

        if (getRandom().nextIntBetweenInclusive(1, 100) >= 90) {
            AlienTransitionUtil.transitionIntoVariant(this, AlienVariant.IRRADIATED);
        }
    }

    @Override
    public void thunderHit(@NotNull ServerLevel level, @NotNull LightningBolt lightning) {
        var aberrantType = getTypeForVariant(AlienVariant.ABERRANT);

        if (aberrantType != null && !Objects.equals(getType(), aberrantType)) {
            AlienTransitionUtil.transitionIntoVariant(this, AlienVariant.ABERRANT);
            return;
        }

        super.thunderHit(level, lightning);
    }

    private void healPassively() {
        if (tickCount % 20 != 0)
            return;
        if (getHealth() >= getMaxHealth())
            return;
        if (!isAlive())
            return;

        if (canHeal()) {
            var healthBefore = getHealth();
            heal(getHealthRegenPerSecond());
            var healed = getHealth() - healthBefore;
            if (healed > 0.0F && this instanceof Dismemberable dismemberable) {
                dismemberable.getDismembermentManager().healLimbDamage(healed);
            }
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return damageSource.is(AlienDamageTypesTags.DOES_NOT_HURT_ALIENS) || super.isInvulnerableTo(damageSource);
    }

    @Override
    public boolean killedEntity(@NotNull ServerLevel level, @NotNull LivingEntity entity) {
        var killedEntity = super.killedEntity(level, entity);

        if (
            // If entity was successfully killed...
            killedEntity
                // AND this alien type can reproduce...
                && AlienVariantTypes.getFor(getVariant()).canReproduce()
                // AND the entity killed was not an alien (hive wars shouldn't result in endless growth)...
                && !entity.getType().is(AlienEntityTypeTags.ALIENS)
        ) {
            // Hive: add a bonus drone or runner (depending on host type) to the reserves of the location whose
            // chunk this alien is standing in. No-op when the alien is outside any claimed chunk — feral aliens
            // don't generate reserves.
            var location = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), chunkPosition());
            if (location != null && location.isAlive()) {
                var wasRunnerHostKilled = entity.getType().is(AlienEntityTypeTags.RUNNER_HOSTS);

                var bonusCount = switch (getGeneManager()) {
                    case GeneManagerProxy.EMPTY ignored -> 1;
                    case GeneManagerProxy.Wrapper geneManagerProxy -> (int) geneManagerProxy.geneManager()
                        .getGeneContainer()
                        .getActiveGeneMap()
                        .getValue(Genes.BONUS_EMBRYO_COUNT);
                };

                var alienEntityType = wasRunnerHostKilled
                    ? Runner.getType(getVariant())
                    : Drone.getType(getVariant());

                location.localReserves().tryAdd((EntityType<?>) alienEntityType, bonusCount);
            }
        }

        if (killedEntity && this instanceof Xenomorph xenomorph) {
            RoyalCandidateProgress.onKilledEntity(xenomorph, level, entity);
        }

        return killedEntity;
    }

    /** Floor chance per limb for an explosion to tear it off, applied even on grazing hits. */
    private static final float EXPLOSION_LIMB_BASE_CHANCE = 0.10F;

    /** Bonus chance per limb scaled by how much of the alien's max health the explosion consumed (capped at 1×). */
    private static final float EXPLOSION_LIMB_DAMAGE_BONUS = 0.40F;

    /** Floor chance per leg for a fall to break it off, applied even on shallow drops that still register damage. */
    private static final float FALL_LEG_BASE_CHANCE = 0.05F;

    /** Bonus chance per leg scaled by how much of the alien's max health the fall consumed (capped at 1×). */
    private static final float FALL_LEG_DAMAGE_BONUS = 0.30F;

    /** Bonus chance per leg scaled by how depleted the alien's health is post-fall (capped at 1×). */
    private static final float FALL_LEG_LOW_HEALTH_BONUS = 0.30F;

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float damage) {
        var healthBefore = getHealth();
        var isHurt = super.hurt(damageSource, damage);

        if (isHurt) {
            this.lastHurtTimeInTicks = tickCount;

            var alienVariantType = AlienVariantTypes.getFor(this);

            if (
                isNetherAfflicted()
                    && !damageSource.is(DamageTypeTags.AVOIDS_GUARDIAN_THORNS)
                    && damageSource.getDirectEntity() instanceof LivingEntity livingEntity
            ) {
                livingEntity.igniteForSeconds(4);
            }

            if (damageSource.getEntity() != null) {
                // Cry for help so that nearby vents may try and summon help.
                gameEvent(alienVariantType.cryForHelpEvent());
            }

            if (canBleedAcid() && damageSource != damageSources().genericKill()) {
                var randomPos = AcidBleedUtil.computeRandomPosFromBoundingBox(this);
                AcidBleedUtil.spawnAcid(this, damage, randomPos);
            }

            if (!level().isClientSide && damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
                var damageDealt = Math.max(0F, healthBefore - getHealth());

                if (damageDealt > 0F) {
                    rollExplosionDismemberment(damageDealt);
                }
            }

            if (!level().isClientSide && damageSource.is(DamageTypeTags.IS_FALL)) {
                var damageDealt = Math.max(0F, healthBefore - getHealth());

                if (damageDealt > 0F) {
                    rollFallLegDismemberment(damageDealt);
                }
            }
        }

        return isHurt;
    }

    /**
     * Rolls each registered limb independently for explosion-driven dismemberment. Probability per limb scales with how
     * much of max health the explosion stripped, so tossing TNT under a drone is much more dangerous than catching the
     * edge of a creeper blast.
     * <p>
     * Eligibility rules:
     * <ul>
     * <li>Head-category limbs are only eligible if the explosion <em>killed</em> the alien — surviving an explosion
     * never costs you your head.</li>
     * <li>Leg-category limbs are off-limits for xenomorphs whose {@code CrawlingManager} reports
     * {@code canCrawl() == false} — they wouldn't be able to crawl after, and standing on remaining legs reads
     * weird.</li>
     * <li>Arm- and tail-category limbs are always eligible.</li>
     * </ul>
     */
    private void rollExplosionDismemberment(float damageDealt) {
        if (!(this instanceof Dismemberable dismemberable)) {
            return;
        }

        var manager = dismemberable.getDismembermentManager();

        if (manager == null) {
            return;
        }

        var definitions = LimbDefinitionRegistry.getDefinitions(getType());

        if (definitions.isEmpty()) {
            return;
        }

        var maxHealth = getMaxHealth();
        var damageRatio = maxHealth > 0F ? Mth.clamp(damageDealt / maxHealth, 0F, 1F) : 0F;
        var perLimbChance = Mth.clamp(
            EXPLOSION_LIMB_BASE_CHANCE + EXPLOSION_LIMB_DAMAGE_BONUS * damageRatio,
            0F,
            1F
        );

        var canLoseLegs = !(this instanceof Xenomorph xeno) || xeno.getCrawlingManager().canCrawl();
        var killedByExplosion = isDeadOrDying();

        for (var definition : definitions) {
            if (manager.isDetached(definition)) {
                continue;
            }

            var category = definition.category();

            if (category.equals(LimbCategories.HEAD) && !killedByExplosion) {
                continue;
            }

            if (!canLoseLegs && category.equals(LimbCategories.LEG)) {
                continue;
            }

            if (random.nextFloat() < perLimbChance) {
                LimbDismemberer.detach(this, definition.id(), null);
            }
        }
    }

    /**
     * Rolls each leg-category limb independently for fall-driven dismemberment. Only crawl-capable xenomorphs are
     * eligible — losing a leg forces them into a crawl, so a mob that can't crawl would otherwise be stuck. Probability
     * scales with both the fraction of max health the fall consumed and how depleted the alien's remaining health is,
     * so a beat-up xeno hitting the ground hard is far more likely to come up missing a leg than a healthy one taking a
     * shallow drop.
     */
    private void rollFallLegDismemberment(float damageDealt) {
        if (!(this instanceof Xenomorph xeno) || !xeno.getCrawlingManager().canCrawl()) {
            return;
        }

        if (!(this instanceof Dismemberable dismemberable)) {
            return;
        }

        var manager = dismemberable.getDismembermentManager();

        if (manager == null) {
            return;
        }

        var legDefinitions = LimbDefinitionRegistry.getDefinitionsByCategory(getType(), LimbCategories.LEG);

        if (legDefinitions.isEmpty()) {
            return;
        }

        var maxHealth = getMaxHealth();

        if (maxHealth <= 0F) {
            return;
        }

        var damageRatio = Mth.clamp(damageDealt / maxHealth, 0F, 1F);
        var lowHealthRatio = Mth.clamp(1F - getHealth() / maxHealth, 0F, 1F);
        var perLegChance = Mth.clamp(
            FALL_LEG_BASE_CHANCE
                + FALL_LEG_DAMAGE_BONUS * damageRatio
                + FALL_LEG_LOW_HEALTH_BONUS * lowHealthRatio,
            0F,
            1F
        );

        for (var definition : legDefinitions) {
            if (manager.isDetached(definition)) {
                continue;
            }

            if (random.nextFloat() < perLegChance) {
                LimbDismemberer.detach(this, definition.id(), null);
            }
        }
    }

    // Prevent the alien from drowning or otherwise running out of air.
    @Override
    public int getAirSupply() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected float getWaterSlowDown() {
        return 0.9F;
    }

    // Prevent fall damage below certain distance values.
    @Override
    public int calculateFallDamage(float fallDistance, float damageMultiplier) {
        return fallDistance < 16 ? 0 : super.calculateFallDamage(fallDistance, damageMultiplier);
    }

    // Max fall distance for pathfinding purposes.
    @Override
    public int getMaxFallDistance() {
        return 14;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance mobEffectInstance) {
        if (mobEffectInstance.getEffect().is(AlienMobEffectTags.DOES_NOT_AFFECT_ALIENS)) {
            return false;
        }

        return super.canBeAffected(mobEffectInstance);
    }

    @Override
    public boolean dampensVibrations() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return isNetherAfflicted() || super.fireImmune();
    }

    @Override
    public boolean isPersistenceRequired() {
        if (super.isPersistenceRequired()) {
            return true;
        }
        // Hive: an alien is persistent if it's standing in a hive location and either (a) the location's boss bar
        // is angry (an active fight), or (b) it's the location's current leader.
        var location = HiveLocationRegistry.INSTANCE.getByChunk(level().dimension(), chunkPosition());
        if (location == null) {
            return false;
        }
        var bossBar = location.bossBar();
        var bossBarAngry = bossBar != null && bossBar.isAngry();
        var isLeader = location.leadership().isLeader(this);
        return bossBarAngry || isLeader;
    }

    @Override
    public boolean shouldBeSaved() {
        if (convoyMembership != null) {
            return false;
        }
        return super.shouldBeSaved();
    }

    @Override
    public void checkDespawn() {
        var wasAlive = isAlive() && !isRemoved();
        var returnLocation = wasAlive && getType().is(AlienEntityTypeTags.XENOMORPHS)
            ? reserveReturnLocation()
            : null;

        super.checkDespawn();

        if (wasAlive && isRemoved()) {
            onDespawned(returnLocation);
        }
    }

    private void onDespawned(@Nullable HiveLocation returnLocation) {
        // Hive: hive-owned xenomorphs always return to their owning location's reserves when vanilla despawns them,
        // even if they wandered into an unclaimed chunk. Feral xenomorphs still count as strain leaks.
        if (getType().is(AlienEntityTypeTags.XENOMORPHS)) {
            if (ConvoyMemberTracker.returnDespawned(this)) {
                return;
            }
            if (returnLocation != null && returnToHiveLocation(returnLocation)) {
                return;
            }

            RoyalBootstrapLeakRecorder.recordIfEligible(this);
            onStrainLeak();
        }
    }

    private @Nullable HiveLocation reserveReturnLocation() {
        var ownedLocation = HiveMemberLocationResolver.reserveReturnLocation(this);
        if (ownedLocation != null) {
            return ownedLocation;
        }

        return HiveLocationRegistry.INSTANCE.getByChunk(level().dimension(), chunkPosition());
    }

    private boolean returnToHiveLocation(HiveLocation location) {
        return location.localReserves().addReturningMember(getType(), 1);
    }

    private void onStrainLeak() {
        StrainLeakData.getOrCreate(level())
            .ifSome(strainLeakData -> {
                var alienVariant = getVariant();
                var wasAlienVariantAlreadyPresent = strainLeakData.hasVariant(alienVariant);
                var alienVariantType = AlienVariantTypes.getFor(this);

                if (!(level() instanceof ServerLevel serverLevel)) {
                    return;
                }

                var strainBasedLeakMessage = getStrainLeakMessageForVariant(alienVariant);

                if (strainBasedLeakMessage == null) {
                    return;
                }

                strainLeakData.add(alienVariant, 1);

                if (!wasAlienVariantAlreadyPresent) {
                    for (var player : serverLevel.players()) {
                        player.sendSystemMessage(
                            Component.literal(strainBasedLeakMessage)
                                .withStyle(alienVariantType.chatColor(), ChatFormatting.ITALIC)
                        );
                    }
                }
            });
    }

    @Override
    public void remove(@NotNull RemovalReason removalReason) {
        super.remove(removalReason);
        // Hive: BLib's faction system handles removal cleanup automatically when the entity is killed or
        // discarded — no manual hive.removeHiveMember call needed.
    }

    @Override
    public void die(@NotNull DamageSource damageSource) {
        // Hive raid attribution: if a player gets the kill credit, record it against every lineage this alien
        // belongs to. Defers to vanilla's getKillCredit so indirect kills (TNT, fall damage from broken block,
        // etc) count when vanilla counts them.
        if (getType().is(AlienEntityTypeTags.XENOMORPHS)) {
            var killer = getKillCredit();
            if (
                !ConvoyMemberTracker.isRaidMember(this)
                    && killer instanceof ServerPlayer player
                    && level() instanceof ServerLevel serverLevel
            ) {
                attributeKillToLineages(player.getUUID(), serverLevel.getGameTime());
                recordHiveCombatKill(player);
            }
            ConvoyMemberTracker.unregisterKilled(this);
            // Hive empress death clears the lineage's empress slot so the next emergence ritual can fire.
            if (getType().is(AlienEntityTypeTags.EMPRESSES)) {
                onEmpressDied();
            }
        }

        super.die(damageSource);
    }

    private void onEmpressDied() {
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(getUUID())) {
            if (!com.alien.common.gameplay.hive.id.LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = com.alien.Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (getUUID().equals(lineage.empressId())) {
                lineage.setEmpressId(null);
            }
            com.alien.Alien.LOGGER.info(
                "Hive: empress {} died — lineage {} has {} location(s); empress slot cleared",
                getUUID(),
                factionId,
                lineage.locationsById().size()
            );
        }
    }

    private void attributeKillToLineages(java.util.UUID playerId, long currentTick) {
        var aggroWindow = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.config().raidAggroWindowTicks();
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(getUUID())) {
            if (!com.alien.common.gameplay.hive.id.LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = com.alien.Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            lineage.recordKillByPlayer(playerId, currentTick, aggroWindow);
        }
    }

    private void recordHiveCombatKill(ServerPlayer player) {
        var location = HiveMemberLocationResolver.reserveReturnLocation(this);
        if (location == null) {
            location = HiveLocationRegistry.INSTANCE.getByChunk(level().dimension(), chunkPosition());
        }
        if (location == null || !location.isAlive()) {
            return;
        }

        location.recordCombatKill(player.blockPosition(), HiveLocationRegistry.INSTANCE.config());
        var faction = com.alien.Alien.MOD.factions().get(location.lineageFactionId());
        if (faction != null && faction.data() instanceof LineageFactionData lineage) {
            lineage.markDirty();
        }
    }

    // TODO: Use level-specific phrasing here.
    private @Nullable String getStrainLeakMessageForVariant(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> "The perfect organism has found a new world to conquer...";
            case NETHER -> "Hell has found its way into this plane of existence...";
            case ABERRANT -> "Genetic experiments have found their way into the wide open world...";
            case IRRADIATED -> null;
        };
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        hiveManager.load(compoundTag);
        moltingManager.load(compoundTag);

        if (compoundTag.contains(NBT_HOST_TYPE)) {
            var resourceLocationString = compoundTag.getString(NBT_HOST_TYPE);
            var resourceLocation = ResourceLocation.parse(resourceLocationString);
            var entityTypeHolderOptional = BuiltInRegistries.ENTITY_TYPE.getHolder(resourceLocation);

            entityTypeHolderOptional.ifPresent($ -> this.hostTypeOption = Option.some(BuiltInRegistries.ENTITY_TYPE.get(resourceLocation)));
        }
        this.convoyMembership = loadConvoyMembership(compoundTag);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        hiveManager.save(compoundTag);
        moltingManager.save(compoundTag);

        hostTypeOption.ifSome(hostType -> {
            var resourceLocation = BuiltInRegistries.ENTITY_TYPE.getKey(hostTypeOption.unwrap());
            compoundTag.putString(NBT_HOST_TYPE, resourceLocation.toString());
        });
        if (convoyMembership != null) {
            var convoyTag = new CompoundTag();
            convoyTag.putString("LineageFactionId", convoyMembership.lineageFactionId().toString());
            convoyTag.putUUID("ConvoyId", convoyMembership.convoyId().value());
            compoundTag.put(NBT_CONVOY_MEMBERSHIP, convoyTag);
        }
    }

    private @Nullable ConvoyMembership loadConvoyMembership(CompoundTag compoundTag) {
        if (compoundTag.contains(NBT_CONVOY_MEMBERSHIP)) {
            var convoyTag = compoundTag.getCompound(NBT_CONVOY_MEMBERSHIP);
            return loadConvoyMembershipTag(convoyTag);
        }
        if (compoundTag.contains(NBT_RAID_MEMBERSHIP)) {
            var raidTag = compoundTag.getCompound(NBT_RAID_MEMBERSHIP);
            return loadConvoyMembershipTag(raidTag);
        }
        if (compoundTag.contains(NBT_LEGACY_CONVOY_MEMBERSHIP)) {
            var convoyTag = compoundTag.getCompound(NBT_LEGACY_CONVOY_MEMBERSHIP);
            return loadConvoyMembershipTag(convoyTag);
        }
        if (compoundTag.contains(NBT_LEGACY_RAID_MEMBERSHIP)) {
            var raidTag = compoundTag.getCompound(NBT_LEGACY_RAID_MEMBERSHIP);
            return loadConvoyMembershipTag(raidTag);
        }
        return null;
    }

    private @Nullable ConvoyMembership loadConvoyMembershipTag(CompoundTag membershipTag) {
        if (!membershipTag.contains("LineageFactionId")) {
            return null;
        }
        if (membershipTag.hasUUID("ConvoyId")) {
            return new ConvoyMembership(
                ResourceLocation.parse(membershipTag.getString("LineageFactionId")),
                new ConvoyId(membershipTag.getUUID("ConvoyId"))
            );
        }
        if (membershipTag.hasUUID("RaidId")) {
            return new ConvoyMembership(
                ResourceLocation.parse(membershipTag.getString("LineageFactionId")),
                new ConvoyId(membershipTag.getUUID("RaidId"))
            );
        }
        return null;
    }

    public MoltingManager getMoltingManager() {
        return moltingManager;
    }

    public GeneManagerProxy getGeneManager() {
        return GeneManagerProxy.getOrCreate(this);
    }

    public HiveManager getHiveManager() {
        return hiveManager;
    }

    public @Nullable ConvoyMembership convoyMembership() {
        return convoyMembership;
    }

    public void setConvoyMembership(ConvoyMembership convoyMembership) {
        this.convoyMembership = convoyMembership;
    }

    public void clearConvoyMembership() {
        this.convoyMembership = null;
    }

    public Option<EntityType<?>> getHostType() {
        return hostTypeOption;
    }

    public int getLastHurtTimeInTicks() {
        return lastHurtTimeInTicks;
    }

    public MovementAnalyzer getMovementAnalyzer() {
        return movementAnalyzer;
    }

    public void setHostType(EntityType<?> hostType) {
        this.hostTypeOption = Option.some(hostType);
    }

    private static final ResourceLocation aberrantDebuff = com.alien.Alien.MOD.resources().createLocation("aberrant_debuff");

    private static final ResourceLocation irradiatedBuff = com.alien.Alien.MOD.resources().createLocation("irradiated_buff");

    private void applyDynamicAttributes() {
        if (isAberrant()) {
            var percentage = -0.2;
            applyBuff(Attributes.MAX_HEALTH, percentage, aberrantDebuff);
            applyBuff(Attributes.ATTACK_DAMAGE, percentage, aberrantDebuff);
            applyBuff(Attributes.ARMOR, percentage, aberrantDebuff);
            applyBuff(Attributes.ARMOR_TOUGHNESS, percentage, aberrantDebuff);
        } else if (isIrradiated()) {
            var percentage = 0.2;
            applyBuff(Attributes.MAX_HEALTH, percentage, irradiatedBuff);
            applyBuff(Attributes.ATTACK_DAMAGE, percentage, irradiatedBuff);
            applyBuff(Attributes.ARMOR, percentage, irradiatedBuff);
            applyBuff(Attributes.ARMOR_TOUGHNESS, percentage, irradiatedBuff);
        }
    }

    private void applyBuff(Holder<Attribute> attribute, double percentage, ResourceLocation resourceLocation) {
        var instance = getAttributes().getInstance(attribute);

        if (instance == null || instance.hasModifier(resourceLocation)) {
            return;
        }

        var modifier = new AttributeModifier(resourceLocation, instance.getBaseValue() * percentage, AttributeModifier.Operation.ADD_VALUE);
        instance.addPermanentModifier(modifier);
    }

    public static AttributeSupplier.Builder createAlienAttributes() {
        return Monster.createMonsterAttributes();
    }
}
