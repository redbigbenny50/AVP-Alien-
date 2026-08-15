package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.founding_move;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import org.jetbrains.annotations.Nullable;

/**
 * Sensors for the founding navigate-to-center behaviour (Option B).
 * <p>
 * A founding queen (location founded, ovipositor not yet created) should actively path to and hold near her hive
 * {@link HiveLocation#centerPos()} while her biomass tank fills, instead of relying solely on the snap-to-center
 * teleport that the commit step performs ({@code OvipositorManager#prepareFoundingChamberIfNeeded}). That teleport is
 * left in place as a backstop; this behaviour just makes the queen <em>already be there</em> when the commit fires, so
 * the snap becomes a no-op.
 * <p>
 * Two signals drive it:
 * <ul>
 * <li>{@link #IS_FOUNDING} — true while this entity is the founder of a not-yet-reproductive location. This is the
 * single on/off switch for the whole package: when it is false the goal and action can never be selected, and the queen
 * falls back to her ordinary idle behaviour plus the snap backstop.</li>
 * <li>{@link #IS_AT_CENTER} — true while she is within a tight radius of her center. Kept small so the eventual snap
 * correction is negligible (navigation effectively proven) while still landing comfortably inside the commit's
 * 3x3-chunk {@code isNearHiveCenter} gate.</li>
 * </ul>
 * <p>
 * <b>Position-independent on purpose.</b> Unlike the combat leash (which resolves the location from the queen's current
 * chunk via {@code getByChunk}), the founding location here is resolved by <em>founder UUID</em> over the whole
 * registry. That is deliberate: if the queen is knocked back or falls out of her claimed chunks, a chunk-based lookup
 * would return null and she could never path home. Scanning by founder keeps the return-to-center backstop working from
 * anywhere. The scan is over the handful of live locations and only runs on GOAP replans, so it is cheap.
 */
public final class FoundingMoveSensors {

    /**
     * Master toggle for the navigate-to-center behaviour.
     * <p>
     * This is the single, intentional disable point. Flip it to {@code false} (or, later, replace the body of
     * {@link #isEnabled()} with a config/condition lookup such as
     * {@code HiveLocationRegistry.INSTANCE.config().foundingNavigateToCenter()}) to disable the behaviour entirely.
     * When disabled, {@link #foundingLocationOrNull} always returns null, so the goal and action drop out of GOAP
     * contention and nothing else changes: the queen keeps her existing idle behaviour and the snap-to-center commit
     * backstop still fires. No other code needs to be touched to turn this off.
     */
    private static final boolean ENABLED = true;

    /** Horizontal distance (blocks) from {@code centerPos} that counts as "at center". Small on purpose; see above. */
    private static final double AT_CENTER_RADIUS = 2.5;

    private static final double AT_CENTER_RADIUS_SQ = AT_CENTER_RADIUS * AT_CENTER_RADIUS;

    public static final Sensor.Mono<Xenomorph, Boolean> IS_FOUNDING = Sensors.map(
        StateKey.sensed("founding_move_is_founding"),
        xenomorph -> foundingLocationOrNull(xenomorph) != null
    );

    public static final Sensor.Mono<Xenomorph, Boolean> IS_AT_CENTER = Sensors.map(
        StateKey.sensed("founding_move_at_center"),
        FoundingMoveSensors::isAtFoundingCenter
    );

    /** Whether the navigate-to-center behaviour is active at all. Single hook for a future config/condition gate. */
    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * The location this entity is currently founding (it is the founder and the location is not yet reproductive), or
     * null. Resolved by founder UUID across the registry so it works even when the queen has been displaced outside her
     * claimed chunks. Returns null when the behaviour is disabled, mirroring "not founding".
     */
    public static @Nullable HiveLocation foundingLocationOrNull(Xenomorph xenomorph) {
        if (!isEnabled()) {
            return null;
        }

        // A bound OR inhibited queen does not found — her front-end is frozen while she is chained or carries an
        // inhibitor. This is what keeps a captured queen whose chains have been broken from re-founding: she stays
        // inhibited and waits to be freed (inhibitor pried off) instead of relocating.
        if (xenomorph instanceof Queen queen && (queen.getBindManager().hasAnyChain() || queen.isInhibited())) {
            return null;
        }

        var dimension = xenomorph.level().dimension();
        var founderId = xenomorph.getUUID();

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (
                location.isAlive()
                    && founderId.equals(location.founderId())
                    && !location.reproductiveEstablished()
                    && location.dimension().equals(dimension)
            ) {
                return location;
            }
        }
        return null;
    }

    private static boolean isAtFoundingCenter(Xenomorph xenomorph) {
        var location = foundingLocationOrNull(xenomorph);
        if (location == null) {
            return false;
        }

        var center = location.centerPos();
        var dx = xenomorph.getX() - (center.getX() + 0.5);
        var dz = xenomorph.getZ() - (center.getZ() + 0.5);
        return (dx * dx + dz * dz) <= AT_CENTER_RADIUS_SQ;
    }

    private FoundingMoveSensors() {
        throw new UnsupportedOperationException();
    }
}
