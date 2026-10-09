package com.alien.common.gameplay.hive.vent;

import com.alien.common.gameplay.block.entity.resin.vent.ResinVentBlockEntity;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.model.alien.variant.AlienVariantType;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * How a vent gets built. There is exactly one way now, and drilling is not it.
 * <p>
 * <b>The old model:</b> a xenomorph rayed horizontally into a wall, carved a three-deep tunnel of resin web with resin
 * walls around it, and left the vent block buried at depth three - INSIDE the wall. That is why nothing could ever path
 * to a vent, why {@code emergencePosNear} had to go hunting for a standable cell nearby, and why a carrier would stand
 * outside a hill forever trying to walk to a vent that was inside it.
 * <p>
 * <b>The new model:</b> the vent sits in an OPEN cell resting against a solid face - a floor, a wall, or a ceiling, it
 * does not matter which. Every side of it that touches air is filled with resin web (which xenomorphs walk through as
 * though it were not there, so the vent stays passable while reading as sealed), and resin spreads over the surrounding
 * rock the way it spreads over ground. Nothing is bored through; the wall stays solid. Ducting between vents is a
 * teleport, so a vent has never actually needed to be a hole.
 */
public final class VentPlacement {

    private VentPlacement() {}

    /** How far the resin creeps over the rock around a new vent. */
    private static final int RESIN_RADIUS = 2;

    /**
     * Build a vent at {@code ventPos} and stamp it with its role.
     *
     * @param ventPos  an OPEN cell that rests against at least one solid face - see {@link #restsOnSolidFace}
     * @param location the hive location that owns this vent; the vent is registered in its {@code ventManager()} here
     *                 so the party system can see it immediately. Registration used to rely solely on the block-entity
     *                 bind tick, which left surface-party and frontier vents stamped on the block entity but ABSENT
     *                 from the manager - so a real surface vent sat on open ground while host hunts reported "no
     *                 near-surface vent". Placing and registering together makes that impossible to forget.
     */
    /**
     * Does this position sit inside a DIFFERENT live hive's claim?
     * <p>
     * ⚠ "Different" is the whole test. A hive placing vents throughout its own territory is ordinary business; only a
     * vent opened in ground another hive holds is an invasion. Compared by location id rather than by lineage, so a
     * daughter tunnelling into her mother's hive still counts - they are separate holdings even when allied.
     * </p>
     */
    public static boolean isInsideForeignTerritory(Level level, BlockPos pos, HiveLocation placer) {
        var occupant = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE
            .getByChunk(level.dimension(), new net.minecraft.world.level.ChunkPos(pos));
        return occupant != null && occupant.isAlive() && !occupant.id().equals(placer.id());
    }

    public static void place(Level level, BlockPos ventPos, AlienVariantType variant, VentKind kind, HiveLocation location) {
        var resin = variant.resin().get();
        var resinWeb = variant.resinWeb().get();
        var resinVent = variant.resinVent().get();

        level.setBlock(ventPos, resinVent.defaultBlockState(), Block.UPDATE_ALL);

        // Record what this vent is for, so nothing downstream ever has to infer it from geometry.
        // ⭐⭐⭐ A VENT DUG INTO SOMEBODY ELSE'S TERRITORY IS AN INVADER'S DOOR, WHOEVER PLACED IT AND WHY.
        //
        // ⚠⚠ THIS WAS DECLARED AND NEVER WIRED. VentKind.INVADER existed, classifyUntagged correctly refused to
        // infer it, and isPartyDoor() accepted it - but NOTHING EVER ASSIGNED IT. Every party dispatch placed
        // SURFACE, so an assault vent inside a rival hive was indistinguishable from that hive's own emergence
        // point, which is exactly the failure the kind was created to prevent.
        //
        // ⭐ DONE AT THE CHOKE POINT, not at the call sites. Every vent in the mod is placed through here, so war
        // offensives, party dispatches into contested ground and anything written later all get it without having
        // to remember. Patching the four current callers would have left the fifth broken.
        //
        // ⚠ It only fires when the ground belongs to a DIFFERENT live location - a hive venting inside its own
        // claim is doing something completely ordinary.
        var effectiveKind = isInsideForeignTerritory(level, ventPos, location) ? VentKind.INVADER : kind;

        if (level.getBlockEntity(ventPos) instanceof ResinVentBlockEntity vent) {
            vent.setKind(effectiveKind);
            vent.markKindCurrent();
            // Bind to the owning hive at birth, so a surface vent on unclaimed frontier ground keeps its owner
            // across reloads instead of relying on getByChunk (which only knows claimed chunks).
            if (location != null) {
                vent.setBoundLocation(location.id());
            }
        }

        // Register with the owning hive's vent manager NOW, not on a later block-entity bind tick. This is the
        // single source of truth the party system queries (findSurfaceVents / findPartyVents).
        if (location != null) {
            location.ventManager().addVent(ventPos.immutable(), effectiveKind);
        }

        // Web every side that touches air. Xenomorphs pass through web freely; nothing else does.
        for (var direction : Direction.values()) {
            var neighbour = ventPos.relative(direction);
            if (isOpen(level, neighbour)) {
                level.setBlock(neighbour, resinWeb.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        // Creep resin over the rock around the mouth, the same way the hive resins ground it claims.
        for (
            var offset : BlockPos.betweenClosed(
                ventPos.offset(-RESIN_RADIUS, -RESIN_RADIUS, -RESIN_RADIUS),
                ventPos.offset(RESIN_RADIUS, RESIN_RADIUS, RESIN_RADIUS)
            )
        ) {
            var pos = offset.immutable();
            if (pos.equals(ventPos)) {
                continue;
            }
            if (isResinnableRock(level, pos, variant)) {
                level.setBlock(pos, resin.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * ⭐ Oct 6 - [stated] "why are xenomorphs making vents in the hive? they should not be". True if {@code pos} lies in
     * a chunk holding one of this hive's built pieces, inside the hive's slab - the room/tunnel volume. A vent there is
     * debris to the room upkeep (it is not part of the template), so the next repair cleared it and left its webbing
     * floating. Same test the xenos' own vent-digging (CreateVentAction.isInsideBuiltStructure) has always used; the
     * hive's emergency and relocation vents now use it too.
     */
    public static boolean isInsideBuiltStructure(HiveLocation location, BlockPos pos) {
        return location != null
            && location.structurePieceByChunk().containsKey(new net.minecraft.world.level.ChunkPos(pos))
            && location.withinSlab(pos.getY());
    }

    /** True if this cell is open (air, or something the hive may grow through) rather than solid rock. */
    public static boolean isOpen(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * True if this OPEN cell rests against at least one solid face - the block whose surface the vent will sit on. Any
     * face counts: a vent may sit on the floor, cling to a wall, or hang from a ceiling.
     */
    public static boolean restsOnSolidFace(Level level, BlockPos pos) {
        for (var direction : Direction.values()) {
            var neighbour = pos.relative(direction);
            if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    /** Solid rock the hive is allowed to resin over. Never touches blocks the xenomorphs cannot work. */
    private static boolean isResinnableRock(Level level, BlockPos pos, AlienVariantType variant) {
        var state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) {
            return false; // not rock - leave the open space open
        }
        if (state.is(AlienBlockTags.XENOMORPH_IMMUNE)) {
            return false;
        }
        if (state.is(variant.resin().get()) || state.is(variant.resinVent().get())) {
            return false; // already ours
        }
        return state.is(variant.resinReplaceableTag());
    }
}
