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
     */
    public static final int ABANDONED_LINGER_TICKS = 120 * 20;

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
