package com.alien.common.gameplay.hive.faction;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.AlienFactionDataTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;

import java.util.HashSet;

/**
 * Helpers for the location-tier faction. Centralizes the lineage-superset invariant
 * ({@code location.members() ⊆ lineage.members()}) so callers can't accidentally add to a location faction without also
 * being in the parent lineage.
 */
public final class LocationMembership {

    private LocationMembership() {}

    /**
     * Idempotent join: ensures the entity is a member of the location's parent lineage faction, then of the location
     * faction itself. Lineage first to maintain the subset invariant. Refuses the join if the entity's variant doesn't
     * match the parent lineage's variant — see {@link FactionVariantPolicy}.
     */
    public static void join(HiveLocation location, Entity entity) {
        // \u2b50\u2b50 Oct 3 - THE CHOKE POINT FOR "A CAPTIVE JOINS NOTHING". Every way into a hive ends here: the
        // spawn
        // auto-join (whole claim column, any height), autoJoinAtPosition after a molt, stray adoption, wild adoption
        // and the rescue reconciliation. A captive queen, or one in her release grace outside a hive's slab, is turned
        // away. Anything that is not a queen passes untouched. See QueenCaptivity.mayJoinHive.
        if (!com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.mayJoinHive(entity)) {
            return;
        }
        var factions = Alien.MOD.factions();
        var member = FactionMember.entity(entity);

        var lineageFaction = factions.get(location.lineageFactionId());
        if (lineageFaction == null || !(lineageFaction.data() instanceof LineageFactionData lineage)) {
            Alien.LOGGER.warn(
                "Hive: LocationMembership.join — lineage faction {} missing for location {}; refusing to add {}",
                location.lineageFactionId(),
                location.id(),
                entity.getUUID()
            );
            return;
        }

        if (!FactionVariantPolicy.variantMatches(entity, lineage.variant())) {
            return;
        }

        if (!lineageFaction.membership().hasMember(member)) {
            lineageFaction.membership().addEntity(entity);
        }

        var locationFaction = factions.getOrCreate(location.id().value(), AlienFactionDataTypes.LOCATION);
        if (!locationFaction.membership().hasMember(member)) {
            locationFaction.membership().addEntity(entity);
        }

        location
            .loadedMembersByType()
            .computeIfAbsent(entity.getType(), $ -> new HashSet<>())
            .add(entity.getUUID());
        var addedKnownMember = location
            .knownMembersByType()
            .computeIfAbsent(entity.getType(), $ -> new HashSet<>())
            .add(entity.getUUID());
        if (addedKnownMember) {
            lineage.markDirty();
        }
    }

    /**
     * Mirrors the auto-join behavior in {@link com.alien.common.gameplay.entity.living.alien.Alien#finalizeSpawn}: if
     * the entity is a xenomorph standing in a live location's claimed chunk, join its lineage and location factions.
     * Idempotent and a no-op outside of any territory.
     */
    public static void autoJoinAtPosition(Entity entity, ServerLevelAccessor level) {
        if (!entity.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
            return;
        }

        var location = HiveLocationRegistry.INSTANCE.getByChunk(
            level.getLevel().dimension(),
            new ChunkPos(entity.blockPosition())
        );
        if (location == null || !location.isAlive()) {
            return;
        }

        join(location, entity);
    }
}
