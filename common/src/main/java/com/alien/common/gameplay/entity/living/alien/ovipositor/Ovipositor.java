package com.alien.common.gameplay.entity.living.alien.ovipositor;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.tag.AlienDamageTypesTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.data_sync.v1.model.DataUser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * ⚠ IMPLEMENTS {@code DataUser} so it can carry a synced field of its own. The interface supplies a default container,
 * so this is a marker rather than work - but without it {@code DataAccessor} has nothing to bind to.
 */
public class Ovipositor extends Mob implements DataUser {

    /**
     * A static entity does no movement work at all.
     * <p>
     * 🚨 SAME WASTE AS THE OVOMORPH: this is a Mob that never moves, yet it ran the full vanilla pipeline every tick -
     * travel -> move -> collide -> collideBoundingBox -> collectColliders -> BlockCollisions - for a delta of zero. A
     * live server profile showed that collision chain as the heaviest branch under entity ticking.
     * </p>
     * <p>
     * ⚠ ONLY WHEN GENUINELY AT REST - on the ground with no residual motion - so anything freshly spawned or falling
     * still settles normally.
     * </p>
     */
    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        // ⭐ Oct 5 perf - WORN, IT DOES NOT TRAVEL. Riding its royal, the sack's position is set every tick by the
        // royal's positionRider; vanilla still ran the whole travel step for it (friction, gravity, an entity
        // collision query, the inside-blocks scan) only for the result to be overwritten. /blib perf measured that
        // at ~26 us per tick for one sack - more than everything else it does.
        if (isPassenger()) {
            setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            return;
        }

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

    /** Below this squared speed the entity is treated as settled. */
    private static final double RESTING_MOTION_EPSILON = 1.0E-7D;

    /**
     * ⭐⭐⭐ A CLICK ON THE EGGSACK IS A CLICK ON THE ROYAL WEARING IT.
     * <p>
     * ⚠⚠ THIS IS WHY PLAYERS "CONSTANTLY REPORT THEY CANT FEED THE QUEEN A ROYAL JELLY BLOCK". The ovipositor is 5.0 x
     * 3.25 and rides her; her own hitbox is smaller, so THE SACK ENVELOPS HER. Aim at the queen and the ray hits the
     * sack, which had no interaction handler at all - so the click did nothing and never reached the code that was
     * working perfectly the whole time.
     * </p>
     * <p>
     * ⚠ IT IS ALSO WHY THE REPORTS LOOKED INCONSISTENT: a queen WITHOUT her sack - newly founded, or just roused off it
     * - accepts the block first try. The feature was only ever unreachable on a seated queen, which is exactly the
     * queen anyone would want to promote.
     * </p>
     * <p>
     * ⭐ Forwarding rather than duplicating: whatever the royal does with an item, the sack does too, now and for
     * anything added later. Nothing about the jelly block is named here.
     * </p>
     */
    @Override
    public @NotNull net.minecraft.world.InteractionResult mobInteract(
        @NotNull net.minecraft.world.entity.player.Player player,
        @NotNull net.minecraft.world.InteractionHand hand
    ) {
        // ⚠ interact(), NOT mobInteract() - the latter is PROTECTED, so it cannot be called on another entity. And
        // interact is the RIGHT door anyway: it is Mob's public, final entry point and runs the standard handling
        // (name tag, lead) before dispatching to mobInteract, so the royal is treated exactly as if the player had
        // clicked her directly.
        if (getVehicle() instanceof Mob royal) {
            return royal.interact(player, hand);
        }
        return super.mobInteract(player, hand);
    }

    /**
     * The eggsack burns only if its royal does.
     * <p>
     * There is ONE ovipositor type for every strain - the sack takes its strain from the queen it rides, which is why
     * the renderer resolves its texture off her too - so there is no nether entity type to tag and no variant of its
     * own to read. Deferring to the royal is the only correct answer, and it is also the most general one: whatever
     * makes her immune makes the organ growing out of her immune, now and for any strain added later.
     * <p>
     * Inherited by {@code EmpressOvipositor}, so the empress's sack is covered by the same rule.
     */
    @Override
    public boolean fireImmune() {
        return (getVehicle() instanceof com.alien.common.gameplay.entity.living.alien.Alien royal && royal.fireImmune())
            || super.fireImmune();
    }

