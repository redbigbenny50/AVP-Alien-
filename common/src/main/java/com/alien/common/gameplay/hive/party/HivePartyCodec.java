package com.alien.common.gameplay.hive.party;

import com.alien.Alien;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.blib.api.common.codec.v1.BLibCodecs;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NBT serialization for the sealed {@link HiveParty} interface. Mirrors {@code ConvoyCodec}'s shape — a discriminator
 * string ({@code "Type"}) dispatches on subtype. Ships {@code "surface_spawn"}, {@code "biomass_hunting"}, and
 * {@code "attack_party"}; the remaining three planned party types will add their own case here alongside their
 * {@code final class} in {@link HiveParty}.
 * <p>
 * Saves a {@link ListTag} of compound tags, one per party. Stored on
 * {@link com.alien.common.gameplay.hive.location.HiveLocation} (not {@code LineageFactionData} — parties are
 * hive-level, unlike convoys).
 */
public final class HivePartyCodec {

    private static final String NBT_TYPE = "Type";

    private static final String NBT_ID = "Id";

    private static final String NBT_SOURCE_LOCATION_ID = "SourceLocationId";

    private static final String NBT_DIMENSION = "Dimension";

    private static final String NBT_COMPOSITION = "Composition";

    private static final String NBT_MATERIALIZED_MEMBERS = "MaterializedMembers";

    private static final String NBT_DISPATCHED_TICK = "DispatchedTick";

    private static final String TYPE_SURFACE_SPAWN = "surface_spawn";

    private static final String TYPE_BIOMASS_HUNTING = "biomass_hunting";

    private static final String TYPE_ATTACK_PARTY = "attack_party";

    private static final String TYPE_HOST_HUNT = "host_hunt";

    private static final String NBT_TARGET_PLAYER_ID = "TargetPlayerId";

    private static final String NBT_ECONOMY_BIAS = "EconomyBias";

    private static final String NBT_LAST_ECONOMY_CHECK_TICK = "LastEconomyCheckTick";

    private HivePartyCodec() {}

    public static ListTag saveAll(List<HiveParty> parties) {
        var listTag = new ListTag();
        for (var party : parties) {
            listTag.add(saveOne(party));
        }
        return listTag;
    }

    public static List<HiveParty> loadAll(ListTag listTag) {
        var parties = new ArrayList<HiveParty>();
        for (var i = 0; i < listTag.size(); i++) {
            var party = loadOne(listTag.getCompound(i));
            if (party != null) {
                parties.add(party);
            }
        }
        return parties;
    }

    public static CompoundTag saveOne(HiveParty party) {
        var tag = new CompoundTag();

        tag.putUUID(NBT_ID, party.id().value());
        tag.putString(NBT_SOURCE_LOCATION_ID, party.sourceLocationId().value().toString());
        tag.putString(NBT_DIMENSION, party.dimension().location().toString());

        var compositionTag = new CompoundTag();
        compositionTag.put("reserves", EntityReserves.CODEC.encode(BLibCodecs.Schema.NBT, party.composition()));
        tag.put(NBT_COMPOSITION, compositionTag);
        tag.put(NBT_MATERIALIZED_MEMBERS, encodeMaterializedMembers(party.materializedMembers()));

        tag.putLong(NBT_DISPATCHED_TICK, party.dispatchedTick());

        if (party instanceof HiveParty.SurfaceSpawn surfaceSpawn) {
            tag.putString(NBT_TYPE, TYPE_SURFACE_SPAWN);
            tag.putString(NBT_ECONOMY_BIAS, surfaceSpawn.economyBias().name());
            tag.putLong(NBT_LAST_ECONOMY_CHECK_TICK, surfaceSpawn.lastEconomyCheckTick());
        } else if (party instanceof HiveParty.BiomassHunting) {
            tag.putString(NBT_TYPE, TYPE_BIOMASS_HUNTING);
        } else if (party instanceof HiveParty.HostHunt) {
            tag.putString(NBT_TYPE, TYPE_HOST_HUNT);
        } else if (party instanceof HiveParty.AttackParty attackParty) {
            tag.putString(NBT_TYPE, TYPE_ATTACK_PARTY);
            tag.putUUID(NBT_TARGET_PLAYER_ID, attackParty.targetPlayerId());
        } else {
            throw new IllegalStateException("Unknown party subtype: " + party.getClass().getName());
        }

        return tag;
    }

    public static HiveParty loadOne(CompoundTag tag) {
        var type = tag.getString(NBT_TYPE);

        var id = new HivePartyId(tag.getUUID(NBT_ID));
        var sourceLocationId = new HiveLocationId(ResourceLocation.parse(tag.getString(NBT_SOURCE_LOCATION_ID)));
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(NBT_DIMENSION)));
        var dispatchedTick = tag.getLong(NBT_DISPATCHED_TICK);

        var compositionTag = tag.getCompound(NBT_COMPOSITION).getCompound("reserves");
        var composition = new EntityReserves();
        EntityReserves.CODEC.decode(BLibCodecs.Schema.NBT, compositionTag)
            .inspectErr(failure -> Alien.LOGGER.error("Failed to load party composition: {}", failure))
            .ifOk(loaded -> composition.putAll(loaded.getBackingMap()));
        var materializedMembers = decodeMaterializedMembers(tag.getList(NBT_MATERIALIZED_MEMBERS, Tag.TAG_COMPOUND));

        return switch (type) {
            case TYPE_SURFACE_SPAWN -> new HiveParty.SurfaceSpawn(
                id,
                sourceLocationId,
                dimension,
                composition,
                materializedMembers,
                dispatchedTick,
                tag.contains(NBT_ECONOMY_BIAS)
                    ? HiveParty.EconomyBias.valueOf(tag.getString(NBT_ECONOMY_BIAS))
                    : HiveParty.EconomyBias.HARVEST,
                tag.getLong(NBT_LAST_ECONOMY_CHECK_TICK)
            );
            case TYPE_BIOMASS_HUNTING -> new HiveParty.BiomassHunting(
                id,
                sourceLocationId,
                dimension,
                composition,
                materializedMembers,
                dispatchedTick
            );
            case TYPE_HOST_HUNT -> new HiveParty.HostHunt(
                id,
                sourceLocationId,
                dimension,
                composition,
                materializedMembers,
                dispatchedTick
            );
            case TYPE_ATTACK_PARTY -> new HiveParty.AttackParty(
                id,
                sourceLocationId,
                dimension,
                composition,
                materializedMembers,
                dispatchedTick,
                tag.getUUID(NBT_TARGET_PLAYER_ID)
            );
            default -> {
                Alien.LOGGER.warn("Unknown party type discriminator '{}' — skipping", type);
                yield null;
            }
        };
    }

    private static ListTag encodeMaterializedMembers(Map<UUID, EntityType<?>> members) {
        var listTag = new ListTag();
        for (var entry : members.entrySet()) {
            var tag = new CompoundTag();
            tag.putUUID("EntityId", entry.getKey());
            tag.putString("EntityType", BuiltInRegistries.ENTITY_TYPE.getKey(entry.getValue()).toString());
            listTag.add(tag);
        }
        return listTag;
    }

    private static Map<UUID, EntityType<?>> decodeMaterializedMembers(ListTag listTag) {
        var members = new HashMap<UUID, EntityType<?>>();
        for (var i = 0; i < listTag.size(); i++) {
            var tag = listTag.getCompound(i);
            if (!tag.hasUUID("EntityId") || !tag.contains("EntityType")) {
                continue;
            }
            var entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(tag.getString("EntityType")));
            members.put(tag.getUUID("EntityId"), entityType);
        }
        return members;
    }
}
