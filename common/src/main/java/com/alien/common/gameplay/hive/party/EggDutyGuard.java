package com.alien.common.gameplay.hive.party;

import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.world.entity.Entity;

/**
 * Guard for xenomorphs on egg/host duty.
 * <p>
 * Party resolution refunds its members and calls {@code entity.discard()} on them - a hard removal with no check for
 * what the member is doing. A worker carrying an ovomorph that gets discarded mid-haul VANISHES and drops its egg,
 * which testers saw as carriers disappearing whenever a party resolved nearby.
 * <p>
 * Rule: a xenomorph carrying an egg is never drafted into a party and never discarded by one. It is left alive to
 * finish the delivery (its reserve slot is still refunded - a stray worker in the loaded pool is harmless, an
 * interrupted egg run is not).
 */
public final class EggDutyGuard {

    private EggDutyGuard() {}

    /** True if this entity is carrying an ovomorph OR a captured host - either way, do not disturb it. */
    public static boolean isOnEggDuty(Entity entity) {
        for (var passenger : entity.getPassengers()) {
            if (passenger.getType().is(AlienEntityTypeTags.OVOMORPHS)) {
                return true;
            }
            // A captured host being carried to the chamber: discarding its captor would strand the capture.
            if (passenger.getType().is(AlienEntityTypeTags.HOSTS)) {
                return true;
            }
        }
        return false;
    }
}
