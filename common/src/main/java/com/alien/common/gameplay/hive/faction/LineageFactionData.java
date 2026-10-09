package com.alien.common.gameplay.hive.faction;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.convoy.Convoy;
import com.alien.common.gameplay.hive.convoy.ConvoyCodec;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import com.blib.api.common.faction.v1.FactionData;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One queen's bloodline. Lives in a single dimension; owns one or more {@link HiveLocation}s and can eventually have an
 * empress.
 * <p>
 * Per {@code HIVE_REDESIGN_12_PERFORMANCE.md} § 3, this class also carries its location records as nested NBT — they're
 * persisted with the lineage shard, not separately.
 * <p>
 * Phase 1 ships the data shape only. Member-tracking, the empress, and convoys all attach in later phases (referenced
 * fields are present but inert for now).
 * <p>
 * See {@code HIVE_REDESIGN_01_FACTIONS.md} § 2.
 */
public class LineageFactionData extends FactionData {

    private static final String NBT_VARIANT_ID = "VariantId";

    private static final String NBT_FACTION_ID = "FactionId";

    private static final String NBT_PARENT_VARIANT_FACTION_ID = "ParentVariantFactionId";

    private static final String NBT_DIMENSION = "Dimension";

    private static final String NBT_FOUNDER_ID = "FounderId";

    private static final String NBT_PARENT_LINEAGE_ID = "ParentLineageId";

    private static final String NBT_SEPARATION_TICK = "SeparationTick";

    private static final String NBT_EMPRESS_ID = "EmpressId";

    private static final String NBT_PRETENDERS = "Pretenders";

    private static final String NBT_SCHISM_GRACE_UNTIL = "SchismGraceUntilTick";

    private static final String NBT_PRETENDER_ID = "Id";

    private static final String NBT_PRETENDER_CROWNED = "CrownedAt";

    private static final String NBT_AGE_IN_TICKS = "AgeInTicks";

    private static final String NBT_PENDING_EMPRESS_EMERGENCE = "PendingEmpressEmergence";

    private static final String NBT_PENDING_EMPRESS_SEAT = "PendingEmpressSeat";

    private static final String NBT_EMPRESS_COOLDOWN_UNTIL = "EmpressCooldownUntil";

    private static final String NBT_EMPRESS_REVEALED = "EmpressRevealed";

    private static final String NBT_LINEAGE_NUMBER = "LineageNumber";

    private static final String NBT_NEXT_LOCATION_NUMBER = "NextLocationNumber";

    private static final String NBT_LOCATIONS = "Locations";

    private static final String LEGACY_NBT_PENDING_FOUNDER_QUEEN = "PendingFounderQueen";

    private static final String NBT_CONVOYS = "Convoys";

    private static final String NBT_KILL_ATTRIBUTION_BY_PLAYER = "KillAttributionByPlayer";

    private static final String NBT_LINEAGE_KILL_CREDIT_PLAYER_ID = "LineageKillCreditPlayerId";

    private static final String NBT_REMOVAL_REASON = "RemovalReason";

    private static final AlienVariant DEFAULT_VARIANT = AlienVariant.NORMAL;

    private AlienVariant variant;

    /**
     * Own BLib faction id. Set right after the faction is minted so onMemberAdded reactive guards can evict mismatches.
     */
    private @Nullable ResourceLocation factionId;

    private @Nullable ResourceLocation parentVariantFactionId;

    private ResourceKey<Level> dimension;

    private @Nullable UUID founderId;

    /** The lineage this one broke away from, if any. See AlienTerritoryWarSystem remembrance. */
    private @Nullable ResourceLocation parentLineageId;

    /** Game time the break-away happened, i.e. when the remembrance window starts. */
    private long separationTick;

    private @Nullable UUID empressId;