    public static AttributeSupplier.Builder createOvipositorAttributes() {
        return createMobAttributes()
            .add(Attributes.MAX_HEALTH, 100)
            .add(Attributes.MOVEMENT_SPEED, 0);
    }

    /**
     * ⭐ The strain of the royal this sack grew out of, REMEMBERED rather than read live.
     * <p>
     * Refreshed every tick while it is being carried, so it is always current AND so a sack that predates this field
     * learns its strain the first time it is seen riding. Once the queen is knocked off, the last value stands - and
     * that is the whole point, because an abandoned sack lingers for two minutes as scenery with no vehicle to ask.
     * </p>
     */
    public final DataAccessor<Integer> royalVariantId;

    public Ovipositor(EntityType<? extends Ovipositor> entityType, Level level) {
        super(entityType, level);

        this.royalVariantId = new DataAccessor<>(this, AlienDataSyncKeys.OVIPOSITOR_ROYAL_VARIANT_ID.get());
    }

    /** The remembered strain, for the renderer. Never null - an unrecognised id reads as NORMAL. */
    public AlienVariant getRoyalVariant() {
        return AlienVariant.getById(royalVariantId.get()).unwrapOr(AlienVariant.NORMAL);
    }

    /**
     * How long an abandoned eggsack stays in the world before it rots away.
     * <p>
     * It used to vanish the instant the queen stood up, which read as the sack never having been real. Leaving it
     * behind gives the scene an aftermath: the thing she was tending is still lying there, and it is what tells a
     * player what happened here. It still yields nothing when it goes — this is scenery, not loot.
     * <p>
     * !! 120s -> 45s. [stated] Aug 24. THE DECIDING CASE IS THE EMPRESS MOLT, THE ONE TRANSFORMATION IN THE MOD WHERE
     * THE MOLTING CREATURE OWNS AN EGGSACK. A queen dismounts, molts, and grows a NEW sack as an empress - so at 120s
     * the dead husk was still lying in the clutch long after the new one appeared, two sacks overlapping in the throne
     * room. 45s is long enough to still read as aftermath and short enough to clear before, or shortly after, she
     * emerges.
     * <p>
     * ⚠ THIS IS BLANKET, NOT MOLT-ONLY. It also shortens the husk left by a queen who was KILLED or who UNLOADED, which
     * is the common case. That is deliberate - a per-cause linger would put knowledge of why she left inside a sack
     * that has no way to know it. The clock starts the tick it stops being carried, whatever the cause, and the log
     * line below reads this constant so it always reports the real number.
     */
    public static final int ABANDONED_LINGER_TICKS = 45 * 20;

    /**
     * How far past the hitbox the eggsack is allowed to draw before the game may cull it.
     * <p>
     * [stated] "when i rotate a certain amount the eggsack vanishes on the empress kinda the same for queens ... any
     * way we can have it not vanish until its actually out of view".
     * </p>
     * <p>
     * !!! CULLING USES THE HITBOX, NOT THE MODEL. Minecraft decides whether to draw an entity by testing
     * getBoundingBoxForCulling() against the view frustum, and that defaults to the COLLISION box - 6.5 x 3.7 for the
     * empress sack, 5.0 x 3.25 for the queen's. The rendered sack sprawls well past both, so turning until the small
     * box left the frustum made the whole model pop out while most of it was still on screen.
     * </p>
     * <p>
     * ⚠ GENEROUS ON PURPOSE. An over-large culling box costs only the occasional draw of something just off screen; an
     * under-sized one is a visible pop. There is no reason to tune this finely.
     * </p>
     */
    private static final double CULLING_INFLATION_BLOCKS = 6.0D;

