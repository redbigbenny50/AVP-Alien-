package com.alien.common.gameplay.entity.living.alien.xenomorph.queen.ai.location_move;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhase;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Sensors for the location-phase dig-to-anchor behaviour (Stage 2b). Mirrors {@code FoundingMoveSensors}, but operates
 * <em>before</em> founding: where founding-move resolves a live {@code HiveLocation} by founder UUID, this resolves the
 * queen's committed anchor straight off her {@link QueenLifecyclePhaseManager}, since no location exists yet.
 * <ul>
 * <li>{@link #IS_LOCATING} — true while she is a never-founded queen in {@link QueenLifecyclePhase#LOCATION} with a
 * committed anchor. The single on/off switch for the package: false ⇒ goal/action can never be selected. Also false
 * when the whole front-end is disabled (the manager never reaches LOCATION), so this package drops out with it.</li>
 * <li>{@link #IS_AT_ANCHOR} — true once she is within a small radius of the committed anchor (3D, since the anchor has
 * a specific target Y she dug down to).</li>
 * </ul>
 */
public final class LocationMoveSensors {

    /** 3D arrival radius (blocks) around the committed anchor. */
    private static final double AT_ANCHOR_RADIUS = 2.0;

    private static final double AT_ANCHOR_RADIUS_SQ = AT_ANCHOR_RADIUS * AT_ANCHOR_RADIUS;

    public static final Sensor.Mono<Xenomorph, Boolean> IS_LOCATING = Sensors.map(
        StateKey.sensed("location_move_is_locating"),
        xenomorph -> locationAnchorOrNull(xenomorph) != null
    );

    public static final Sensor.Mono<Xenomorph, Boolean> IS_AT_ANCHOR = Sensors.map(
        StateKey.sensed("location_move_at_anchor"),
        LocationMoveSensors::isAtAnchor
    );

    /**
     * The committed location-phase anchor for this entity, or null if it isn't a queen actively in the LOCATION phase
     * (or the front-end is disabled). Resolved off the queen's phase manager — there is no {@code HiveLocation} yet.
     */
    public static @Nullable BlockPos locationAnchorOrNull(Xenomorph xenomorph) {
        if (!QueenLifecyclePhaseManager.isEnabled() || !(xenomorph instanceof Queen queen)) {
            return null;
        }

        // A bound OR inhibited queen does not locate or dig — her front-end is frozen while she is chained or carries
        // an inhibitor, so a captured queen whose chains are broken waits (to be freed) instead of re-locating.
        if (queen.getBindManager().hasAnyChain() || queen.isInhibited()) {
            return null;
        }

        var manager = queen.getLifecyclePhaseManager();
        if (manager.getPhase() != QueenLifecyclePhase.LOCATION) {
            return null;
        }
        return manager.getAnchor();
    }

    private static boolean isAtAnchor(Xenomorph xenomorph) {
        var anchor = locationAnchorOrNull(xenomorph);
        if (anchor == null) {
            return false;
        }

        var dx = xenomorph.getX() - (anchor.getX() + 0.5);
        var dy = xenomorph.getY() - anchor.getY();
        var dz = xenomorph.getZ() - (anchor.getZ() + 0.5);
        return (dx * dx + dy * dy + dz * dz) <= AT_ANCHOR_RADIUS_SQ;
    }

    private LocationMoveSensors() {
        throw new UnsupportedOperationException();
    }
}