    /**
     * ⭐⭐⭐ EVERY OTHER EMPRESS IN THIS LINEAGE, OLDEST FIRST. {@link #empressId} REMAINS THE SOVEREIGN.
     * <p>
     * [stated] "yes the oldest is soverign when there is two and if something happens the second oldest takes over."
     * </p>
     * <p>
     * ⚠⚠ THE SOVEREIGN IS DELIBERATELY STILL A SINGLE FIELD. {@code empressId()} has FORTY-EIGHT readers across the mod
     * and every one of them is asking the same question — "who rules this lineage" — whose answer is still one UUID.
     * Turning it into a collection would have touched all forty-eight and broken the invariant they rely on. A list
     * BESIDE it means nothing outside this class needs to know a second empress can exist.
     * </p>
     * <p>
     * ⚠ CROWNING TIME IS STORED, NOT DERIVED. "Oldest" cannot be read off the entity — she may be unloaded, or in
     * another dimension — so without a persisted stamp the succession order would be arbitrary after a reload, which is
     * exactly the kind of bug that only shows up in someone else's world a week later.
     * </p>
     */
    private final java.util.List<Pretender> pretenders = new java.util.ArrayList<>();

    /**
     * ⭐⭐⭐ A BREAKAWAY LINEAGE'S HEAD START. Game time until which it cannot be absorbed and buys members without paying
     * biomass.
     * <p>
     * [stated] "give a grace period because she will need to raise her troops and populate the hive ... the grace
     * period should be a time of 3 minecraft days but member production has no biomass cost just a jelly cost."
     * </p>
     * <p>
     * ⚠⚠ THE FREE PRODUCTION IS THE LOAD-BEARING HALF, NOT THE IMMUNITY. A schism founds ONE hive beside an empire of
     * eight, and the absorption roll weighs network size and banked strength - her defence floors at ten percent.
     * Protecting her without letting her BUILD would only postpone the same loss by three days; the player who spawned
     * her would still see nothing happen. [stated] "a hive with a grace period has a period of member growth to make
     * the war possible."
     * </p>
     * <p>
     * ⚠ JELLY IS STILL CHARGED, so she cannot conjure an army from nothing - she spends what she has, faster. Same
     * shape as the irradiated and forced-empress carve waivers: waive the DEBT, never the TIME.
     * </p>
     */
    private long schismGraceUntilTick;

    /** An empress of this lineage who is not its sovereign, with the game time she was crowned. */
    private record Pretender(
        UUID id,
        long crownedAtGameTime
    ) {}

    private long ageInTicks;

    private boolean pendingEmpressEmergence;

    /**
     * The location whose seated queen has been ELECTED empress but has not physically molted yet.
     * <p>
     * Emergence is decided abstractly, the same way growth and claims are: the seat is chosen from persisted location
     * data with nothing loaded, {@code empressId} is assigned immediately, and the lineage starts behaving as an
     * empress lineage right away. This field is what remembers WHERE the body still has to appear. Non-null means
     * "elected, not yet materialized"; it is cleared the moment she molts, and also cleared if the seat dies or loses
     * its queen first (which releases {@code empressId} so a new seat can be elected).
     */
    private @Nullable HiveLocationId pendingEmpressSeatId;

    /**
     * Game tick before which this lineage may not crown another empress. Set when an empress DIES; 0 = no cooldown.
     * <p>
     * Without it, killing her only starts the race for her successor - the lineage still holds 4+ hives, so the very
     * next scan elects again and the players get nothing for the kill.
     */
    private long empressCooldownUntilTick;

    /**
     * Her position has been given away by a second rescue into the same hive. One-way for this empress.
     * <p>
     * She does not relocate afterwards - the player earned the coordinates. This flag is what her dig-in response
     * reads, and it stops the reveal message repeating on every subsequent transfer.
     */
    private boolean empressRevealed;

    /** Per-variant lineage index assigned at mint (used in {@link FactionNaming} paths). -1 = unassigned. */
    private long lineageNumber;

    /** Monotonic per-lineage location counter. Allocated at location mint via {@link #allocateLocationNumber()}. */
    private long nextLocationNumber;

    /** Insertion-ordered so the "oldest location" tiebreak is stable across restarts. */
    private final Map<HiveLocationId, HiveLocation> locationsById;

    /** In-flight convoys belonging to this lineage. Persisted via {@link ConvoyCodec}. Wired in Phase 8. */
    private final List<Convoy> convoys;

