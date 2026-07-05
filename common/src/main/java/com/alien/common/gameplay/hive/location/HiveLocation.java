package com.alien.common.gameplay.hive.location;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.alien.common.gameplay.hive.structure.FrontierSocket;
import com.alien.common.gameplay.hive.structure.HiveStructureRole;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A fixed-spot hive settlement owned by exactly one lineage faction. The canonical anchor for territory, reserves,
 * leadership, and a boss bar.
 * <p>
 * Per the redesign, {@link #centerPos} is set at founding and never moves. Records of this type live nested inside
 * their owning {@link LineageFactionData}'s NBT — the lineage shard is the authoritative on-disk home. The
 * {@link HiveLocationRegistry}'s indexes are rebuilt from that NBT at server start (see
 * {@code HIVE_REDESIGN_12_PERFORMANCE.md} § 3).
 * <p>
 * Phase 3 wires three behavioral managers behind the data: {@link HiveLocationLeadership} (per-territory leader pick),
 * {@link HiveLocationBossBar} (distance-visible per-location bar), and {@link HiveLocationReserves} (cap-aware spawn
 * budget). The boss bar is constructed lazily on first {@link #tick} call because it needs the lineage's variant.
 */
public final class HiveLocation {

    /**
     * Vertical extent of the hive's active "slab", measured upward from {@link #centerPos}'s Y. The hive's structures
     * are 16 blocks tall, so the slab spans [floorY, floorY + SLAB_HEIGHT). Spawning and resin spread are confined to
     * this band so players are not swarmed when far above or below the hive even while standing in a claimed chunk.
     * <p>
     * TODO(phase1-config): move these to {@code HiveConfig} once config persistence lands so they are tunable in-game.
     * Hardcoded for now.
     */
    private static final int SLAB_HEIGHT = 16;

    /**
     * Extra blocks of leeway added above and below the slab when testing whether a Y is "inside" the hive. A small
     * tolerance avoids edge cases where a spawn or resin target one block outside the structure shell is wrongly
     * rejected.
     */
    private static final int SLAB_TOLERANCE = 2;

    private static final String NBT_ID = "Id";

    private static final String NBT_LINEAGE_FACTION_ID = "LineageFactionId";

    private static final String NBT_DIMENSION = "Dimension";

    private static final String NBT_CENTER_POS = "CenterPos";

    private static final String NBT_FOUNDER_ID = "FounderId";

    private static final String NBT_AGE_IN_TICKS = "AgeInTicks";

    private static final String NBT_LAST_GROWTH_TICK = "LastGrowthTick";

    private static final String NBT_LAST_PASSIVE_CLAIM_TICK = "LastPassiveClaimTick";

    private static final String NBT_LAST_ABSTRACT_SPREAD_TICK = "LastAbstractSpreadTick";

    private static final String NBT_LAST_ABSTRACT_SPREAD_ATTEMPT = "LastAbstractSpreadAttempt";

    private static final String NBT_PEAK_XENOMORPH_COUNT = "PeakXenomorphCount";

    private static final String NBT_PEAK_DECAY_ELAPSED = "PeakDecayElapsedTicks";

    private static final String NBT_EVACUATING_REMAINING = "EvacuatingRemainingTicks";

    private static final String NBT_NO_CONTACT_TICKS_ACCRUED = "NoContactTicksAccrued";

    private static final String NBT_COMBAT_RESPITE_REMAINING_TICKS = "CombatRespiteRemainingTicks";

    private static final String NBT_COMBAT_KILLS_SINCE_LAST_RESPITE = "CombatKillsSinceLastRespite";

    private static final String NBT_LOCATION_NUMBER = "LocationNumber";

    private static final String NBT_QUEENLESS_MATURATION_LAST_ADVANCE = "QueenlessMaturationLastAdvanceTick";

    private static final String NBT_QUEENLESS_LEADER_SNAPSHOT = "QueenlessLeaderSnapshot";

    private static final String NBT_FIREWALL_FUND_AVAILABLE = "FirewallFundAvailable";

    private static final String NBT_FIREWALL_STABLE_ACCRUED_TICKS = "FirewallStableAccruedTicks";

    private static final String NBT_FIREWALL_BIOMASS_SAMPLE_TICK = "FirewallBiomassSampleTick";

    private static final String NBT_FIREWALL_BIOMASS_SAMPLE_VALUE = "FirewallBiomassSampleValue";

    private static final String NBT_BIOMASS = "Biomass";

    private static final String NBT_ROYAL_JELLY = "RoyalJelly";

    private static final String NBT_SCOURGE_JELLY = "ScourgeJelly";

    private static final String NBT_ROYAL_JELLY_ACCUMULATOR = "RoyalJellyAccumulator";

    private static final String NBT_QUEEN_SCOURGE_ACCUMULATOR = "QueenScourgeAccumulator";

    private static final String NBT_HARBINGER_SCOURGE_ACCUMULATOR = "HarbingerScourgeAccumulator";

    private static final String NBT_REPRODUCTIVE_ESTABLISHED = "ReproductiveEstablished";

    private static final String NBT_INHIBITED = "Inhibited";

    private static final String NBT_CLAIMED_CHUNKS = "ClaimedChunks";

    private static final String NBT_CHUNK_CLAIM_TICKS = "ChunkClaimTicks";

    private static final String NBT_DECORATED_CHUNKS = "DecoratedChunks";

    // Phase 2 structure scaffolding (additive state). Mirrors the claimed/decorated chunk serialization pattern.
    private static final String NBT_STRUCTURE_ROLES = "StructureRoles";

    private static final String NBT_STRUCTURE_PIECES = "StructurePieces";

    private static final String NBT_FRONTIER_SOCKETS = "FrontierSockets";

    private static final String NBT_LOCAL_RESERVES = "LocalReserves";

    private static final String NBT_PARTIES = "Parties";

    private static final String NBT_ATTACK_CAMPAIGNS = "AttackCampaigns";

    private static final String NBT_GRUDGE_PLAYER_ID = "GrudgePlayerId";

    private static final String NBT_RESCUE_CAMPAIGN = "RescueCampaign";

    private static final String NBT_KNOWN_MEMBERS_BY_TYPE = "KnownMembersByType";

    private static final String NBT_LEADERSHIP = "Leadership";

    private static final String NBT_REMOVAL_REASON = "RemovalReason";

    private final HiveLocationId id;

    private ResourceLocation lineageFactionId;

    private ResourceKey<Level> dimension;

    private BlockPos centerPos;

    private @Nullable UUID founderId;

    private long ageInTicks;

    private long lastGrowthTick;

    private long lastPassiveClaimTick;

    private long lastAbstractSpreadTick;

    private AbstractSpreadAttemptDebug lastAbstractSpreadAttempt;

    /**
     * Decays at 1/min during normal ticking; floored at 1 to avoid the NaN-divide bug from
     * {@code HIVE_SYSTEM_ANALYSIS.md} § 9.1.4.
     */
    private int peakXenomorphCount;

    private long peakDecayElapsedTicks;

    private long evacuatingRemainingTicks;

    /**
     * Accrued ticks during which this location has had no loaded location-faction member in any of its claimed chunks
     * while at least one claimed chunk was loaded. Drives the no-contact safety-net kill in
     * {@link com.alien.common.gameplay.hive.lifecycle.LocationDormancyTask}: when this exceeds
     * {@code config.locationMaxNoContactTicks()} the location dies.
     * <p>
     * Pauses (neither advances nor resets) when no claimed chunk is loaded; resets to 0 when a member is observed
     * inside the territory. Persisted across restarts.
     */
    private long noContactTicksAccrued;

    /**
     * Whether the royal-replacement "firewall" fund is currently available to cover a queen crowning in
     * {@link com.alien.common.gameplay.hive.lifecycle.QueenlessMaturationTask}. Starts {@code true} (a fresh location
     * always covers its first queen loss). Flips to {@code false} the moment a queen is successfully crowned through
     * that pathway, and back to {@code true} once {@link #firewallStableAccruedTicks} reaches
     * {@code config.firewallCooldownTicks()}. While {@code false}, the final molt into a queen is denied — the
     * location stays queenless (and, if attrition continues, eventually dies via the existing
     * {@link com.alien.common.gameplay.hive.lifecycle.LocationDormancyTask} population/no-contact rules — no separate
     * "permanently dead" mechanic is needed).
     */
    private boolean firewallFundAvailable;

    /**
     * Ticks accrued while this location meets all four stability conditions (has an active ovipositor, jelly reserves
     * above {@code config.firewallJellyFloor()}, a positive biomass income rate, and population room to grow) since
     * the fund was last spent. Reaching {@code config.firewallCooldownTicks()} refills the fund. Pauses (holds
     * progress, does not reset) whenever any condition is unmet — sustained pressure denies refill without erasing
     * prior progress the moment stability briefly returns.
     */
    private long firewallStableAccruedTicks;

    /** Game tick of the last biomass sample taken for the firewall's income-rate check. {@code Long.MIN_VALUE} = none yet. */
    private long firewallBiomassSampleTick;

    /** Biomass balance at {@link #firewallBiomassSampleTick}, used to derive the income rate on the next sample. */
    private int firewallBiomassSampleValue;

    private long combatRespiteRemainingTicks;

    private int combatKillsSinceLastRespite;

    /**
     * Per-lineage index assigned at mint, used in {@link com.alien.common.gameplay.hive.faction.FactionNaming}. -1 =
     * unassigned.
     */
    private long locationNumber;

    /**
     * Tick at which {@link com.alien.common.gameplay.hive.lifecycle.QueenlessMaturationTask} last advanced this
     * location's leader through a growth stage. {@link Long#MIN_VALUE} means "no advance yet." Combined with
     * {@link #queenlessLeaderSnapshot}, lets the maturation task detect leader changes (i.e., when an alien finishes
     * cocooning into its next form, the new entity has a fresh UUID — we reset the timer).
     */
    private long queenlessMaturationLastAdvanceTick;

    /** UUID of the alien observed as leader on the last queenless maturation advance. Persisted across restarts. */
    private @Nullable UUID queenlessLeaderSnapshot;

    private int biomass;

    private boolean reproductiveEstablished;

    /**
     * Wild → inhibited flag (Part 2). When true the location is a severed, contained breeder: all hive autonomy (claim
     * expansion, biomass economy, caste spawning, contests) is suppressed by the growth tasks. Her combat and
     * egg-laying are unaffected (those live off the entity / ovipositor path). Persisted.
     */
    private boolean inhibited;

    /** Refined resource produced by queens (1/min). Used by hive unit purchases to upgrade castes. */
    private int royalJelly;

    /** Rare resource produced by queens (1/100min) and harbingers (1/min). Powers high-tier caste purchases. */
    private int scourgeJelly;

    /**
     * Tick accumulator: incremented once per loaded queen per tick. At {@code royalJellyTicksPerProduction}, +1 royal.
     */
    private long royalJellyAccumulator;

    /**
     * Tick accumulator: incremented once per loaded queen per tick. At {@code scourgeJellyTicksPerQueenProduction}, +1
     * scourge.
     */
    private long queenScourgeAccumulator;

    /**
     * Tick accumulator: incremented once per loaded harbinger per tick. At
     * {@code scourgeJellyTicksPerHarbingerProduction}, +1 scourge.
     */
    private long harbingerScourgeAccumulator;

    private final Set<ChunkPos> claimedChunks;

    private final Map<ChunkPos, Long> chunkClaimTicks;

    private final Set<ChunkPos> decoratedChunks;

    // Phase 2 structure scaffolding (additive state). Per-chunk structure role + which template piece occupies it, plus
    // the set of open frontier doorways where the hive can expand. Populated by the founding hook (2.3) and the
    // planner/placer (Phase 3); empty for hives founded before this state existed (graceful default).
    private final Map<ChunkPos, HiveStructureRole> structureRoleByChunk;

    private final Map<ChunkPos, String> structurePieceByChunk;

    private final Set<FrontierSocket> frontierSockets;

    private final HiveLocationReserves localReserves;

    /** In-flight hive parties (recovery, revenge, surface spawn, etc.). Persisted via {@link com.alien.common.gameplay.hive.party.HivePartyCodec}. Hive-level — unlike {@code Convoy}, not empress-gated. */
    private final java.util.List<com.alien.common.gameplay.hive.party.HiveParty> parties;

    /** Per-player attack-retribution campaigns (territorial-intrusion 2-wave model). Keyed by player UUID. See {@link com.alien.common.gameplay.hive.party.AttackCampaign}. */
    private final java.util.Map<UUID, com.alien.common.gameplay.hive.party.AttackCampaign> attackCampaigns;

    /** Post-replacement grudge: set when this location's founder queen is killed by a player; the crowned successor prioritizes raiding this player on her first raid, then it clears. Null = no grudge. */
    private @Nullable UUID grudgePlayerId;

    /** Recovery campaign for this location's lost (captured) founder queen. Null = no queen currently lost. See {@link com.alien.common.gameplay.hive.party.RescueCampaign}. */
    private @Nullable com.alien.common.gameplay.hive.party.RescueCampaign rescueCampaign;

    private final HiveLocationLeadership leadership;

    private final com.alien.common.gameplay.hive.vent.HiveVentManager ventManager;

    private final Map<EntityType<?>, Set<UUID>> knownMembersByType;

    private final Map<EntityType<?>, Set<UUID>> loadedMembersByType;

    private @Nullable HiveLocationBossBar bossBar;

    private @Nullable HiveLocationRemovalReason removalReason;

    public HiveLocation(
            HiveLocationId id,
            ResourceLocation lineageFactionId,
            ResourceKey<Level> dimension,
            BlockPos centerPos,
            @Nullable UUID founderId
    ) {
        this(id);
        this.lineageFactionId = lineageFactionId;
        this.dimension = dimension;
        this.centerPos = centerPos;
        this.founderId = founderId;
    }

    private HiveLocation(HiveLocationId id) {
        this.id = id;
        this.lineageFactionId = ResourceLocation.fromNamespaceAndPath(Alien.MOD_ID, "lineage/unbound");
        this.dimension = Level.OVERWORLD;
        this.centerPos = BlockPos.ZERO;
        this.founderId = null;
        this.ageInTicks = 0L;
        this.lastGrowthTick = 0L;
        this.lastPassiveClaimTick = 0L;
        this.lastAbstractSpreadTick = 0L;
        this.lastAbstractSpreadAttempt = AbstractSpreadAttemptDebug.none();
        this.peakXenomorphCount = 1;
        this.peakDecayElapsedTicks = 0L;
        this.evacuatingRemainingTicks = 0L;
        this.noContactTicksAccrued = 0L;
        this.firewallFundAvailable = true;
        this.firewallStableAccruedTicks = 0L;
        this.firewallBiomassSampleTick = Long.MIN_VALUE;
        this.firewallBiomassSampleValue = 0;
        this.combatRespiteRemainingTicks = 0L;
        this.combatKillsSinceLastRespite = 0;
        this.locationNumber = -1L;
        this.queenlessMaturationLastAdvanceTick = Long.MIN_VALUE;
        this.queenlessLeaderSnapshot = null;
        this.biomass = 0;
        this.royalJelly = 0;
        this.scourgeJelly = 0;
        this.royalJellyAccumulator = 0L;
        this.queenScourgeAccumulator = 0L;
        this.harbingerScourgeAccumulator = 0L;
        this.claimedChunks = new LinkedHashSet<>();
        this.chunkClaimTicks = new HashMap<>();
        this.decoratedChunks = new HashSet<>();
        this.structureRoleByChunk = new HashMap<>();
        this.structurePieceByChunk = new HashMap<>();
        this.frontierSockets = new LinkedHashSet<>();
        this.localReserves = new HiveLocationReserves(this::lineageVariantOrNull);
        this.parties = new java.util.ArrayList<>();
        this.attackCampaigns = new java.util.HashMap<>();
        this.grudgePlayerId = null;
        this.rescueCampaign = null;
        this.leadership = new HiveLocationLeadership();
        this.ventManager = new com.alien.common.gameplay.hive.vent.HiveVentManager();
        this.knownMembersByType = new HashMap<>();
        this.loadedMembersByType = new HashMap<>();
        this.bossBar = null;
        this.removalReason = null;
    }

    public HiveLocationId id() {
        return id;
    }

    public ResourceLocation lineageFactionId() {
        return lineageFactionId;
    }

    public @Nullable AlienVariant lineageVariantOrNull() {
        var faction = Alien.MOD.factions().get(lineageFactionId);
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return null;
        }
        return lineage.variant();
    }

    public void setLineageFactionId(ResourceLocation lineageFactionId) {
        this.lineageFactionId = lineageFactionId;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos centerPos() {
        return centerPos;
    }

    /**
     * Relocates this location's anchor. <b>Founded hives never call this</b> — their center is fixed at founding
     * because their structures and slab band are anchored to it (see the class doc). It exists solely for an inhibited,
     * single-chunk location whose claim follows its queen as she roams: she has no structures or slab to invalidate, so
     * the anchor can chase her current chunk. The inhibitor follow-chunk migrator is the only caller.
     */
    public void setCenterPos(BlockPos centerPos) {
        this.centerPos = centerPos;
    }

    /**
     * Y of the hive's floor — the elevation the hive was founded at. The slab band is measured from here.
     */
    public int hiveFloorY() {
        return centerPos.getY();
    }

    /**
     * Y of the top of the hive's slab band (exclusive). Floor + {@link #SLAB_HEIGHT}.
     */
    public int hiveCeilingY() {
        return centerPos.getY() + SLAB_HEIGHT;
    }

    /**
     * Whether the given world Y falls inside this hive's active slab band (with a small tolerance above and below).
     * Spawning and resin spread should be confined to Ys for which this returns {@code true}, so the hive only operates
     * at its built level rather than throughout the entire claimed chunk column.
     *
     * @param y a world Y coordinate
     * @return true if {@code y} is within [floorY - tolerance, ceilingY + tolerance)
     */
    public boolean withinSlab(int y) {
        return y >= hiveFloorY() - SLAB_TOLERANCE
                && y < hiveCeilingY() + SLAB_TOLERANCE;
    }

    public @Nullable UUID founderId() {
        return founderId;
    }

    public void setFounderId(@Nullable UUID founderId) {
        this.founderId = founderId;
    }

    public long ageInTicks() {
        return ageInTicks;
    }

    public void incrementAge() {
        this.ageInTicks++;
    }

    public long lastGrowthTick() {
        return lastGrowthTick;
    }

    public void setLastGrowthTick(long lastGrowthTick) {
        this.lastGrowthTick = lastGrowthTick;
    }

    public long lastPassiveClaimTick() {
        return lastPassiveClaimTick;
    }

    public void setLastPassiveClaimTick(long lastPassiveClaimTick) {
        this.lastPassiveClaimTick = Math.max(0L, lastPassiveClaimTick);
    }

    public long lastAbstractSpreadTick() {
        return lastAbstractSpreadTick;
    }

    public void setLastAbstractSpreadTick(long lastAbstractSpreadTick) {
        this.lastAbstractSpreadTick = Math.max(0L, lastAbstractSpreadTick);
    }

    public AbstractSpreadAttemptDebug lastAbstractSpreadAttempt() {
        return lastAbstractSpreadAttempt;
    }

    public void recordAbstractSpreadAttempt(
            long tick,
            String result,
            @Nullable ChunkPos candidateChunk,
            @Nullable HiveLocationId createdLocationId,
            String detail
    ) {
        this.lastAbstractSpreadAttempt = new AbstractSpreadAttemptDebug(tick, result, candidateChunk, createdLocationId, detail);
    }

    public int peakXenomorphCount() {
        return peakXenomorphCount;
    }

    public void setPeakXenomorphCount(int peakXenomorphCount) {
        this.peakXenomorphCount = Math.max(1, peakXenomorphCount);
    }

    public long peakDecayElapsedTicks() {
        return peakDecayElapsedTicks;
    }

    public void setPeakDecayElapsedTicks(long peakDecayElapsedTicks) {
        this.peakDecayElapsedTicks = Math.max(0L, peakDecayElapsedTicks);
    }

    public long evacuatingRemainingTicks() {
        return evacuatingRemainingTicks;
    }

    public void setEvacuatingRemainingTicks(long evacuatingRemainingTicks) {
        this.evacuatingRemainingTicks = Math.max(0L, evacuatingRemainingTicks);
    }

    public long noContactTicksAccrued() {
        return noContactTicksAccrued;
    }

    public void setNoContactTicksAccrued(long noContactTicksAccrued) {
        this.noContactTicksAccrued = Math.max(0L, noContactTicksAccrued);
    }

    public boolean firewallFundAvailable() {
        return firewallFundAvailable;
    }

    public void setFirewallFundAvailable(boolean firewallFundAvailable) {
        this.firewallFundAvailable = firewallFundAvailable;
    }

    public long firewallStableAccruedTicks() {
        return firewallStableAccruedTicks;
    }

    public void setFirewallStableAccruedTicks(long firewallStableAccruedTicks) {
        this.firewallStableAccruedTicks = Math.max(0L, firewallStableAccruedTicks);
    }

    public long firewallBiomassSampleTick() {
        return firewallBiomassSampleTick;
    }

    public void setFirewallBiomassSampleTick(long firewallBiomassSampleTick) {
        this.firewallBiomassSampleTick = firewallBiomassSampleTick;
    }

    public int firewallBiomassSampleValue() {
        return firewallBiomassSampleValue;
    }

    public void setFirewallBiomassSampleValue(int firewallBiomassSampleValue) {
        this.firewallBiomassSampleValue = firewallBiomassSampleValue;
    }

    public long combatRespiteRemainingTicks() {
        return combatRespiteRemainingTicks;
    }

    public boolean isInCombatRespite() {
        return combatRespiteRemainingTicks > 0L;
    }

    public int combatKillsSinceLastRespite() {
        return combatKillsSinceLastRespite;
    }

    public void recordCombatKill(BlockPos playerPos, HiveConfig config) {
        if (isInCombatRespite() || config.combatRespiteKillThreshold() <= 0) {
            return;
        }

        combatKillsSinceLastRespite = Math.max(0, combatKillsSinceLastRespite) + 1;
        if (combatKillsSinceLastRespite < config.combatRespiteKillThreshold()) {
            return;
        }

        combatKillsSinceLastRespite = 0;
        combatRespiteRemainingTicks = respiteDurationTicks(playerPos, config);
    }

    private long respiteDurationTicks(BlockPos playerPos, HiveConfig config) {
        var min = Math.max(0L, config.combatRespiteMinTicks());
        var max = Math.max(min, config.combatRespiteMaxTicks());
        if (max <= min) {
            return min;
        }

        var centerChunkRadiusBlocks = 16.0;
        var outerRadiusBlocks = Math.max(centerChunkRadiusBlocks + 1.0, config.bossBarDisplayRadiusBlocks());
        var distance = Math.sqrt(playerPos.distSqr(centerPos));
        var normalized = (distance - centerChunkRadiusBlocks) / (outerRadiusBlocks - centerChunkRadiusBlocks);
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        return Math.round(min + (max - min) * normalized);
    }

    public long locationNumber() {
        return locationNumber;
    }

    public void setLocationNumber(long locationNumber) {
        this.locationNumber = locationNumber;
    }

    public long queenlessMaturationLastAdvanceTick() {
        return queenlessMaturationLastAdvanceTick;
    }

    public void setQueenlessMaturationLastAdvanceTick(long tick) {
        this.queenlessMaturationLastAdvanceTick = tick;
    }

    public @Nullable UUID queenlessLeaderSnapshot() {
        return queenlessLeaderSnapshot;
    }

    public void setQueenlessLeaderSnapshot(@Nullable UUID uuid) {
        this.queenlessLeaderSnapshot = uuid;
    }

    public int biomass() {
        return biomass;
    }

    /**
     * Whether this location's founding queen has become reproductive (created her ovipositor / begun laying). Until
     * this is true, the hive should not spend biomass on expansion (chunk claims) so it can accumulate the ovipositor
     * cost instead of bankrupting itself growing. Set once, persists. See the founding-priority design.
     */
    public boolean reproductiveEstablished() {
        return reproductiveEstablished;
    }

    public void setReproductiveEstablished(boolean value) {
        this.reproductiveEstablished = value;
    }

    /** Whether this location is an inhibited (severed, contained-breeder) claim with all hive autonomy suppressed. */
    public boolean isInhibited() {
        return inhibited;
    }

    public void setInhibited(boolean value) {
        this.inhibited = value;
    }

    public void setBiomass(int biomass) {
        this.biomass = Math.max(0, biomass);
    }

    public int royalJelly() {
        return royalJelly;
    }

    public void setRoyalJelly(int royalJelly) {
        this.royalJelly = Math.max(0, royalJelly);
    }

    public int scourgeJelly() {
        return scourgeJelly;
    }

    public void setScourgeJelly(int scourgeJelly) {
        this.scourgeJelly = Math.max(0, scourgeJelly);
    }

    public long royalJellyAccumulator() {
        return royalJellyAccumulator;
    }

    public void setRoyalJellyAccumulator(long value) {
        this.royalJellyAccumulator = Math.max(0L, value);
    }

    public long queenScourgeAccumulator() {
        return queenScourgeAccumulator;
    }

    public void setQueenScourgeAccumulator(long value) {
        this.queenScourgeAccumulator = Math.max(0L, value);
    }

    public long harbingerScourgeAccumulator() {
        return harbingerScourgeAccumulator;
    }

    public void setHarbingerScourgeAccumulator(long value) {
        this.harbingerScourgeAccumulator = Math.max(0L, value);
    }

    public Set<ChunkPos> claimedChunks() {
        return claimedChunks;
    }

    public Map<ChunkPos, Long> chunkClaimTicks() {
        return chunkClaimTicks;
    }

    public Set<ChunkPos> decoratedChunks() {
        return decoratedChunks;
    }

    // --- Phase 2 structure scaffolding accessors (additive; no behavior yet) ---

    public Map<ChunkPos, HiveStructureRole> structureRoleByChunk() {
        return structureRoleByChunk;
    }

    public Map<ChunkPos, String> structurePieceByChunk() {
        return structurePieceByChunk;
    }

    public Set<FrontierSocket> frontierSockets() {
        return frontierSockets;
    }

    /**
     * Role of a chunk, or {@link HiveStructureRole#UNASSIGNED} if no role has been assigned (e.g. a plain claimed chunk,
     * or a hive founded before structure state existed).
     */
    public HiveStructureRole structureRole(ChunkPos chunk) {
        return structureRoleByChunk.getOrDefault(chunk, HiveStructureRole.UNASSIGNED);
    }

    /**
     * Assigns a chunk's structure role and the template piece occupying it. Pass a null/blank pieceId to record only the
     * role (e.g. a part chunk whose piece is tracked on the center).
     */
    public void assignStructure(ChunkPos chunk, HiveStructureRole role, @Nullable String pieceId) {
        structureRoleByChunk.put(chunk, role);
        if (pieceId != null && !pieceId.isBlank()) {
            structurePieceByChunk.put(chunk, pieceId);
        }
    }

    public HiveLocationReserves localReserves() {
        return localReserves;
    }

    /** Live mutable list of in-flight parties. Callers should treat this as append/remove-and-save, same as {@code Convoy#convoys}. */
    public java.util.List<com.alien.common.gameplay.hive.party.HiveParty> parties() {
        return parties;
    }

    /** Live mutable map of per-player attack-retribution campaigns. */
    public java.util.Map<UUID, com.alien.common.gameplay.hive.party.AttackCampaign> attackCampaigns() {
        return attackCampaigns;
    }

    public @Nullable UUID grudgePlayerId() {
        return grudgePlayerId;
    }

    public void setGrudgePlayerId(@Nullable UUID grudgePlayerId) {
        this.grudgePlayerId = grudgePlayerId;
    }

    public @Nullable com.alien.common.gameplay.hive.party.RescueCampaign rescueCampaign() {
        return rescueCampaign;
    }

    public void setRescueCampaign(@Nullable com.alien.common.gameplay.hive.party.RescueCampaign rescueCampaign) {
        this.rescueCampaign = rescueCampaign;
    }

    public HiveLocationLeadership leadership() {
        return leadership;
    }

    public com.alien.common.gameplay.hive.vent.HiveVentManager ventManager() {
        return ventManager;
    }

    public Map<EntityType<?>, Set<UUID>> knownMembersByType() {
        return knownMembersByType;
    }

    public Map<EntityType<?>, Set<UUID>> loadedMembersByType() {
        return loadedMembersByType;
    }

    /**
     * Returns the boss bar if one has been constructed (only after the first {@link #tick}). Null is OK and meaningful:
     * a location that has never ticked yet has no live bar.
     */
    public @Nullable HiveLocationBossBar bossBar() {
        return bossBar;
    }

    public @Nullable HiveLocationRemovalReason removalReason() {
        return removalReason;
    }

    public void setRemovalReason(@Nullable HiveLocationRemovalReason removalReason) {
        this.removalReason = removalReason;
    }

    public boolean isAlive() {
        return removalReason == null;
    }

    /**
     * Per-server-tick driver. Looks up the owning lineage, lazy-inits the boss bar on first call, then drives leader
     * pick → boss bar update → reserve top-up. Phase 3 stops here; later phases append biomass income, claim attempts,
     * convoy interactions, etc.
     */
    public void tick(MinecraftServer server, LineageFactionData lineage) {
        decayCombatRespite();
        leadership.pickBestLeader(loadedMembersByType);

        if (bossBar == null) {
            bossBar = new HiveLocationBossBar(this, lineage.variant(), () -> HiveLocationRegistry.INSTANCE.config());
        }

        bossBar.tick(server, lineage.variant(), lineage);

        // Phase 3 leaves reserve top-up empty here. Phase 4 will hook the
        // periodic outer-edge top-up from HIVE_REDESIGN_05_RESERVES.md § 3 row 3.
    }

    private void decayCombatRespite() {
        if (combatRespiteRemainingTicks > 0L) {
            combatRespiteRemainingTicks--;
        }
    }

    /** Called by the registry when this location is unregistered (lineage absorbed or location death). */
    public void onUnregistered() {
        if (bossBar != null) {
            bossBar.onRemoved();
            bossBar = null;
        }
    }

    public CompoundTag save() {
        var tag = new CompoundTag();

        tag.putString(NBT_ID, id.value().toString());
        tag.putString(NBT_LINEAGE_FACTION_ID, lineageFactionId.toString());
        tag.putString(NBT_DIMENSION, dimension.location().toString());

        var centerComponents = new int[] { centerPos.getX(), centerPos.getY(), centerPos.getZ() };
        tag.putIntArray(NBT_CENTER_POS, centerComponents);

        if (founderId != null) {
            tag.putUUID(NBT_FOUNDER_ID, founderId);
        }

        tag.putLong(NBT_AGE_IN_TICKS, ageInTicks);
        tag.putLong(NBT_LAST_GROWTH_TICK, lastGrowthTick);
        if (lastPassiveClaimTick > 0L) {
            tag.putLong(NBT_LAST_PASSIVE_CLAIM_TICK, lastPassiveClaimTick);
        }
        if (lastAbstractSpreadTick > 0L) {
            tag.putLong(NBT_LAST_ABSTRACT_SPREAD_TICK, lastAbstractSpreadTick);
        }
        if (lastAbstractSpreadAttempt.tick() >= 0L) {
            tag.put(NBT_LAST_ABSTRACT_SPREAD_ATTEMPT, lastAbstractSpreadAttempt.save());
        }
        tag.putInt(NBT_PEAK_XENOMORPH_COUNT, peakXenomorphCount);
        tag.putLong(NBT_PEAK_DECAY_ELAPSED, peakDecayElapsedTicks);
        tag.putLong(NBT_EVACUATING_REMAINING, evacuatingRemainingTicks);
        if (noContactTicksAccrued > 0L) {
            tag.putLong(NBT_NO_CONTACT_TICKS_ACCRUED, noContactTicksAccrued);
        }
        // Only persist when it deviates from the fresh-location default (available, nothing accrued, no sample yet) —
        // keeps untouched locations' NBT unchanged, matching the sibling fields' save-if-nonzero convention.
        if (!firewallFundAvailable) {
            tag.putBoolean(NBT_FIREWALL_FUND_AVAILABLE, false);
        }
        if (firewallStableAccruedTicks > 0L) {
            tag.putLong(NBT_FIREWALL_STABLE_ACCRUED_TICKS, firewallStableAccruedTicks);
        }
        if (firewallBiomassSampleTick != Long.MIN_VALUE) {
            tag.putLong(NBT_FIREWALL_BIOMASS_SAMPLE_TICK, firewallBiomassSampleTick);
            tag.putInt(NBT_FIREWALL_BIOMASS_SAMPLE_VALUE, firewallBiomassSampleValue);
        }
        if (combatRespiteRemainingTicks > 0L) {
            tag.putLong(NBT_COMBAT_RESPITE_REMAINING_TICKS, combatRespiteRemainingTicks);
        }
        if (combatKillsSinceLastRespite > 0) {
            tag.putInt(NBT_COMBAT_KILLS_SINCE_LAST_RESPITE, combatKillsSinceLastRespite);
        }
        if (locationNumber >= 0) {
            tag.putLong(NBT_LOCATION_NUMBER, locationNumber);
        }
        if (queenlessMaturationLastAdvanceTick != Long.MIN_VALUE) {
            tag.putLong(NBT_QUEENLESS_MATURATION_LAST_ADVANCE, queenlessMaturationLastAdvanceTick);
        }
        if (queenlessLeaderSnapshot != null) {
            tag.putUUID(NBT_QUEENLESS_LEADER_SNAPSHOT, queenlessLeaderSnapshot);
        }
        tag.putInt(NBT_BIOMASS, biomass);
        tag.putBoolean(NBT_REPRODUCTIVE_ESTABLISHED, reproductiveEstablished);
        tag.putBoolean(NBT_INHIBITED, inhibited);
        if (royalJelly > 0) {
            tag.putInt(NBT_ROYAL_JELLY, royalJelly);
        }
        if (scourgeJelly > 0) {
            tag.putInt(NBT_SCOURGE_JELLY, scourgeJelly);
        }
        if (royalJellyAccumulator > 0L) {
            tag.putLong(NBT_ROYAL_JELLY_ACCUMULATOR, royalJellyAccumulator);
        }
        if (queenScourgeAccumulator > 0L) {
            tag.putLong(NBT_QUEEN_SCOURGE_ACCUMULATOR, queenScourgeAccumulator);
        }
        if (harbingerScourgeAccumulator > 0L) {
            tag.putLong(NBT_HARBINGER_SCOURGE_ACCUMULATOR, harbingerScourgeAccumulator);
        }

        var claimedTag = new ListTag();
        var claimTicksTag = new ListTag();

        for (var chunk : claimedChunks) {
            var chunkTag = new CompoundTag();
            chunkTag.putInt("X", chunk.x);
            chunkTag.putInt("Z", chunk.z);
            claimedTag.add(chunkTag);

            var claimedAt = chunkClaimTicks.getOrDefault(chunk, 0L);
            var ticksTag = new CompoundTag();
            ticksTag.putInt("X", chunk.x);
            ticksTag.putInt("Z", chunk.z);
            ticksTag.putLong("Tick", claimedAt);
            claimTicksTag.add(ticksTag);
        }

        tag.put(NBT_CLAIMED_CHUNKS, claimedTag);
        tag.put(NBT_CHUNK_CLAIM_TICKS, claimTicksTag);

        var decoratedTag = new ListTag();
        for (var chunk : decoratedChunks) {
            var chunkTag = new CompoundTag();
            chunkTag.putInt("X", chunk.x);
            chunkTag.putInt("Z", chunk.z);
            decoratedTag.add(chunkTag);
        }
        tag.put(NBT_DECORATED_CHUNKS, decoratedTag);

        // Phase 2 structure scaffolding: per-chunk role + piece, and frontier sockets.
        var rolesTag = new ListTag();
        for (var entry : structureRoleByChunk.entrySet()) {
            var entryTag = new CompoundTag();
            entryTag.putInt("X", entry.getKey().x);
            entryTag.putInt("Z", entry.getKey().z);
            entryTag.putString("Role", entry.getValue().name());
            rolesTag.add(entryTag);
        }
        tag.put(NBT_STRUCTURE_ROLES, rolesTag);

        var piecesTag = new ListTag();
        for (var entry : structurePieceByChunk.entrySet()) {
            var entryTag = new CompoundTag();
            entryTag.putInt("X", entry.getKey().x);
            entryTag.putInt("Z", entry.getKey().z);
            entryTag.putString("Piece", entry.getValue());
            piecesTag.add(entryTag);
        }
        tag.put(NBT_STRUCTURE_PIECES, piecesTag);

        var socketsTag = new ListTag();
        for (var socket : frontierSockets) {
            socketsTag.add(socket.toTag());
        }
        tag.put(NBT_FRONTIER_SOCKETS, socketsTag);

        var reservesTag = new CompoundTag();
        localReserves.save(reservesTag);
        tag.put(NBT_LOCAL_RESERVES, reservesTag);

        if (!parties.isEmpty()) {
            tag.put(NBT_PARTIES, com.alien.common.gameplay.hive.party.HivePartyCodec.saveAll(parties));
        }

        if (!attackCampaigns.isEmpty()) {
            var campaignsTag = new ListTag();
            for (var entry : attackCampaigns.entrySet()) {
                var entryTag = entry.getValue().save();
                entryTag.putUUID("PlayerId", entry.getKey());
                campaignsTag.add(entryTag);
            }
            tag.put(NBT_ATTACK_CAMPAIGNS, campaignsTag);
        }

        if (grudgePlayerId != null) {
            tag.putUUID(NBT_GRUDGE_PLAYER_ID, grudgePlayerId);
        }

        if (rescueCampaign != null) {
            tag.put(NBT_RESCUE_CAMPAIGN, rescueCampaign.save());
        }

        var knownMembersTag = new ListTag();
        for (var entry : knownMembersByType.entrySet()) {
            var members = entry.getValue();
            if (members.isEmpty()) {
                continue;
            }
            var row = new CompoundTag();
            row.putString("Type", BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey()).toString());

            var membersTag = new ListTag();
            for (var uuid : members) {
                var memberTag = new CompoundTag();
                memberTag.putUUID("Uuid", uuid);
                membersTag.add(memberTag);
            }
            row.put("Members", membersTag);
            knownMembersTag.add(row);
        }
        tag.put(NBT_KNOWN_MEMBERS_BY_TYPE, knownMembersTag);

        var leadershipTag = new CompoundTag();
        leadership.save(leadershipTag);
        tag.put(NBT_LEADERSHIP, leadershipTag);

        if (removalReason != null) {
            tag.put(NBT_REMOVAL_REASON, HiveLocationRemovalReason.save(removalReason));
        }

        return tag;
    }

    public static HiveLocation load(CompoundTag tag) {
        var id = HiveLocationId.of(ResourceLocation.parse(tag.getString(NBT_ID)));
        var location = new HiveLocation(id);

        location.lineageFactionId = ResourceLocation.parse(tag.getString(NBT_LINEAGE_FACTION_ID));
        location.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString(NBT_DIMENSION)));

        if (tag.contains(NBT_CENTER_POS)) {
            var components = tag.getIntArray(NBT_CENTER_POS);
            if (components.length >= 3) {
                location.centerPos = new BlockPos(components[0], components[1], components[2]);
            }
        }

        if (tag.hasUUID(NBT_FOUNDER_ID)) {
            location.founderId = tag.getUUID(NBT_FOUNDER_ID);
        }

        location.ageInTicks = tag.getLong(NBT_AGE_IN_TICKS);
        location.lastGrowthTick = tag.getLong(NBT_LAST_GROWTH_TICK);
        location.lastPassiveClaimTick = tag.contains(NBT_LAST_PASSIVE_CLAIM_TICK)
                ? Math.max(0L, tag.getLong(NBT_LAST_PASSIVE_CLAIM_TICK))
                : Math.max(0L, location.lastGrowthTick);
        location.lastAbstractSpreadTick = Math.max(0L, tag.getLong(NBT_LAST_ABSTRACT_SPREAD_TICK));
        location.lastAbstractSpreadAttempt = tag.contains(NBT_LAST_ABSTRACT_SPREAD_ATTEMPT)
                ? AbstractSpreadAttemptDebug.load(tag.getCompound(NBT_LAST_ABSTRACT_SPREAD_ATTEMPT))
                : AbstractSpreadAttemptDebug.none();
        location.peakXenomorphCount = Math.max(1, tag.getInt(NBT_PEAK_XENOMORPH_COUNT));
        location.peakDecayElapsedTicks = Math.max(0L, tag.getLong(NBT_PEAK_DECAY_ELAPSED));
        location.evacuatingRemainingTicks = Math.max(0L, tag.getLong(NBT_EVACUATING_REMAINING));
        location.noContactTicksAccrued = tag.contains(NBT_NO_CONTACT_TICKS_ACCRUED)
                ? Math.max(0L, tag.getLong(NBT_NO_CONTACT_TICKS_ACCRUED))
                : 0L;
        // Hives saved before this state existed load gracefully as a fresh location: fund available, nothing
        // accrued, no sample taken yet — exactly the private-constructor defaults, so no migration is needed.
        location.firewallFundAvailable = !tag.contains(NBT_FIREWALL_FUND_AVAILABLE) || tag.getBoolean(NBT_FIREWALL_FUND_AVAILABLE);
        location.firewallStableAccruedTicks = tag.contains(NBT_FIREWALL_STABLE_ACCRUED_TICKS)
                ? Math.max(0L, tag.getLong(NBT_FIREWALL_STABLE_ACCRUED_TICKS))
                : 0L;
        location.firewallBiomassSampleTick = tag.contains(NBT_FIREWALL_BIOMASS_SAMPLE_TICK)
                ? tag.getLong(NBT_FIREWALL_BIOMASS_SAMPLE_TICK)
                : Long.MIN_VALUE;
        location.firewallBiomassSampleValue = tag.contains(NBT_FIREWALL_BIOMASS_SAMPLE_VALUE)
                ? tag.getInt(NBT_FIREWALL_BIOMASS_SAMPLE_VALUE)
                : 0;
        location.combatRespiteRemainingTicks = Math.max(0L, tag.getLong(NBT_COMBAT_RESPITE_REMAINING_TICKS));
        location.combatKillsSinceLastRespite = Math.max(0, tag.getInt(NBT_COMBAT_KILLS_SINCE_LAST_RESPITE));
        location.locationNumber = tag.contains(NBT_LOCATION_NUMBER) ? tag.getLong(NBT_LOCATION_NUMBER) : -1L;
        location.queenlessMaturationLastAdvanceTick = tag.contains(NBT_QUEENLESS_MATURATION_LAST_ADVANCE)
                ? tag.getLong(NBT_QUEENLESS_MATURATION_LAST_ADVANCE)
                : Long.MIN_VALUE;
        location.queenlessLeaderSnapshot = tag.hasUUID(NBT_QUEENLESS_LEADER_SNAPSHOT)
                ? tag.getUUID(NBT_QUEENLESS_LEADER_SNAPSHOT)
                : null;
        location.biomass = Math.max(0, tag.getInt(NBT_BIOMASS));
        location.reproductiveEstablished = tag.getBoolean(NBT_REPRODUCTIVE_ESTABLISHED);
        location.inhibited = tag.getBoolean(NBT_INHIBITED);
        location.royalJelly = Math.max(0, tag.getInt(NBT_ROYAL_JELLY));
        location.scourgeJelly = Math.max(0, tag.getInt(NBT_SCOURGE_JELLY));
        location.royalJellyAccumulator = Math.max(0L, tag.getLong(NBT_ROYAL_JELLY_ACCUMULATOR));
        location.queenScourgeAccumulator = Math.max(0L, tag.getLong(NBT_QUEEN_SCOURGE_ACCUMULATOR));
        location.harbingerScourgeAccumulator = Math.max(0L, tag.getLong(NBT_HARBINGER_SCOURGE_ACCUMULATOR));

        if (tag.contains(NBT_CLAIMED_CHUNKS)) {
            var claimedTag = tag.getList(NBT_CLAIMED_CHUNKS, Tag.TAG_COMPOUND);
            for (var i = 0; i < claimedTag.size(); i++) {
                var chunkTag = claimedTag.getCompound(i);
                location.claimedChunks.add(new ChunkPos(chunkTag.getInt("X"), chunkTag.getInt("Z")));
            }
        }

        if (tag.contains(NBT_CHUNK_CLAIM_TICKS)) {
            var ticksTag = tag.getList(NBT_CHUNK_CLAIM_TICKS, Tag.TAG_COMPOUND);
            for (var i = 0; i < ticksTag.size(); i++) {
                var entryTag = ticksTag.getCompound(i);
                var chunk = new ChunkPos(entryTag.getInt("X"), entryTag.getInt("Z"));
                location.chunkClaimTicks.put(chunk, entryTag.getLong("Tick"));
            }
        }

        if (tag.contains(NBT_DECORATED_CHUNKS)) {
            var decoratedTag = tag.getList(NBT_DECORATED_CHUNKS, Tag.TAG_COMPOUND);
            for (var i = 0; i < decoratedTag.size(); i++) {
                var chunkTag = decoratedTag.getCompound(i);
                location.decoratedChunks.add(new ChunkPos(chunkTag.getInt("X"), chunkTag.getInt("Z")));
            }
        }

        // Phase 2 structure scaffolding: per-chunk role + piece, and frontier sockets. Absent on pre-existing saves
        // (graceful empty default - the maps/sets were initialized in the constructor).
        if (tag.contains(NBT_STRUCTURE_ROLES)) {
            var rolesTag = tag.getList(NBT_STRUCTURE_ROLES, Tag.TAG_COMPOUND);
            for (var i = 0; i < rolesTag.size(); i++) {
                var entryTag = rolesTag.getCompound(i);
                location.structureRoleByChunk.put(
                        new ChunkPos(entryTag.getInt("X"), entryTag.getInt("Z")),
                        HiveStructureRole.byName(entryTag.getString("Role"))
                );
            }
        }

        if (tag.contains(NBT_STRUCTURE_PIECES)) {
            var piecesTag = tag.getList(NBT_STRUCTURE_PIECES, Tag.TAG_COMPOUND);
            for (var i = 0; i < piecesTag.size(); i++) {
                var entryTag = piecesTag.getCompound(i);
                location.structurePieceByChunk.put(
                        new ChunkPos(entryTag.getInt("X"), entryTag.getInt("Z")),
                        entryTag.getString("Piece")
                );
            }
        }

        if (tag.contains(NBT_FRONTIER_SOCKETS)) {
            var socketsTag = tag.getList(NBT_FRONTIER_SOCKETS, Tag.TAG_COMPOUND);
            for (var i = 0; i < socketsTag.size(); i++) {
                location.frontierSockets.add(FrontierSocket.fromTag(socketsTag.getCompound(i)));
            }
        }

        if (tag.contains(NBT_LOCAL_RESERVES)) {
            location.localReserves.load(tag.getCompound(NBT_LOCAL_RESERVES));
        }

        location.parties.clear();
        if (tag.contains(NBT_PARTIES)) {
            location.parties.addAll(
                    com.alien.common.gameplay.hive.party.HivePartyCodec.loadAll(tag.getList(NBT_PARTIES, Tag.TAG_COMPOUND))
            );
        }

        location.grudgePlayerId = tag.hasUUID(NBT_GRUDGE_PLAYER_ID) ? tag.getUUID(NBT_GRUDGE_PLAYER_ID) : null;
        location.rescueCampaign = tag.contains(NBT_RESCUE_CAMPAIGN)
                ? com.alien.common.gameplay.hive.party.RescueCampaign.load(tag.getCompound(NBT_RESCUE_CAMPAIGN))
                : null;

        location.attackCampaigns.clear();
        if (tag.contains(NBT_ATTACK_CAMPAIGNS)) {
            var campaignsTag = tag.getList(NBT_ATTACK_CAMPAIGNS, Tag.TAG_COMPOUND);
            for (var i = 0; i < campaignsTag.size(); i++) {
                var entryTag = campaignsTag.getCompound(i);
                location.attackCampaigns.put(
                        entryTag.getUUID("PlayerId"),
                        com.alien.common.gameplay.hive.party.AttackCampaign.load(entryTag)
                );
            }
        }

        if (tag.contains(NBT_KNOWN_MEMBERS_BY_TYPE)) {
            var knownMembersTag = tag.getList(NBT_KNOWN_MEMBERS_BY_TYPE, Tag.TAG_COMPOUND);
            for (var i = 0; i < knownMembersTag.size(); i++) {
                var row = knownMembersTag.getCompound(i);
                var typeId = ResourceLocation.parse(row.getString("Type"));
                var type = BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
                if (type == null) {
                    continue;
                }

                var membersTag = row.getList("Members", Tag.TAG_COMPOUND);
                var members = location.knownMembersByType.computeIfAbsent(type, $ -> new HashSet<>());
                for (var j = 0; j < membersTag.size(); j++) {
                    var memberTag = membersTag.getCompound(j);
                    if (memberTag.hasUUID("Uuid")) {
                        members.add(memberTag.getUUID("Uuid"));
                    }
                }
            }
        }

        if (tag.contains(NBT_LEADERSHIP)) {
            location.leadership.load(tag.getCompound(NBT_LEADERSHIP));
        }

        if (tag.contains(NBT_REMOVAL_REASON)) {
            location.removalReason = HiveLocationRemovalReason.load(tag.getCompound(NBT_REMOVAL_REASON));
        }

        return location;
    }

    public record AbstractSpreadAttemptDebug(
            long tick,
            String result,
            @Nullable ChunkPos candidateChunk,
            @Nullable HiveLocationId createdLocationId,
            String detail
    ) {

        private static final String NBT_TICK = "Tick";

        private static final String NBT_RESULT = "Result";

        private static final String NBT_CANDIDATE_CHUNK_X = "CandidateChunkX";

        private static final String NBT_CANDIDATE_CHUNK_Z = "CandidateChunkZ";

        private static final String NBT_CREATED_LOCATION_ID = "CreatedLocationId";

        private static final String NBT_DETAIL = "Detail";

        public AbstractSpreadAttemptDebug {
            if (result == null || result.isBlank()) {
                result = "unknown";
            }
            if (detail == null) {
                detail = "";
            }
        }

        public static AbstractSpreadAttemptDebug none() {
            return new AbstractSpreadAttemptDebug(
                    -1L,
                    "never",
                    null,
                    null,
                    "No abstract spread attempt has been recorded."
            );
        }

        public CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong(NBT_TICK, tick);
            tag.putString(NBT_RESULT, result);
            if (candidateChunk != null) {
                tag.putInt(NBT_CANDIDATE_CHUNK_X, candidateChunk.x);
                tag.putInt(NBT_CANDIDATE_CHUNK_Z, candidateChunk.z);
            }
            if (createdLocationId != null) {
                tag.putString(NBT_CREATED_LOCATION_ID, createdLocationId.value().toString());
            }
            tag.putString(NBT_DETAIL, detail);
            return tag;
        }

        public static AbstractSpreadAttemptDebug load(CompoundTag tag) {
            var candidateChunk = tag.contains(NBT_CANDIDATE_CHUNK_X) && tag.contains(NBT_CANDIDATE_CHUNK_Z)
                    ? new ChunkPos(tag.getInt(NBT_CANDIDATE_CHUNK_X), tag.getInt(NBT_CANDIDATE_CHUNK_Z))
                    : null;
            var createdLocationId = tag.contains(NBT_CREATED_LOCATION_ID)
                    ? new HiveLocationId(ResourceLocation.parse(tag.getString(NBT_CREATED_LOCATION_ID)))
                    : null;

            return new AbstractSpreadAttemptDebug(
                    tag.contains(NBT_TICK) ? tag.getLong(NBT_TICK) : -1L,
                    tag.contains(NBT_RESULT) ? tag.getString(NBT_RESULT) : "unknown",
                    candidateChunk,
                    createdLocationId,
                    tag.contains(NBT_DETAIL) ? tag.getString(NBT_DETAIL) : ""
            );
        }
    }
}