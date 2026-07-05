package com.alien.common.gameplay.hive.structure;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;

/**
 * An open doorway socket on the hive's frontier - a place where the hive can attach a new piece. Phase 2 scaffolding.
 * <p>
 * Records the chunk that owns the doorway, the direction the doorway faces (which neighboring chunk a new piece would
 * attach into), and the door type label (e.g. {@code avp_alien:hive_door}, {@code avp_alien:hive_royal_door}) so the
 * planner only connects matching socket types. Step 2.3 registers the queen chamber's N/S/E/W exits as frontier
 * sockets; Phase 3 consumes them when placing hallways/rooms and registers the new piece's open exits in turn.
 *
 * @param chunk    the chunk on whose edge this doorway sits
 * @param facing   the direction the doorway faces (toward the chunk a new attached piece would occupy)
 * @param doorType the door type label this socket matches against (only same-label sockets connect)
 */
public record FrontierSocket(
    ChunkPos chunk,
    Direction facing,
    String doorType
) {

    private static final String NBT_X = "X";

    private static final String NBT_Z = "Z";

    private static final String NBT_FACING = "Facing";

    private static final String NBT_DOOR_TYPE = "DoorType";

    public CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putInt(NBT_X, chunk.x);
        tag.putInt(NBT_Z, chunk.z);
        tag.putString(NBT_FACING, facing.getName());
        tag.putString(NBT_DOOR_TYPE, doorType);
        return tag;
    }

    public static FrontierSocket fromTag(CompoundTag tag) {
        var chunk = new ChunkPos(tag.getInt(NBT_X), tag.getInt(NBT_Z));
        var facing = Direction.byName(tag.getString(NBT_FACING));
        if (facing == null) {
            facing = Direction.NORTH;
        }
        return new FrontierSocket(chunk, facing, tag.getString(NBT_DOOR_TYPE));
    }
}
