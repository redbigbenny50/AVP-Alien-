package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.location.HiveLocationSpacing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * Pure decision function: given a queen and a candidate position, returns what {@link HiveLocationFoundingService}
 * should do — found a new lineage, add a new location to one of her existing lineages, or refuse.
 * <p>
 * Encodes the rules from {@code HIVE_REDESIGN_08_LINEAGE_SPREAD.md} § 1 (spread zone),
 * {@code HIVE_REDESIGN_03_LOCATIONS.md} § 9 (location founding), and {@code HIVE_REDESIGN_02_FACTION_LIFECYCLES.md} § 2
 * (lineage founding). The "settlement validity" sub-check (solid ground, resin-replaceable surface, Y-level) is parked
 * for a future phase — Phase 5 only enforces the spread-zone and overlap rules.
 */
public final class SpreadZoneCheck {

    private SpreadZoneCheck() {}

    public static SpreadZoneResult evaluate(Queen queen, BlockPos position) {
        var dimension = queen.level().dimension();
        var candidateChunk = new ChunkPos(position);

        // Rule 1: never settle on top of an existing location's territory (any lineage, any variant).
        var occupant = HiveLocationRegistry.INSTANCE.getByChunk(dimension, candidateChunk);
        if (occupant != null) {
            return new SpreadZoneResult.Blocked(
                "chunk " + candidateChunk + " is already claimed by location " + occupant.id()
            );
        }

        var queenVariant = queen.getVariant();
        var config = HiveLocationRegistry.INSTANCE.config();
        if (
            !HiveLocationSpacing.isFarEnoughFromExistingLocations(
                dimension,
                candidateChunk,
                config.minimumHiveLocationDistanceChunks()
            )
        ) {
            return new SpreadZoneResult.Blocked(
                "chunk " + candidateChunk + " is too close to an existing hive location"
            );
        }

        var maxSpread = config.maxLineageSpreadChunks();

        // Rule 2: collect this queen's lineage memberships (filtered to lineages of her variant + dimension).
        ResourceLocation ownLineageInRange = null;
        for (var factionId : Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (lineage.variant() != queenVariant || !lineage.dimension().equals(dimension)) {
                continue;
            }
            if (isWithinSpreadZone(lineage, candidateChunk, maxSpread)) {
                ownLineageInRange = factionId;
                break;
            }
        }

        if (ownLineageInRange != null) {
            return new SpreadZoneResult.NewLocation(ownLineageInRange);
        }

        // Rule 3: forager case — she has no lineage in range. Make sure no OTHER same-variant lineage's
        // spread zone covers this position. If it does, she's blocked from founding here.
        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (lineage.variant() != queenVariant || !lineage.dimension().equals(dimension)) {
                continue;
            }
            if (isWithinSpreadZone(lineage, candidateChunk, maxSpread)) {
                return new SpreadZoneResult.Blocked(
                    "another " + queenVariant + " lineage " + factionId + " covers this spread zone"
                );
            }
        }

        return new SpreadZoneResult.NewLineage();
    }

    /**
     * Whether {@link #evaluate} would permit founding in {@code chunk} for this queen (identical spacing + spread-zone
     * rules). The queen's anchor picker uses this so it only proposes anchors founding will actually accept: otherwise
     * it can pick a spot that clears the minimum LOCATION spacing yet sits inside another lineage's larger SPREAD zone,
     * whereupon founding blocks, she restarts LOCATION, re-picks the very same spot, and loops forever. Y is irrelevant
     * to the spacing/spread rules, so the chunk centre at the queen's Y is used.
     */
    public static boolean wouldAllow(Queen queen, ChunkPos chunk) {
        var probe = chunk.getMiddleBlockPosition(queen.blockPosition().getY());
        return !(evaluate(queen, probe) instanceof SpreadZoneResult.Blocked);
    }

    private static boolean isWithinSpreadZone(LineageFactionData lineage, ChunkPos candidate, int maxSpread) {
        for (var location : lineage.locationsById().values()) {
            if (chunkDistance(location, candidate) <= maxSpread) {
                return true;
            }
        }
        return false;
    }

    private static int chunkDistance(HiveLocation location, ChunkPos other) {
        var locationChunk = new ChunkPos(location.centerPos());
        var dx = Math.abs(locationChunk.x - other.x);
        var dz = Math.abs(locationChunk.z - other.z);
        // Chebyshev distance — the spread zone is a square, not a circle, matching how chunk distances feel in-game.
        return Math.max(dx, dz);
    }
}
