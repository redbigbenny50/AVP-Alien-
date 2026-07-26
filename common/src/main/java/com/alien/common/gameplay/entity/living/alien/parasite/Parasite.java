package com.alien.common.gameplay.entity.living.alien.parasite;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.model.alien.FreeMob;
import com.alien.common.registry.init.AlienDataSyncKeys;
import com.alien.common.registry.tag.AlienEntityTypeTags;
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

    /**
     * Consent flag for {@link #stopRiding()}. Rideable host mobs buck unrecognized riders off — vanilla untamed horses
     * and llamas, Naturalist's giraffe/zebra/elephant (their taming aiStep calls
     * {@code getControllingPassenger().stopRiding()} on ANY living first passenger, every tick, until tamed). An
     * attached facehugger must not be dislodgeable that way, so {@link #stopRiding()} refuses while legitimately
     * attached unless the detach came through {@link #detach()} (or entity removal). All of the mod's own detach paths
     * (attachment falloff, implant completion, struggle win, invalid host, death) go through consent.
     */
    private boolean detachConsented;

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

    /** Client-side: consecutive ticks our vehicle has disagreed with the synced server truth. */
    private int clientAttachmentMismatchTicks;

    /**
     * Client-side: TRUE once this client has actually received a synced positive attachment id for this parasite. The
     * self-heal may only trust a "-1, detached" truth after this - BLib's sync is dirty-flag (change) based, so a
     * client that starts tracking an ALREADY-attached parasite may simply never have received the id and reads the
     * default -1. Ghost huggers can only afflict clients that WITNESSED the attach (they got the mount packet), and
     * every witness was tracking at attach time and so also got the id sync - witnesses can always heal, late arrivals
     * can never false-dismount a valid attachment.
     */
    private boolean clientSawSyncedAttachment;

    /** The client tolerates this much truth/vehicle disagreement (sync latency at attach) before self-healing. */
    private static final int CLIENT_ATTACHMENT_MISMATCH_GRACE_TICKS = 60;

    @Override
    public void tick() {
        super.tick();
        attachmentManager.tick();

        // CLIENT SELF-HEAL: the client's passenger list can go stale in both directions (a dismount the cling gate
        // refused, or a lost packet), leaving a ghost hugger glued to a host the server no longer rides - which
        // freezes a hugged player's input and decorates animals forever. The synced attachedHostId is the server's
        // own truth: when the client's vehicle disagrees with it for longer than the sync-latency grace, the client
        // dismounts itself through detach() (consented, so the cling gate cannot refuse it). Placement is untouched:
        // while genuinely attached, truth and vehicle agree and nothing here fires.
        if (level().isClientSide) {
            var clientVehicleId = getVehicle() != null ? getVehicle().getId() : -1;
            var truthfulHostId = attachmentManager.attachedHostId();
            if (truthfulHostId > -1) {
                clientSawSyncedAttachment = true;
            }
            // A -1 truth is only actionable for a WITNESS (see clientSawSyncedAttachment); a positive truth pointing
            // at a different entity is unambiguous on its own.
            var truthIsActionable = truthfulHostId > -1 || clientSawSyncedAttachment;
            if (clientVehicleId != -1 && clientVehicleId != truthfulHostId && truthIsActionable) {
                clientAttachmentMismatchTicks++;
                if (clientAttachmentMismatchTicks > CLIENT_ATTACHMENT_MISMATCH_GRACE_TICKS) {
                    clientAttachmentMismatchTicks = 0;
                    com.alien.Alien.LOGGER.info(
                        "Parasite client self-heal: dismounting ghost hugger {} (synced truth={}, client vehicle={})",
                        getId(),
                        truthfulHostId,
                        clientVehicleId
                    );
                    detach();
                }
            } else {
                clientAttachmentMismatchTicks = 0;
            }
        }

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

    /** The mod's own detach entry point: consents to the dismount, then performs it. */
    public void detach() {
        detachConsented = true;

        try {
            stopRiding();
        } finally {
            detachConsented = false;
        }
    }

    /**
     * True while this parasite is legitimately latched onto a non-alien host and must resist host-side ejection. Alien
     * vehicles (carrier spine rides) stay freely dismountable for hive logistics; dead/dying/removed states on either
     * side always allow the dismount so vanilla cleanup is never fought.
     */
    private boolean shouldClingToHost() {
        if (detachConsented || isDeadOrDying() || isRemoved()) {
            return false;
        }

        if (!(getVehicle() instanceof LivingEntity host)) {
            return false;
        }

        if (host.getType().is(AlienEntityTypeTags.ALIENS)) {
            return false;
        }

        return host.isAlive() && !host.isRemoved() && AlienPredicates.isHost(host);
    }

    @Override
    public void stopRiding() {
        if (shouldClingToHost()) {
            // A host-side buck (untamed horse/llama/giraffe taming logic and the like): refuse it. This runs on BOTH
            // sides on purpose - the client-side refusal is what keeps face placement alive against gameplay logic
            // leaking onto the client. Stale client passenger lists (a genuinely detached hugger the client still
            // wears) are healed by the synced-truth check in tick(), not by weakening this gate.
            return;
        }

        var host = attachmentManager.getHost();

        if (host instanceof Mob mob) {
            ((FreeMob) mob).restoreFreedom();
        }

        super.stopRiding();

        // Will update the player's riding entities properly after the parasite detaches.
        tryUpdatePlayerRiding(host);
    }

    @Override
    public void remove(@NotNull Entity.RemovalReason removalReason) {
        // Removal (discard, kill command, chunk cleanup) must always win over clinging, or the host would keep a
        // ghost passenger reference to an entity that no longer exists.
        detachConsented = true;

        try {
            super.remove(removalReason);
        } finally {
            detachConsented = false;
        }
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
