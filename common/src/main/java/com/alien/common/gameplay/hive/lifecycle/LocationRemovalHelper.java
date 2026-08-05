package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.growth.HiveLocationClaims;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.location.HiveLocationRemovalReason;
import com.alien.common.gameplay.level.saveddata.HiveRuinsData;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;

/**
 * Minimal primitive to remove a {@link HiveLocation} from the world. Releases every claimed chunk through BLib's
 * territory manager + the registry's index, sets the location's {@code removalReason}, and unregisters it from the
 * lineage's location map and the registry.
 * <p>
 * <b>Use this directly only when the caller has already handled the location's reserves and members</b> — for example,
 * {@code MigrationDispatch} drains reserves into the convoy composition before calling here. For natural death
 * (dormancy, contest loss, player kill, admin), use {@link LocationDeathHandler} instead — it adds death-specific
 * membership and advancement handling on top of this primitive.
 */
public final class LocationRemovalHelper {

    private LocationRemovalHelper() {}

    public static void remove(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        HiveLocationRemovalReason reason
    ) {
        // A removed location leaves its rooms STANDING - nothing here demolishes anything. Hand every chunk the
        // location actually BUILT in, plus its slab band, to the per-dimension ruins index BEFORE the registry
        // forgets them. Without this the natural-spawn deny mixin loses its only handle on the site and vanilla
        // repopulates the derelict hive; with it the deny holds over the whole room, including the bare stone the
        // stamp never wrote and the bone-block trophy plinths. Roles are recorded for every occupied chunk of a
        // multi-chunk piece, so that map is the superset - the piece map is unioned in purely belt-and-braces.
        var ruins = HiveRuinsData.getOrCreate(level);
        var builtChunks = new HashSet<>(location.structureRoleByChunk().keySet());
        builtChunks.addAll(location.structurePieceByChunk().keySet());
        for (var chunk : builtChunks) {
            ruins.recordBuiltChunk(chunk, location.hiveFloorY(), location.hiveCeilingY());
        }
        if (!builtChunks.isEmpty()) {
            Alien.LOGGER.info(
                "Hive: location {} left {} built chunk(s) as a ruin (band Y {}..{}); natural spawning stays denied there.",
                location.id(),
                builtChunks.size(),
                location.hiveFloorY(),
                location.hiveCeilingY()
            );
        }

        // Snapshot the claimed chunks before iterating — release() mutates the set.
        var chunksToRelease = new HashSet<>(location.claimedChunks());
        for (var chunk : chunksToRelease) {
            HiveLocationClaims.release(level, location, chunk, true);
        }

        location.setRemovalReason(reason);
        HiveLocationRegistry.INSTANCE.unregister(location.id());
        lineage.removeLocation(location.id());

        // The BLib LocationFactionData faction is keyed on the location's id; once the HiveLocation is gone, that
        // faction has no backing data and would otherwise linger in the FactionBrowser. Deleting it here prevents
        // stale-faction inspector requests (which can't be answered because HiveLocationRegistry.get() returns null)
        // and keeps the per-location membership from outliving the location.
        var locationFactionId = location.id().value();
        if (Alien.MOD.factions().exists(locationFactionId)) {
            Alien.MOD.factions().remove(locationFactionId);
        }

        Alien.LOGGER.info(
            "Hive: removed location {} (lineage {}); reason={}",
            location.id(),
            location.lineageFactionId(),
            reason.typeKind()
        );
    }
}
