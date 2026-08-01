package com.alien.common.gameplay.hive.faction;

import com.alien.Alien;
import com.alien.common.gameplay.hive.id.VariantIds;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.common.faction.v1.FactionData;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The species-tier faction. Exactly one per {@link AlienVariant}, world-wide. Holds the future Queen Mother slot.
 * <p>
 * Variant factions never die. Their id is fixed ({@code avp_alien:variant/<variant_name>}); creation is idempotent.
 * <p>
 * See {@code HIVE_REDESIGN_01_FACTIONS.md} § 1, § 3.
 */
public class VariantFactionData extends FactionData {

    private static final String NBT_VARIANT_ID = "VariantId";

    private static final String NBT_AGE_IN_TICKS = "AgeInTicks";

    private static final String NBT_NEXT_LINEAGE_NUMBER = "NextLineageNumber";

    private static final String NBT_QUEEN_MOTHERS = "QueenMothersByDimension";

    private static final String NBT_EMPRESS_RESCUES = "EmpressRescuesSpent";

    private static final String NBT_COUNT_KEY = "Count";

    private static final String NBT_DIMENSION_KEY = "Dimension";

    private static final String NBT_UUID_KEY = "Uuid";

    private static final AlienVariant DEFAULT_VARIANT = AlienVariant.NORMAL;

    /**
     * Kept stable for the lifetime of this faction. Set once by {@link VariantFactionRegistry} when the faction is
     * first created from a known variant id; round-tripped through NBT thereafter.
     */
    private AlienVariant variant;

    private long ageInTicks;

    /** Monotonic per-variant lineage counter. Allocated at lineage mint via {@link #allocateLineageNumber()}. */
    private long nextLineageNumber;

    private final Map<ResourceKey<Level>, UUID> queenMotherIdsByDimension;

    /**
     * Network-wide rescue transfers already spent, keyed by EMPRESS ID.
     * <p>
     * Keyed on her id rather than on a lineage because her corridor network spans several, and rather than on her
     * entity because abstract-first means she can be ruling without a body. A NEW empress is a new UUID and so starts
     * clean - killing her is a genuine reset, not a partial one.
     * <p>
     * PRUNED BY RECONCILE, never by deletion at the point of use. Five separate paths null an empressId - the task's
     * release, the ritual's release, exile, her death, and a corridor being severed - and expecting every one of them
     * to also clean up here is exactly the kind of bookkeeping that gets missed when a sixth is added.
     */
    private final Map<UUID, Integer> empressRescuesSpent;

    public VariantFactionData() {
        this.variant = DEFAULT_VARIANT;
        this.ageInTicks = 0L;
        this.nextLineageNumber = 0L;
        this.queenMotherIdsByDimension = new HashMap<>();
        this.empressRescuesSpent = new HashMap<>();
    }

    /** Returns the next lineage number and advances the counter. Monotonic — dead lineages don't release numbers. */
    public long allocateLineageNumber() {
        var n = nextLineageNumber++;
        markDirty();
        return n;
    }

    public long nextLineageNumber() {
        return nextLineageNumber;
    }

    public void setNextLineageNumber(long nextLineageNumber) {
        this.nextLineageNumber = nextLineageNumber;
        markDirty();
    }

    @Override
    public void onMemberAdded(FactionMember member, Entity entity) {
        if (FactionVariantPolicy.variantMatches(entity, variant)) {
            return;
        }
        Alien.LOGGER.warn(
            "Hive: evicting variant-mismatched member {} (type={}) from variant faction {} (variant={})",
            entity.getUUID(),
            entity.getType(),
            VariantIds.of(variant),
            variant
        );
        var faction = Alien.MOD.factions().get(VariantIds.of(variant));
        if (faction != null) {
            faction.membership().removeMember(member);
        }
    }

    public AlienVariant variant() {
        return variant;
    }

    public void setVariant(AlienVariant variant) {
        this.variant = variant;
        markDirty();
    }

