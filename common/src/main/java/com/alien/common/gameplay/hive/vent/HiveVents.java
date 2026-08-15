package com.alien.common.gameplay.hive.vent;

import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import java.util.Optional;
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
        // Ceiled dimensions have no sky surface - the heightmap reports the bedrock ROOF, so the old test asked
        // whether a nether vent sat within a few blocks of Y ~127 and branded every shelf vent FRONTIER. And
        // classification is write-once, so the wrong answer was then PERSISTED ([stated] "none of the vents they
        // have placed are registering as surface vents"). In shelf dimensions "the surface" is the open shelf the
        // vent stands on: resolve the nearest shelf floor through the dimension profile (sturdy footing plus the
        // profile's air clearance above - a tight rock pocket has no shelf and correctly stays FRONTIER) and
        // measure against THAT.
        if (level.dimensionType().hasCeiling() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(serverLevel);
            int shelfY = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.surfaceY(
                serverLevel,
                profile,
                vent.getX(),
                vent.getZ(),
                vent.getY()
            );
            return shelfY != com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.NO_SURFACE
                && Math.abs(vent.getY() - shelfY) <= band;
        }
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
       /** One shortcut through the interior duct network: which vent to enter by, and which to come out of. */
    public record DuctLeg(
        BlockPos entry,
        BlockPos exit
    ) {}

    /**
     * Below this, walking is simply quicker than finding a vent, going in and coming out.
     * <p>
     * Overridable by callers that have ALREADY FAILED to walk somewhere - at that point the duct is not an
     * optimisation, it is the only route left, and distance stops being the right question.
     * </p>
     */
    public static final double LEG_WORTHWHILE_DIST_SQUARED = 32.0 * 32.0;

    private static final int LEG_SEARCH_RADIUS_CHUNKS = 1;

    /**
     * Plans a shortcut through the hive's INTERIOR ducts, or returns empty if walking is the better answer.
     * <p>
     * This was written three times: privately inside {@code PickUpEggAction}, again inside {@code DropOffEggAction},
     * and not at all anywhere else - which is why egg hauling was the only job in the mod that could cross a hive
     * quickly while repair crews walked the whole way. It lives here now so any hive job can take a duct.
     * </p>
     * <p>
     * ⚠ STRUCTURE VENTS ONLY. Surface and frontier vents are the hive's MOUTHS - the ones parties, defenders and host
     * hunters use to get OUT. Ducting through those to cross a room would post workers into open cave, so the two
     * networks stay separate: this is the inside, {@code PartyVentUtil.findSurfaceVents} is the outside.
     * </p>
     *
     * @param ignoreProximity skip the worthwhile-distance test, for a caller whose walk has already failed
     */
    public static Optional<DuctLeg> planInteriorLeg(LivingEntity traveller, BlockPos target, boolean ignoreProximity) {
        if (!(traveller.level() instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }

        if (!ignoreProximity && traveller.blockPosition().distSqr(target) < LEG_WORTHWHILE_DIST_SQUARED) {
            return Optional.empty();
        }

        var location = HiveLocationRegistry.INSTANCE.getByChunk(serverLevel.dimension(), traveller.chunkPosition());
        if (location == null) {
            return Optional.empty();
        }

        var vents = location.ventManager();
        Predicate<BlockPos> interiorOnly = vent -> vents.isKind(vent, VentKind.STRUCTURE);
        var entry = nearestVent(vents, traveller.blockPosition(), LEG_SEARCH_RADIUS_CHUNKS, interiorOnly);
        var exit = nearestVent(vents, target, LEG_SEARCH_RADIUS_CHUNKS, interiorOnly);

        if (entry == null || exit == null || entry.equals(exit)) {
            return Optional.empty();
        }

        // A duct that drops you no closer than you already were is a detour, not a shortcut.
        if (exit.distSqr(target) >= traveller.blockPosition().distSqr(target)) {
            return Optional.empty();
        }

        return Optional.of(new DuctLeg(entry, exit));
    }

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

    /**
     * ⭐⭐ RE-LABEL EXISTING FRONTIER VENTS THAT ARE ACTUALLY ON THE SURFACE.
     * <p>
     * ⚠⚠ THIS EXISTS BECAUSE CLASSIFICATION IS WRITE-ONCE AND PERSISTED. `CreateVentAction` used to hardcode FRONTIER
     * outside ceiled dimensions, so every vent a xenomorph dug up onto grass was stamped FRONTIER and STAYED FRONTIER.
     * Fixing the classifier only helps vents dug from now on - [stated] "there was clearly a vent on the surface but it
     * kept acting like it wasnt" describes vents that are ALREADY WRONG in his world, and no amount of new digging
     * repairs them.
     * </p>
     * <p>
     * ⚠ ONLY EVER PROMOTES FRONTIER → SURFACE. It never touches STRUCTURE (the hive's own doors, deliberately
     * classified) and never demotes a SURFACE vent, so a hive cannot lose a door it already has. The worst case is that
     * it does nothing.
     * </p>
     *
     * @return how many vents were promoted
     */
    public static int reclassifyStaleFrontierVents(
        net.minecraft.server.level.ServerLevel level,
        com.alien.common.gameplay.hive.location.HiveLocation location,
        int surfaceBandBlocks
    ) {
        var promoted = 0;

        for (var vent : new java.util.ArrayList<>(location.ventManager().ventsOfKind(VentKind.FRONTIER))) {
            // ⚠ SKIP UNLOADED CHUNKS. isNearSurface reads the heightmap and the world; forcing a load to relabel a
            // vent would drag half the hive into memory. They are picked up next time the area is loaded.
            if (!level.hasChunk(vent.getX() >> 4, vent.getZ() >> 4)) {
                continue;
            }

            if (classifyUntagged(level, location, vent, surfaceBandBlocks) != VentKind.SURFACE) {
                continue;
            }

            location.ventManager().addVent(vent, VentKind.SURFACE);

            if (level.getBlockEntity(vent) instanceof com.alien.common.gameplay.block.entity.resin.vent.ResinVentBlockEntity blockEntity) {
                blockEntity.setKind(VentKind.SURFACE);
                blockEntity.markKindCurrent();
            }

            promoted++;
        }

        return promoted;
    }
}
