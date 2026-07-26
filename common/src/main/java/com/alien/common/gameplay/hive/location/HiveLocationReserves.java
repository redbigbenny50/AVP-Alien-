package com.alien.common.gameplay.hive.location;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.FactionVariantPolicy;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.common.codec.v1.BLibCodecs;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Variant-aware wrapper around BLib's {@link EntityReserves}. Each location gets one, and it is the authoritative
 * storage for that hive location's abstract members. Population caps gate buying and spawning elsewhere; reserve
 * storage itself is uncapped so already-owned members are not lost when they unload, travel, or return from convoys.
 */
public final class HiveLocationReserves {

    private static final String NBT_KEY = "EntityReserves";

    private static final String NBT_IDENTITY_KEY = "IdentityEntityReserves";

    private static final String NBT_BROOD_KEY = "BroodEntityReserves";

    private final EntityReserves underlying;

    /**
     * The BROOD BANK: host-born adults the hive absorbed after they finished growing. These are real gains - a body was
     * caught, implanted, and burst for each one - so they sit outside every population rule: no cap, not counted
     * against the member cap, and DRAWN ON FIRST whenever anything spawns from reserves. They are also wildcards: when
     * a caste is needed that neither bank holds, a brood member converts into it 1:1 (a steady host supply can bankroll
     * warriors, or even scourge units, that the hive never explicitly bought).
     */
    private final EntityReserves brood;

    private final HiveIdentityReserves identity;

    private final Supplier<AlienVariant> variantSupplier;

    public HiveLocationReserves(Supplier<AlienVariant> variantSupplier) {
        this.underlying = new EntityReserves();
        this.brood = new EntityReserves();
        this.identity = new HiveIdentityReserves();
        this.variantSupplier = variantSupplier;
    }

    /**
     * Adds all of {@code count} to this location's reserves when the entity type matches the location variant.
     */
    public boolean tryAdd(EntityType<?> type, int count) {
        if (count <= 0) {
            return false;
        }
        if (!accepts(type)) {
            var required = variantSupplier.get();
            Alien.LOGGER.warn(
                "Hive: rejected {} local reserve add of {} because it does not match location variant {}.",
                count,
                BuiltInRegistries.ENTITY_TYPE.getKey(type),
                required
            );
            return false;
        }

        underlying.add(type, count);
        return true;
    }

    /**
     * Returns already-owned live members to this location. Kept separate for readability at call sites that are
     * preserving existing population rather than buying new units.
     */
    public boolean addReturningMember(EntityType<?> type, int count) {
        return tryAdd(type, count);
    }

    /**
     * Returns a persistent already-owned member to this location while preserving its entity NBT and UUID.
     */
    public boolean addReturningIdentityMember(Entity entity) {
        if (!accepts(entity.getType())) {
            var required = variantSupplier.get();
            Alien.LOGGER.warn(
                "Hive: rejected identity reserve add of {} ({}) because it does not match location variant {}.",
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                required
            );
            return false;
        }

        var entry = HiveIdentityReserveEntry.capture(entity);
        if (entry == null) {
            return false;
        }
        return identity.add(entry);
    }

    /** Bank a host-born member into the brood bank. Uncapped; still variant-gated like every other add. */
    public boolean addBrood(EntityType<?> type, int count) {
        if (count <= 0 || !accepts(type)) {
            return false;
        }
        brood.add(type, count);
        return true;
    }

    /** Everything currently in the brood bank, for inspection/debug. */
    public int broodCount() {
        return brood.getCount();
    }

    /**
     * Unconditional spawn-side decrement. Returns true if a unit was successfully consumed; false if the type had zero.
     * <p>
     * Draw order: BROOD FIRST (exact type), then the main bank, then a brood WILDCARD - if neither bank holds the
     * requested caste but the brood holds anything at all, one brood member converts into the request 1:1. Because
     * every dispatch and top-up in the mod comes through here, host-born gains are spent before simulated stock
     * everywhere, with no changes at any call site.
     */
    public boolean trySpawn(EntityType<?> type) {
        if (!accepts(type)) {
            return false;
        }
        if (brood.getCount(type) > 0) {
            brood.add(type, -1);
            return true;
        }
        if (underlying.getCount(type) > 0) {
            underlying.add(type, -1);
            return true;
        }
        // Wildcard: convert any brood member into the requested caste.
        var donorTypes = brood.getAvailableEntityTypes();
        if (!donorTypes.isEmpty()) {
            brood.add(donorTypes.get(0), -1);
            return true;
        }
        return false;
    }

    public boolean canSpawn(EntityType<?> type) {
        return accepts(type) && (underlying.getCount(type) > 0 || brood.getCount() > 0);
    }

    /** Type-specific availability: main bank plus SAME-TYPE brood (wildcards are not advertised per-type). */
    public int getCount(EntityType<?> type) {
        return accepts(type) ? underlying.getCount(type) + brood.getCount(type) : 0;
    }

