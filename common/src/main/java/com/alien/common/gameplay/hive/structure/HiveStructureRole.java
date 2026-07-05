package com.alien.common.gameplay.hive.structure;

import org.jetbrains.annotations.Nullable;

/**
 * The structural role a claimed chunk plays within a hive's built layout.
 * <p>
 * This is Phase 2 scaffolding (additive state only). It records what a chunk has been assigned to be in the hive's
 * structure - the queen's chamber, a hallway, a functional chamber, etc. - so the assembler/placer (Phase 2.2/2.3) and
 * the growth planner (Phase 3) can reason about the existing layout without re-deriving it from blocks.
 * <p>
 * Distinct from {@code claimedChunks} (territory the hive owns) and {@code decoratedChunks} (resin spread): a chunk can
 * be claimed long before it is assigned a structure role, and a role is assigned when the hive decides to build a piece
 * there.
 * <ul>
 * <li>{@link #UNASSIGNED} - claimed but no structure decided yet (the default for a plain claimed chunk).</li>
 * <li>{@link #QUEEN_CHAMBER_CENTER} - the center chunk of the queen's chamber (the founding seed origin).</li>
 * <li>{@link #QUEEN_CHAMBER_PART} - a non-center chunk belonging to the (multi-chunk) queen's chamber.</li>
 * <li>{@link #ROYAL_HALLWAY} - a chunk belonging to a royal hallway (queen-chamber-only connector).</li>
 * <li>{@link #HALLWAY} - a chunk belonging to a standard hallway/corridor piece.</li>
 * <li>{@link #CHAMBER} - a chunk belonging to a functional chamber (storage, egg, spawning, etc.).</li>
 * <li>{@link #JUNCTION} - a chunk belonging to a junction-chamber (multi-exit hub).</li>
 * <li>{@link #JELLY_CHAMBER} - a chunk belonging to a jelly chamber.</li>
 * </ul>
 */
public enum HiveStructureRole {
    UNASSIGNED,
    QUEEN_CHAMBER_CENTER,
    QUEEN_CHAMBER_PART,
    ROYAL_HALLWAY,
    HALLWAY,
    CHAMBER,
    JUNCTION,
    JELLY_CHAMBER;

    /**
     * Null-safe, throw-free lookup by name for NBT round-tripping. Returns {@link #UNASSIGNED} for null or unknown
     * values (forward-compatible: if a save references a role this version doesn't know, it degrades gracefully instead
     * of crashing the load).
     */
    public static HiveStructureRole byName(@Nullable String name) {
        if (name == null) {
            return UNASSIGNED;
        }
        for (var role : values()) {
            if (role.name().equals(name)) {
                return role;
            }
        }
        return UNASSIGNED;
    }
}