package com.alien.common.gameplay.entity.living.alien.parasite;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.FreeMob;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.util.AlienPredicates;
import com.blib.api.common.data_sync.v1.DataAccessor;
import com.blib.api.common.entity.v1.BLibEntityPredicates;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public abstract class Parasite extends Alien {

    public final DataAccessor<Boolean> isFertile;

    protected final ParasiteAttachmentManager attachmentManager;

    protected Parasite(EntityType<? extends Parasite> entityType, Level level) {
        super(entityType, level);

        this.isFertile = new DataAccessor<>(this, AlienDataSyncKeys.PARASITE_IS_FERTILE.get());

        this.attachmentManager = new ParasiteAttachmentManager(this);

        isFertile.onChange(this::handleFertilityChange);
        isFertile.onLoad(this::handleFertilityChange);
    }

    public void restoreAllGoals() {
        removeAllGoals(BLibEntityPredicates.alwaysTrue());
        registerGoals();
    }

    /** Point-blank pounce range. Deliberately short: this is a last step, not a substitute for hunting. */
    private static final double POUNCE_RANGE = 2.0;

    /** The pounce scan is cheap, but there can be a lot of huggers. Every quarter second is plenty. */
    private static final int POUNCE_INTERVAL_TICKS = 5;

    @Override
    public void tick() {
        super.tick();
        attachmentManager.tick();

        if (!level().isClientSide) {
            var currentTarget = getTarget();

            if (currentTarget != null && !isValidHost(currentTarget)) {
                setTarget(null);
            }

            if (tickCount % POUNCE_INTERVAL_TICKS == 0) {
                tryPounce();
            }
        }
    }

    /**
     * Attach to a host that is RIGHT THERE.
     * <p>
     * A facehugger had no way to take a host at point-blank range. Its whole repertoire was:
     * <ul>
     * <li>{@code LUNGE_AT_HOST} - which requires the target to be at least
     * {@code AttachToHostSensors.MIN_LUNGE_RANGE_IN_BLOCKS} (10!) blocks away, so it cannot fire up close;</li>
     * <li>{@code MOVE_TO_HOST} - which paths INTO the host's own position, and</li>
     * <li>{@link #doPush} / {@link #doHurtTarget} - collision, which only happens if it actually moves.</li>
     * </ul>
     * A host webbed to a chamber wall is NOT a pathable position, so {@code MOVE_TO_HOST} returned NO_PATH and aborted,
     * the hugger never moved, never collided, and simply sat next to its victim until it despawned. Shoving it by hand
     * made it collide and attach instantly - which is the whole tell.
     * <p>
     * Line of sight is required, so it cannot reach a host through a wall.
     */
    private void tryPounce() {
        if (!isFertile.get() || isPassenger() || isVehicle()) {
            return;
        }

        LivingEntity best = null;
        var bestDistance = Double.MAX_VALUE;

        for (var candidate : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(POUNCE_RANGE))) {
            if (!canAttachToHost(candidate) || !getSensing().hasLineOfSight(candidate)) {
                continue;
            }
            var distance = distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }

        if (best != null) {
            startRiding(best, true);
        }
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity entity) {
        if (canAttachToHost(entity)) {
            startRiding(entity, true);
        }

        return true;
    }

    public boolean isValidHost(LivingEntity target) {
        return isFertile.get() && AlienPredicates.isFreeHost(this, target);
    }

    @Override
    public boolean startRiding(@NotNull Entity entity, boolean bl) {
        var isRiding = super.startRiding(entity, bl);

        if (isRiding) {
            // Will update the player's riding entities properly after the parasite detaches.
            tryUpdatePlayerRiding(entity);
        }

        return isRiding;
    }

    @Override
    public void stopRiding() {
        var host = attachmentManager.getHost();

        if (host instanceof Mob mob) {
            ((FreeMob) mob).restoreFreedom();
        }

        super.stopRiding();

        // Will update the player's riding entities properly after the parasite detaches.
        tryUpdatePlayerRiding(host);
    }

    @Override
    public boolean isPushable() {
        return isFertile.get();
    }

    @Override
    protected void doPush(@NotNull Entity entity) {
        super.doPush(entity);

        if (canAttachToHost(entity)) {
            startRiding(entity);
        }
    }

    @Override
    protected boolean canHeal() {
        return isFertile.get() && super.canHeal();
    }

    @Override
    protected boolean canBleedAcid() {
        return isFertile.get() || attachmentManager.isAttachedToHost();
    }

    protected boolean canAttachToHost(Entity entity) {
        return entity instanceof LivingEntity livingEntity &&
                isValidHost(livingEntity) &&
                !BLibEntityPredicates.hasShield(entity) && !(this.isPassenger() || this.isVehicle());
    }

    private void tryUpdatePlayerRiding(Entity entity) {
        if (!level().isClientSide && entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetPassengersPacket(entity));
        }
    }

    public ParasiteAttachmentManager getAttachmentManager() {
        return attachmentManager;
    }

    private void handleFertilityChange(Boolean isFertile) {
        if (!isFertile) {
            ((FreeMob) this).removeFreedom();
            this.removeAllGoals(BLibEntityPredicates.alwaysTrue());
        } else {
            ((FreeMob) this).restoreFreedom();
            this.restoreAllGoals();
        }
    }
}