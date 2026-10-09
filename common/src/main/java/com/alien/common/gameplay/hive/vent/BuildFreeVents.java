package com.alien.common.gameplay.hive.vent;

import com.alien.common.gameplay.hive.config.BuildFreeMode;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * BUILD-FREE VENT SPACING - the rule a structureless hive uses instead of template ducts.
 * <p>
 * 🚨 WHY (Oct 1 audit). Build-free has no template vents, so drones make them all. A drone vent inside the claim and
 * band is classed STRUCTURE, but the only cap ({@code VentSensors.CAN_CREATE_VENT}) counts FRONTIER vents - so in this
 * mode there was no cap at all, and every drone placed a vent on every cooldown. [stated] the fear was the old mod's
 * "30 vents in a chunk all over the floor and walls".
 * </p>
 * <p>
 * THE RULE, [stated]: "max 3 vents IN a chunk, and a 1-chunk gap between CHUNKS that contain vents" - applied per
 * STOREY of the band ({@code buildFreeVentVerticalGap} blocks tall), so a hive with several floors gets doors on each,
 * plus a hard per-hive ceiling. All four numbers are config fields.
 * </p>
 * <p>
 * ⚠ Only ever REFUSES a placement. It never removes or reclassifies a vent that already exists, so turning the mode on
 * over an existing hive changes nothing that is already in the world.
 * </p>
 */
public final class BuildFreeVents {

    private BuildFreeVents() {}

    /** True when the mode is on for this hive (End-style hives keep their own rules). */
    public static boolean applies(HiveLocation location) {
        return BuildFreeMode.isEnabled() && !location.isEndStyleHive();
    }

    /** The hive-wide ceiling - cheap enough to check every sensor tick. */
    public static boolean underHiveCap(HiveLocation location) {
        var cap = Math.max(0, HiveLocationRegistry.INSTANCE.config().buildFreeVentMaxPerHive());
        return location.ventManager().ventCount() < cap;
    }

    /** Whether a new vent at {@code pos} fits the per-storey chunk limit, the chunk gap and the hive ceiling. */
    public static boolean allows(HiveLocation location, BlockPos pos) {
        if (!underHiveCap(location)) {
            return false;
        }
        var config = HiveLocationRegistry.INSTANCE.config();
        var perChunk = Math.max(0, config.buildFreeVentsPerChunkPerTier());
        var gap = Math.max(0, config.buildFreeVentChunkGap());
        var tier = tierOf(location, pos.getY());
        var chunk = new ChunkPos(pos);

        var sameChunkSameTier = 0;
        for (var vent : location.ventManager().allVents()) {
            if (tierOf(location, vent.getY()) != tier) {
                continue;
            }
            var ventChunk = new ChunkPos(vent);
            if (ventChunk.equals(chunk)) {
                if (++sameChunkSameTier >= perChunk) {
                    return false;
                }
                continue;
            }
            var distance = Math.max(Math.abs(ventChunk.x - chunk.x), Math.abs(ventChunk.z - chunk.z));
            if (distance <= gap) {
                return false; // a neighbouring chunk on this storey already has the doors
            }
        }
        return perChunk > 0;
    }

    private static int tierOf(HiveLocation location, int y) {
        var height = Math.max(1, HiveLocationRegistry.INSTANCE.config().buildFreeVentVerticalGap());
        return Math.floorDiv(y - location.hiveFloorY(), height);
    }
}
