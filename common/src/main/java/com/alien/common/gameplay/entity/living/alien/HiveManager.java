package com.alien.common.gameplay.entity.living.alien;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.VariantFactionRegistry;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.lifecycle.HiveLocationFoundingService;
import com.alien.common.gameplay.hive.lifecycle.QueenSettlementDetector;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck;
import com.alien.common.gameplay.hive.lifecycle.SpreadZoneResult;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.faction.v1.FactionMember;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import com.just.core.functional.option.Option;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3f;

/**
 * Per-alien hive manager. Drives:
 * <ul>
 * <li>Variant-faction membership idempotency (event-driven via {@code Alien.finalizeSpawn} + the BLib
 * {@code onEntityLoad} listener — this class only exposes the entry point).</li>
 * <li>Queen settlement detection — every tick, drives {@link QueenSettlementDetector} for queens, runs
 * {@link SpreadZoneCheck}, and hands off to {@link HiveLocationFoundingService} on success.</li>
 * <li>Lineage shed timer — periodic chunk-presence refresh + shed eligibility check per
 * {@code HIVE_REDESIGN_05_RESERVES.md} § 5.</li>
 * </ul>
 * <p>
 * Persists {@code lastInsideLineageChunkTick}. The legacy per-faction hive id has been removed in Phase 12 — there is
 * no longer a single "this alien's hive id"; alien membership is tracked entirely through BLib's faction system with
 * the variant + lineage memberships.
 */
public class HiveManager implements NBTSerializable {

    private static final String LAST_INSIDE_LINEAGE_CHUNK_TICK_KEY = "LastInsideLineageChunkTick";

    /** Sentinel meaning "never set" — initialized lazily to the current game time on first tick. */
    private static final long UNSET_TIMESTAMP = 0L;

    private final com.alien.common.gameplay.entity.living.alien.Alien alien;

    /**
     * Most recent game tick at which this alien was observed standing in any of its lineages' claimed chunks. Used by
     * the shed timer in {@link #tryShedFromLineages(long)}. Persisted across restarts.
     */
    private long lastInsideLineageChunkTick;

    public HiveManager(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        this.alien = alien;
        this.lastInsideLineageChunkTick = UNSET_TIMESTAMP;
    }

    public void tick() {
        var level = alien.level();

        if (level.isClientSide) {
            return;
        }

        // Settlement detection: every tick. Cheap (Map lookup + counter increment) and gives responsive UX —
        // the queen settles exactly SETTLEMENT_TICKS after she stops moving.
        if (alien instanceof Queen queen) {
            tryQueenSettlement(queen, level.getGameTime());
        }

        if (alien.tickCount % (20 * 10) == 0) {
            // Variant-faction join is event-driven (Alien.finalizeSpawn + onEntityLoad listener), not tick-driven.
            // The lineage-presence refresh and shed check do still need to run periodically — they're observations
            // of the alien's chunk position over time.
            refreshLineageChunkPresence(level.getGameTime());
            tryShedFromLineages(level.getGameTime());
        }
    }

    /**
     * Drives the per-tick queen-settlement check. When {@link QueenSettlementDetector} reports the queen has stood
     * still in a chunk long enough, evaluates {@link SpreadZoneCheck} and (if permitted) hands off to
     * {@link HiveLocationFoundingService} to mint a new lineage or location.
     */
    private void tryQueenSettlement(Queen queen, long currentGameTime) {
        // Front-end life-cycle gate: a queen must finish developing -> location -> hibernation before she may settle.
        // When the phase machine is disabled this is always true, so the legacy settlement path runs unchanged.
        if (!queen.getLifecyclePhaseManager().isReadyToFound()) {
            return;
        }

        // Already founded (she belongs to a lineage) — stop settling and drop her founding aura. The front-end phase
        // machine is off by default, so isReadyToFound() stays true forever; without this she perpetually re-banks
        // settlement after founding, re-firing the ritual and trailing particles nonstop.
        if (belongsToLineage(queen)) {
            QueenSettlementDetector.forget(queen.getUUID());
            return;
        }

        var settlementPos = QueenSettlementDetector.observe(queen, currentGameTime);
        if (settlementPos == null) {
            // Still counting down the out-of-combat settlement timer — trail purple "founding" particles so the act of
            // settling is visible in-world. (The actual found() below is instant once the timer expires.)
            if (QueenSettlementDetector.isSettling(queen.getUUID())) {
                spawnFoundingParticles(queen);
            }
            return;
        }

        var result = SpreadZoneCheck.evaluate(queen, settlementPos);
        if (result instanceof SpreadZoneResult.Blocked) {
            // The committed anchor is no longer foundable — a hive is too close, typically a neighbour that founded
            // during her hibernation. Re-pick a fresh anchor away from current claims and run her back through
            // LOCATION -> HIBERNATION rather than leaving her stuck on a stale spot forever.
            queen.getLifecyclePhaseManager().restartLocationPhase();
            return;
        }

        HiveLocationFoundingService.foundFromResult(queen, settlementPos, result);
    }

