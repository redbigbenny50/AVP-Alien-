package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared "is this vent near the surface" logic for the party system — used by {@code SurfacePartyLifecycleTask}'s
 * vent-drop cap and {@link BiomassHuntingPartyDispatch}'s vent-spawn-point lookup, so both use the same definition of
 * "surface" rather than drifting apart.
 */
public final class PartyVentUtil {

    private PartyVentUtil() {}

    public static boolean isNearSurface(ServerLevel level, BlockPos ventPos, int bandBlocks) {
        var surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ventPos.getX(), ventPos.getZ());
        return Math.abs(ventPos.getY() - surfaceY) <= bandBlocks;
    }

    /** All of {@code location}'s known vents that count as near-surface, per {@link #isNearSurface}. */
    public static List<BlockPos> findSurfaceVents(ServerLevel level, HiveLocation location, int bandBlocks) {
        var result = new ArrayList<BlockPos>();
        for (var pos : location.ventManager().allVents()) {
            if (isNearSurface(level, pos, bandBlocks)) {
                result.add(pos);
            }
        }
        return result;
    }
}