    public int getCountMatching(Predicate<EntityType<?>> predicate) {
        return underlying.getCountMatching(type -> accepts(type) && predicate.test(type))
            + brood.getCountMatching(type -> accepts(type) && predicate.test(type));
    }

    public int getCount() {
        return underlying.getCount() + brood.getCount();
    }

    public List<EntityType<?>> getAvailableEntityTypes() {
        var types = new LinkedHashSet<EntityType<?>>();
        underlying.getAvailableEntityTypes().stream().filter(this::accepts).forEach(types::add);
        brood.getAvailableEntityTypes().stream().filter(this::accepts).forEach(types::add);
        return List.copyOf(types);
    }

    public int getReliableCount(EntityType<?> type) {
        return getCount(type) + identity.getCount(type);
    }

    public int getReliableCountMatching(Predicate<EntityType<?>> predicate) {
        return getCountMatching(predicate) + identity.getCountMatching(type -> accepts(type) && predicate.test(type));
    }

    public int getReliableCount() {
        return getCount() + identity.getCountMatching(this::accepts);
    }

    public List<EntityType<?>> getReliableAvailableEntityTypes() {
        var types = new LinkedHashSet<EntityType<?>>();
        types.addAll(getAvailableEntityTypes());
        for (var type : identity.getAvailableEntityTypes()) {
            if (accepts(type)) {
                types.add(type);
            }
        }
        return List.copyOf(types);
    }

    public @Nullable HiveIdentityReserveEntry removeIdentity(EntityType<?> type) {
        return identity.removeFirst(type);
    }

    public void restoreIdentity(HiveIdentityReserveEntry entry) {
        identity.add(entry);
    }

    public HiveIdentityReserves identity() {
        return identity;
    }

    /** Direct access for callers that need to interoperate with raw BLib APIs. */
    public EntityReserves underlying() {
        return underlying;
    }

    public void save(CompoundTag tag) {
        tag.put(NBT_KEY, EntityReserves.CODEC.encode(BLibCodecs.Schema.NBT, underlying));
        tag.put(NBT_IDENTITY_KEY, identity.save());
        tag.put(NBT_BROOD_KEY, EntityReserves.CODEC.encode(BLibCodecs.Schema.NBT, brood));
    }

    public void load(CompoundTag tag) {
        if (tag.contains(NBT_KEY)) {
            EntityReserves.CODEC.decode(BLibCodecs.Schema.NBT, tag.getCompound(NBT_KEY))
                .inspectErr(failure -> Alien.LOGGER.error("Failed to load HiveLocationReserves: {}", failure))
                .ifOk(loaded -> {
                    var rejected = 0;
                    for (var entry : loaded.getBackingMap().entrySet()) {
                        var count = Math.max(0, entry.getValue());
                        if (count <= 0) {
                            continue;
                        }
                        if (accepts(entry.getKey())) {
                            underlying.add(entry.getKey(), count);
                        } else {
                            rejected += count;
                        }
                    }
                    if (rejected > 0) {
                        Alien.LOGGER.warn(
                            "Hive: discarded {} variant-mismatched local reserve entries while loading a hive location.",
                            rejected
                        );
                    }
                });
        }

        if (tag.contains(NBT_BROOD_KEY)) {
            EntityReserves.CODEC.decode(BLibCodecs.Schema.NBT, tag.getCompound(NBT_BROOD_KEY))
                .inspectErr(failure -> Alien.LOGGER.error("Failed to load brood bank: {}", failure))
                .ifOk(loaded -> {
                    for (var entry : loaded.getBackingMap().entrySet()) {
                        var count = Math.max(0, entry.getValue());
                        if (count > 0 && accepts(entry.getKey())) {
                            brood.add(entry.getKey(), count);
                        }
                    }
                });
        }

        if (tag.contains(NBT_IDENTITY_KEY, Tag.TAG_LIST)) {
            var rejectedIdentity = identity.load(HiveIdentityReserves.listTag(tag, NBT_IDENTITY_KEY), this::accepts);
            if (rejectedIdentity > 0) {
                Alien.LOGGER.warn(
                    "Hive: discarded {} invalid or variant-mismatched identity reserve entries while loading a hive location.",
                    rejectedIdentity
                );
            }
        }
    }

    public boolean accepts(EntityType<?> type) {
        var required = variantSupplier.get();
        return required == null || FactionVariantPolicy.variantMatches(type, required);
    }

    public int removeVariantMismatches(AlienVariant required) {
        var removed = 0;
        for (var entry : new ArrayList<>(underlying.getBackingMap().entrySet())) {
            if (FactionVariantPolicy.variantMatches(entry.getKey(), required)) {
                continue;
            }
            var count = Math.max(0, entry.getValue());
            if (count <= 0) {
                continue;
            }
            underlying.add(entry.getKey(), -count);
            removed += count;
        }
        removed += identity.removeIf(type -> !FactionVariantPolicy.variantMatches(type, required));
        return removed;
    }
}