    /**
     * A deliberately oversized box so the sack is only culled once it is genuinely out of view.
     * <p>
     * ⚠ CULLING ONLY. This does not change collision, pathing or hit detection - those all still use the real bounding
     * box.
     * </p>
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(CULLING_INFLATION_BLOCKS);
    }

    /** When it lost its royal, or {@link Long#MIN_VALUE} while it still has one. */
    private long abandonedAtGameTime = Long.MIN_VALUE;

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            return;
        }

        if (hasValidRoyalVehicle()) {
            // Still being carried, so any earlier abandonment is off - a rescued queen keeps the sack she had.
            abandonedAtGameTime = Long.MIN_VALUE;

            // Remember her strain WHILE we can still ask. This is the only window in which the answer exists.
            if (getVehicle() instanceof Alien royal) {
                var variantId = royal.getVariant().getId();

                if (royalVariantId.get() != variantId) {
                    royalVariantId.set(variantId);
                }
            }

            return;
        }

        if (abandonedAtGameTime == Long.MIN_VALUE) {
            // The moment it stops being carried, whatever the cause. Pairs with OvipositorManager.logEggsackRemoval:
            // if THIS line appears without one of those, something dismounted it outside the manager entirely.
            var vehicle = getVehicle();
            com.alien.Alien.LOGGER.info(
                "Eggsack lost its royal at {} - starting the {}s linger. (vehicle={})",
                blockPosition(),
                ABANDONED_LINGER_TICKS / 20,
                vehicle == null ? "none" : vehicle.getType().toString()
            );
            abandonedAtGameTime = level().getGameTime();
            return;
        }

        if (level().getGameTime() - abandonedAtGameTime >= ABANDONED_LINGER_TICKS) {
            discard();
        }
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float amount) {
        if (damageSource.is(AlienDamageTypesTags.DOES_NOT_HURT_ALIENS)) {
            return false;
        }

        return super.hurt(damageSource, amount);
    }

    /**
     * The eggsack takes exactly what its royal's strain takes. One entity type serves every strain, so no entity tag
     * can say "a nether queen's sac ignores cold" - the royal's variant is remembered here for the renderer already,
     * and it answers this too. [stated] Sep 22: "ovipositor and royal cocoon ... also have strains so the immunity
     * should match their strain."
     */
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource damageSource) {
        return com.alien.common.gameplay.entity.living.alien.StrainHazardImmunity.isImmune(getRoyalVariant(), damageSource)
            || super.isInvulnerableTo(damageSource);
    }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    protected boolean canRide(@NotNull Entity vehicle) {
        return super.canRide(vehicle) && vehicle.getType().is(AlienEntityTypeTags.QUEENS);
    }

    private boolean hasValidRoyalVehicle() {
        var vehicle = getVehicle();
        return vehicle != null
            && vehicle.isAlive()
            && !vehicle.isRemoved()
            && vehicle.getType().is(AlienEntityTypeTags.QUEENS);
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    protected final boolean canAddPassenger(@NotNull Entity passenger) {
        return false;
    }

    @Override
    protected void doPush(@NotNull Entity entity) {}

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public int getAirSupply() {
        return Integer.MAX_VALUE;
    }

    /**
     * True when this is a captive queen's chained eggsack (grown while inhibited + fully bound), false for a normal
     * founding ovipositor. Lets {@code OvipositorManager} tell the two apart so a captured queen drops her founding
     * eggsack and swaps to the chained one. Persisted so the distinction survives reload.
     */
    private boolean chainedEggsack = false;

    public boolean isChainedEggsack() {
        return chainedEggsack;
    }

    public void setChainedEggsack(boolean value) {
        this.chainedEggsack = value;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.putBoolean("ChainedEggsack", chainedEggsack);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        this.chainedEggsack = compoundTag.getBoolean("ChainedEggsack");
    }
}
