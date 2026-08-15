package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.anchor_break;

import com.alien.common.gameplay.block.entity.capture.anchor.AnchorIndex;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * GOAP state for anchor demolition: is there a capture anchor in our own hive worth tearing down.
 * <p>
 * Only the royal defenders get this package (praetorians and crushers). The queen never does - she is the thing the
 * anchors are used against and cannot free herself, which is the whole point of a capture setup.
 */
public final class AnchorBreakSensors {

    private AnchorBreakSensors() {}

    /** How far a defender will go out of its way for an anchor. */
    public static final double ANCHOR_SEARCH_RADIUS = 32.0;

    public static final Sensor.Mono<Xenomorph, Boolean> HAS_TARGET_ANCHOR = Sensors.map(
        StateKey.sensed("has_target_anchor"),
        xenomorph -> findAnchor(xenomorph) != null
    );

    /**
     * The nearest loaded anchor inside this defender's own hive claim, or null.
     * <p>
     * Restricting to the claim is what keeps defenders home: an anchor a player set up out in the world is none of the
     * hive's business, but one planted inside its territory is a threat to the queen and gets torn out.
     */
    @Nullable
    public static BlockPos findAnchor(Xenomorph xenomorph) {
        var level = xenomorph.level();
        if (level.isClientSide) {
            return null;
        }
        var dimension = level.dimension();
        var location = HiveLocationRegistry.INSTANCE.getByChunk(dimension, xenomorph.chunkPosition());
        if (location == null) {
            return null;
        }
        var claimed = location.claimedChunks();
        if (claimed.isEmpty()) {
            return null;
        }
        return AnchorIndex.nearest(
            level,
            xenomorph.blockPosition(),
            ANCHOR_SEARCH_RADIUS,
            pos -> claimed.contains(new ChunkPos(pos))
        );
    }
}
