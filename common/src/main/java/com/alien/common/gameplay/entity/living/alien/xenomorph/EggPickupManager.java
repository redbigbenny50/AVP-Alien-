package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

public class EggPickupManager implements GameEventListener.Provider<EggPickupRequestListener> {

    private final DynamicGameEventListener<EggPickupRequestListener> dynamicEggPickupRequestListener;

    private final EggPickupRequestListener eggPickupRequestListener;

    private final Xenomorph xenomorph;

    private Ovomorph targetOvomorph;

    public EggPickupManager(Xenomorph xenomorph) {
        this.eggPickupRequestListener = new EggPickupRequestListener(xenomorph, this::acknowledgePickupRequest);
        this.dynamicEggPickupRequestListener = new DynamicGameEventListener<>(eggPickupRequestListener);
        this.xenomorph = xenomorph;
    }

    @Override
    public @NotNull EggPickupRequestListener getListener() {
        return eggPickupRequestListener;
    }

    public void updateDynamicGameEventListener(@NotNull BiConsumer<DynamicGameEventListener<?>, ServerLevel> biConsumer) {
        if (xenomorph.level() instanceof ServerLevel serverLevel) {
            biConsumer.accept(dynamicEggPickupRequestListener, serverLevel);
        }
    }

    public void tick() {
        if (xenomorph.level().isClientSide) {
            return;
        }

        // Drop all ovomorphs if they can't be held or if the xenomorph has an attack target.
        getPassengerOvomorphs()
            .stream()
            .filter(ovomorph -> xenomorph.getTarget() != null || !ovomorph.canBeHeld())
            .forEach(Entity::stopRiding);

        if (xenomorph.getTarget() != null || (targetOvomorph != null && !targetOvomorph.canBeHeld())) {
            // Set the target ovomorph to null.
            setTargetOvomorph(null);
        }

        // Release a claim the moment this worker takes on a host. A host hauler has no attack target (egg-duty
        // disarm clears it), so the check above never fired for it: the worker kept the egg reserved while it
        // walked the host home, and the egg - already marked acknowledged - had stopped broadcasting, so no other
        // worker could ever hear it. That stranded every host-bound egg permanently.
        if (targetOvomorph != null && isCarryingHost()) {
            setTargetOvomorph(null);
        }
    }

    private List<Ovomorph> getPassengerOvomorphs() {
        return xenomorph.getPassengers()
            .stream()
            .filter(passenger -> passenger.getType().is(AlienEntityTypeTags.OVOMORPHS))
            .map(entity -> (Ovomorph) entity)
            .toList();
    }

    public @Nullable Ovomorph getTargetOvomorphOrNull() {
        return targetOvomorph;
    }

    public void setTargetOvomorph(Ovomorph targetOvomorph) {
        if (targetOvomorph == null) {
            if (this.targetOvomorph != null) {
                this.targetOvomorph.pickupRequestAcknowledged = false;
            }
        } else {
            targetOvomorph.pickupRequestAcknowledged = true;
        }

        this.targetOvomorph = targetOvomorph;
    }

    /** True while this worker is hauling a captured host - it is busy and must not reserve an egg. */
    private boolean isCarryingHost() {
        return xenomorph.getPassengers()
            .stream()
            .anyMatch(passenger -> passenger.getType().is(AlienEntityTypeTags.HOSTS));
    }

    private void acknowledgePickupRequest(Ovomorph ovomorph) {
        if (
            // ⭐⭐ SOMEONE ELSE ALREADY CLAIMED THIS EGG. [stated] haulers "always got stuck on the egg and nothing
            // else on eachother" - the pile in his screenshot is several workers converging on ONE ovomorph.
            //
            // ⚠⚠ EVERY OTHER CHECK HERE ASKS ABOUT THE WORKER; NOTHING ASKED ABOUT THE EGG. A pickup request is a
            // game event broadcast to every listener in range, so two free workers hearing the same broadcast BOTH
            // passed (neither had a target yet) and both set the same ovomorph as theirs. One picks it up; the rest
            // stand over the spot holding a claim they can never fulfil, and the egg's own flag - already true -
            // silences it for anyone who might have helped.
            //
            // ⚠ SAFE AGAINST STALE CLAIMS because the egg already lapses its own reservation after
            // PICKUP_CLAIM_TIMEOUT_TICKS if no pickup follows, then starts broadcasting again. Without that valve
            // this line would strand an egg whose claimer died; with it, refusing here costs at most one timeout.
            ovomorph.pickupRequestAcknowledged
                // If this xenomorph already has a target ovomorph, then ignore this other requesting ovomorph.
                || targetOvomorph != null
                // If the xenomorph is already moving an ovomorph, don't acknowledge this other ovomorph's request.
                || !getPassengerOvomorphs().isEmpty()
                // A worker hauling a host is busy: claiming an egg here silences it for everyone else.
                || isCarryingHost()
                // If the xenomorph can't reach the ovomorph, don't try to pick the ovomorph up.
                || (xenomorph.getNavigation().createPath(ovomorph, 0) == null)
        ) {
            return;
        }

        ovomorph.pickupRequestAcknowledged = true;
        setTargetOvomorph(ovomorph);
    }
}
