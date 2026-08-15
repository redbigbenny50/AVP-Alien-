package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.hive.id.HiveLocationId;
import com.blib.api.common.entity.v1.EntityReserves;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * A specialized group of xenomorphs dispatched by a single hive location for a purpose — distinct from {@code Convoy},
 * which is the empress-gated cross-hive supply chain (reinforcement/migration/raid). Parties are hive-level: any hive
 * can raise one from its own reserves, empress or not.
 * <p>
 * Sealed so codecs can dispatch on subtype, mirroring {@code Convoy}'s shape. Ships {@link SurfaceSpawn} first (the
 * vent-seeding "general surface spawn" party — see {@code AVP_Party_System_Design.md} § 1). Five more party types are
 * planned (host hunt, biomass hunting, attack, rescue/recovery, revenge) — the vent-dependent trio (host hunt, biomass
 * hunting, attack) and the target-tracking pair (rescue, revenge) will each add their own {@code final class} here,
 * sharing this interface's common lifecycle (composition, materialization tracking, dispatch bookkeeping) while
 * specializing trigger/spawn/travel behavior per their group.
 * <p>
 * Common semantics across all subtypes:
 * <ul>
 * <li>{@link #composition} — the {@link EntityReserves} bag of unmaterialized members still owed to the world (mirrors
 * {@code Convoy#composition}).</li>
 * <li>{@link #materializedMembers} — live entities currently spawned for this party, tracked by UUID so the resolution
 * task can find and despawn/refund them without a level-wide entity scan.</li>
 * <li>{@link #dispatchedTick} — when the party was minted.</li>
 * </ul>
 */
public sealed interface HiveParty {

    HivePartyId id();

    HiveLocationId sourceLocationId();

    ResourceKey<Level> dimension();

    EntityReserves composition();

    Map<UUID, EntityType<?>> materializedMembers();

    void trackMaterializedMember(UUID memberId, EntityType<?> entityType);

    @Nullable
    EntityType<?> untrackMaterializedMember(UUID memberId);

    int materializedCountMatching(Predicate<EntityType<?>> predicate);

    long dispatchedTick();

    /**
     * The night/day "general surface spawn" party — all-Runner, spawns on the surface at night, returns to reserves at
     * dawn. Seeds the surface vent network the vent-dependent party trio (host hunt, biomass hunting, attack) depends
     * on to spawn at all.
     * <p>
     * Behavior (see {@code AVP_Party_System_Design.md} § 1):
     * <ul>
     * <li>Constant baseline: scouts/patrols the surface, using default Runner combat targeting throughout — no custom
     * combat AI. Killing low-priority/weak targets when the hive's biomass runs low is already handled hive-wide by
     * {@code AlienPredicates#isTargetThreatAllowed}'s biomass-gated threat tiers (any xenomorph, party member or not,
     * becomes willing to engage THREAT_2 targets once the hive drops to ≤25% of its biomass cap) — no party-specific
     * targeting hook is needed or present.</li>
     * <li>{@link EconomyBias#HARVEST} while {@code AlienPredicates.isLocationLowOnBiomass(location)} is true (the same
     * 25%-of-cap ratio the hive-wide threat gate uses — single source of truth, no separate party threshold) —
     * currently a no-op state (reserved for a future party-specific behavior if one is ever needed); combat aggression
     * already comes from the hive-wide gate above.</li>
     * <li>{@link EconomyBias#EXPAND} otherwise — may opportunistically claim the chunk a member currently occupies
     * (subject to the normal claim-cost and territory-radius gates every other claim path already respects). This is
     * the one bias value with real behavior attached.</li>
     * <li>On dawn resolution: surviving members refund to the source hive's reserves; a 10% roll may drop a vent +
     * resin at the party's last known surface position, capped at {@code config.surfacePartyMaxVentsPerClaim()} vents
     * per chunk, counted against near-surface vents only (see {@code SurfacePartyLifecycleTask}).</li>
     * </ul>
     * <p>
     * {@link #economyBias} and {@link #lastEconomyCheckTick} are re-evaluated periodically while active (dynamic, not
     * locked in at dispatch) — see {@code SurfacePartyLifecycleTask}.
     */
    final class SurfaceSpawn implements HiveParty {

        private final HivePartyId id;

        private final HiveLocationId sourceLocationId;

        private final ResourceKey<Level> dimension;

        private final EntityReserves composition;

        private final Map<UUID, EntityType<?>> materializedMembers;

        private final long dispatchedTick;

        private EconomyBias economyBias;

        private long lastEconomyCheckTick;

        public SurfaceSpawn(
            HivePartyId id,
            HiveLocationId sourceLocationId,
            ResourceKey<Level> dimension,
            EntityReserves composition,
            long dispatchedTick
        ) {
            this(id, sourceLocationId, dimension, composition, new HashMap<>(), dispatchedTick, EconomyBias.HARVEST, -1L);
        }

        public SurfaceSpawn(
            HivePartyId id,
            HiveLocationId sourceLocationId,
            ResourceKey<Level> dimension,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            long dispatchedTick,
            EconomyBias economyBias,
            long lastEconomyCheckTick
        ) {
            this.id = id;
            this.sourceLocationId = sourceLocationId;
            this.dimension = dimension;
            this.composition = composition;
            this.materializedMembers = new HashMap<>(materializedMembers);
            this.dispatchedTick = dispatchedTick;
            this.economyBias = economyBias;
            this.lastEconomyCheckTick = lastEconomyCheckTick;
        }

        @Override
        public HivePartyId id() {
            return id;
        }

        @Override
        public HiveLocationId sourceLocationId() {
            return sourceLocationId;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public EntityReserves composition() {
            return composition;
        }

        @Override
        public Map<UUID, EntityType<?>> materializedMembers() {
            return materializedMembers;
        }

        @Override
        public void trackMaterializedMember(UUID memberId, EntityType<?> entityType) {
            materializedMembers.put(memberId, entityType);
        }

        @Override
        public @Nullable EntityType<?> untrackMaterializedMember(UUID memberId) {
            return materializedMembers.remove(memberId);
        }

        @Override
        public int materializedCountMatching(Predicate<EntityType<?>> predicate) {
            var count = 0;
            for (var type : materializedMembers.values()) {
                if (predicate.test(type)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public long dispatchedTick() {
            return dispatchedTick;
        }

        public EconomyBias economyBias() {
            return economyBias;
        }

        public void setEconomyBias(EconomyBias economyBias) {
            this.economyBias = economyBias;
        }

        public long lastEconomyCheckTick() {
            return lastEconomyCheckTick;
        }

        public void setLastEconomyCheckTick(long lastEconomyCheckTick) {
            this.lastEconomyCheckTick = lastEconomyCheckTick;
        }
    }

    /**
     * Prowler + Warrior (+ bonus Spitters if available) party that hunts for biomass — vent-dependent (needs an
     * existing surface vent to spawn, and teleports back through it when returning home rather than walking). Kills
     * feed the hive automatically via the existing {@code HiveBiomassEvents}/{@code MixinLivingEntity_HiveBiomass}
     * pipeline — no bespoke economy code needed here.
     * <p>
     * Unlike {@link SurfaceSpawn}, this party's members are tagged with a {@link PartyMembership} so
     * {@code AlienPredicates#isTargetThreatAllowed} can recognize them and bypass the hive-wide biomass-gated THREAT_2
     * restriction — the party's whole purpose is proactively hunting low-danger targets, not just an emergency response
     * when the hive is already struggling, so it needs a genuine eligibility override rather than inheriting the
     * hive-wide gate.
     * <p>
     * No day/night restriction (unspecified in design, unlike {@link SurfaceSpawn}) — runs on a fixed
     * {@code config.biomassHuntingPartyDurationTicks()} active duration instead, tracked via {@link #dispatchedTick()}.
     */
    final class BiomassHunting implements HiveParty {

        private final HivePartyId id;

        private final HiveLocationId sourceLocationId;

        private final ResourceKey<Level> dimension;

        private final EntityReserves composition;

        private final Map<UUID, EntityType<?>> materializedMembers;

        private final long dispatchedTick;

        public BiomassHunting(
            HivePartyId id,
            HiveLocationId sourceLocationId,
            ResourceKey<Level> dimension,
            EntityReserves composition,
            long dispatchedTick
        ) {
            this(id, sourceLocationId, dimension, composition, new HashMap<>(), dispatchedTick);
        }

        public BiomassHunting(
            HivePartyId id,
            HiveLocationId sourceLocationId,
            ResourceKey<Level> dimension,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            long dispatchedTick
        ) {
            this.id = id;
            this.sourceLocationId = sourceLocationId;
            this.dimension = dimension;
            this.composition = composition;
            this.materializedMembers = new HashMap<>(materializedMembers);
            this.dispatchedTick = dispatchedTick;
        }

        @Override
        public HivePartyId id() {
            return id;
        }

        @Override
        public HiveLocationId sourceLocationId() {
            return sourceLocationId;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public EntityReserves composition() {
            return composition;
        }

        @Override
        public Map<UUID, EntityType<?>> materializedMembers() {
            return materializedMembers;
        }

        @Override
        public void trackMaterializedMember(UUID memberId, EntityType<?> entityType) {
            materializedMembers.put(memberId, entityType);
        }

        @Override
        public @Nullable EntityType<?> untrackMaterializedMember(UUID memberId) {
            return materializedMembers.remove(memberId);
        }

        @Override
        public int materializedCountMatching(Predicate<EntityType<?>> predicate) {
            var count = 0;
            for (var type : materializedMembers.values()) {
                if (predicate.test(type)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public long dispatchedTick() {
            return dispatchedTick;
        }
    }

    /**
     * Host hunt (§ 2): drones sent out to bring back a live host for the egg-morphing pipeline.
     * <p>
     * Vent-dependent like the rest of the trio - it spawns AT a near-surface vent and cannot dispatch before one
     * exists. Members are DRONES: they capture rather than kill, and only creatures on the host lists are ever taken
     * (anything else is simply ignored unless it attacks first). A captured host rides its captor to the nearest
     * surface vent, which is the hand-off point INTO the hive - hosts are never walked home overland - and is embedded
     * in a host-chamber spot to await an egg.
     * <p>
     * Resolves the same way as {@link BiomassHunting}: a fixed active duration, then instant vent-teleport home and
     * refund - see {@code HostHuntPartyLifecycleTask}.
     */
    final class HostHunt implements HiveParty {

        private final HivePartyId id;

        private final HiveLocationId sourceLocationId;

        private final ResourceKey<Level> dimension;

        private final EntityReserves composition;

        private final Map<UUID, EntityType<?>> materializedMembers;

        private final long dispatchedTick;

        public HostHunt(
            HivePartyId id,
            HiveLocationId sourceLocationId,
            ResourceKey<Level> dimension,
            EntityReserves composition,
            long dispatchedTick
        ) {
            this(id, sourceLocationId, dimension, composition, new HashMap<>(), dispatchedTick);
        }

        public HostHunt(
            HivePartyId id,
            HiveLocationId sourceLocationId,
            ResourceKey<Level> dimension,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            long dispatchedTick
        ) {
            this.id = id;
            this.sourceLocationId = sourceLocationId;
            this.dimension = dimension;
            this.composition = composition;
            this.materializedMembers = new HashMap<>(materializedMembers);
            this.dispatchedTick = dispatchedTick;
        }

        @Override
        public HivePartyId id() {
            return id;
        }

        @Override
        public HiveLocationId sourceLocationId() {
            return sourceLocationId;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public EntityReserves composition() {
            return composition;
        }

        @Override
        public Map<UUID, EntityType<?>> materializedMembers() {
            return materializedMembers;
        }

        @Override
        public void trackMaterializedMember(UUID memberId, EntityType<?> entityType) {
            materializedMembers.put(memberId, entityType);
        }

        @Override
        public @Nullable EntityType<?> untrackMaterializedMember(UUID memberId) {
            return materializedMembers.remove(memberId);
        }

        @Override
        public int materializedCountMatching(Predicate<EntityType<?>> predicate) {
            var count = 0;
            for (var type : materializedMembers.values()) {
                if (predicate.test(type)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public long dispatchedTick() {
            return dispatchedTick;
        }
    }

    /**
     * Retribution party — primarily Warriors/Prowlers, also Crushers/Praetorians, dispatched against a specific player
     * who breached this hive location's claim and lingered while fighting (see {@code AttackCampaign} for the
     * territorial-intrusion two-wave model). Vent-dependent like {@link HostHunt}. The campaign state (waves sent,
     * cleared flag) lives on {@code HiveLocation#attackCampaigns}, not on the party itself, since it must persist
     * across and between wave dispatches.
     * <p>
     * Separate from empress-gated raids by design — {@code AVP_Party_System_Design.md} § 4 notes it can reinforce an
     * active raid, but that cross-system hook (hive-level party topping up a lineage-level raid convoy) is deliberately
     * deferred; not implemented here.
     * <p>
     * Resolves the same way as {@link HostHunt}: a fixed active duration, then instant vent-teleport home and refund —
     * see {@code AttackPartyLifecycleTask}. Also resolves early if {@link #targetPlayerId} is confirmed dead.
     */
    final class AttackParty implements HiveParty {

        private final HivePartyId id;

        private final HiveLocationId sourceLocationId;

        private final ResourceKey<Level> dimension;

        private final EntityReserves composition;

        private final Map<UUID, EntityType<?>> materializedMembers;

        private final long dispatchedTick;

        private final UUID targetPlayerId;

        public AttackParty(
            HivePartyId id,
            HiveLocationId sourceLocationId,
            ResourceKey<Level> dimension,
            EntityReserves composition,
            long dispatchedTick,
            UUID targetPlayerId
        ) {
            this(id, sourceLocationId, dimension, composition, new HashMap<>(), dispatchedTick, targetPlayerId);
        }

        public AttackParty(
            HivePartyId id,
            HiveLocationId sourceLocationId,
            ResourceKey<Level> dimension,
            EntityReserves composition,
            Map<UUID, EntityType<?>> materializedMembers,
            long dispatchedTick,
            UUID targetPlayerId
        ) {
            this.id = id;
            this.sourceLocationId = sourceLocationId;
            this.dimension = dimension;
            this.composition = composition;
            this.materializedMembers = new HashMap<>(materializedMembers);
            this.dispatchedTick = dispatchedTick;
            this.targetPlayerId = targetPlayerId;
        }

        @Override
        public HivePartyId id() {
            return id;
        }

        @Override
        public HiveLocationId sourceLocationId() {
            return sourceLocationId;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public EntityReserves composition() {
            return composition;
        }

        @Override
        public Map<UUID, EntityType<?>> materializedMembers() {
            return materializedMembers;
        }

        @Override
        public void trackMaterializedMember(UUID memberId, EntityType<?> entityType) {
            materializedMembers.put(memberId, entityType);
        }

        @Override
        public @Nullable EntityType<?> untrackMaterializedMember(UUID memberId) {
            return materializedMembers.remove(memberId);
        }

        @Override
        public int materializedCountMatching(Predicate<EntityType<?>> predicate) {
            var count = 0;
            for (var type : materializedMembers.values()) {
                if (predicate.test(type)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public long dispatchedTick() {
            return dispatchedTick;
        }

        public UUID targetPlayerId() {
            return targetPlayerId;
        }
    }

    /**
     * Dynamic, periodically re-evaluated economic posture for {@link SurfaceSpawn} (not locked in at dispatch).
     * {@code HARVEST} = source hive biomass at/below 25% of cap (per {@code AlienPredicates.isLocationLowOnBiomass}) —
     * currently a no-op state; killing weak targets already happens hive-wide via {@code AlienPredicates}'s
     * biomass-gated threat tiers, no party-specific behavior needed. {@code EXPAND} = otherwise, may opportunistically
     * claim.
     */
    enum EconomyBias {
        HARVEST,
        EXPAND
    }
}
