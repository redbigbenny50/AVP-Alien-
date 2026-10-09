package com.alien.common.gameplay.entity.living.alien.royal_cocoon;

import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonState;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.tag.AlienDamageTypesTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The resin "royal cocoon" a praetorian or crusher forms inside while metamorphosing into a queen. A placed, stationary
 * shell — it mirrors {@code Ovipositor} (non-pushable, no knockback, doesn't drown) but is <em>attackable</em>: the
 * metamorphosing creature plays its molt animation at this position, visually inside the cage.
 * <p>
 * Lifecycle (Stage B wiring): spawned when the metamorphosis begins and removed via {@code discard()} when she emerges;
 * if a player destroys it first it dies normally, which interrupts the transformation and drops resin. The strain
 * (regular / aberrant / nether) is encoded by the entity type, which also selects the texture in the renderer.
 */
public class RoyalCocoon extends Mob {

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
     * A nether cocoon does not burn.
     * <p>
     * {@code Alien.fireImmune()} grants this to every nether xenomorph, but a cocoon is a {@link Mob} rather than an
     * {@code Alien}, so it inherited nothing and a nether royal molting in her own biome cooked inside her shell.
     * Driven off the NETHER_ALIENS tag rather than a type comparison, so any nether type added later is covered by the
     * data alone.
     */
    @Override
    public boolean fireImmune() {
        // Nether and, since the Sep 22 ruling, irradiated - the same two strains Alien.fireImmune names.
        return getType().is(AlienEntityTypeTags.NETHER_ALIENS)
            || getType().is(AlienEntityTypeTags.IRRADIATED_ALIENS)
            || super.fireImmune();
    }

    /** A cocoon takes exactly what its strain takes - see StrainHazardImmunity. */
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource damageSource) {
        return com.alien.common.gameplay.entity.living.alien.StrainHazardImmunity.isImmune(getVariant(), damageSource)
            || super.isInvulnerableTo(damageSource);
    }

    /**
     * A deliberately oversized culling box, for the same reason the eggsack has one: culling tests the HITBOX, and a 4
     * x 6 box is smaller than the cage that draws around a molting royal, so turning could pop it out of view while
     * most of it was still on screen. Culling only - collision and hit detection are unaffected.
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.0D);
    }

    /** How often the orphan check runs. Rare, because the answer only changes when a molt ends. */
    private static final int ORPHAN_CHECK_INTERVAL_TICKS = 40;

    /**
     * How long an orphaned cage is tolerated before it is removed.
     * <p>
     * Generous on purpose: the handover between the source's cocoon manager and the destination's is not atomic, so a
     * cage is legitimately occupant-less for a moment during a normal molt. Removing one too eagerly would delete the
     * cage out from under a molt that is working.
     * </p>
     */
    private static final int ORPHAN_GRACE_TICKS = 20 * 10;

    private int orphanTicks;

    /**
     * !!! THE ONLY THING THAT CAN EVER REMOVE THIS ENTITY USED TO BE CocoonManager.discardRoyalCage().
     * <p>
     * RoyalCocoon had no tick at all - no lifetime, no orphan check, nothing. So the cage's entire existence depended
     * on one hand-off chain surviving: the source's manager holds its id, hands it to the destination's manager, and
     * that manager reaches EMERGING and clears. Break any link - a molt cancelled, an occupant killed inside, an unload
     * at the wrong moment, or an eggsack pushing her out early - and the cage stood in the world FOREVER, with nothing
     * in the game able to touch it.
     * </p>
     * <p>
     * ⭐ This is the systemic half of the fix. The eggsack gate stops the cause; this stops any FUTURE cause leaving
     * permanent litter, covers every caste rather than the empress alone, and clears cages already standing in worlds
     * that hit the bug.
     * </p>
     * <p>
     * ⚠ AN OCCUPANT WHO IS NO LONGER COCOONING COUNTS AS ORPHANED. A cage whose occupant walked out - which is exactly
     * the reported case - is just as abandoned as one whose occupant no longer exists.
     * </p>
     */
    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide || tickCount % ORPHAN_CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!isOrphaned(serverLevel)) {
            orphanTicks = 0;
            return;
        }

        orphanTicks += ORPHAN_CHECK_INTERVAL_TICKS;
        if (orphanTicks < ORPHAN_GRACE_TICKS) {
            return;
        }

        com.alien.Alien.LOGGER.info(
            "Royal cocoon at {} had no molting occupant for {}s - removing the abandoned cage.",
            blockPosition(),
            ORPHAN_GRACE_TICKS / 20
        );

        // ⚠ Clear the link FIRST. The cage kills its occupant when it DIES, and there is nobody in here to kill.
        setOccupantId(null);
        discard();
    }

    private boolean isOrphaned(ServerLevel serverLevel) {
        if (occupantId == null) {
            return true;
        }

        if (!(serverLevel.getEntity(occupantId) instanceof Xenomorph occupant) || !occupant.isAlive()) {
            return true;
        }

        return !occupant.getCocoonManager().shouldRunCocoonAction();
    }

    public static AttributeSupplier.Builder createRoyalCocoonAttributes() {
        return createMobAttributes()
            .add(Attributes.MAX_HEALTH, 60)
            .add(Attributes.MOVEMENT_SPEED, 0);
    }

    private static final String OCCUPANT_TAG = "RoyalCocoonOccupant";

    private static final String ADOLESCENT_TAG = "RoyalCocoonAdolescent";

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> ADOLESCENT_SIZED =
        net.minecraft.network.syncher.SynchedEntityData.defineId(
            RoyalCocoon.class,
            net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN
        );

    /**
     * ⭐ WHO IS ACTUALLY INSIDE. Written by {@code CocoonManager} when the cage is spawned, and RE-POINTED by it at the
     * source -> destination swap, because the emerging royal is a different entity with a different UUID.
     * <p>
     * This exists to replace a proximity search. The old code took every {@code Xenomorph} within one block that was
     * cocooning at all and discarded it, on the reasoning that a stored link could not survive the swap. It can - the
     * swap happens in one place and both entities are in hand there - and the proximity version was a trap: anything
     * else molting nearby was executed as though it were the occupant. Harmless while only praetorians and crushers
     * could cocoon; not harmless now that adolescents molt too.
     * </p>
     */
    private @Nullable UUID occupantId;

    public RoyalCocoon(EntityType<? extends RoyalCocoon> entityType, Level level) {
        super(entityType, level);
    }

    public void setOccupantId(@Nullable UUID occupantId) {
        this.occupantId = occupantId;
    }

    /**
     * ⭐⭐ A COCOON GROWN AROUND AN ADOLESCENT IS SMALLER THAN ONE GROWN AROUND AN ADULT.
     * <p>
     * [stated] "the royal cocoon that spawns when an pred adol or royal adol is changing into a royal needs to be
     * smaller ... 60% but only for that scenario when the adults transform into queens it can stay the current size."
     * </p>
     * <p>
     * ⚠ SET FROM THE OCCUPANT AT SPAWN, not from the cocoon's own type. Every royal cocoon of a given strain is the
     * same entity type whichever creature climbed into it, so the type cannot tell them apart - only the occupant can,
     * and only at the moment it is created.
     * </p>
     * <p>
     * ⚠ SYNCED, because this is what the renderer scales by. A server-only field would leave every client drawing the
     * full-size cocoon and the shrink would be invisible to the person looking at it.
     * </p>
     */
    public boolean isAdolescentSized() {
        return entityData.get(ADOLESCENT_SIZED);
    }

    public void setAdolescentSized(boolean value) {
        entityData.set(ADOLESCENT_SIZED, value);
    }

    /** [stated] "60%". Applied to both the drawn model and the hitbox, so it cannot be a box you bump into. */
    public static final float ADOLESCENT_SCALE = 0.6F;

    public @Nullable UUID getOccupantId() {
        return occupantId;
    }

    /**
     * ⚠⚠ WITHOUT THIS THE ENTITY CRASHES ON SPAWN. A synced field must be declared here before anything reads or writes
     * it - the accessor alone is not enough, and the failure is at spawn time rather than at compile time.
     */
    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ADOLESCENT_SIZED, false);
    }

    /**
     * ⭐⭐ THE HITBOX SHRINKS WITH THE MODEL.
     * <p>
     * ⚠ A 60% cocoon you still collide with at full size is worse than not shrinking it at all: the player is blocked
     * by, and can attack, empty air around something visibly smaller. Scaling both keeps what you see and what you
     * touch the same object.
     * </p>
     */
    @Override
    public net.minecraft.world.entity.@NotNull EntityDimensions getDefaultDimensions(
        net.minecraft.world.entity.@NotNull Pose pose
    ) {
        var dimensions = super.getDefaultDimensions(pose);
        return isAdolescentSized() ? dimensions.scale(ADOLESCENT_SCALE) : dimensions;
    }

    @Override

    public void readAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);

        entityData.set(ADOLESCENT_SIZED, compoundTag.getBoolean(ADOLESCENT_TAG));

        if (compoundTag.hasUUID(OCCUPANT_TAG)) {
            this.occupantId = compoundTag.getUUID(OCCUPANT_TAG);
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);

        if (occupantId != null) {
            compoundTag.putUUID(OCCUPANT_TAG, occupantId);
        }

        compoundTag.putBoolean(ADOLESCENT_TAG, isAdolescentSized());
    }

    /**
     * The strain this cocoon belongs to, derived from its entity type (see class doc). Drives strain-aware targeting: a
     * xenomorph ignores its own strain's forming royal but a rival strain attacks it.
     */
    public AlienVariant getVariant() {
        var type = getType();
        if (type == AlienEntityTypes.ABERRANT_ROYAL_COCOON.get()) {
            return AlienVariant.ABERRANT;
        }
        if (type == AlienEntityTypes.NETHER_ROYAL_COCOON.get()) {
            return AlienVariant.NETHER;
        }
        // ⚠ WITHOUT THIS AN IRRADIATED COCOON READS AS NORMAL, and strain-aware targeting would have the irradiated
        // hive attacking its OWN forming royal while normal xenos ignored it - exactly backwards.
        if (type == AlienEntityTypes.IRRADIATED_ROYAL_COCOON.get()) {
            return AlienVariant.IRRADIATED;
        }
        return AlienVariant.NORMAL;
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float amount) {
        if (damageSource.is(AlienDamageTypesTags.DOES_NOT_HURT_ALIENS)) {
            return false;
        }
        return super.hurt(damageSource, amount);
    }

    @Override
    public void knockback(double strength, double x, double z) {}

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

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void die(@NotNull DamageSource damageSource) {
        // Destroyed before she emerged: interrupt the transformation (the vulnerable forming queen is killed) and
        // scatter resin from the broken cage. A successful emergence removes the cocoon via discard(), which does not
        // route through die(), so neither of these fire on a normal birth.
        interruptMetamorphosis();
        dropResin();
        super.die(damageSource);
    }

    private void interruptMetamorphosis() {
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        // Kill whoever is currently cocooning inside this cage - BY IDENTITY. The occupant is recorded when the cage
        // is spawned and re-pointed across the source -> destination swap, so exactly one entity can ever be hit,
        // and only if it really is mid-molt.
        if (occupantId == null || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (
            serverLevel.getEntity(occupantId) instanceof Xenomorph occupant
                && occupant.cocoonState.get() != CocoonState.NONE
        ) {
            occupant.discard();
        }
    }

    private void dropResin() {
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        spawnAtLocation(AlienResinBlocks.RESIN.get(), 3);
    }
}