    /**
     * Player-kill attribution table for Phase 8b raid dispatch. Each entry is the list of game-tick timestamps when
     * that player killed members of this lineage. {@link #recordKillByPlayer} prunes timestamps older than the raid
     * aggro window on each insertion.
     */
    private final Map<UUID, List<Long>> killAttributionByPlayer;

    private @Nullable UUID lineageKillCreditPlayerId;

    private @Nullable LineageRemovalReason removalReason;

    public LineageFactionData() {
        this.variant = DEFAULT_VARIANT;
        this.factionId = null;
        this.parentVariantFactionId = null;
        this.dimension = Level.OVERWORLD;
        this.founderId = null;
        this.empressId = null;
        this.ageInTicks = 0L;
        this.pendingEmpressEmergence = false;
        this.pendingEmpressSeatId = null;
        this.empressCooldownUntilTick = 0L;
        this.empressRevealed = false;
        this.lineageNumber = -1L;
        this.nextLocationNumber = 0L;
        this.locationsById = new LinkedHashMap<>();
        this.convoys = new ArrayList<>();
        this.killAttributionByPlayer = new HashMap<>();
        this.lineageKillCreditPlayerId = null;
        this.removalReason = null;
    }

    @Override
    public void onMemberAdded(FactionMember member) {
        markDirty();
    }

    @Override
    public void onMemberAdded(FactionMember member, Entity entity) {
        // Reactive variant guard: lineage factions accept only members of their own variant. Reject mismatches
        // immediately by removing them. (Proactive guards in LocationMembership.join and
        // FactionMembershipTransfer.apply catch the common paths; this is defense-in-depth for direct addEntity calls
        // from queenless maturation, debug commands, etc.)
        if (!FactionVariantPolicy.variantMatches(entity, variant) && factionId != null) {
            Alien.LOGGER.warn(
                "Hive: evicting variant-mismatched member {} (type={}) from lineage {} (variant={})",
                entity.getUUID(),
                entity.getType(),
                factionId,
                variant
            );
            var faction = Alien.MOD.factions().get(factionId);
            if (faction != null) {
                faction.membership().removeMember(member);
            }
            return;
        }

        // The entity is, by virtue of being passed here, currently loaded, so route it
        // into the per-location loadedMembersByType immediately — BLib does not fire
        // onMemberLoaded for entities that were already loaded when added.
        registerLoadedMember(entity);
        markDirty();
    }

    @Override
    public void onMemberRemoved(FactionMember member) {
        if (member instanceof FactionMember.Entity entityMember) {
            for (var location : locationsById.values()) {
                removeLoadedMemberByUuid(location, entityMember.uuid());
                removeKnownMemberByUuid(location, entityMember.uuid());
            }
        }
        markDirty();
    }

    @Override
    public void onMemberLoaded(Entity entity) {
        registerLoadedMember(entity);
    }

    @Override
    public void onMemberUnloaded(Entity entity) {
        // Be defensive: an entity may have crossed a chunk boundary into a sister location's
        // territory between load and unload. Sweep every owned location to remove the UUID.
        for (var location : locationsById.values()) {
            removeLoadedMember(location, entity.getType(), entity.getUUID());
        }
    }

    private void registerLoadedMember(Entity entity) {
        var location = locationContaining(entity);
        if (location == null) {
            return;
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
            markDirty();
        }
    }

    private @Nullable HiveLocation locationContaining(Entity entity) {
        if (!entity.level().dimension().equals(dimension)) {
            return null;
        }
        var chunk = new ChunkPos(entity.blockPosition());
        var hit = HiveLocationRegistry.INSTANCE.getByChunk(dimension, chunk);

        if (hit == null) {
            return null;
        }

        // Cross-lineage same-variant chunk overlaps are possible per HIVE_REDESIGN_03_LOCATIONS § 3.
        // Only route the entity to a location we actually own.
        return locationsById.containsKey(hit.id()) ? hit : null;
    }

    private static void removeLoadedMember(HiveLocation location, EntityType<?> type, UUID uuid) {
        var perType = location.loadedMembersByType().get(type);
        if (perType == null) {
            return;
        }
        perType.remove(uuid);
        if (perType.isEmpty()) {
            location.loadedMembersByType().remove(type);
        }
    }