    public long ageInTicks() {
        return ageInTicks;
    }

    public void incrementAge() {
        this.ageInTicks++;
    }

    /**
     * Record the queen mother for a dimension. Prefer {@code QueenMotherAscension.ascend}, which also restores every
     * hive's daughter allowance - that surge is the point of the tier and should not be a separate step someone has to
     * remember.
     */
    public void setQueenMother(ResourceKey<Level> dimension, UUID queenMotherId) {
        queenMotherIdsByDimension.put(dimension, queenMotherId);
        markDirty();
    }

    /** Rescue transfers this empress has already spent network-wide. */
    public int empressRescuesSpent(UUID empressId) {
        return empressRescuesSpent.getOrDefault(empressId, 0);
    }

    public void recordEmpressRescue(UUID empressId) {
        empressRescuesSpent.merge(empressId, 1, Integer::sum);
        markDirty();
    }

    /**
     * Drop every budget entry whose empress no longer rules anything. Self-cleaning: an id orphaned by ANY route -
     * including ones not yet invented - disappears on the next sweep.
     */
    public void pruneEmpressRescues(java.util.Set<UUID> empressIdsInUse) {
        if (empressRescuesSpent.keySet().retainAll(empressIdsInUse)) {
            markDirty();
        }
    }

    public Map<ResourceKey<Level>, UUID> queenMotherIdsByDimension() {
        return queenMotherIdsByDimension;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains(NBT_VARIANT_ID)) {
            this.variant = AlienVariant.getById(tag.getByte(NBT_VARIANT_ID)).unwrapOr(DEFAULT_VARIANT);
        }

        this.ageInTicks = tag.getLong(NBT_AGE_IN_TICKS);
        this.nextLineageNumber = tag.getLong(NBT_NEXT_LINEAGE_NUMBER);

        queenMotherIdsByDimension.clear();
        if (tag.contains(NBT_QUEEN_MOTHERS)) {
            var listTag = tag.getList(NBT_QUEEN_MOTHERS, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var i = 0; i < listTag.size(); i++) {
                var entry = listTag.getCompound(i);
                var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entry.getString(NBT_DIMENSION_KEY)));
                queenMotherIdsByDimension.put(dim, entry.getUUID(NBT_UUID_KEY));
            }
        }

        empressRescuesSpent.clear();
        if (tag.contains(NBT_EMPRESS_RESCUES)) {
            var listTag = tag.getList(NBT_EMPRESS_RESCUES, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var i = 0; i < listTag.size(); i++) {
                var entry = listTag.getCompound(i);
                empressRescuesSpent.put(entry.getUUID(NBT_UUID_KEY), entry.getInt(NBT_COUNT_KEY));
            }
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putByte(NBT_VARIANT_ID, (byte) variant.getId());
        tag.putLong(NBT_AGE_IN_TICKS, ageInTicks);
        tag.putLong(NBT_NEXT_LINEAGE_NUMBER, nextLineageNumber);

        var rescues = new net.minecraft.nbt.ListTag();
        for (var entry : empressRescuesSpent.entrySet()) {
            var entryTag = new CompoundTag();
            entryTag.putUUID(NBT_UUID_KEY, entry.getKey());
            entryTag.putInt(NBT_COUNT_KEY, entry.getValue());
            rescues.add(entryTag);
        }
        tag.put(NBT_EMPRESS_RESCUES, rescues);

        var queenMothers = new net.minecraft.nbt.ListTag();
        for (var entry : queenMotherIdsByDimension.entrySet()) {
            var entryTag = new CompoundTag();
            entryTag.putString(NBT_DIMENSION_KEY, entry.getKey().location().toString());
            entryTag.putUUID(NBT_UUID_KEY, entry.getValue());
            queenMothers.add(entryTag);
        }
        tag.put(NBT_QUEEN_MOTHERS, queenMothers);
    }
}
