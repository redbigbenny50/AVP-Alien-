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
import com.alien.common.util.AlienPredicates;
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

    /**
     * Drop-out fraction for the run gait. The run/walk decision used a single raw threshold, so an alien travelling
     * near it flipped state several times a second - and every flip restarts the animation track into its transition,
     * so neither gait ever advanced. That is what froze the drone's legs while it was digging (start-stop carve work
     * sits right on the threshold), and it makes every alien's walk/run transition stutter. Enter the run gait at the
     * full threshold, but keep it until speed falls to this fraction of it.
     */
    private static final double RUN_ANIMATION_EXIT_THRESHOLD_FACTOR = 0.7D;

    public final DataAccessor<Boolean> hasTarget;

    public final DataAccessor<Boolean> isPoisoned;

    public final DataAccessor<Boolean> isWithered;

    /** Born of an irradiated host - see {@code AlienDataSyncKeys.ALIEN_IS_BOILER_DESTINED}. */
    public final DataAccessor<Boolean> isBoilerDestined;

    public final DataAccessor<Float> moltAlpha;

    public final DataAccessor<Boolean> isMovingHorizontally;

    public final DataAccessor<Boolean> isMovingQuickly;

    protected final HiveManager hiveManager;

    protected final MovementAnalyzer movementAnalyzer;

    private final MoltingManager moltingManager;

    private Option<EntityType<?>> hostTypeOption;

    private @Nullable ConvoyMembership convoyMembership;

    private @Nullable com.alien.common.gameplay.hive.party.PartyMembership partyMembership;

    private int lastHurtTimeInTicks;

    /**
     * Game time at which this alien was told to walk to a vent and fold into its hive's reserves (0 = not marked).
     * TRANSIENT by design: if the chunk unloads or the server restarts mid-walk, the mark is simply lost and the alien
     * carries on as an ordinary member - a harmless fallback, never a leak. Set by CarveWorkers.disband; consumed by
     * BroodBankTask, which routes marked aliens to the nearest vent and returns them IDENTITY-INTACT.
     */
    private long reserveReturnMarkedAtTick;

    private boolean hasAnimationMovementSample;

    private double lastAnimationMovementSampleX;

    private double lastAnimationMovementSampleZ;

    protected Alien(EntityType<? extends Alien> entityType, Level level) {
        super(entityType, level);

        this.hasTarget = new DataAccessor<>(this, BLibDataSyncKeys.ENTITY_HAS_TARGET.get());
        this.isPoisoned = new DataAccessor<>(this, AlienDataSyncKeys.ALIEN_IS_POISONED.get());
        this.isWithered = new DataAccessor<>(this, AlienDataSyncKeys.ALIEN_IS_WITHERED.get());
        this.isBoilerDestined = new DataAccessor<>(this, AlienDataSyncKeys.ALIEN_IS_BOILER_DESTINED.get());
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
        // KIN MERCY, enforced at the door rather than only in the sensors. The targeting pipeline already refuses a
        // helpless same-strain queen, but every sensor check in the world is worthless against a direct setTarget:
        // convoy dispatch, hive territory aggro and CryForHelpListener.retargetIfPossible all hand a target straight
        // to a mob, and that last one copies whatever the crier was already fighting onto a newly summoned defender.
        // A chained queen reaching a defender that way would be clawed by her own kin with nothing to stop it.
        if (
            livingEntity != null
                && (AlienPredicates.isHelplessKinQueen(this, livingEntity)
                    || AlienPredicates.isHelplessQueenStrikingKin(this, livingEntity))
        ) {
            return;
        }

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
            // Hard-avoid lava/fire for non-Nether aliens.
            //
            // The SIGN is the whole story here, and it is the opposite of what it looks like. In
            // WalkNodeEvaluator a path type is traversable when its malus is >= 0; a NEGATIVE malus means the node
            // is never even offered as a neighbour (-1 is exactly what vanilla stamps on a blocked node). A large
            // POSITIVE malus does not forbid anything - it just prices the tile, and the pathfinder will happily
            // pay it when the route is otherwise convenient.
            //
            // So an all-positive 16.0F did the reverse of what it intended: vanilla LAVA is already -1
            // (impassable), and overriding it to +16 turned lava into a merely expensive shortcut - which is why
            // workers walked into flowing lava at a carve site.
            // LAVA / DAMAGE_FIRE are the hazard ITSELF (the lava, the fire, the magma block): never step onto one.
            setPathfindingMalus(PathType.LAVA, -1.0F);
            setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
            // DANGER_FIRE is NOT the hazard - vanilla's checkNeighbourBlocks stamps it on any node with lava or
            // fire in the surrounding 3x3x3, i.e. "within one block of". Making it impassable walled off a
            // one-block halo around every lava block, which can sever a corridor outright and turn reachable
            // nursery beds into NO_PATH failures. Keep it steeply priced but PASSABLE, as vanilla does (+8),
            // so a worker will edge past a lava seam rather than treat the whole area as a wall.
            setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        }
    }

    public boolean isPoisoned() {
        return isPoisoned.get();
    }

    /** Withered mark - see {@code AlienDataSyncKeys.ALIEN_IS_WITHERED}. Rides growth transitions via NBT. */
    public boolean isWithered() {
        return isWithered.get();
    }

    public void setWithered(boolean withered) {
        this.isWithered.set(withered);
    }

    /**
     * Boiler destiny - the irradiated-host birthright. Read by {@code GrowthManager.canBecomeBoiler} at the adolescent
     * -> adult transition; rides every growth step until then.
     */
    public boolean isBoilerDestined() {
        return isBoilerDestined.get();
    }

    public void setBoilerDestined(boolean boilerDestined) {
        this.isBoilerDestined.set(boilerDestined);
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

        // The withered mark, made visible. This was ParticleTypes.SMOKE at a quarter of ticks - vanilla's smoke is
        // GREY, and on something as small and quick as a chestburster, in a dark hive already full of acid, it read as
        // nothing at all. SQUID_INK is the only genuinely BLACK particle vanilla has; it is given a slight upward
        // drift so it behaves like smoke coming off the thing rather than ink sinking through it, and a grey wisp
        // still rises with it so the effect keeps some volume.
        if (level().isClientSide && isWithered() && random.nextInt(2) == 0) {
            level()
                .addParticle(
                    net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                    getRandomX(0.6),
                    getRandomY(),
                    getRandomZ(0.6),
                    0,
                    0.03,
                    0
                );
            level()
                .addParticle(
                    net.minecraft.core.particles.ParticleTypes.SMOKE,
                    getRandomX(0.6),
                    getRandomY(),
                    getRandomZ(0.6),
                    0,
                    0.02,
                    0
                );
        }

        if (!level().isClientSide) {
            movementAnalyzer.tick();
        }

        hiveManager.tick();
        moltingManager.tick();
        if (!level().isClientSide) {
            // Capture/delivery itself is a GOAP action (HostActions) - only the stun timer ticks here.
            com.alien.common.gameplay.hive.party.HostGrabImmunity.tickStun(this);
        }

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

        // Hysteresis: a moving alien must slow well below the entry threshold before it drops back to a walk, so
        // jitter around the boundary can no longer flip the gait (and restart the animation) tick after tick.
        var travelledSquared = deltaX * deltaX + deltaZ * deltaZ;
        var enterSquared = speedThreshold * speedThreshold;
        if (isMovingQuickly.get()) {
            var exitFactorSquared = RUN_ANIMATION_EXIT_THRESHOLD_FACTOR * RUN_ANIMATION_EXIT_THRESHOLD_FACTOR;
            return travelledSquared >= enterSquared * exitFactorSquared;
        }
        return travelledSquared >= enterSquared;
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
            heal(getHealthRegenPerSecond());
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
        // Rescue: hurting a xenomorph that is carrying a captured host makes it drop the host and be stunned.
        //
        // The captive itself is excluded: a carried player can look down and hit the drone under them (see
        // MixinProjectileUtil_AllowHittingVehicle, which deliberately allows hitting an alien vehicle), and letting
        // that free them would bypass the struggle bar entirely. Rescue is something SOMEONE ELSE does for you; your
        // own way out is HostStruggle.
        if (!level().isClientSide) {
            var captive = com.alien.common.gameplay.hive.party.HostCaptureTask.carriedHost(this);
            var attacker = damageSource.getEntity();
            var directAttacker = damageSource.getDirectEntity();
            if (captive != null && attacker != captive && directAttacker != captive) {
                com.alien.common.gameplay.hive.party.HostGrabImmunity.breakCapture(this, captive);
            }
        }
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

            if (getType().is(AlienEntityTypeTags.XENOMORPHS) && damageSource.getEntity() instanceof ServerPlayer player) {
                recordAttackByPlayer(player);
            }

            // SIEGE CLOCK FEED. [stated] territory attrition keys on "prolonged combat ... over 15 minutes or so of
            // active combat/aggro with a player or enemy faction" in the hive's territory. A qualifying hit is: a
            // hive member, standing in a chunk its location claims, hurt by a player or by a living attacker from
            // OUTSIDE its own lineage (same-lineage crossfire and acid splash are not a siege). Environmental damage
            // has no attacking entity and never qualifies. Duration filtering happens in PopulationPressureDecayTask
            // - this just timestamps the hit.
            if (
                !level().isClientSide
                    && getType().is(AlienEntityTypeTags.XENOMORPHS)
                    && damageSource.getEntity() instanceof LivingEntity attackerEntity
            ) {
                recordSiegeDamage(attackerEntity);
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

    /**
     * AVPHuman's full-suit radiation armor tag, referenced BY ID so no avp_human class is touched - the tag simply has
     * no members when the mod is absent.
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> RADIATION_RESISTANT_ARMORS =
        net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("avp_human", "radiation_resistant_armors")
        );

    /**
     * AVPHuman's radiation effect, resolved lazily by id - a SOFT dependency: empty when avp_human is absent and the
     * irradiated touch simply does nothing. Cached after the first lookup (registries are frozen by then).
     */
    private static java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.effect.MobEffect>> radiationEffect;

    public static java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.effect.MobEffect>> radiationEffect() {
        if (radiationEffect == null) {
            radiationEffect = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("avp_human", "radiation")
            );
        }
        return radiationEffect;
    }

    @Override
    public boolean doHurtTarget(@NotNull net.minecraft.world.entity.Entity target) {
        var hurt = super.doHurtTarget(target);

        // Withered aliens fight like wither skeletons: every landed hit inflicts the wither. Fellow aliens are
        // untouched - wither sits in the does_not_affect_aliens effect tag.
        if (hurt && isWithered() && target instanceof net.minecraft.world.entity.LivingEntity livingTarget) {
            livingTarget.addEffect(
                new MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 200, 0),
                this
            );
        }

        // IRRADIATED strain: every landed hit adds to the victim's AVPHuman radiation EXPOSURE, so a fight
        // escalates them up the sickness ladder instead of handing out one fixed dose. Optional dependency - the
        // compat class is only touched when avp_human is actually present.
        if (
            hurt
                && target instanceof net.minecraft.world.entity.LivingEntity irradiatedTarget
                && canBeIrradiatedByTouch(irradiatedTarget)
                && AlienVariantTypes.getFor(getVariant()) == AlienVariantTypes.IRRADIATED
                && com.alien.compatibility.avp_human.AVPHuman.MOD.isLoaded()
        ) {
            com.alien.compatibility.avp_human.RadiationCompat.irradiateOnHit(irradiatedTarget);
        }

        return hurt;
    }

    /**
     * Whether the irradiated touch may dose this victim. Deliberately mirrors AVPHuman's own {@code canBeIrradiated}
     * gate, because this hook calls {@code addEffect} directly and would otherwise bypass every protection the mod
     * grants its players:
     * <ul>
     * <li><b>A full radiation-resistant armor set stops it outright.</b> The MK50 suit trades armor points and mobility
     * for radiation protection - a claw through it must not make that trade worthless.</li>
     * <li><b>Fellow aliens are never dosed</b> - the species is radiation-immune by decree.</li>
     * <li><b>An active radiation effect is never refreshed.</b> AVPHuman's damage curve is driven by how much of the
     * ORIGINAL duration has elapsed; re-applying on every claw would keep resetting a victim into the harmless
     * incubation window, so a dose is a dose and it must be allowed to run.</li>
     * </ul>
     */
    private boolean canBeIrradiatedByTouch(net.minecraft.world.entity.LivingEntity victim) {
        // Aliens are radiation-immune AS A SPECIES - except the aberrant strain, which is not. Aberrants are the
        // weak line: it is why they cannot convert to irradiated the way normal and nether do, and it is why they
        // burn instead. The avp_human:radiation_resistant tag we contribute lists every alien EXCEPT them, and this
        // mirrors it so claws and talons agree with the environment.
        if (victim instanceof Alien irradiatedAlien && irradiatedAlien.getVariant() != AlienVariant.ABERRANT) {
            return false;
        }
        // NOTE: deliberately NOT refused for an already-irradiated victim any more. Under AVPHuman's exposure
        // counter, repeat hits are supposed to accumulate - refusing them would mean a swarm could never take you
        // past the first rung of the ladder.
        return !com.blib.api.common.entity.v1.BLibEntityPredicates.hasFullArmorSetMatching(
            victim,
            stack -> stack.is(RADIATION_RESISTANT_ARMORS)
        );
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
        // Load-window guard: the hive registry is rebuilt from BLib faction data on load, and until that has happened
        // once it cannot place ANY member in a hive - so every member would read as non-persistent and vanilla would
        // despawn it the moment it ticks on load. That is the "on join, every xeno but the queen vanished" bug (the
        // queen has her own registry-independent persistence). Until the registry is ready, hold every hive xenomorph
        // persistent so nothing is culled in that window.
        if (!HiveLocationRegistry.INSTANCE.hasRebuilt() && getType().is(AlienEntityTypeTags.XENOMORPHS)) {
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

    private void recordSiegeDamage(LivingEntity attackerEntity) {
        // Creative/spectator hits are not a siege - same rule as the intrusion timer ([stated] Aug 1): an admin
        // sword-testing in creative must not start 15 minutes of territory attrition against the hive.
        if (
            attackerEntity instanceof net.minecraft.world.entity.player.Player player
                && (player.isCreative() || player.isSpectator())
        ) {
            return;
        }
        var location = HiveMemberLocationResolver.reserveReturnLocation(this);
        if (location == null || !location.claimedChunks().contains(chunkPosition())) {
            return;
        }
        if (attackerEntity instanceof Alien attackerAlien) {
            var attackerLocation = HiveMemberLocationResolver.reserveReturnLocation(attackerAlien);
            if (
                attackerLocation != null
                    && attackerLocation.lineageFactionId() != null
                    && attackerLocation.lineageFactionId().equals(location.lineageFactionId())
            ) {
                return;
            }
        }
        location.recordCombatDamage(level().getGameTime());
    }

    /** Marks this alien to walk to a vent and fold back into reserves. See {@code reserveReturnMarkedAtTick}. */
    public void markForReserveReturn(long gameTime) {
        this.reserveReturnMarkedAtTick = Math.max(1L, gameTime);
    }

    public boolean isMarkedForReserveReturn() {
        return reserveReturnMarkedAtTick > 0L;
    }

    public long reserveReturnMarkedAtTick() {
        return reserveReturnMarkedAtTick;
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
                    announceStrainArrival(serverLevel, alienVariant, strainBasedLeakMessage);
                }
            });
    }

    @Override
    public void remove(@NotNull RemovalReason removalReason) {
        // DIAGNOSTIC (queen bug 1 - "she seems to despawn while digging her core"). Every mod-side removal path was
        // audited and none can touch a queen (eviction exempts avp mobs, brood bank is host-born-only, convoys never
        // carry queens, the reserve unload handler guards the QUEENS tag), and vanilla distance-despawn is disabled
        // by her persistence - so whatever removes her is unknown, and this names it: reason + caller stack at the
        // exact moment. Prime suspect: PEACEFUL difficulty, which Monster.checkDespawn honours REGARDLESS of
        // persistence (shouldDespawnInPeaceful is never overridden in this mod). Remove once the culprit is known.
        if (
            !level().isClientSide
                && !isRemoved()
                && getType().is(AlienEntityTypeTags.QUEENS)
        ) {
            if (removalReason == RemovalReason.UNLOADED_TO_CHUNK || removalReason == RemovalReason.CHANGED_DIMENSION) {
                // Normal chunk churn - one line, no stack, so "unloaded at T and never seen again" is visible in a
                // timeline without drowning the log.
                com.alien.Alien.LOGGER.info(
                    "QUEEN-DIAG {} {} removed reason={} pos={} - routine unload, should reappear on chunk load",
                    getType().builtInRegistryHolder().key().location(),
                    getUUID(),
                    removalReason,
                    blockPosition()
                );
            } else {
                com.alien.Alien.LOGGER.warn(
                    "QUEEN-DIAG {} {} REMOVED reason={} pos={} difficulty={} persistent={} phase={} - caller stack follows",
                    getType().builtInRegistryHolder().key().location(),
                    getUUID(),
                    removalReason,
                    blockPosition(),
                    level().getDifficulty(),
                    isPersistenceRequired(),
                    this instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen queen
                        ? String.valueOf(queen.getLifecyclePhaseManager().getPhase())
                        : "n/a",
                    new Throwable("QUEEN-DIAG removal stack (not an error)")
                );
            }
        }

        super.remove(removalReason);
        // Hive: BLib's faction system handles removal cleanup automatically when the entity is killed or
        // discarded — no manual hive.removeHiveMember call needed.
    }

    @Override
    public void die(@NotNull DamageSource damageSource) {
        // Free any egg destination this hauler had reserved - a dead carrier must not keep a nursery bed or a
        // webbed host locked out of the hive.
        if (!level().isClientSide) {
            com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.EggSpotClaims.releaseAll(getUUID());
        }

        // Untrack this member from its party NOW, while we know it truly died. Resolution cannot otherwise tell a
        // dead member from one that is merely in an unloaded chunk, and was writing off both.
        if (!level().isClientSide) {
            com.alien.common.gameplay.hive.party.PartyMemberDeath.onDeath(this);
        }
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
            // ANY royal death (queen or empress, any cause, killer or not) surrenders the founder pointer on the
            // hives she was seated at. Before this, setFounderId(null) had exactly one caller in the whole tree -
            // QueenInhibitionService - so a location kept naming a long-dead queen forever. That made
            // "founderId != null" read as "once had a queen" rather than "has a living queen", which stalled the
            // post-replacement grudge (it waits for a successor that the queenless path never crowned) and let the
            // empress election seat a hive with no queen in it at all.
            if (getType().is(AlienEntityTypeTags.QUEENS)) {
                onRoyalDiedClearFounder();
            }
            // Hive empress death clears the lineage's empress slot so the next emergence ritual can fire.
            if (getType().is(AlienEntityTypeTags.EMPRESSES)) {
                // BROKEN THRONE: she was cast out by her own empire and died on the ruin it left her, guarded by
                // whatever refused to leave. Checked BEFORE onEmpressDied because exile has already surrendered the
                // lineage's empressId - the only thing that still remembers what she was is the flag on her.
                // A code grant rather than a kill criterion: "exiled" is entity state, not an entity type, so no tag
                // could tell this death apart from killing a reigning empress.
                if (
                    this instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress empress
                        && empress.isExiled()
                        && getKillCredit() instanceof net.minecraft.server.level.ServerPlayer throneBreaker
                ) {
                    com.alien.common.data.AlienAdvancements.BROKEN_THRONE.grant(throneBreaker);
                }
                onEmpressDied();
            }
            // Queen killed by a player → revenge raid (ungated 3-wave strike against the killer), and mark her
            // location for the post-replacement grudge so the crowned successor prioritizes that player.
            if (
                getType().is(AlienEntityTypeTags.QUEENS)
                    && getKillCredit() instanceof ServerPlayer queenKiller
                    && level() instanceof ServerLevel queenLevel
            ) {
                onQueenKilled(queenKiller, queenLevel);
            }
        }

        super.die(damageSource);
    }

    private void onQueenKilled(ServerPlayer killer, ServerLevel serverLevel) {
        var server = serverLevel.getServer();

        // Case A — a captured queen (severed into her own inhibited lineage) dies mid-recovery. She's no longer a
        // member of her original hive, so the ONLY link back is the RescueCampaign holding her UUID. Scan all
        // locations for it: convert the recovery into a revenge raid carrying the logged failure count, then clear the
        // campaign so the firewall can finally crown a replacement.
        if (convertRescueToRevengeOnDeath(server, killer)) {
            return;
        }

        // Case B — a normal (in-hive) founder queen killed by a player: grudge + straight revenge raid.
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(getUUID())) {
            if (!com.alien.common.gameplay.hive.id.LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = com.alien.Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }

            // Mark this queen's own location (the one she founded) for the post-replacement grudge.
            for (var location : lineage.locationsById().values()) {
                if (getUUID().equals(location.founderId())) {
                    location.setGrudgePlayerId(killer.getUUID());
                    var lineageFaction = com.alien.Alien.MOD.factions().get(location.lineageFactionId());
                    if (lineageFaction != null && lineageFaction.data() instanceof LineageFactionData ld) {
                        ld.markDirty();
                    }
                }
            }

            com.alien.common.gameplay.hive.convoy.RaidDispatch.onQueenKilled(server, lineage, factionId, killer);
        }
    }

    /**
     * If this dying queen has an open {@link com.alien.common.gameplay.hive.party.RescueCampaign} on some hive
     * location, converts recovery → revenge: dispatches a revenge raid from that original hive carrying the campaign's
     * failure count context, stamps the grudge, and clears the campaign (unblocking the firewall). Returns true if a
     * conversion happened.
     */
    private boolean convertRescueToRevengeOnDeath(net.minecraft.server.MinecraftServer server, ServerPlayer killer) {
        for (var factionId : new java.util.ArrayList<>(com.alien.Alien.MOD.factions().getAllIds())) {
            if (!com.alien.common.gameplay.hive.id.LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = com.alien.Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            for (var location : lineage.locationsById().values()) {
                var campaign = location.rescueCampaign();
                if (campaign == null || !getUUID().equals(campaign.queenUuid())) {
                    continue;
                }

                // Convert. The revenge raid fires ungated against the killer; the grudge is stamped so the eventual
                // replacement also prioritizes them (the 3-strike patience was against the loss, not the method).
                location.setGrudgePlayerId(killer.getUUID());
                location.setRescueCampaign(null);
                lineage.markDirty();
                com.alien.common.gameplay.hive.convoy.RaidDispatch.onQueenKilled(server, lineage, factionId, killer);
                com.alien.Alien.LOGGER.info(
                    "Hive: recovery converted to revenge — captured queen {} killed by {}; {} rescue attempts had failed",
                    getUUID(),
                    killer.getUUID(),
                    campaign.attemptsFailed()
                );
                return true;
            }
        }
        return false;
    }

    /**
     * Clear this royal's founder pointer on every hive that named her.
     * <p>
     * Scans the locations of every lineage she belonged to rather than trusting a single resolver, because a royal can
     * die outside her own claimed chunks (dragged off by a raid, killed mid-convoy, executed in a player's lab) and the
     * seat still has to be released. Null founder is the established "queenless" state the growth, economy and
     * maturation tasks already read - see QueenInhibitionService, which sets exactly this.
     */
    private void onRoyalDiedClearFounder() {
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(getUUID())) {
            if (!com.alien.common.gameplay.hive.id.LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = com.alien.Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            for (var location : lineage.locationsById().values()) {
                if (getUUID().equals(location.founderId())) {
                    location.setFounderId(null);
                    com.alien.Alien.LOGGER.info(
                        "Hive: royal {} died - location {} is now queenless",
                        getUUID(),
                        location.id()
                    );
                }
            }
            lineage.markDirty();
        }
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
                lineage.setPendingEmpressSeatId(null);

                // Killing her has to BUY something. Without this the lineage still holds 4+ hives, so the very next
                // scan crowns her successor and the players get nothing for the fight.
                var server = level().getServer();
                if (server != null) {
                    var cooldown = HiveLocationRegistry.INSTANCE.config().empressCrowningCooldownTicks();
                    lineage.setEmpressCooldownUntilTick(server.overworld().getGameTime() + cooldown);
                }
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

    /**
     * Notes that {@code player} was hostile to a member of this alien's home hive location right now — feeds
     * {@code HiveTerritoryAggroTask}'s intrusion dwell tracking (a player who breaches the claim, fights members, and
     * lingers past the dwell threshold earns a two-wave retribution campaign). Only the "recently hostile" timestamp is
     * stamped here; the dwell accrual and campaign start happen in the aggro task, which has the in-claim timing
     * context this per-hit hook lacks.
     */
    private void recordAttackByPlayer(ServerPlayer player) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        var location = HiveMemberLocationResolver.reserveReturnLocation(this);
        if (location == null) {
            location = HiveLocationRegistry.INSTANCE.getByChunk(level().dimension(), chunkPosition());
        }
        if (location == null || !location.isAlive()) {
            return;
        }

        var campaign = location.attackCampaigns()
            .computeIfAbsent(
                player.getUUID(),
                $ -> new com.alien.common.gameplay.hive.party.AttackCampaign()
            );
        campaign.setLastHostileTick(serverLevel.getGameTime());
    }

    /**
     * Tells the whole world a strain has arrived, and gives it a voice.
     * <p>
     * Public because IRRADIATED does not come through the leak path at all - it is announced from
     * {@code NukeConversion} on every conversion, since a hive being MADE is a thing that can happen repeatedly and is
     * worth hearing about each time. The other three fire once, on first sighting.
     * <p>
     * The sound is played AT EACH PLAYER rather than at a position, so a world-wide announcement is actually heard
     * world-wide instead of only by whoever happens to be standing near the newcomer.
     */
    public static void announceStrainArrival(ServerLevel serverLevel, AlienVariant alienVariant, String message) {
        var variantType = AlienVariantTypes.getFor(alienVariant);
        var sound = strainArrivalSound(alienVariant);

        for (var player : serverLevel.players()) {
            player.sendSystemMessage(
                Component.literal(message).withStyle(variantType.chatColor(), ChatFormatting.ITALIC)
            );

            serverLevel.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                sound,
                net.minecraft.sounds.SoundSource.HOSTILE,
                1.0F,
                1.0F
            );
        }
    }

    /** One signature per strain, so you know what has turned up before you read the line. */
    private static net.minecraft.sounds.SoundEvent strainArrivalSound(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> com.alien.common.registry.init.AlienSoundEvents.ENTITY_QUEEN_SCREAM.get();
            case NETHER -> net.minecraft.sounds.SoundEvents.WITHER_SPAWN;
            case ABERRANT -> net.minecraft.sounds.SoundEvents.ANVIL_USE;
            case IRRADIATED -> net.minecraft.sounds.SoundEvents.WARDEN_EMERGE;
        };
    }

    // TODO: Use level-specific phrasing here.
    private @Nullable String getStrainLeakMessageForVariant(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> "The perfect organism has found a new world to conquer...";
            case NETHER -> "Hell has found its way into this plane of existence...";
            case ABERRANT -> "Genetic experiments have found their way into the wide open world...";
            // Deliberately silent HERE. An irradiated strain is not a leak: nothing crossed over from anywhere, it
            // is MADE out of a hive that was already present. It also has to announce itself on EVERY conversion
            // rather than once per world the way a genuine leak does, so the line lives in NukeConversion instead.
            case IRRADIATED -> null;
        };
    }

    /**
     * NBT key for {@link #hostBorn}. Deliberately NOT in {@code GrowthManager.TRANSITION_NBT_KEY_BLACKLIST}, so the
     * flag rides {@code EntityTransitionUtil.transitionInto} through every growth step: the chestburster that crawls
     * out of a cow is tagged once, and the adult it eventually becomes still knows it was host-born.
     */
    private static final String NBT_HOST_BORN = "avp_host_born";

    /** True if this line began inside a host (chestburst), rather than being simulated out of a reserve bank. */
    private boolean hostBorn;

    public boolean isHostBorn() {
        return hostBorn;
    }

    public void setHostBorn(boolean hostBorn) {
        this.hostBorn = hostBorn;
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        hiveManager.load(compoundTag);
        moltingManager.load(compoundTag);
        this.hostBorn = compoundTag.getBoolean(NBT_HOST_BORN);

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
        compoundTag.putBoolean(NBT_HOST_BORN, hostBorn);

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

    public @Nullable com.alien.common.gameplay.hive.party.PartyMembership partyMembership() {
        return partyMembership;
    }

    public void setPartyMembership(com.alien.common.gameplay.hive.party.PartyMembership partyMembership) {
        this.partyMembership = partyMembership;
    }

    public void clearPartyMembership() {
        this.partyMembership = null;
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

    private static final ResourceLocation predalienBuff = com.alien.Alien.MOD.resources().createLocation("predalien_buff");

    private static final ResourceLocation irradiatedBuff = com.alien.Alien.MOD.resources().createLocation("irradiated_buff");

    private static final ResourceLocation empressBuff = com.alien.Alien.MOD.resources().createLocation("empress_buff");

    /**
     * Empress influence changes rarely, so the membership walk is throttled rather than run every tick.
     * <p>
     * Phased by entity id, not raw tickCount: a hive that spawns a wave of forty aliens in one tick would otherwise
     * give them all the same modulo phase, and every one of them would do the walk on the same tick forever.
     */
    private static final int EMPRESS_BUFF_RECHECK_TICKS = 40;

    /**
     * Strain buffs, and the health correction that has to ride with them.
     * <p>
     * THE BUG THIS FIXES: {@link #applyBuff} raises MAX_HEALTH by 20% for an irradiated alien, but nothing moved its
     * CURRENT health to match - so one spawned at its unbuffed value against a taller bar and visibly regenerated the
     * difference through {@code healPassively}. It arrived wounded for no reason.
     * <p>
     * Corrected by SCALING rather than topping up, which is the same one line for two different situations:
     * <ul>
     * <li>A FRESH SPAWN is at full health, so the ratio holds it at full - 40/40 becomes 48/48.</li>
     * <li>An alien TRANSITIONING to irradiated mid-life keeps the wound it already had - 20/40 becomes 24/48, still
     * half. Topping up would have healed it as a side effect of changing strain, which is a free heal for anything
     * caught in a nuke.</li>
     * </ul>
     * BOTH DIRECTIONS, deliberately - the aberrant debuff is the same code path at -20%, an 0.8x multiplier. It never
     * showed the bug because dropping the maximum below current health makes vanilla clamp on its own, but that clamp
     * is NOT proportional: a wounded aberrant at 20/40 keeps its 20 against a new max of 32 and comes out at 62%,
     * relatively healthier for having been debuffed. Scaling both ways keeps a half-health alien at half whichever
     * direction its maximum moved.
     */
    private void applyDynamicAttributes() {
        var maxHealthBefore = getMaxHealth();

        applyVariantBuffs();

        // Only ever true on the tick a modifier actually lands - applyBuff is guarded by hasModifier, so every
        // subsequent tick leaves the maximum untouched and this does nothing.
        var maxHealthAfter = getMaxHealth();
        if (maxHealthAfter != maxHealthBefore && maxHealthBefore > 0.0F) {
            setHealth(getHealth() * (maxHealthAfter / maxHealthBefore));
        }
    }

    private void applyVariantBuffs() {
        // PREDALIEN, and deliberately NOT part of the strain chain below - it is a CASTE buff, not a strain one, so it
        // stacks with whichever strain the predalien happens to be.
        //
        // [stated] "predaliens get a buff of 1.5x... if they are irradiated they get both so a total of 1.7x. if its
        // an aberrant predalien its 1.3x." That falls out for free from how applyBuff works: every modifier is
        // computed as baseValue * percentage and added with ADD_VALUE, so they SUM against the base rather than
        // compounding. On a 40-health base: +50% is +20, +20% irradiated is +8, and 40 + 20 + 8 = 68 = 1.7x exactly.
        // Aberrant instead subtracts 8, giving 52 = 1.3x.
        if (getType().is(AlienEntityTypeTags.PREDALIENS)) {
            var percentage = 0.5;
            applyBuff(Attributes.MAX_HEALTH, percentage, predalienBuff);
            applyBuff(Attributes.ATTACK_DAMAGE, percentage, predalienBuff);
            applyBuff(Attributes.ARMOR, percentage, predalienBuff);
            applyBuff(Attributes.ARMOR_TOUGHNESS, percentage, predalienBuff);
        }

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

        // EMPRESS: a third, independent axis. Strain is what you ARE and caste is what you GREW INTO; this is who
        // you ANSWER TO, so it stacks with both. [stated] "an irradaiated predalien empress for example would have 3
        // bonuses empress bonus, predalien bonus, and irradiated bonus." Summed against base like every other buff
        // here, so on a 40-health base: +50% predalien +20, +20% irradiated +8, +30% empress +12, total 80 = 2.0x.
        //
        // The ONLY buff on this list that can be taken away, so unlike the others it also has to be REMOVED - she
        // dies, she is exiled, the hive leaves her lineage, or the alien simply walks off her territory. The
        // enclosing applyDynamicAttributes rescales current health proportionally in BOTH directions, so losing it
        // wounds rather than kills.
        if (!level().isClientSide && (tickCount + getId()) % EMPRESS_BUFF_RECHECK_TICKS == 0) {
            if (isUnderEmpressInfluence()) {
                var percentage = 0.3;
                applyBuff(Attributes.MAX_HEALTH, percentage, empressBuff);
                applyBuff(Attributes.ATTACK_DAMAGE, percentage, empressBuff);
                applyBuff(Attributes.ARMOR, percentage, empressBuff);
                applyBuff(Attributes.ARMOR_TOUGHNESS, percentage, empressBuff);
            } else {
                removeBuff(Attributes.MAX_HEALTH, empressBuff);
                removeBuff(Attributes.ATTACK_DAMAGE, empressBuff);
                removeBuff(Attributes.ARMOR, empressBuff);
                removeBuff(Attributes.ARMOR_TOUGHNESS, empressBuff);
            }
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

    /**
     * Whether this alien BELONGS to a hive under empress influence - by MEMBERSHIP, not by where it is standing.
     * <p>
     * [stated] "its meant to apply to the whole hive raids included a empress is punishing it makes you want to find
     * and kill her." A positional test would have quietly switched the buff off the moment a raid crossed its own
     * border, which is exactly the fight where it is supposed to matter. Strain and caste buffs travel with the
     * creature; so does this one.
     */
    private boolean isUnderEmpressInfluence() {
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(getUUID())) {
            if (!com.alien.common.gameplay.hive.id.HiveLocationIds.isHiveLocationId(factionId)) {
                continue;
            }
            var location = HiveLocationRegistry.INSTANCE.get(
                com.alien.common.gameplay.hive.id.HiveLocationId.of(factionId)
            );
            if (location != null && location.isEmpressInfluenced()) {
                return true;
            }
        }
        return false;
    }

    /** The mirror of {@link #applyBuff} - only needed for buffs that can be revoked, which today means the empress. */
    private void removeBuff(Holder<Attribute> attribute, ResourceLocation resourceLocation) {
        var instance = getAttributes().getInstance(attribute);

        if (instance == null || !instance.hasModifier(resourceLocation)) {
            return;
        }

        instance.removeModifier(resourceLocation);
    }

    public static AttributeSupplier.Builder createAlienAttributes() {
        return Monster.createMonsterAttributes();
    }
}
