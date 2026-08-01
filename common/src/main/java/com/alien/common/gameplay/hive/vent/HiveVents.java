package com.alien.common.gameplay.hive.vent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Vent travel for hive AI: the resin vents (tracked per-location by {@link HiveVentManager}) form the hive's duct
 * network. An alien near one vent can duct to another - used by the egg haulers to shortcut long deliveries, and
 * generic enough for any future vent traversal. Travel is a hop: reach the entry vent, squelch, emerge at a standable
 * spot beside the exit vent, squelch. Riders (a carried egg) come along.
 */
public final class HiveVents {

    private HiveVents() {}

    /** The nearest known vent to {@code from} within {@code radiusChunks} chunk columns, or null. */
    @Nullable
    public static BlockPos nearestVent(HiveVentManager vents, BlockPos from, int radiusChunks) {
        return nearestVent(vents, from, radiusChunks, null);
    }

    /** As {@link #nearestVent(HiveVentManager, BlockPos, int)}, considering only vents passing {@code filter}. */
    @Nullable
    public static BlockPos nearestVent(HiveVentManager vents, BlockPos from, int radiusChunks, @Nullable Predicate<BlockPos> filter) {
        var found = ventsNear(vents, from, radiusChunks, filter);
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * All known vents within {@code radiusChunks} chunk columns of {@code from}, nearest first, optionally filtered.
     */
    public static List<BlockPos> ventsNear(HiveVentManager vents, BlockPos from, int radiusChunks, @Nullable Predicate<BlockPos> filter) {
        var center = new ChunkPos(from);
        var found = new ArrayList<BlockPos>();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                for (var vent : vents.getVentsWithinChunk(new ChunkPos(center.x + dx, center.z + dz))) {
                    if (filter == null || filter.test(vent)) {
                        found.add(vent);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(v -> v.distSqr(from)));
        return found;
    }

    /** Whether this vent sits in the surface band (at/above terrain height minus {@code band}) - a surface vent. */
    public static boolean isNearSurface(Level level, BlockPos vent, int band) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, vent.getX(), vent.getZ());
        return vent.getY() >= surfaceY - band;
    }

    /**
     * Work out what an untagged vent is for, ONCE. Two kinds of vent reach us with no recorded kind:
     * <ul>
     * <li><b>Template-stamped vents.</b> The hive structures write plain blocks with no NBT, so a structure vent can
     * never carry a flag from placement - it has to be recognised by where it sits.</li>
     * <li><b>Vents from before kinds existed.</b> The one-time migration: an old world's vents get classified on first
     * load by the rule that used to be applied on the fly, and the answer is then persisted.</li>
     * </ul>
     * Inside a built structure chunk and within the slab, it is part of the hive proper. Otherwise, if it is up at the
     * surface it is a front door; anything else is an outpost in the rock.
     */
    public static VentKind classifyUntagged(
        Level level,
        com.alien.common.gameplay.hive.location.HiveLocation location,
        BlockPos vent,
        int surfaceBandBlocks
    ) {
        var chunk = new net.minecraft.world.level.ChunkPos(vent);
        if (location.structurePieceByChunk().containsKey(chunk) && location.withinSlab(vent.getY())) {
            return VentKind.STRUCTURE;
        }
        if (isNearSurface(level, vent, surfaceBandBlocks)) {
            return VentKind.SURFACE;
        }
        return VentKind.FRONTIER;
    }

    /**
     * The spot an alien pops out at: 1-2 blocks IN FRONT of the vent. Vents carry no facing state, so "front" is the
     * template convention - the horizontal direction from the vent toward its chunk's centre (chamber vents sit on
     * walls facing the room). Webbing counts as open space: the templates web most vent mouths, and xenomorphs walk
     * through resin web like it isn't there. Falls back to any standable neighbour, then below the vent.
     */
    @Nullable
    public static BlockPos emergencePosNear(Level level, BlockPos vent) {
        var candidates = new ArrayList<BlockPos>();
        var forward = towardChunkCenter(vent);
        if (forward != null) {
            candidates.add(vent.relative(forward));
            candidates.add(vent.relative(forward).below());
            candidates.add(vent.relative(forward, 2));
            candidates.add(vent.relative(forward, 2).below());
        }
        for (var dir : Direction.Plane.HORIZONTAL) {
            candidates.add(vent.relative(dir));
            candidates.add(vent.relative(dir).below());
        }
        // Surface-vent geometry (party-dropped vents sit ON the ground at the chunk centre, ringed by a resin
        // collar): pop out the TOP, stand atop the collar, or land on the ground just beyond it.
        candidates.add(vent.above());
        for (var dir : Direction.Plane.HORIZONTAL) {
            candidates.add(vent.relative(dir).above());
            candidates.add(vent.relative(dir, 2));
            candidates.add(vent.relative(dir, 2).below());
        }
        for (int down = 1; down <= 3; down++) {
            candidates.add(vent.below(down));
        }
        for (var pos : candidates) {
            if (
                passable(level, pos)
                    && passable(level, pos.above())
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
            ) {
                return pos;
            }
        }
        return null;
    }

    /** Open space for a xenomorph: air, or resin web (aliens pass through webbing unaffected). */
    private static boolean passable(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        // Resin VEINS grow over vents just like webs do. A vein must not seal a vent shut - xenomorphs pass
        // straight through their own resin.
        return state.isAir()
            || state.is(com.alien.common.registry.tag.AlienBlockTags.RESIN_WEBS)
            || state.is(com.alien.common.registry.tag.AlienBlockTags.RESIN_VEINS);
    }

    /**
     * The vent's "front": the horizontal direction toward its chunk's centre, null when it sits on the centre column.
     */
    @Nullable
    private static Direction towardChunkCenter(BlockPos vent) {
        var chunk = new ChunkPos(vent);
        int dx = (chunk.getMinBlockX() + 8) - vent.getX();
        int dz = (chunk.getMinBlockZ() + 8) - vent.getZ();
        if (dx == 0 && dz == 0) {
            return null;
        }
        return Math.abs(dx) >= Math.abs(dz)
            ? (dx > 0 ? Direction.EAST : Direction.WEST)
            : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /**
     * Executes the duct hop: squelch at the entry, teleport {@code traveller} (re-seating any riders, e.g. a carried
     * egg) to the emergence spot at {@code exitVent}, squelch there. Returns false - and moves nothing - when the exit
     * has no standable spot.
     */
    public static boolean ductTravel(LivingEntity traveller, BlockPos entryVent, BlockPos exitVent) {
        var level = traveller.level();
        var emergence = emergencePosNear(level, exitVent);
        if (emergence == null) {
            return false;
        }
        var riders = new ArrayList<>(traveller.getPassengers());
        level.playSound(null, entryVent, SoundEvents.BEEHIVE_ENTER, SoundSource.HOSTILE, 1.0F, 0.8F);
        traveller.teleportTo(emergence.getX() + 0.5, emergence.getY(), emergence.getZ() + 0.5);
        for (var rider : riders) {
            rider.teleportTo(emergence.getX() + 0.5, emergence.getY(), emergence.getZ() + 0.5);
            rider.startRiding(traveller, true);
        }
        level.playSound(null, emergence, SoundEvents.BEEHIVE_EXIT, SoundSource.HOSTILE, 1.0F, 0.8F);
        return true;
    }
}