    /** True once she has founded — membership in any lineage faction is the phase-independent "founded" signal. */
    private static boolean belongsToLineage(Queen queen) {
        for (var factionId : Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (LineageIds.isLineageId(factionId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Near-black founding motes. Dust is recolourable (unlike the old fixed-purple spell swirl); tweak the RGB to
     * taste.
     */
    private static final DustParticleOptions FOUNDING_DUST = new DustParticleOptions(new Vector3f(0.05F, 0.05F, 0.05F), 1.0F);

    /** Black dust aura while she is actively settling, throttled so it reads as a gentle aura rather than a fog. */
    private void spawnFoundingParticles(Queen queen) {
        if (queen.tickCount % 4 != 0 || !(queen.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var width = queen.getBbWidth();
        var height = queen.getBbHeight();
        serverLevel.sendParticles(
            FOUNDING_DUST,
            queen.getX(),
            queen.getY() + height * 0.6,
            queen.getZ(),
            4,
            width * 0.6,
            height * 0.5,
            width * 0.6,
            0.02
        );
    }

    /**
     * Idempotently adds this alien to its variant's BLib faction. Wired to two event hooks (per the design contract in
     * {@code HIVE_REDESIGN_01_FACTIONS.md} § 1: <em>"Every alien of variant V joins variant V's faction the first time
     * it loads on the server"</em>):
     * <ul>
     * <li>{@link com.alien.common.gameplay.entity.living.alien.Alien#finalizeSpawn} — fresh spawns (natural, spawn egg,
     * command).</li>
     * <li>BLib {@code onEntityLoad} listener in the mod entry point — chunk-load reads from disk.</li>
     * </ul>
     * <p>
     * {@code addEntity} returns false when already a member, so duplicate calls between the two hooks are free.
     * <p>
     * The variant faction is sticky: a normal-variant alien that mutates to irradiated keeps its {@code variant/normal}
     * membership. Lineage-tier eviction-on-mismatch is enforced by Phase 9's {@code LineageInvariantTask}; the variant
     * tier never evicts.
     */
    public void ensureVariantFactionMembership() {
        var variant = alien.getVariant();
        if (variant == null) {
            return;
        }

        var faction = VariantFactionRegistry.getOrCreate(variant);
        if (faction.membership().hasMember(FactionMember.entity(alien))) {
            return;
        }

        faction.membership().addEntity(alien);
    }

    /**
     * Ensures every alien gets a fresh shed-grace window the first time it ticks under hive — protects newly added
     * lineage members from being shed immediately if their {@link #lastInsideLineageChunkTick} is still the sentinel
     * value 0. After the first observation the timestamp is updated normally based on chunk presence.
     */
    private void refreshLineageChunkPresence(long currentTick) {
        if (lastInsideLineageChunkTick == UNSET_TIMESTAMP) {
            lastInsideLineageChunkTick = currentTick;
        }

        if (!alien.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
            return;
        }

        var alienChunk = new ChunkPos(alien.blockPosition());
        var dimension = alien.level().dimension();
        var hit = HiveLocationRegistry.INSTANCE.getByChunk(dimension, alienChunk);
        if (hit == null) {
            return;
        }

        // Only count the chunk as "inside" if the location belongs to a lineage this alien is in.
        for (var lineageId : Alien.MOD.factions().getFactionIds(alien.getUUID())) {
            if (!LineageIds.isLineageId(lineageId)) {
                continue;
            }
            if (lineageId.equals(hit.lineageFactionId())) {
                lastInsideLineageChunkTick = currentTick;
                return;
            }
        }
    }

    /**
     * Per-lineage shed check. If this alien has stayed outside its lineage territory long enough, remove its live
     * entity and return the unit to its owning hive location reserves.
     * <p>
     * Empresses and location leaders are exempt. Playable xenomorphs (parked future direction) will be exempt via a
     * one-line {@code instanceof Player} check that's currently unreachable since {@link Alien} doesn't extend Player.
     */
    public boolean tryShedFromLineages(long currentTick) {
        // Royalty is never shed. Empresses and queens are the heart of a hive; culling a founding queen for standing
        // outside her (often tiny, new) claimed footprint deletes the hive's only royal. Empresses were already exempt;
        // queens must be too, otherwise a queen that hasn't yet settled into an ovipositor gets shed after the grace
        // window and the lineage is left queenless.
        if (
            alien.getType().is(AlienEntityTypeTags.EMPRESSES)
                || alien.getType().is(AlienEntityTypeTags.QUEENS)
        ) {
            return false;
        }

        if (!alien.isAlive() || alien.isRemoved()) {
            return false;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        if (currentTick - lastInsideLineageChunkTick < config.shedGraceTicks()) {
            return false;
        }

        var returnLocation = HiveMemberLocationResolver.reserveReturnLocation(alien);
        if (returnLocation == null) {
            return false;
        }

        var faction = Alien.MOD.factions().get(returnLocation.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            return false;
        }

        if (!shouldShed(lineage, currentTick, config.minLineageAgeForShedding())) {
            return false;
        }

        if (!returnLocation.localReserves().addReturningMember(alien.getType(), 1)) {
            return false;
        }

        // Drop the alien from every location faction owned by this lineage first (preserving the
        // location subset lineage invariant); idempotent for locations the alien wasn't a member of.
        for (var location : lineage.locationsById().values()) {
            var locationFaction = Alien.MOD.factions().get(location.id().value());
            if (locationFaction != null) {
                locationFaction.membership().removeMember(FactionMember.entity(alien));
            }
        }

        // Remove from lineage membership (BLib will fire onMemberRemoved which clears
        // loadedMembersByType for us via Phase 3's hook).
        faction.membership().removeMember(FactionMember.entity(alien));

        // Despawn the entity entirely; the unit is now stored in the hive location reserves.
        alien.discard();

        return true;
    }

    private boolean shouldShed(LineageFactionData lineage, long currentTick, long minLineageAge) {
        if (lineage.locationsById().isEmpty()) {
            return false;
        }

        if (lineage.ageInTicks() < minLineageAge) {
            return false;
        }

        // Leader of any of this lineage's locations is exempt.
        var alienUuid = alien.getUUID();
        for (var location : lineage.locationsById().values()) {
            if (location.leadership().isLeader(alienUuid)) {
                return false;
            }
        }

        return true;
    }

    /** Test/debug seam: jumps the timestamp into the past so the next periodic shed check fires immediately. */
    public void debugForceShedEligible() {
        this.lastInsideLineageChunkTick = 1L;
    }

    public long lastInsideLineageChunkTick() {
        return lastInsideLineageChunkTick;
    }

    /**
     * Returns this alien's primary lineage faction id, if any. "Primary" is the first lineage id encountered in BLib's
     * membership lookup — for the typical single-lineage case this is the only one. Used by signature-equality checks
     * (e.g., {@code AlienPredicates.isFromSameHive}) to decide whether two aliens share a hive.
     */
    public Option<ResourceLocation> signature() {
        for (var factionId : Alien.MOD.factions().getFactionIds(alien.getUUID())) {
            if (LineageIds.isLineageId(factionId)) {
                return Option.some(factionId);
            }
        }
        return Option.none();
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(LAST_INSIDE_LINEAGE_CHUNK_TICK_KEY)) {
            this.lastInsideLineageChunkTick = compoundTag.getLong(LAST_INSIDE_LINEAGE_CHUNK_TICK_KEY);
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        compoundTag.putLong(LAST_INSIDE_LINEAGE_CHUNK_TICK_KEY, lastInsideLineageChunkTick);
    }
}
