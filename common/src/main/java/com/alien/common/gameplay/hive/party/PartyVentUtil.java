package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.vent.VentKind;
import com.alien.common.registry.init.block.AlienResinBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which vents a party may use as a door.
 * <p>
 * This used to ask the heightmap: a vent counted as "near-surface" if its Y was within a band of the terrain height of
 * ITS OWN column. That is not the same question as "can a party actually get in and out here" - a vent buried under a
 * hill is near the surface of the hill, and a carrier standing on open ground at y=3 would set off to walk to it at
 * y=-4, fail to path, and stand there holding a live host forever.
 * <p>
 * A vent's role is now recorded when it is placed ({@link VentKind}), so this just asks.
 * <ul>
 * <li><b>Host hunts</b> use SURFACE vents only, both to launch from and to carry a captive home to. The front
 * door.</li>
 * <li><b>Biomass hunts and attack parties</b> use SURFACE or FRONTIER - anywhere that opens onto the world.</li>
 * <li><b>Nothing</b> launches from a STRUCTURE vent: those are the hive's internal ducts and lead nowhere outside.</li>
 * </ul>
 */
public final class PartyVentUtil {

    private PartyVentUtil() {}

    /**
     * Where a party emerging from {@code vent} should actually appear.
     * <p>
     * A vent is a BEACON for its chunk, not a doorway. Members used to be placed at the vent block itself, but vents
     * are embedded in terrain - often several blocks underground - so parties MATERIALISED INSIDE SOLID GROUND, could
     * not path anywhere, and stood at the vent for their whole duration with valid quarry in plain sight. Surface
     * anywhere standable in the vent's chunk instead, preferring spots near the vent, so it no longer matters whether
     * the vent itself is buried.
     * <p>
     * Returns null when the chunk has nowhere dry to stand (an ocean vent). Callers must NOT fall back to the vent
     * block - that is the burial bug again.
     */
    public static @Nullable BlockPos surfaceEmergeSpot(ServerLevel level, BlockPos vent) {
        var chunk = new ChunkPos(vent);
        var minX = chunk.getMinBlockX();
        var minZ = chunk.getMinBlockZ();

        var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(level);
        var candidates = new ArrayList<BlockPos>();
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                // Ceiled dimensions: emerge on the vent's own cavern shelf, not the bedrock roof the heightmap reports.
                var y = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.surfaceY(level, profile, x, z, vent.getY());
                if (y == com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.NO_SURFACE) {
                    continue;
                }
                var pos = new BlockPos(x, y, z);
                if (isStandable(level, pos)) {
                    candidates.add(pos);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(vent)));
        return candidates.get(level.random.nextInt(Math.min(candidates.size(), 8)));
    }

    /** Open headroom on solid, DRY ground - somewhere an alien can actually stand. */
    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        var ground = pos.below();
        var groundState = level.getBlockState(ground);
        if (!groundState.getFluidState().isEmpty()) {
            return false; // never stand a party on water
        }
        if (!groundState.isFaceSturdy(level, ground, Direction.UP)) {
            return false;
        }
        return isFree(level, pos) && isFree(level, pos.above());
    }

    /**
     * Air, or the hive's own resin growth. NOT water: {@code canBeReplaced()} is true for fluids, so accepting it would
     * let a party surface straight into an ocean column.
     */
    private static boolean isFree(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir()
            || state.canBeReplaced()
            || state.is(AlienResinBlocks.RESIN_VEIN.get())
            || state.is(AlienResinBlocks.RESIN_WEB.get());
    }

    /** The hive's front doors: vents dropped in the open by surface parties. Host hunts use only these. */
    public static List<BlockPos> findSurfaceVents(ServerLevel level, HiveLocation location) {
        return new ArrayList<>(location.ventManager().ventsOfKind(VentKind.SURFACE));
    }

    /** Every vent that opens onto the outside world - the surface doors plus the cave-mouth outposts. */
    public static List<BlockPos> findPartyVents(ServerLevel level, HiveLocation location) {
        return new ArrayList<>(location.ventManager().ventsOfKind(VentKind.SURFACE, VentKind.FRONTIER));
    }

    /** Vents a defender may safely emerge from. Never FRONTIER - it would strand them out in a cave. */
    public static List<BlockPos> findDefenderVents(ServerLevel level, HiveLocation location) {
        return new ArrayList<>(location.ventManager().ventsOfKind(VentKind.STRUCTURE, VentKind.SURFACE));
    }
}
