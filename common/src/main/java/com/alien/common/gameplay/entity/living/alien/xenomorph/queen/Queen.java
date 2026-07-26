package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphAttackConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.XenomorphPathConfig;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer;
import com.alien.common.gameplay.entity.living.alien.xenomorph.drone.Drone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.QueenGOAP;
import com.alien.common.gameplay.hive.lifecycle.QueenInhibitionService;
import com.alien.common.gameplay.level.saveddata.QueenSpawnChunkData;
import com.alien.common.gameplay.level.saveddata.StrainLeakData;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.entity.v1.PlayerUtil;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class Queen extends Xenomorph implements GOAPUser<Queen>, EggLayer {

    public static final AttackType SWIPE_DOWN = AttackType.builder("queen_swipe_down")
        .requiresAnyArm()
        .defaultDurationInTicks(18)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType BACKHAND = AttackType.builder("queen_backhand")
        .requiresAnyArm()
        .defaultDurationInTicks(15)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    public static final AttackType TAIL_STRIKE = AttackType.builder("queen_tail_strike")
        .requiresTail()
        .defaultDurationInTicks(20)
        .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
        .build();

    private static final XenomorphConfig CONFIG = XenomorphConfig.builder(XenomorphPathConfig.WIDE_TALL, Queen::getType)
        .attackConfig(
            XenomorphAttackConfig.builder()
                .addRegular(SWIPE_DOWN)
                .addRegular(BACKHAND)
                .addRegular(TAIL_STRIKE)
                .build()
        )
        .parallelDigCount(4)
        .pushedByFluid(false)
        .canCrawl(false)
        .build();

    /**
     * Must match {@code QueenLifecyclePhaseManager}'s phase tag — its absence in a save marks a pre-lifecycle queen.
     */
    private static final String LIFECYCLE_PHASE_TAG = "lifecyclePhase";

    private static final String LEGACY_DORMANT_TAG = "legacyDormant";

    private static final String LEGACY_DORMANT_COMPAT_TAG = "LegacyDormant";

    public static AttributeSupplier.Builder createQueenAttributes() {
        return Alien.createAlienAttributes()
            .add(Attributes.ARMOR, 16.0F)
            .add(Attributes.ARMOR_TOUGHNESS, 16.0F)
            .add(Attributes.ATTACK_DAMAGE, PlayerStatConstants.BASE_HEALTH * 1F)
            .add(Attributes.FOLLOW_RANGE, 35F)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1f)
            .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 12.5F)
            .add(Attributes.MOVEMENT_SPEED, PlayerStatConstants.BASE_WALK_SPEED * 1.1F);
    }

    private final QueenAnimationDispatcher animationDispatcher;

    private final OvipositorManager ovipositorManager;

    private final QueenIncapacitationManager incapacitationManager;

    private final QueenData queenData;

    private final QueenLifecyclePhaseManager lifecyclePhaseManager;

    /**
     * Synced chain count for the client (shackle reveal + chain render). The bind manager keeps it in lockstep with the
     * anchor list each server tick.
     */
    public final DataAccessor<Integer> bindChainCount;

    private final QueenBindManager bindManager;

    private final QueenRescueManager rescueManager;

    /**
     * Synced + persisted: whether the inhibitor device is attached. Drives the {@code gInhibitor} bone reveal and (in
     * later slices) the contained-breeder behaviour — hive autonomy off, claim capped at one chunk.
     */
    public final DataAccessor<Boolean> hasInhibitor;

    public final DataAccessor<Boolean> tracked;

    /** Synced + persisted: the involuntary, defeat-induced downed state. Drives the incapacitated animations. */
    public final DataAccessor<Boolean> incapacitated;

    /**
     * Synced, transient: true while she is carving her founding chamber (construction economy step 6). Set by the carve
     * tick server-side; the client QueenAnimator drives the stand-dig animation triptych off its edges.
     */
    public final DataAccessor<Boolean> standDiggingSynced;

    /**
     * Transient: true while clip-digging to her location anchor (Stage 2b). Not saved — a reload never stays noclip.
     */
    private boolean digging;

    /**
     * Facing captured the moment she becomes a pacified captive breeder; held so she doesn't turn under the eggsack.
     */
    private Float containedYRotLock = null;

    /**
     * Legacy-recovery state. A queen saved before the lifecycle system existed loads without a {@code lifecyclePhase}
     * tag; {@link #wasLoadedWithoutLifecycleState()} reports that so {@code LegacyHiveRecovery} can treat her as a
     * legacy queen. She is parked {@link #isLegacyDormant() legacy-dormant} until recovery wakes her via
     * {@link #wakeFromLegacyDormantRecovery()}, which hands her back to the normal lifecycle (LOCATION phase).
     */
    private boolean legacyDormant;

    /** Transient: set at load time when the save carried no lifecycle-phase state (a pre-lifecycle-system queen). */
    private boolean loadedWithoutLifecycleState;

    public Queen(EntityType<? extends Queen> entityType, Level level) {
        super(entityType, level, CONFIG);
        this.animationDispatcher = new QueenAnimationDispatcher(this);
        this.ovipositorManager = new OvipositorManager(this);
        this.incapacitationManager = new QueenIncapacitationManager(this);
        this.queenData = new QueenData();
        this.lifecyclePhaseManager = new QueenLifecyclePhaseManager(this);
        this.bindChainCount = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_BIND_CHAIN_COUNT.get());
        this.bindManager = new QueenBindManager(this);
        this.rescueManager = new QueenRescueManager(this);
        this.hasInhibitor = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_HAS_INHIBITOR.get());
        this.tracked = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_IS_TRACKED.get());
        this.incapacitated = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_IS_INCAPACITATED.get());
        this.standDiggingSynced = new DataAccessor<>(this, AlienDataSyncKeys.QUEEN_IS_STAND_DIGGING.get());
    }

    @Override
    public Agent.Builder<Queen> blib$applyGOAPAgentProperties(Agent.Builder<Queen> agentBuilder) {
        return QueenGOAP.applyAgentProperties(agentBuilder);
    }

    @Override
    public @Nullable Graph<Queen> blib$getGOAPGraphOrNull() {
        return getActiveGOAPGraph(isOnOvipositor() ? QueenGOAP.OVIPOSITOR_GRAPH : QueenGOAP.GRAPH);
    }

    private boolean isOnOvipositor() {
        return ovipositorManager != null && ovipositorManager.hasOvipositor();
    }

    @Override
    public void tick() {
        super.tick();
        ovipositorManager.tick();
        queenData.tick();
        lifecyclePhaseManager.tick();
        bindManager.tick();
        rescueManager.tick();
        incapacitationManager.tick();

        // A pacified captive breeder holds still AND holds her FACING: idle look control would keep turning her body
        // in place, twisting her against the eggsack that is anchored to her rotation. Capture her facing once when
        // she enters the state and pin body + head to it every tick; release it when she is no longer contained.
        if (isInhibited() && isRidingOvipositor()) {
            if (containedYRotLock == null) {
                // Capture the settled facing (yBodyRot is what the eggsack copied at creation) so body and
                // eggsack hold the exact same angle.
                containedYRotLock = yBodyRot;
            }
            setYRot(containedYRotLock);
            yBodyRot = containedYRotLock;
            yHeadRot = containedYRotLock;
        } else if (containedYRotLock != null) {
            containedYRotLock = null;
        }

        if (isInhibited() && tickCount % 20 == 0 && level() instanceof ServerLevel serverLevel) {
            QueenInhibitionService.tickFollow(serverLevel, this);
        }

        if (isTracked() && level() instanceof ServerLevel trackedLevel) {
            TrackedQueenRegistry.getOrCreate(trackedLevel).ifSome(registry -> {
                if (registry.consumePendingDestroy(getUUID())) {
                    // "Destroy tracker" was requested while she was unloaded; clear the tag now instead of re-adding.
                    setTracked(false);
                } else if (tickCount % 40 == 0) {
                    registry.updatePosition(
                        getUUID(),
                        blockPosition(),
                        trackedLevel.dimension(),
                        trackedLevel.getGameTime()
                    );
                }
            });
        }
    }

    @Override
    public void startAttack(AttackType attack, @Nullable LivingEntity target) {
        int attackIdBefore = attackId.get();
        super.startAttack(attack, target);
        // A real attack just began (attackId advanced) — give her capture chains a chance to snap (1-3 chains only).
        if (attackId.get() != attackIdBefore) {
            bindManager.onQueenAttack();
        }
    }

    @Override
    protected boolean canEntityRideAlien(@NotNull Entity passenger) {
        return Objects.equals(passenger.getType(), AlienEntityTypes.OVIPOSITOR.get());
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (passenger.getType() == AlienEntityTypes.OVIPOSITOR.get()) {
            var relativePos = com.blib.api.common.entity.v1.EntityUtil.getRelativePosition(this, 3, 0.01, 5.25);
            callback.accept(passenger, relativePos.x, relativePos.y, relativePos.z);
            return;
        }

        super.positionRider(passenger, callback);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
        @NotNull ServerLevelAccessor serverLevelAccessor,
        @NotNull DifficultyInstance difficulty,
        @NotNull MobSpawnType spawnType,
        @Nullable SpawnGroupData spawnGroupData
    ) {
        if (spawnType == MobSpawnType.NATURAL) {
            applyNaturalSpawnEffects();
        }

        return super.finalizeSpawn(serverLevelAccessor, difficulty, spawnType, spawnGroupData);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        // Queens are persistent by nature -- they anchor a hive and may be tracked, so they must never despawn.
        return false;
    }

    private void applyNaturalSpawnEffects() {
        var level = level();

        if (level.isClientSide) {
            return;
        }

        alertPlayersOfSpawn();
        spawnGuards();
        resetQueenSpawnCooldown();

        StrainLeakData.getOrCreate(level)
            .ifSome(strainLeakData -> strainLeakData.add(getVariant(), -1));
    }

    private void alertPlayersOfSpawn() {
        for (var player : PlayerUtil.getTrackingPlayers(this)) {
            player.playNotifySound(AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(), SoundSource.MASTER, 1, 1);
            player.sendSystemMessage(
                Component.literal("A scream from the depths sends chills down your spine...")
                    .withStyle(AlienVariantTypes.getFor(this).chatColor(), ChatFormatting.ITALIC)
            );
        }
    }

    private void spawnGuards() {
        var droneType = Drone.getType(getVariant());

        for (var i = 0; i < 4; i++) {
            var drone = droneType.spawn((ServerLevel) level(), blockPosition(), MobSpawnType.NATURAL);

            if (drone != null) {
                drone.setPersistenceRequired();
            }
        }
    }

    private void resetQueenSpawnCooldown() {
        QueenSpawnChunkData.getOrCreate(level())
            .ifSome(queenSpawnChunkData -> queenSpawnChunkData.getSpawnCooldown().reset());
    }

    @Override
    public float maxUpStep() {
        return 2.5F;
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return AlienSoundEvents.ENTITY_QUEEN_IDLE.get();
    }

    @Override
    protected @NotNull SoundEvent getDeathSound() {
        return AlienSoundEvents.ENTITY_QUEEN_DEATH.get();
    }

    @Override
    protected @NotNull SoundEvent getHurtSound(@NotNull DamageSource damageSource) {
        return AlienSoundEvents.ENTITY_QUEEN_HURT.get();
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        if (
            !ovipositorManager.hasOvipositor()
                || !entity.getType().is(AlienEntityTypeTags.ALIENS)
        ) {
            super.doPush(entity);
        }
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    public QueenAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public OvipositorManager getOvipositorManager() {
        return ovipositorManager;
    }

    public QueenData getQueenData() {
        return queenData;
    }

    public QueenLifecyclePhaseManager getLifecyclePhaseManager() {
        return lifecyclePhaseManager;
    }

    /**
     * True if this queen was loaded from a save that predates the lifecycle system (no lifecycle-phase tag was
     * present). Used by {@code LegacyHiveRecovery} to identify queens that need migrating into the current lifecycle.
     */
    public boolean wasLoadedWithoutLifecycleState() {
        return loadedWithoutLifecycleState;
    }

    /** True while this queen is parked as a legacy-dormant queen awaiting recovery. */
    public boolean isLegacyDormant() {
        return legacyDormant;
    }

    /**
     * Marks (or clears) this queen's legacy-dormant state. Persisted so she stays parked across reloads until woken.
     */
    public void setLegacyDormant(boolean dormant) {
        this.legacyDormant = dormant;
    }

    /**
     * Wakes a legacy-dormant queen and hands her back to the normal lifecycle: clears the dormant flag and the
     * loaded-without-state marker, then restarts her LOCATION phase so she resumes founding/holding a hive under the
     * current system. Safe to call on an already-awake queen (the flags simply clear and the phase restarts).
     */
    public void wakeFromLegacyDormantRecovery() {
        this.legacyDormant = false;
        this.loadedWithoutLifecycleState = false;
        lifecyclePhaseManager.restartLocationPhase();
    }

    public QueenBindManager getBindManager() {
        return bindManager;
    }

    /**
     * Whether she is riding her ovipositor (the chained eggsack). Safe to call on the CLIENT: the ovipositor is a
     * passenger of the queen, and passengers are vanilla-synced - unlike the server-only OvipositorManager.
     */
    public boolean isRidingOvipositor() {
        return getPassengers()
            .stream()
            .anyMatch(passenger -> Objects.equals(passenger.getType(), AlienEntityTypes.OVIPOSITOR.get()));
    }

    /**
     * A CAPTIVE breeder is pacified and stays put: an inhibited queen riding her chained eggsack must not shuffle
     * around under idle AI, or her body drifts and rotates against the static eggsack that is anchored to her, leaving
     * her off-centre and contorted. Freeze her movement in that state only. A FOUNDING/reproductive queen (rides an
     * eggsack but is NOT inhibited) is untouched and can still shuffle to lay.
     */
    @Override
    public void travel(@NotNull Vec3 vec3) {
        if (isInhibited() && isRidingOvipositor()) {
            // Freeze horizontal drift only - keep vertical velocity so gravity still settles her onto the ground
            // if she was caught mid-air or on uneven terrain (a hard Vec3.ZERO would leave her hanging).
            var v = getDeltaMovement();
            setDeltaMovement(0.0, v.y, 0.0);
            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(vec3);
    }

    /** Whether the inhibitor device is attached (synced + persisted). */
    @Override
    public void die(@NotNull DamageSource damageSource) {
        // She is leaving the world - never leave her incapacitation bar stuck on a player's screen.
        incapacitationManager.onRemoved();
        super.die(damageSource);

        if (level() instanceof ServerLevel serverLevel) {
            TrackedQueenRegistry.markLostAndAnnounce(serverLevel, getUUID(), TrackedQueenRegistry.REASON_DECEASED);
        }
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        // Pry the inhibitor off: sneak + right-click an inhibited queen with a sword or axe. Re-enables her autonomy,
        // tears down her inhibited claim, and drops the inhibitor so it's recoverable. Costs the tool some durability.
        if (
            isInhibited()
                && player.isShiftKeyDown()
                && (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem)
        ) {
            if (level() instanceof ServerLevel serverLevel) {
                setInhibited(false);
                QueenInhibitionService.onReleased(serverLevel, this);
                spawnAtLocation(AlienItems.INHIBITOR.get());
                stack.hurtAndBreak(
                    5,
                    player,
                    hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND
                );
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        return super.mobInteract(player, hand);
    }

    public boolean isInhibited() {
        return hasInhibitor.get();
    }

    /** Attach or remove the inhibitor device. Server-authoritative; syncs and persists automatically. */
    public void setInhibited(boolean inhibited) {
        hasInhibitor.set(inhibited);
    }

    public boolean isTracked() {
        return tracked.get();
    }

    /** Attach or remove the tracker tag. Server-authoritative; syncs and persists automatically. */
    public void setTracked(boolean value) {
        tracked.set(value);
    }

    /**
     * Whether she is in the involuntary, defeat-induced incapacitated state (Part 2). Not yet implemented — the
     * incapacitation state machine (incap HP bar, kill/heal/self-recovery/capture exits) is a deferred feature, so this
     * hook returns {@code false} for now. When that state lands it should read its flag here, and the inhibitor gate
     * below picks it up automatically.
     */
    public boolean isIncapacitated() {
        return incapacitated.get();
    }

    /** Server-authoritative. Set by {@link QueenIncapacitationManager}; syncs and persists automatically. */
    public void setIncapacitated(boolean value) {
        incapacitated.set(value);
    }

    public QueenIncapacitationManager getIncapacitationManager() {
        return incapacitationManager;
    }

    /**
     * Whether the inhibitor may be applied to her right now. Per design she must be helpless in one of three ways:
     * incapacitated, in the {@link QueenLifecyclePhase#HIBERNATION} phase, or already secured with all four chains.
     */
    public boolean canBeInhibited() {
        return isIncapacitated()
            || lifecyclePhaseManager.getPhase() == QueenLifecyclePhase.HIBERNATION
            || bindManager.isFullyBound();
    }

    /**
     * Whether she is contained — subdued enough to be a captive breeder that grows a chained eggsack. For now this is
     * the four-chain full bind; a human titanium enclosure becomes a second containment source later.
     */
    public boolean isContained() {
        // Synced chain count (mirrors the bind anchors), so this is correct on both server and client
        // — the chained-eggsack renderer reads it off the vehicle queen.
        return bindChainCount.get() >= QueenBindManager.FULLY_BOUND_CHAINS;
    }

    public boolean isDigging() {
        return digging;
    }

    /**
     * Toggles the location-phase spectator dig. While digging she clips through blocks (noPhysics) and ignores gravity
     * so she can travel straight to her committed anchor; noPhysics also suppresses suffocation, and
     * {@link #isInvulnerableTo} adds fire/lava immunity. She stays an ordinary, attackable entity in every other
     * respect. Owned by {@code QueenLifecyclePhaseManager}, which reconciles it every tick.
     */
    public void setDigging(boolean digging) {
        if (this.digging == digging) {
            return;
        }
        this.digging = digging;
        this.noPhysics = digging;
        setNoGravity(digging);
        isDiggingSynced.set(digging);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (digging && source.is(DamageTypeTags.IS_FIRE)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // While DOWN, damage eats the incapacitation bar instead of her health - that bar IS the finisher. She
        // only truly dies when it is drained to zero.
        if (!level().isClientSide && isIncapacitated()) {
            if (incapacitationManager.onDamageWhileDown(amount)) {
                setIncapacitated(false);
                setNoAi(false);
                return super.hurt(damageSource, Float.MAX_VALUE); // finished off for real
            }
            return true; // absorbed by the bar
        }

        // A blow that WOULD kill her puts her down instead - unless her strain cannot be incapacitated, or she
        // has been worn down too many times inside the window, in which case it is a real death.
        if (!level().isClientSide && amount >= getHealth() && incapacitationManager.onLethalDamage()) {
            return true;
        }

        var wasHurt = super.hurt(damageSource, amount);
        if (wasHurt) {
            if (!level().isClientSide) {
                ovipositorManager.abandonOvipositor();
            }
            // Stage 3b: a solid hit rouses a hibernating queen (the manager filters by phase + damage threshold).
            getLifecyclePhaseManager().onHibernationDamage(amount);
        }
        return wasHurt;
    }

    @Override
    public Entity asEntity() {
        return this;
    }

    @Override
    public boolean isEggLayCooldownReady() {
        return queenData.isEggLayCooldownReady();
    }

    @Override
    public void resetEggLayCooldown() {
        queenData.resetEggLayCooldown();
    }

    @Override
    public boolean hasOvipositor() {
        return ovipositorManager.hasOvipositor();
    }

    @Override
    public Vec3 getEggLayingPosition() {
        return ovipositorManager.getEggLayingPosition();
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        // A pre-lifecycle-system save carries neither the lifecycle-phase tag nor the legacy-dormant marker. Detect
        // that
        // BEFORE loading the managers so LegacyHiveRecovery can migrate her.
        this.loadedWithoutLifecycleState =
            !compoundTag.contains(LIFECYCLE_PHASE_TAG)
                && !compoundTag.contains(LEGACY_DORMANT_TAG)
                && !compoundTag.contains(LEGACY_DORMANT_COMPAT_TAG);
        this.legacyDormant = compoundTag.getBoolean(LEGACY_DORMANT_TAG)
            || compoundTag.getBoolean(LEGACY_DORMANT_COMPAT_TAG);
        ovipositorManager.load(compoundTag);
        queenData.load(compoundTag);
        lifecyclePhaseManager.load(compoundTag);
        bindManager.load(compoundTag);
        rescueManager.load(compoundTag);
        bindManager.onLoaded(); // drop any chain whose anchor was broken while she was unloaded (phantom bind)
        incapacitationManager.load(compoundTag);
        incapacitationManager.onLoaded(); // a downed queen must not come back with her AI switched on
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putBoolean(LEGACY_DORMANT_TAG, legacyDormant);
        ovipositorManager.save(compoundTag);
        queenData.save(compoundTag);
        lifecyclePhaseManager.save(compoundTag);
        bindManager.save(compoundTag);
        rescueManager.save(compoundTag);
        incapacitationManager.save(compoundTag);
    }

    public static EntityType<? extends Alien> getType(AlienVariant alienVariant) {
        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.QUEEN.get();
            case NETHER -> AlienEntityTypes.NETHER_QUEEN.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_QUEEN.get();
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_QUEEN.get();
        };
    }
}
