package com.alien.common.gameplay.hive.structure;

import net.minecraft.world.level.ChunkPos;

import java.util.List;

/**
 * A hive's intended layout, decided at founding before any corridors are routed: a set of spaced room {@link Goal}s
 * (each a room type at a target chunk) plus per-side {@link #exits} on the footprint edge. The router (a later phase)
 * carves corridors from the core out to each goal and exit; goals are chosen dispersed so rooms never cluster and every
 * side gets a way in and out. The blueprint is derived deterministically from a per-location seed, so every hive rolls
 * a different plan while the same spot regenerates the same one.
 */
public record HiveBlueprint(
    List<Goal> goals,
    List<ChunkPos> exits
) {

    /** One planned room: a type (e.g. chamber_host, chamber_egg, hub_2x2_4way) at a target chunk in the footprint. */
    public record Goal(
        String roomType,
        ChunkPos chunk
    ) {}
}
