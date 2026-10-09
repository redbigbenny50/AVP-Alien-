package com.alien.common.gameplay.entity.living.alien.ovomorph;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.GrowthManager;
import com.alien.common.gameplay.entity.living.alien.IrradiatedDetonation;
import com.alien.common.gameplay.entity.living.alien.ovomorph.ai.OvomorphGOAP;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg.QueenEggZone;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
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

    /**
     * An egg only runs GOAP when it has something to decide.
     * <p>
     * 🚨🚨 A NULL GRAPH SKIPS THE AGENT ENTIRELY - see MixinLivingEntity_GOAPUser, which returns before touching the
     * agent when this is null. That makes it the cheapest gate available: not a throttle, not a smaller graph, but no
     * GOAP at all.
     * </p>
     * <p>
     * ⚠⚠ EGGS ARE THE MOST NUMEROUS THING IN A HIVE AND THE LEAST DECISIVE. A live server profile showed Ovomorph.tick
     * at 9.10% of the entire server thread - more than any single caste - for entities that sit still and hold one
     * boolean. The graph is two sensors, one goal and one action, so that cost was the GOAP MACHINERY running a full
     * plan cycle every tick for an answer that changes once in an egg's life.
     * </p>
     * <p>
     * ⚠ WANTING TO HATCH IS THE ONLY DECISION AN EGG MAKES. Until then it needs no plan; once hatched it needs none
     * again. Both ends return null, so an ordinary egg sitting in a chamber costs nothing at all.
     * </p>
     * <p>
     * ⚠ The desire itself is still evaluated - HatchDesireManager.tick runs on its own 20-tick cadence from the entity
     * tick and is unaffected by this. An egg still notices a host walking up to it.
     * </p>
     */
    @Override
    public @Nullable Graph<Ovomorph> blib$getGOAPGraphOrNull() {
        if (hatchManager.isHatched() || !hatchManager.getHatchDesireManager().wantsToHatch()) {
            return null;
        }

        return OvomorphGOAP.GRAPH;
    }

    /**
     * A settled egg does no movement work at all.
     * <p>
     * 🚨🚨 AN OVOMORPH IS A MOB THAT NEVER MOVES, AND IT WAS PAYING FULL PRICE FOR MOVEMENT ANYWAY. Every tick it ran
     * the whole vanilla pipeline - travel -> handleRelativeFrictionAndCalculateMovement -> move -> collide ->
     * collideBoundingBox -> collectColliders -> BlockCollisions -> getBlockState - for a delta of zero.
     * </p>
     * <p>
     * ⚠⚠ AND THEY ARE THE BIGGEST POPULATION IN A HIVE. A live server profile showed 53 ovomorphs against 24 warriors,
     * with that collision chain the heaviest branch under Xenomorph.tick. Eggs outnumber everything, so the waste
     * scales faster than any other caste.
     * </p>
     * <p>
     * ⚠ ONLY WHEN GENUINELY AT REST: on the ground with no residual motion. An egg that has just been laid, is falling,
     * or has been pushed still travels normally, so nothing floats and nothing lands wrong.
     * </p>
     */
    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        // 🚨🚨 onGround() IS ONLY UPDATED BY Entity.move(), WHICH THIS SKIP BYPASSES. Once settled, the flag
        // froze true - so when the block underneath was later mined the entity never re-evaluated and simply
        // HUNG IN THE AIR. Reported as eggs floating.
        //
        // ⭐ So the support is verified directly instead of trusted. One block read per tick against the entire
        // collision sweep it replaces - still overwhelmingly the cheaper path, and now it cannot go stale.
        if (
            onGround()
                && getDeltaMovement().lengthSqr() < RESTING_MOTION_EPSILON
                && hasSolidSupportBelow()
        ) {
            // Match what travel() would have left behind for a stationary entity, without the collision sweep.
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            calculateEntityAnimation(false);
            return;
        }

        super.travel(travelVector);
    }

    /**
     * Whether the block underneath can actually hold this entity up.
     * <p>
     * ⚠ Checked every tick rather than cached: the whole point is to notice the moment the floor is removed.
     * </p>
     */
    private boolean hasSolidSupportBelow() {
        var below = blockPosition().below();

        return level().getBlockState(below).entityCanStandOn(level(), below, this);
    }

    /** Below this squared speed an egg is treated as settled. Well under a single tick of gravity. */
    private static final double RESTING_MOTION_EPSILON = 1.0E-7D;

    @Override
    public @Nullable EntityType<? extends Alien> getTypeForVariant(AlienVariant alienVariant) {
        return getType(alienVariant, isRoyal());
    }

    /**
     * ⭐⭐ AN EGG SWALLOWED BY A ROYAL BODY GETS ITSELF OUT. [stated] "eggs seem to get pushed inside the queens hotbox
     * and they cant get to it, if that happens they should be able to get them or if theres an egg they cant get to
     * then that egg despawns into the bank."
     * <p>
     * ⚠ THIS IS THE SAFETY NET, NOT THE FIX. The real fix is that xenomorphs no longer push ovomorphs at all
     * (Xenomorph.doPush), so eggs should stop ending up here. This catches the ones ALREADY buried in a live world, and
     * anything that gets under her by some other route - a queen walking onto a laid egg, a chunk reload settling her
     * on top of one.
     * </p>
     * <p>
     * TWO OUTCOMES, IN HIS ORDER OF PREFERENCE: relocate to a free cell in her own clutch zone if one exists, and only
     * bank the egg when there is genuinely nowhere to put it. Walking through her hitbox was his third option and is
     * the one I did not take - it would need a per-entity collision exemption on the hauler AND the egg, and it leaves
     * the egg somewhere no path can reach anyway.
     * </p>
     * <p>
     * ⚠ CHEAP BY CONSTRUCTION: the scan runs once a second and only when this egg is actually intersecting a royal. An
     * egg sitting normally in a nursery never gets past the first line.
     * </p>
     */
    private void rescueFromRoyalHitbox() {
        if (tickCount % ROYAL_RESCUE_INTERVAL_TICKS != 0 || isPassenger() || !isAlive()) {
            return;
        }

        // [stated] Oct 5: "this egg keeps jumping ... its rooted by resin so it shouldnt be moving at all". The rescue
        // exists for LOOSE eggs a hauler cannot reach under her. A ROOTED egg is never collected - it hatches where it
        // is - so being under her costs nothing, and moving it dragged chamber eggs into her front clutch (why a rooted
        // egg sat right beside her). Rooted eggs stay put.
        if (isRooted.get()) {
            return;
        }

        // Oct 9 - a queen OR an empress: an empress-led hive now rings her with eggs too, so one can end up under her.
        net.minecraft.world.entity.LivingEntity queen = null;
        var royals = level().getEntitiesOfClass(Queen.class, getBoundingBox().inflate(0.05));

        if (!royals.isEmpty()) {
            queen = royals.get(0);
        } else {
            var empresses = level().getEntitiesOfClass(
                com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress.class,
                getBoundingBox().inflate(0.05)
            );

            if (empresses.isEmpty()) {
                return;
            }

            queen = empresses.get(0);
        }

        for (var candidate : QueenEggZone.candidates(level(), queen)) {
            // ⚠ The candidate must not be under her either, or the egg is simply re-buried next second and the
            // rescue turns into a shuffle that never terminates.
            var target = new net.minecraft.world.phys.Vec3(
                candidate.getX() + 0.5,
                candidate.getY(),
                candidate.getZ() + 0.5
            );

            // [stated] Oct 5: the WHOLE egg must clear her, not just its centre point. A spot that kept only the centre
            // outside her box left the egg still overlapping her, so it was "rescued" again next second - and her
            // clutch zone is laid out from her FACING, so each time she turned it went somewhere new: the jumping.
            // Clear by a margin, and never on top of another egg.
            var eggBox = getDimensions(getPose()).makeBoundingBox(target);

            if (queen.getBoundingBox().inflate(RESCUE_CLEARANCE).intersects(eggBox)) {
                continue;
            }

            if (!level().getEntitiesOfClass(Ovomorph.class, eggBox, other -> other != this).isEmpty()) {
                continue;
            }

            moveTo(target.x, target.y, target.z, getYRot(), getXRot());
            return;
        }

        // Nowhere in her clutch to put it - hand the egg back to the hive as stock rather than leave it unreachable.
        bankAndRemove();
    }

    /** Returns this egg to the hive's reserves and removes the entity. No hive to return to = leave it alone. */
    private void bankAndRemove() {
        var location = com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver.reserveReturnLocation(this);

        if (location == null) {
            return;
        }

        location.localReserves().addBrood(getType(), 1);
        discard();
    }

    /** How far a rescued egg must clear the royal's hitbox, in blocks - so it is not under her again next second. */
    private static final double RESCUE_CLEARANCE = 0.25D;

    /** How often a buried egg checks whether it is inside a royal. One second - this is a rescue, not a hot path. */
    private static final int ROYAL_RESCUE_INTERVAL_TICKS = 20;

    @Override
    public void tick() {
        super.tick();

        // Oct 6 - profiler v3 laps; free while no session runs.
        var perfLap = com.blib.api.common.perf.v1.BLibPerf.start();
        growthManager.tick();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "ovomorph.growth", perfLap);
        hatchManager.tick();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "ovomorph.hatch", perfLap);
        vibrationSystemManager.tick();
        perfLap = com.blib.api.common.perf.v1.BLibPerf.lap(this, "ovomorph.vibration", perfLap);
        // Spent shell (already hatched): despawn after a Minecraft day if nothing eats it.
        com.alien.common.gameplay.entity.living.alien.MoltFeeding.tickRemainsLifetime(this);

        if (!level().isClientSide) {
            rescueFromRoyalHitbox();
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

            com.blib.api.common.perf.v1.BLibPerf.lap(this, "ovomorph.remains+raid+pickup", perfLap);
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
     * aberrants in avp_alien without the AVPHuman genetic system. Eggs only - adults are never converted. Aberrant and
     * irradiated eggs take the strike like any other mob.
     * <p>
     * The replacement keeps the egg's physical state (hatch state, spawn count, rooted, name, persistence) but
     * deliberately drops hive-logistics state (pickup claims, host-delivery stamp) and lineage membership - the old
     * lineage would treat the mutated egg as a rival anyway, and the variant-faction auto-join on entity load slots the
     * new egg into the aberrant variant faction on its own. When the nether-aberrant strain exists, the nether egg
     * mapping here is the one line to retarget.
     */
    @Override
    public void thunderHit(@NotNull ServerLevel serverLevel, @NotNull LightningBolt lightningBolt) {
        var target = aberrantConversionTarget();

        if (target == null) {
            super.thunderHit(serverLevel, lightningBolt);
            return;
        }

        if (replaceWith(serverLevel, target) == null) {
            super.thunderHit(serverLevel, lightningBolt);
        }
    }

    /**
     * Swaps this egg for one of another type in place, carrying the state an egg should keep across a mutation: hatch
     * progress, spawn count, rooting, custom name, persistence. Deliberately DROPS hive-logistics state (pickup claims,
     * host-delivery stamps) and lineage membership - the variant faction re-homes the new egg on load, and a mutated
     * egg's old lineage would treat it as a rival anyway. Shared by every egg conversion (lightning aberrant genesis,
     * royal jelly promotion) so the paths cannot drift apart. Returns the new egg, or null when the type could not be
     * created.
     */
    /**
     * Turns this egg into an irradiated one, crown and all.
     * <p>
     * Deliberately resolves the NON-ROYAL type: there is no royal irradiated line, so a royal egg caught by a nuke
     * becomes a plain irradiated one and the crown is spent - the same trade the jelly-fed conversion makes. Returns
     * false when there is nothing to do (already irradiated, or client side).
     */
    public boolean convertToIrradiated(ServerLevel serverLevel) {
        if (getVariant() == AlienVariant.IRRADIATED) {
            return false;
        }

        var target = getType(AlienVariant.IRRADIATED, false);
        return target != null && replaceWith(serverLevel, target) != null;
    }

    private @Nullable Ovomorph replaceWith(ServerLevel serverLevel, EntityType<? extends Ovomorph> target) {
        var converted = target.create(serverLevel);

        if (converted == null) {
            return null;
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

        // GENES RIDE ACROSS. An egg carrying avp_human genes that is promoted with royal jelly (or converted by
        // lightning, or by a nuke) is the SAME egg wearing a new type - losing its genetics to a rank change makes
        // the two systems contradict each other, since the whole point of gene work is that it survives the
        // lifecycle. Direction matches the existing call sites (LayEggAction, MixinLivingEntity_Host): A.transfer(B)
        // pushes A's genes INTO B, so this is old egg -> new egg. activateDormantGenes stays FALSE so a promotion
        // never silently switches dormant genes on - it copies the genome as it stands.
        // No-ops without avp_human: GeneManagerProxy resolves to EMPTY and every branch falls through.
        getGeneManager().transfer(converted.getGeneManager(), false);

        // ⭐⭐ AND THE ATTRIBUTE MODIFIERS RIDE ACROSS TOO, [stated] "he had a large egg with multiple huggers in it he
        // used jelly on it and it became a small normal sized egg again".
        // <p>
        // A big egg is NOT a different entity or a bigger model - OVOMORPH and ROYAL_OVOMORPH are registered with the
        // identical .sized(), the renderer uses a flat 1.35F, and nothing in this mod scales an egg at all. The size
        // comes from avp_human: Genes.handleBonusParasiteCount reads BONUS_PARASITE_COUNT off the ACTIVE gene map and
        // applies an AttributeModifier(value / 2.0, ADD_VALUE) to Attributes.SCALE. So "large egg with extra huggers"
        // is ONE gene, expressed as a derived ATTRIBUTE MODIFIER rather than as stored state.
        // </p>
        // <p>
        // ⚠ The gene VALUE already rode across on the line above - that is why he saw genes survive while the egg
        // still shrank. What did not ride across is the MODIFIER, because target.create() builds an entity with a
        // fresh AttributeMap and nothing here ever copied one. assignAllValues copies base values AND modifiers, and
        // it is safe precisely because every ovomorph type shares one attribute supplier - there is no per-type value
        // to clobber.
        // </p>
        // <p>
        // ⚠ THIS IS DELIBERATELY BELT-AND-BRACES. avp_human's GeneManager.tick re-applies effects for any key in
        // GeneMap.getDirtyKeys(), and the transfer above DOES mark them dirty (a null-to-value change counts), so in
        // principle the modifier should come back on its own. It demonstrably did not. Copying the modifiers restores
        // the egg regardless of whether that re-apply fires, and costs one call.
        // </p>
        converted.getAttributes().assignAllValues(getAttributes());

        serverLevel.addFreshEntity(converted);
        discard();

        return converted;
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
        } else if (!isRoyal() && itemStack.is(com.alien.common.registry.init.item.AlienItems.RAW_ROYAL_JELLY.get())) {
            // ROYAL JELLY PROMOTION: feeding raw royal jelly to an ordinary egg makes it a ROYAL egg. This replaces
            // the old Metamorphosis-potion path (removed from the growth stages) - royalty is now something you
            // deliberately invest in, one egg and one jelly at a time, instead of a side effect of splashing a
            // potion across a clutch. Irradiated eggs have no royal form, so getType returns null and the jelly is
            // left in hand.
            var royalType = getType(getVariant(), true);

            if (royalType != null && level() instanceof ServerLevel serverLevel) {
                if (replaceWith(serverLevel, royalType) != null) {
                    itemStack.consume(1, player);
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (
            getVariant() != AlienVariant.IRRADIATED
                && itemStack.is(com.alien.common.registry.init.item.AlienItems.RAW_IRRADIATED_JELLY.get())
        ) {
            // IRRADIATION: the same deliberate, one-egg-and-one-jelly investment royal jelly represents, pointed at
            // STRAIN instead of rank. Normal, nether and aberrant eggs all take it.
            //
            // ROYALTY DOES NOT SURVIVE IT. There is no royal irradiated line - getType(IRRADIATED, true) is null by
            // design - so a royal egg fed this becomes a plain irradiated one and the crown is spent. That is the
            // trade, not an oversight: the irradiated strain does not breed through hosts at all, so a royal egg has
            // nothing left to be royal FOR.
            var irradiatedType = getType(AlienVariant.IRRADIATED, false);

            if (irradiatedType != null && level() instanceof ServerLevel serverLevel) {
                if (replaceWith(serverLevel, irradiatedType) != null) {
                    itemStack.consume(1, player);
                    return InteractionResult.SUCCESS;
                }
            }
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
            // An irradiated egg is ordnance, not a nursery. Damage sets it off instead of hatching it - the only way
            // to open one deliberately is to let a host trigger it, and then what comes out is a bomb too.
            //
            // SHEARING IS UNAFFECTED: that removes the resin holding the egg down and never damages the egg, so
            // harvesting one stays exactly as safe as it is for every other strain.
            if (getVariant() == AlienVariant.IRRADIATED) {
                IrradiatedDetonation.detonate(this);
                discard();
                return isHurt;
            }

            tryHatch();
        }

        return isHurt;
    }

    /**
     * ⭐⭐ A ROOTED EGG DOES NO PUSH WORK AT ALL.
     * <p>
     * ⚠⚠ THIS IS A REAL TICK COST, NOT A MICRO-OPTIMISATION. Vanilla {@code LivingEntity.pushEntities} runs TWO
     * {@code Level.getEntities} AABB queries EVERY TICK FOR EVERY LIVING ENTITY, before it has even looked at what it
     * found. A mature hive holds hundreds of rooted eggs, and every one of them was paying for two spatial queries a
     * tick to discover that anchored furniture cannot push anything.
     * </p>
     * <p>
     * A rooted egg is already unpushABLE ({@link #isPushable()}), so it was never a target - this closes the other
     * half, and the pair means a settled nursery costs nothing.
     * </p>
     * <p>
     * ⚠ THIS CHANGES NOTHING ABOUT EGG STACKING, which is why it is safe to keep while egg-on-egg pushing stays on. A
     * rooted egg was never in anyone's pushable list to begin with, so a loose egg dropped against one was already
     * unaffected by it - before this change and after. Only LOOSE eggs ever pushed each other apart, and they still do.
     * </p>
     */
    @Override
    protected void pushEntities() {
        if (isRooted.get()) {
            return;
        }

        super.pushEntities();
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

        // ⭐⭐ EGGS DO PUSH EACH OTHER, AND THAT IS LOAD-BEARING - DO NOT "OPTIMISE" IT AWAY.
        //
        // I removed this once for the tick cost and it was the wrong call. [stated] "i dont want them to make
        // infinite super stacks and some player machines rely on the eggs making chains to turn off machines and
        // such with sensors." Mutual repulsion is what spreads loose eggs into a line instead of a single pile -
        // so it is simultaneously the stack limiter AND a mechanic players have built machines on top of.
        //
        // ⚠ THE COST IS REAL AND ACCEPTED: N loose eggs in contact is O(N²) push calls per tick, and each push
        // imparts velocity, so every egg then runs a full move-with-collision and broadcasts a position update.
        // The mitigation is the ROOTED skip in pushEntities above, which covers the hundreds of eggs in an actual
        // hive; a player's crate of LOOSE eggs pays full price by design, because the churn IS the feature.
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
            case IRRADIATED -> AlienEntityTypes.IRRADIATED_OVOMORPH.get();
        };
    }
}
