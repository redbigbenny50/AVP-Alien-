package com.alien.common.network.handler;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LocationFactionData;
import com.alien.common.gameplay.hive.faction.VariantFactionData;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.inspection.HiveInspectionSnapshot;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.network.payload.C2SRequestHiveInspectionPayload;
import com.alien.common.network.payload.S2CHiveInspectionPayload;
import com.blib.api.common.faction.v1.Faction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side handler for {@link C2SRequestHiveInspectionPayload}. Resolves the requested faction id, dispatches to the
 * matching snapshot builder (Location / Lineage / Variant), and replies with {@link S2CHiveInspectionPayload}. Op-gated
 * (permission level ≥ 2) since the panel is a debug surface.
 */
public final class HiveInspectionRequestHandler {

    private static final long LOCATION_REBUILD_RETRY_TICKS = 20L * 5L;

    private static final Map<ResourceLocation, Long> LAST_LOCATION_REBUILD_TICK_BY_FACTION = new HashMap<>();

    private HiveInspectionRequestHandler() {}

    public static void handle(C2SRequestHiveInspectionPayload payload, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (!sp.hasPermissions(2)) {
            return;
        }

        var factionId = payload.factionId();
        var faction = Alien.MOD.factions().get(factionId);
        if (faction == null) {
            return;
        }
        var data = faction.data();
        if (data instanceof LocationFactionData) {
            // Location faction id is exactly the HiveLocationId's ResourceLocation, so prefer a direct registry lookup
            // over LocationFactionData.locationId() — the latter is nullable on legacy / mid-load state and silently
            // skipping the reply makes the panel appear stuck.
            var locationId = HiveLocationId.of(factionId);
            var location = HiveLocationRegistry.INSTANCE.get(locationId);
            if (location == null) {
                location = rebuildAndRetryLocationLookup(sp, factionId, locationId);
            }
            if (location == null) {
                sendInspection(
                    sp,
                    HiveInspectionSnapshot.KIND_LOCATION,
                    factionId,
                    HiveInspectionSnapshot.buildMissingLocation(factionId, "missing from hive registry")
                );
                return;
            }
            var snapshot = HiveInspectionSnapshot.buildLocation(location, sp.server);
            sendInspection(sp, HiveInspectionSnapshot.KIND_LOCATION, factionId, snapshot);
        } else if (data instanceof LineageFactionData) {
            @SuppressWarnings("unchecked")
            var lineage = (Faction<LineageFactionData>) faction;
            var snapshot = HiveInspectionSnapshot.buildLineage(lineage, sp.server);
            sendInspection(sp, HiveInspectionSnapshot.KIND_LINEAGE, factionId, snapshot);
        } else if (data instanceof VariantFactionData) {
            @SuppressWarnings("unchecked")
            var variant = (Faction<VariantFactionData>) faction;
            var snapshot = HiveInspectionSnapshot.buildVariant(variant, sp.server);
            sendInspection(sp, HiveInspectionSnapshot.KIND_VARIANT, factionId, snapshot);
        }
    }

    private static com.alien.common.gameplay.hive.location.HiveLocation rebuildAndRetryLocationLookup(
        ServerPlayer player,
        ResourceLocation factionId,
        HiveLocationId locationId
    ) {
        var currentTick = player.server.overworld().getGameTime();
        var lastTick = LAST_LOCATION_REBUILD_TICK_BY_FACTION.get(factionId);
        var canRetry = lastTick == null
            || currentTick < lastTick
            || currentTick - lastTick >= LOCATION_REBUILD_RETRY_TICKS;
        if (!canRetry) {
            return null;
        }

        // ⚠ AGE OUT STALE ENTRIES. This map is only a rate limiter, but it is keyed by FACTION ID and nothing ever
        // removed from it - so a long-running pack accumulated one entry per lineage that ever failed a lookup,
        // forever, including lineages long since dead. Anything older than the retry window can no longer affect a
        // decision, so it is safe to drop.
        LAST_LOCATION_REBUILD_TICK_BY_FACTION
            .entrySet()
            .removeIf(entry -> currentTick >= entry.getValue() + LOCATION_REBUILD_RETRY_TICKS);

        LAST_LOCATION_REBUILD_TICK_BY_FACTION.put(factionId, currentTick);
        Alien.LOGGER.warn(
            "Hive inspector could not resolve location {}; rebuilding hive indexes from loaded factions.",
            factionId
        );
        Alien.rebuildHiveRegistryFromFactions(player.server);
        return HiveLocationRegistry.INSTANCE.get(locationId);
    }

    private static void sendInspection(
        ServerPlayer player,
        String kind,
        ResourceLocation factionId,
        net.minecraft.nbt.CompoundTag snapshot
    ) {
        Alien.MOD
            .networking()
            .sendToClient(player, new S2CHiveInspectionPayload(kind, factionId, snapshot));
    }
}
