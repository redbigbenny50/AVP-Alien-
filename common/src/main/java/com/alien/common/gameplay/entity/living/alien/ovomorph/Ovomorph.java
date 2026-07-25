package com.alien.common.gameplay.entity.living.alien.ovomorph;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.GrowthManager;
import com.alien.common.gameplay.entity.living.alien.ovomorph.ai.OvomorphGOAP;
import com.alien.common.gameplay.hive.convoy.ConvoyMemberTracker;
import com.alien.common.model.alien.HatchState;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.PlayerStatConstants;
import com.blib.api.common.entity.v1.vibration.VibrationSystemManager;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.graph.Graph;
import com.just.core.functional.option.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class Ovomorph extends Alien implements GOAPUser<Ovomorph>, Shearable {

    public static final HatchState DEFAULT_HATCH_STATE = HatchState.SLEEPING;

    private static final int HOST_VIBRATION_RADIUS = 8;

    private static final double RAID_FRENZY_HATCH_CONTEXT_RADIUS_BLOCKS = 32.0D;

    public static AttributeSupplier.Builder createOvomorphAttributes() {
        return Alien.createAlienAttributes()
                .add(Attributes.ARMOR, 0f)
                .add(Attributes.ARMOR_TOUGHNESS, 0f)
                .add(Attributes.ATTACK_DAMAGE, 0f)
                .add(Attributes.FOLLOW_RANGE, 0f)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1f)
                .add(Attributes.MAX_HEALTH, PlayerStatConstants.BASE_HEALTH * 1.5F)
                .add(Attributes.MOVEMENT_SPEED, 0f);
    }

    public final DataAccessor<Byte> hatchStateId;

    public final DataAccessor<Byte> maxSpawnCount;

    public final DataAccessor<Boolean> isRooted;

    private final GrowthManager growthManager;

    private final OvomorphAnimationDispatcher animationDispatcher;

    private final VibrationSystemManager vibrationSystemManager;

    private final HatchManager hatchManager;

    /** How long a reserved-but-uncollected egg waits before it re-broadcasts for a carrier (30s). */
    private static final int PICKUP_CLAIM_TIMEOUT_TICKS = 20 * 30;

    public boolean pickupRequestAcknowledged;

    /**
     * Ticks this egg has sat reserved without anyone actually collecting it. An acknowledged egg goes SILENT (it stops
     * broadcasting pickup requests), so if the worker that claimed it dies, unloads, or wanders off, the egg would wait
     * forever and never be delivered. After {@link #PICKUP_CLAIM_TIMEOUT_TICKS} the claim lapses and it starts calling
     * for a carrier again.
     */
    private int pickupClaimTicks;

    public boolean wantsPickup;

    /**
     * Host-delivery stamp: the host-chamber egg-drop cell this egg is designated for, or null when it is not
     * host-bound. The stamp IS the delivery - it lives on the egg (not the hauler), survives reloads and changing
     * hands, and is the ONLY thing the inbound-egg gate counts. Fresh clutch eggs and ordinary nursery hauls are
     * unstamped, so they can never hide a host delivery or block one another. Set by the ferry on release (or by a
     * carrier claiming a drop opportunistically); cleared on rooting or when the delivery is abandoned.
     */
    @Nullable
    private BlockPos hostDropTarget;

    public Ovomorph(EntityType<? extends Ovomorph> entityType, Level level) {
        super(entityType, level);

        this.hatchStateId = new DataAccessor<>(this, AlienDataSyncKeys.OVOMORPH_HATCH_STATE.get());
        this.maxSpawnCount = new DataAccessor<>(this, AlienDataSyncKeys.OVOMORPH_MAXIMUM_SPAWN_COUNT.get());
        this.isRooted = new DataAccessor<>(this, AlienDataSyncKeys.OVOMORPH_IS_ROOTED.get());

        this.growthManager = new GrowthManager(this);
        this.animationDispatcher = new OvomorphAnimationDispatcher(this);
        this.hatchManager = new HatchManager(this, 3 * 20, 3 * 20);
        this.wantsPickup = false;
        this.vibrationSystemManager = new VibrationSystemManager(this, 2.5F, HOST_VIBRATION_RADIUS);
    }

    @Override
    public void updateDynamicGameEventListener(@NotNull BiConsumer<DynamicGameEventListener<?>, ServerLevel> biConsumer) {
        vibrationSystemManager.updateDynamicGameEventListener(biConsumer);
    }

    @Override
    public @Nullable Graph<Ovomorph> blib$getGOAPGraphOrNull() {
        return OvomorphGOAP.GRAPH;
    }

    @Override
    public @Nullable EntityType<? extends Alien> getTypeForVariant(AlienVariant alienVariant) {
        return getType(alienVariant, isRoyal());
    }

    @Override
    public void tick() {
        super.tick();
        growthManager.tick();
        hatchManager.tick();
        vibrationSystemManager.tick();
        // Spent shell (already hatched): despawn after a Minecraft day if nothing eats it.
        com.alien.common.gameplay.entity.living.alien.MoltFeeding.tickRemainsLifetime(this);

        if (!level().isClientSide) {
            tryRaidFrenzyHatch();

            this.wantsPickup = canBePickedUp();

            // Lapse a stale reservation: a claim that never turns into an actual pickup must not mute this egg
            // forever (the claimer may have died, unloaded, or been pulled onto other work).
            if (pickupRequestAcknowledged && wantsPickup && !isPassenger()) {
                if (++pickupClaimTicks > PICKUP_CLAIM_TIMEOUT_TICKS) {
                    this.pickupRequestAcknowledged = false;
                    this.pickupClaimTicks = 0;
                }
            } else {
                this.pickupClaimTicks = 0;
            }

            if (!pickupRequestAcknowledged && wantsPickup && tickCount % 20 == 0) {
                var alienVariantType = AlienVariantTypes.getFor(this);
                var deferredHolder = alienVariantType.eggPickupRequestEvent();

                if (deferredHolder != null) {
                    gameEvent(deferredHolder);
                }
            }

            if (isPassenger()) {
                this.pickupRequestAcknowledged = false;
            }
        }
    }

    private void tryRaidFrenzyHatch() {
        if (!hasEffect(AlienMobEffects.getFrenzyHolder())) {
            return;
        }
        if (!ConvoyMemberTracker.isNearActiveRaidContext(this, RAID_FRENZY_HATCH_CONTEXT_RADIUS_BLOCKS)) {
            return;
        }
        tryHatch();
    }

    public @Nullable BlockPos getHostDropTarget() {
        return hostDropTarget;
    }

    public void setHostDropTarget(@Nullable BlockPos hostDropTarget) {
        this.hostDropTarget = hostDropTarget == null ? null : hostDropTarget.immutable();
    }

    /**
     * True while this egg is the designated in-flight delivery for {@code dropCell}: stamped for that exact cell, still
     * deliverable (unhatched), and not yet rooted - either loose awaiting pickup or riding a hauler. This is the
     * identity test the inbound-egg gate runs; proximity alone never counts.
     */
    public boolean isHostBoundInTransit(BlockPos dropCell) {
        return dropCell.equals(hostDropTarget)
                && isAlive()
                && getHatchState().contains(HatchState.SLEEPING)
                && (!isRooted.get() || isPassenger());
    }

    private static final String NBT_HOST_DROP_TARGET = "HostDropTarget";

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        if (hostDropTarget != null) {
            compoundTag.putLong(NBT_HOST_DROP_TARGET, hostDropTarget.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        this.hostDropTarget = compoundTag.contains(NBT_HOST_DROP_TARGET)
                ? BlockPos.of(compoundTag.getLong(NBT_HOST_DROP_TARGET))
                : null;
    }

    /**
     * Natural aberrant genesis, mirroring the villager-to-witch precedent: lightning striking a normal or nether egg
     * mutates it into an aberrant egg (royal eggs mutate into royal aberrant eggs). This is the only way to create
     * aberrants in avp_alien without the AVPHuman genetic system. Eggs only - adults are never converted. Aberrant
     * and irradiated eggs take the strike like any other mob.
     * <p>
     * The replacement keeps the egg's physical state (hatch state, spawn count, rooted, name, persistence) but
     * deliberately drops hive-logistics state (pickup claims, host-delivery stamp) and lineage membership - the old
     * lineage would treat the mutated egg as a rival anyway, and the variant-faction auto-join on entity load slots
     * the new egg into the aberrant variant faction on its own. When the nether-aberrant strain exists, the nether
     * egg mapping here is the one line to retarget.
     */
    @Override
    public void thunderHit(@NotNull ServerLevel serverLevel, @NotNull LightningBolt lightningBolt) {
        var target = aberrantConversionTarget();

        if (target == null) {
            super.thunderHit(serverLevel, lightningBolt);
            return;
        }

        var converted = target.create(serverLevel);

        if (converted == null) {
            super.thunderHit(serverLevel, lightningBolt);
            return;
        }

        converted.copyPosition(this);
        converted.hatchStateId.set(this.hatchStateId.get());
        converted.maxSpawnCount.set(this.maxSpawnCount.get());
        converted.isRooted.set(this.isRooted.get());

        if (hasCustomName()) {
            converted.setCustomName(getCustomName());
            converted.setCustomNameVisible(isCustomNameVisible());
        }

        if (isPersistenceRequired()) {
            converted.setPersistenceRequired();
        }

        serverLevel.addFreshEntity(converted);
        discard();
    }

    /**
     * The aberrant egg type a lightning strike turns this egg into, or {@code null} when this egg's strain does not
     * convert (aberrant and irradiated lines).
     */
    private @Nullable EntityType<Ovomorph> aberrantConversionTarget() {
        var type = getType();

        if (type == AlienEntityTypes.OVOMORPH.get() || type == AlienEntityTypes.NETHER_OVOMORPH.get()) {
            return AlienEntityTypes.ABERRANT_OVOMORPH.get();
        }

        if (type == AlienEntityTypes.ROYAL_OVOMORPH.get() || type == AlienEntityTypes.ROYAL_NETHER_OVOMORPH.get()) {
            return AlienEntityTypes.ROYAL_ABERRANT_OVOMORPH.get();
        }

        return null;
    }

    public boolean canBeHeld() {
        return isAlive()
                && !isDeadOrDying()
                && !isRooted.get()
                && getHatchState().contains(HatchState.SLEEPING);
    }

    public boolean canBePickedUp() {
        return canBeHeld()
                && onGround()
                && !isPassenger();
    }

    public void tryHatch() {
        if (
                !level().isClientSide
                        && !hatchManager.isHatching()
                        && !hatchManager.isHatched()
                        && !isIrradiated()
        ) {
            hatchManager.hatch();
        }
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand interactionHand) {
        if (level().isClientSide) {
            return super.mobInteract(player, interactionHand);
        }

        var itemStack = player.getItemInHand(interactionHand);
        var resinBallItem = AlienVariantTypes.getFor(this).resinBall().get();

        if (isRooted.get() && itemStack.is(Items.SHEARS)) {
            shear(SoundSource.PLAYERS);
            gameEvent(GameEvent.SHEAR, player);
            itemStack.hurtAndBreak(1, player, getSlotForHand(interactionHand));

            return InteractionResult.SUCCESS;
        } else if (!isRooted.get() && itemStack.is(resinBallItem)) {
            level().playSound(null, this, AlienSoundEvents.ENTITY_OVOMORPH_ROOT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            isRooted.set(true);
            itemStack.consume(1, player);
        }

        return super.mobInteract(player, interactionHand);
    }

    @Override
    public void shear(@NotNull SoundSource soundSource) {
        isRooted.set(false);
        level().playSound(null, this, SoundEvents.SHEEP_SHEAR, soundSource, 1.0F, 1.0F);
        level().playSound(null, this, AlienSoundEvents.ENTITY_OVOMORPH_SHEAR.get(), soundSource, 1.0F, 1.0F);
        var resinBallItem = AlienVariantTypes.getFor(this).resinBall().get();

        var itemEntity = this.spawnAtLocation(resinBallItem, 1);

        if (itemEntity != null) {
            itemEntity.setDeltaMovement(
                    itemEntity.getDeltaMovement()
                            .add(
                                    (random.nextFloat() - random.nextFloat()) * 0.1F,
                                    random.nextFloat() * 0.05F,
                                    (random.nextFloat() - random.nextFloat()) * 0.1F
                            )
            );
        }
    }

    @Override
    public boolean readyForShearing() {
        return isRooted.get();
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float damage) {
        var isHurt = super.hurt(damageSource, damage);

        if (!level().isClientSide && isHurt && damageSource.getEntity() != null) {
            tryHatch();
        }

        return isHurt;
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            super.doPush(entity);
            return;
        }

        if (AlienPredicates.isFreeHost(this, entity)) {
            tryHatch();
        }

        if (
            // Entity is not an alien...
                !entity.getType().is(AlienEntityTypeTags.ALIENS)
                        // OR entity is an ovomorph.
                        || entity.getType().is(AlienEntityTypeTags.OVOMORPHS)
        ) {
            super.doPush(entity);
        }
    }

    @Override
    protected boolean canBleedAcid() {
        return !hatchManager.isHatching()
                && !hatchManager.isHatched();
    }

    @Override
    public boolean isPushedByFluid() {
        return !isRooted.get();
    }

    @Override
    public boolean isPushable() {
        return !isRooted.get();
    }

    @Override
    public boolean isPersistenceRequired() {
        if (hatchManager.isHatched()) {
            // If the ovomorph is hatched, then defer to super and no other factors.
            return super.isPersistenceRequired();
        }

        // Otherwise if super check passes or if ovomorph is not rooted, then persist the ovomorph.
        return super.isPersistenceRequired() || !isRooted.get();
    }

    @Override
    protected boolean canHeal() {
        return !hatchManager.isHatching()
                && !hatchManager.isHatched()
                && super.canHeal();
    }

    @Override
    protected boolean canAlienRideVehicle(@NotNull Entity vehicle) {
        return !isRooted.get();
    }

    @Override
    protected float getHealthRegenPerSecond() {
        return 0.5F;
    }

    public HatchManager getHatchManager() {
        return hatchManager;
    }

    public Option<HatchState> getHatchState() {
        var id = (int) hatchStateId.get();
        return Option.ofNullable(HatchState.ID_TO_HATCH_STATE_MAP.get(id));
    }

    public void setHatchState(HatchState hatchState) {
        hatchStateId.set((byte) hatchState.getId());
    }

    public OvomorphAnimationDispatcher getAnimationDispatcher() {
        return animationDispatcher;
    }

    public VibrationSystemManager getVibrationSystemManager() {
        return vibrationSystemManager;
    }

    public static @Nullable EntityType<? extends Ovomorph> getType(AlienVariant alienVariant, boolean isRoyal) {
        if (isRoyal) {
            return switch (alienVariant) {
                case NORMAL -> AlienEntityTypes.ROYAL_OVOMORPH.get();
                case NETHER -> AlienEntityTypes.ROYAL_NETHER_OVOMORPH.get();
                case ABERRANT -> AlienEntityTypes.ROYAL_ABERRANT_OVOMORPH.get();
                case IRRADIATED -> null;
            };
        }

        return switch (alienVariant) {
            case NORMAL -> AlienEntityTypes.OVOMORPH.get();
            case NETHER -> AlienEntityTypes.NETHER_OVOMORPH.get();
            case ABERRANT -> AlienEntityTypes.ABERRANT_OVOMORPH.get();
            case IRRADIATED -> null;
        };
    }
}