    private static void removeLoadedMemberByUuid(HiveLocation location, UUID uuid) {
        var iterator = location.loadedMembersByType().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            entry.getValue().remove(uuid);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private static void removeKnownMemberByUuid(HiveLocation location, UUID uuid) {
        var iterator = location.knownMembersByType().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            entry.getValue().remove(uuid);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }

    public AlienVariant variant() {
        return variant;
    }

    public void setVariant(AlienVariant variant) {
        this.variant = variant;
        markDirty();
    }

    public @Nullable ResourceLocation factionId() {
        return factionId;
    }

    public void setFactionId(ResourceLocation factionId) {
        this.factionId = factionId;
        markDirty();
    }

    public long lineageNumber() {
        return lineageNumber;
    }

    public void setLineageNumber(long lineageNumber) {
        this.lineageNumber = lineageNumber;
        markDirty();
    }

    public long nextLocationNumber() {
        return nextLocationNumber;
    }

    public void setNextLocationNumber(long nextLocationNumber) {
        this.nextLocationNumber = nextLocationNumber;
        markDirty();
    }

    /** Returns the next location number and advances the counter. Monotonic — dead locations don't release numbers. */
    public long allocateLocationNumber() {
        var n = nextLocationNumber++;
        markDirty();
        return n;
    }

    public @Nullable ResourceLocation parentVariantFactionId() {
        return parentVariantFactionId;
    }

    public void setParentVariantFactionId(ResourceLocation parentVariantFactionId) {
        this.parentVariantFactionId = parentVariantFactionId;
        markDirty();
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public void setDimension(ResourceKey<Level> dimension) {
        this.dimension = dimension;
        markDirty();
    }

    public @Nullable UUID founderId() {
        return founderId;
    }

    public void setFounderId(@Nullable UUID founderId) {
        this.founderId = founderId;
        markDirty();
    }

    public @Nullable ResourceLocation parentLineageId() {
        return parentLineageId;
    }

    public long separationTick() {
        return separationTick;
    }

    /**
     * ⭐ Records which lineage this one broke away from, and when. [stated] "i would say theres a period of rememberance
     * where they are nuetral to allow the daughter to leave and found" - without the parent link there is nothing to be
     * neutral TOWARD, since every separated daughter otherwise reads as an unrelated rival lineage.
     */
    public void setParentLineage(@Nullable ResourceLocation parentLineageId, long separationTick) {
        this.parentLineageId = parentLineageId;
        this.separationTick = Math.max(0L, separationTick);
        markDirty();
    }

    public @Nullable UUID empressId() {
        return empressId;
    }

    public void setEmpressId(@Nullable UUID empressId) {
        this.empressId = empressId;
        markDirty();
    }

    /**
     * Records an empress who is NOT the sovereign - situation 6, and any rival crowned while one already reigns.
     * <p>
     * ⚠ If the lineage has no sovereign she simply becomes it: a pretender with nobody to yield to is the ruler.
     * </p>
     */
    public void addPretender(UUID id, long crownedAtGameTime) {
        if (id.equals(empressId)) {
            return;
        }
        if (empressId == null) {
            setEmpressId(id);
            return;
        }
        for (var pretender : pretenders) {
            if (pretender.id().equals(id)) {
                return;
            }
        }
        pretenders.add(new Pretender(id, crownedAtGameTime));
        pretenders.sort(java.util.Comparator.comparingLong(Pretender::crownedAtGameTime));
        markDirty();
    }

    /** Whether this lineage is inside its breakaway grace period. */
    public boolean isInSchismGrace(long gameTime) {
        return gameTime < schismGraceUntilTick;
    }

    public void beginSchismGrace(long untilGameTime) {
        this.schismGraceUntilTick = untilGameTime;
        markDirty();
    }

    public void removePretender(UUID id) {
        if (pretenders.removeIf(pretender -> pretender.id().equals(id))) {
            markDirty();
        }
    }

    /** Every empress of this lineage that is not the sovereign, oldest first. */
    public java.util.List<UUID> pretenders() {
        return pretenders.stream().map(Pretender::id).toList();
    }

    /**
     * ⭐⭐ PROMOTES THE OLDEST PRETENDER INTO THE THRONE. Call when the sovereign dies.
     * <p>
     * [stated] "if something happens the second oldest takes over." The list is kept sorted by crowning time, so the
     * next in line is simply the first entry.
     * </p>
     * <p>
     * ⚠ RETURNS FALSE WHEN THERE IS NOBODY, which must leave the ordinary queenless path to run. A lineage whose only
     * empress dies with no rival is exactly the case the crowning cooldown exists for, and skipping it would hand the
     * players nothing for the fight.
     * </p>
     */
    public boolean promoteNextEmpress() {
        if (pretenders.isEmpty()) {
            return false;
        }
        var heir = pretenders.remove(0);
        setEmpressId(heir.id());
        return true;
    }

    public long ageInTicks() {
        return ageInTicks;
    }

    public void incrementAge() {
        this.ageInTicks++;
    }

    public boolean pendingEmpressEmergence() {
        return pendingEmpressEmergence;
    }

    public void setPendingEmpressEmergence(boolean pendingEmpressEmergence) {
        this.pendingEmpressEmergence = pendingEmpressEmergence;
        markDirty();
    }

    /** The elected-but-not-yet-molted empress seat, or null. See the field javadoc. */
    public @Nullable HiveLocationId pendingEmpressSeatId() {
        return pendingEmpressSeatId;
    }

    public void setPendingEmpressSeatId(@Nullable HiveLocationId pendingEmpressSeatId) {
        this.pendingEmpressSeatId = pendingEmpressSeatId;
        markDirty();
    }

    /**
     * Locations that still COUNT as hives of this lineage - everything except exiled empress remnants.
     * <p>
     * A remnant is alive and fightable but the empire has written it off, so it must not satisfy the emergence
     * threshold, occupy a slot against the spread caps, or make a lone lineage look like it has a sister to evacuate
     * to. Use this anywhere a count drives a DECISION; raw locationsById() is still right for display.
     */
    public int activeLocationCount() {
        var count = 0;
        for (var location : locationsById().values()) {
            // !!! isAlive() WAS MISSING AND ONLY isExiled() WAS ASKED. A location keeps its entry in the map after it
            // has been marked dead, so a hive that decayed, lost its queen, or never finished founding still counted
            // as a hive of this lineage.
            //
            // [stated] "dead hives should not count towards making an empress. so if a location or hive dies before
            // an empress is naturally made it should be removed from the counting towards making her."
            //
            // WARNING: THIS IS THE FOUR-HIVE EMPRESS GATE, AND THAT IS HOW AN EMPRESS ARRIVED AT TWO. Reported as
            // "Got an empress from just 2 daughter queens" - two live hives plus leftovers reached the threshold.
            // The same count also gates spread caps, migration, reinforcement, war projection and the schism, and
            // counting corpses is wrong in every one of them, so the guard goes here rather than at the gate.
            if (location.isAlive() && !location.isExiled()) {
                count++;
            }
        }
        return count;
    }

    /** True once a second rescue has given her position away. See the field javadoc. */
    public boolean empressRevealed() {
        return empressRevealed;
    }

    public void setEmpressRevealed(boolean empressRevealed) {
        this.empressRevealed = empressRevealed;
        markDirty();
    }

    /** Game tick before which no new empress may be crowned. 0 = none. See the field javadoc. */
    public long empressCooldownUntilTick() {
        return empressCooldownUntilTick;
    }

    public void setEmpressCooldownUntilTick(long empressCooldownUntilTick) {
        this.empressCooldownUntilTick = empressCooldownUntilTick;
        markDirty();
    }

    public Map<HiveLocationId, HiveLocation> locationsById() {
        return locationsById;
    }

    /** Live mutable list of in-flight convoys. Callers should call {@link #markDirty()} after modifying. */
    public List<Convoy> convoys() {
        return convoys;
    }

    public Set<HiveLocationId> locationIds() {
        return locationsById.keySet();
    }

    public void addLocation(HiveLocation location) {
        locationsById.put(location.id(), location);
        markDirty();
    }

    public @Nullable HiveLocation removeLocation(HiveLocationId id) {
        var removed = locationsById.remove(id);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public Map<UUID, List<Long>> killAttributionByPlayer() {
        return killAttributionByPlayer;
    }

    /**
     * Records that {@code playerId} killed a member of this lineage at {@code currentTick}. Prunes timestamps outside
     * the raid aggro window so the list stays bounded.
     */
    public void recordKillByPlayer(UUID playerId, long currentTick, long aggroWindowTicks) {
        var timestamps = killAttributionByPlayer.computeIfAbsent(playerId, $ -> new ArrayList<>());
        if (timestamps.removeIf(t -> currentTick - t > aggroWindowTicks)) {
            markDirty();
        }
        timestamps.add(currentTick);
        lineageKillCreditPlayerId = playerId;
        markDirty();
    }

    public @Nullable UUID lineageKillCreditPlayerId() {
        return lineageKillCreditPlayerId;
    }

    public void recordLineageKillCredit(UUID playerId) {
        lineageKillCreditPlayerId = playerId;
        markDirty();
    }

    /** Count of recent kills by {@code playerId}, dropping entries older than {@code aggroWindowTicks}. */
    public int countRecentKills(UUID playerId, long currentTick, long aggroWindowTicks) {
        var timestamps = killAttributionByPlayer.get(playerId);
        if (timestamps == null) {
            return 0;
        }
        pruneKillTimestamps(playerId, timestamps, currentTick, aggroWindowTicks);
        var pruned = killAttributionByPlayer.get(playerId);
        return pruned == null ? 0 : pruned.size();
    }

    public void clearKillAttributionForPlayer(UUID playerId) {
        if (killAttributionByPlayer.remove(playerId) != null) {
            markDirty();
        }
    }

    private void pruneKillTimestamps(UUID playerId, List<Long> timestamps, long currentTick, long aggroWindowTicks) {
        var changed = timestamps.removeIf(t -> currentTick - t > aggroWindowTicks);
        if (!timestamps.isEmpty()) {
            if (changed) {
                markDirty();
            }
            return;
        }

        if (killAttributionByPlayer.remove(playerId) != null) {
            markDirty();
        }
    }

    public @Nullable LineageRemovalReason removalReason() {
        return removalReason;
    }

    public void setRemovalReason(@Nullable LineageRemovalReason removalReason) {
        this.removalReason = removalReason;
        markDirty();
    }

    public boolean isAlive() {
        return removalReason == null;
    }

    @Override
    public void load(CompoundTag tag) {
        if (tag.contains(NBT_VARIANT_ID)) {
            this.variant = AlienVariant.getById(tag.getByte(NBT_VARIANT_ID)).unwrapOr(DEFAULT_VARIANT);
        }

        if (tag.contains(NBT_FACTION_ID)) {
            this.factionId = ResourceLocation.parse(tag.getString(NBT_FACTION_ID));
        }

        if (tag.contains(NBT_PARENT_VARIANT_FACTION_ID)) {
            this.parentVariantFactionId = ResourceLocation.parse(tag.getString(NBT_PARENT_VARIANT_FACTION_ID));
        }

        if (tag.contains(NBT_DIMENSION)) {
            this.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(NBT_DIMENSION)));
        }

        if (tag.hasUUID(NBT_FOUNDER_ID)) {
            this.founderId = tag.getUUID(NBT_FOUNDER_ID);
            if (tag.contains(NBT_PARENT_LINEAGE_ID)) {
                this.parentLineageId = ResourceLocation.tryParse(tag.getString(NBT_PARENT_LINEAGE_ID));
                this.separationTick = tag.getLong(NBT_SEPARATION_TICK);
            }
        }

        // \u26a0 Order is REBUILT FROM THE STORED STAMPS, not trusted from list order, so a hand-edited or
        // partially-written save still yields a correct succession rather than a silently wrong one.
        pretenders.clear();
        var pretenderList = tag.getList(NBT_PRETENDERS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (var index = 0; index < pretenderList.size(); index++) {
            var entry = pretenderList.getCompound(index);
            if (entry.hasUUID(NBT_PRETENDER_ID)) {
                pretenders.add(
                    new Pretender(entry.getUUID(NBT_PRETENDER_ID), entry.getLong(NBT_PRETENDER_CROWNED))
                );
            }
        }
        pretenders.sort(java.util.Comparator.comparingLong(Pretender::crownedAtGameTime));
        this.schismGraceUntilTick = tag.getLong(NBT_SCHISM_GRACE_UNTIL);

        if (tag.hasUUID(NBT_EMPRESS_ID)) {
            this.empressId = tag.getUUID(NBT_EMPRESS_ID);
        }

        this.ageInTicks = tag.getLong(NBT_AGE_IN_TICKS);
        this.pendingEmpressEmergence = tag.getBoolean(NBT_PENDING_EMPRESS_EMERGENCE);

        if (tag.contains(NBT_PENDING_EMPRESS_SEAT)) {
            var seat = ResourceLocation.tryParse(tag.getString(NBT_PENDING_EMPRESS_SEAT));
            this.pendingEmpressSeatId = seat == null ? null : HiveLocationId.of(seat);
        } else {
            this.pendingEmpressSeatId = null;
        }

        this.empressCooldownUntilTick = tag.getLong(NBT_EMPRESS_COOLDOWN_UNTIL);
        this.empressRevealed = tag.getBoolean(NBT_EMPRESS_REVEALED);
        this.lineageNumber = tag.contains(NBT_LINEAGE_NUMBER) ? tag.getLong(NBT_LINEAGE_NUMBER) : -1L;
        this.nextLocationNumber = tag.getLong(NBT_NEXT_LOCATION_NUMBER);

        locationsById.clear();
        var removedMismatchedReserveEntries = 0;
        var convertedPendingFounderQueens = 0;
        if (tag.contains(NBT_LOCATIONS)) {
            var listTag = tag.getList(NBT_LOCATIONS, Tag.TAG_COMPOUND);
            for (var i = 0; i < listTag.size(); i++) {
                var locationTag = listTag.getCompound(i);
                var location = HiveLocation.load(locationTag);
                if (locationTag.getBoolean(LEGACY_NBT_PENDING_FOUNDER_QUEEN)) {
                    var queenType = Queen.getType(variant);
                    if (queenType != null) {
                        location.localReserves().tryAdd((EntityType<?>) queenType, 1);
                        convertedPendingFounderQueens++;
                    }
                }
                removedMismatchedReserveEntries += location.localReserves().removeVariantMismatches(variant);
                locationsById.put(location.id(), location);
            }
        }
        if (convertedPendingFounderQueens > 0) {
            markDirty();
        }
        if (removedMismatchedReserveEntries > 0) {
            Alien.LOGGER.warn(
                "Hive: removed {} variant-mismatched reserve entries while loading lineage {} (variant={})",
                removedMismatchedReserveEntries,
                factionId,
                variant
            );
            markDirty();
        }

        convoys.clear();
        if (tag.contains(NBT_CONVOYS)) {
            convoys.addAll(ConvoyCodec.loadAll(tag.getList(NBT_CONVOYS, Tag.TAG_COMPOUND)));
        }

        killAttributionByPlayer.clear();
        if (tag.contains(NBT_KILL_ATTRIBUTION_BY_PLAYER)) {
            var listTag = tag.getList(NBT_KILL_ATTRIBUTION_BY_PLAYER, Tag.TAG_COMPOUND);
            for (var i = 0; i < listTag.size(); i++) {
                var entry = listTag.getCompound(i);
                if (!entry.hasUUID("PlayerId")) {
                    continue;
                }
                var timestampsTag = entry.getList("Timestamps", Tag.TAG_LONG);
                if (timestampsTag.isEmpty()) {
                    continue;
                }
                var timestamps = new ArrayList<Long>();
                for (var j = 0; j < timestampsTag.size(); j++) {
                    if (timestampsTag.get(j) instanceof LongTag timestamp) {
                        timestamps.add(timestamp.getAsLong());
                    }
                }
                if (timestamps.isEmpty()) {
                    continue;
                }
                killAttributionByPlayer.put(entry.getUUID("PlayerId"), timestamps);
            }
        }

        lineageKillCreditPlayerId = tag.hasUUID(NBT_LINEAGE_KILL_CREDIT_PLAYER_ID)
            ? tag.getUUID(NBT_LINEAGE_KILL_CREDIT_PLAYER_ID)
            : null;

        if (tag.contains(NBT_REMOVAL_REASON)) {
            this.removalReason = LineageRemovalReason.load(tag.getCompound(NBT_REMOVAL_REASON));
        }
    }

    @Override
    public void save(CompoundTag tag) {
        tag.putByte(NBT_VARIANT_ID, (byte) variant.getId());

        if (factionId != null) {
            tag.putString(NBT_FACTION_ID, factionId.toString());
        }

        if (parentVariantFactionId != null) {
            tag.putString(NBT_PARENT_VARIANT_FACTION_ID, parentVariantFactionId.toString());
        }

        tag.putString(NBT_DIMENSION, dimension.location().toString());

        if (founderId != null) {
            tag.putUUID(NBT_FOUNDER_ID, founderId);
        }
        if (parentLineageId != null) {
            tag.putString(NBT_PARENT_LINEAGE_ID, parentLineageId.toString());
            tag.putLong(NBT_SEPARATION_TICK, separationTick);
        }

        if (empressId != null) {
            tag.putUUID(NBT_EMPRESS_ID, empressId);
        }
        if (!pretenders.isEmpty()) {
            var pretenderList = new net.minecraft.nbt.ListTag();
            for (var pretender : pretenders) {
                var entry = new net.minecraft.nbt.CompoundTag();
                entry.putUUID(NBT_PRETENDER_ID, pretender.id());
                entry.putLong(NBT_PRETENDER_CROWNED, pretender.crownedAtGameTime());
                pretenderList.add(entry);
            }
            tag.put(NBT_PRETENDERS, pretenderList);
        }
        if (schismGraceUntilTick > 0L) {
            tag.putLong(NBT_SCHISM_GRACE_UNTIL, schismGraceUntilTick);
        }

        tag.putLong(NBT_AGE_IN_TICKS, ageInTicks);
        tag.putBoolean(NBT_PENDING_EMPRESS_EMERGENCE, pendingEmpressEmergence);
        if (pendingEmpressSeatId != null) {
            tag.putString(NBT_PENDING_EMPRESS_SEAT, pendingEmpressSeatId.value().toString());
        }
        tag.putLong(NBT_EMPRESS_COOLDOWN_UNTIL, empressCooldownUntilTick);
        if (empressRevealed) {
            tag.putBoolean(NBT_EMPRESS_REVEALED, true);
        }
        if (lineageNumber >= 0) {
            tag.putLong(NBT_LINEAGE_NUMBER, lineageNumber);
        }
        tag.putLong(NBT_NEXT_LOCATION_NUMBER, nextLocationNumber);

        var locationsTag = new ListTag();
        for (var location : locationsById.values()) {
            locationsTag.add(location.save());
        }
        tag.put(NBT_LOCATIONS, locationsTag);

        tag.put(NBT_CONVOYS, ConvoyCodec.saveAll(convoys));

        if (!killAttributionByPlayer.isEmpty()) {
            var listTag = new ListTag();
            for (var entry : killAttributionByPlayer.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                var entryTag = new CompoundTag();
                entryTag.putUUID("PlayerId", entry.getKey());
                var timestampsTag = new ListTag();
                for (var timestamp : entry.getValue()) {
                    timestampsTag.add(LongTag.valueOf(timestamp));
                }
                entryTag.put("Timestamps", timestampsTag);
                listTag.add(entryTag);
            }
            tag.put(NBT_KILL_ATTRIBUTION_BY_PLAYER, listTag);
        }

        if (lineageKillCreditPlayerId != null) {
            tag.putUUID(NBT_LINEAGE_KILL_CREDIT_PLAYER_ID, lineageKillCreditPlayerId);
        }

        if (removalReason != null) {
            tag.put(NBT_REMOVAL_REASON, LineageRemovalReason.save(removalReason));
        }
    }
}
