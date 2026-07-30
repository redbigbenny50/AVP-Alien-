package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.faction.FactionMembershipTransfer;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.TrackedQueenRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Per-queen empress promotion timer. {@link #start} marks a queen as emerging — she's set
 * {@code invulnerable + noAi + persistenceRequired} for
 * {@link com.alien.common.gameplay.hive.config.HiveConfig#empressMoltDurationTicks()} (default 30 seconds). At
 * completion, her queen entity is replaced with the matching variant {@link Empress} entity at her position; the
 * lineage's {@code empressId} is set; the empress is added to the lineage's BLib membership; and the
 * {@code pendingEmpressEmergence} flag is cleared.
 * <p>
 * State is in-memory only (per the {@link com.alien.common.gameplay.hive.lifecycle.QueenSettlementDetector} pattern). A
 * server restart aborts in-flight emergences cleanly — the queens stay queens (with their invulnerable/noAi flags reset
 * by the next start attempt or by save/load).
 * <p>
 * Phase 10 ships the timer + swap; the visual molt animation pipeline is parked for a polish pass and can hook into the
 * existing {@link com.alien.common.gameplay.entity.living.alien.MoltingManager}.
 */
public final class EmpressEmergenceRitual {

    private static final Map<UUID, EmergenceState> states = new HashMap<>();

    /**
     * Loaded ticks an elected seat has been waiting for its queen to resolve, keyed by lineage. A bounded wait, not an
     * open one: waiting is right for the tick or two she takes to load in, but an unbounded wait would let a seat whose
     * queen is simply GONE hold the crown forever - the lineage collecting every empress benefit behind an empress who
     * can never be found and never killed.
     */
    private static final Map<ResourceLocation, Integer> materializeWaits = new HashMap<>();

    private static final int MAX_MATERIALIZE_WAIT_TICKS = 200;

    private EmpressEmergenceRitual() {}

    public static void clear() {
        states.clear();
        materializeWaits.clear();
    }

    /**
     * Abandon any in-flight molt for {@code lineageFactionId} and give the queen her body back.
     * <p>
     * {@link #start} sets {@code invulnerable + noAi + persistenceRequired}, and all three PERSIST to NBT while the
     * timer driving them lives only in memory. Dropping the state without this leaves an unkillable, brainless queen
     * standing in the hive permanently. She is restored rather than killed: a half-molted queen is still a working
     * queen, and destroying a hive's royal as a side effect of an election changing its mind would be a far larger
     * consequence than the election deserves.
     */
    public static void cancelFor(MinecraftServer server, ResourceLocation lineageFactionId) {
        materializeWaits.remove(lineageFactionId);
        var iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            var state = iterator.next().getValue();
            if (!state.lineageFactionId().equals(lineageFactionId)) {
                continue;
            }
            restoreQueen(server, state);
            iterator.remove();
        }
    }

    /** Undo the molt flags on the queen named by {@code state}, if she is still around to receive them. */
    private static void restoreQueen(MinecraftServer server, EmergenceState state) {
        var serverLevel = server.getLevel(state.dimension());
        if (serverLevel == null) {
            return;
        }
        if (serverLevel.getEntity(state.queenId()) instanceof Queen queen) {
            queen.setInvulnerable(false);
            queen.setNoAi(false);
            Alien.LOGGER.info("Hive: empress molt cancelled - queen {} restored", state.queenId());
        }
    }

    /** Whether some queen of {@code lineageFactionId} is currently in the emergence ritual. */
    public static boolean isEmergingFor(ResourceLocation lineageFactionId) {
        for (var state : states.values()) {
            if (state.lineageFactionId().equals(lineageFactionId)) {
                return true;
            }
        }
        return false;
    }

    /** Read-only snapshot for debug commands. */
    public static Map<UUID, EmergenceState> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(states));
    }

    /**
     * Give an already-ELECTED empress her body, if her seat is loaded and she is still eligible.
     * <p>
     * Called from the loaded tick of the elected seat. The election happened abstractly and possibly a very long time
     * ago, so this both waits for the queen entity to tick in and re-checks she has not been captured or inhibited
     * since. If she is gone or ineligible the seat is RELEASED along with {@code empressId}, and the next
     * {@link EmpressEmergenceTask} scan elects somewhere else.
     */
    public static void tryMaterialize(
        ServerLevel serverLevel,
        com.alien.common.gameplay.hive.location.HiveLocation location,
        LineageFactionData lineage
    ) {
        var lineageFactionId = lineage.factionId();
        if (lineageFactionId == null || isEmergingFor(lineageFactionId)) {
            return;
        }

        var founderId = location.founderId();
        if (founderId != null && serverLevel.getEntity(founderId) == null) {
            // Chunks are loaded but she has not ticked in yet. Waiting is correct - releasing immediately would
            // thrash the election every time a player walked into the seat hive - but the wait is BOUNDED, so a seat
            // whose queen is genuinely gone eventually surrenders the crown instead of holding it forever.
            var waited = materializeWaits.merge(lineageFactionId, 1, Integer::sum);
            if (waited < MAX_MATERIALIZE_WAIT_TICKS) {
                return;
            }
        }

        var queen = EmpressCandidatePicker.resolveSeatedQueen(serverLevel, location);
        if (queen == null) {
            materializeWaits.remove(lineageFactionId);
            Alien.LOGGER.info(
                "Hive: empress election released - elected seat {} has no eligible queen on load (dead, captured or "
                    + "inhibited); surrendering the crown for re-election",
                location.id()
            );
            lineage.setPendingEmpressSeatId(null);
            lineage.setEmpressId(null);
            return;
        }

        materializeWaits.remove(lineageFactionId);
        start(queen, lineageFactionId, serverLevel.getGameTime());
    }

    /**
     * Begin the molt for {@code queen}. Idempotent: a queen already in the map is left alone.
     */
    public static void start(Queen queen, ResourceLocation lineageFactionId, long currentTick) {
        var uuid = queen.getUUID();
        if (states.containsKey(uuid)) {
            return;
        }

        queen.setInvulnerable(true);
        queen.setNoAi(true);
        queen.setPersistenceRequired();

        states.put(uuid, new EmergenceState(uuid, lineageFactionId, queen.level().dimension(), currentTick));

        Alien.LOGGER.info(
            "Hive: empress emergence started for queen {} (lineage {}) — duration {}t",
            uuid,
            lineageFactionId,
            HiveLocationRegistry.INSTANCE.config().empressMoltDurationTicks()
        );
    }

    /**
     * Per-server-tick driver. Walks every emerging queen; aborts those whose entity is gone, fires completion when the
     * molt timer elapses.
     */
    public static void tick(MinecraftServer server) {
        if (states.isEmpty()) {
            return;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        Iterator<Map.Entry<UUID, EmergenceState>> iterator = states.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            var state = entry.getValue();
            var serverLevel = server.getLevel(state.dimension());

            if (serverLevel == null) {
                Alien.LOGGER.info(
                    "Hive: empress emergence aborted for queen {} — dimension {} unloaded",
                    state.queenId(),
                    state.dimension().location()
                );
                iterator.remove();
                continue;
            }

            var entity = serverLevel.getEntity(state.queenId());
            if (!(entity instanceof Queen queen) || !queen.isAlive() || queen.isRemoved()) {
                Alien.LOGGER.info("Hive: empress emergence aborted — queen {} no longer present", state.queenId());
                iterator.remove();
                continue;
            }

            var elapsed = serverLevel.getGameTime() - state.startedAtTick();
            if (elapsed < config.empressMoltDurationTicks()) {
                continue;
            }

            // Completion.
            iterator.remove();
            complete(server, queen, state);
        }
    }

    private static void complete(MinecraftServer server, Queen queen, EmergenceState state) {
        var faction = Alien.MOD.factions().get(state.lineageFactionId());
        if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
            Alien.LOGGER.warn(
                "Hive: empress emergence completed for queen {} but lineage {} is missing — leaving queen as-is",
                state.queenId(),
                state.lineageFactionId()
            );
            queen.setInvulnerable(false);
            queen.setNoAi(false);
            return;
        }

        var variant = queen.getVariant();
        var empressType = Empress.getType(variant);
        var serverLevel = server.getLevel(state.dimension());
        if (serverLevel == null) {
            // Shouldn't happen — we passed dim check on the iteration. Defensive cleanup anyway.
            queen.setInvulnerable(false);
            queen.setNoAi(false);
            return;
        }

        // Snapshot the queen's hive factions before discard. EntityType.spawn calls finalizeSpawn which auto-joins
        // the empress if she's in a claimed chunk, but the carry-over also handles the (rare) out-of-territory case.
        var factionSnapshot = FactionMembershipTransfer.snapshot(queen);

        var spawnPos = queen.blockPosition();

        // Built by hand rather than via EntityType.spawn so her UUID can be forced to the one the ELECTION already
        // published as empressId. The lineage has been acting on that id since the moment she was crowned - letting
        // the spawn mint a fresh one would silently orphan every abstract effect keyed to it, including
        // Alien.onEmpressDied, which would then never fire for her.
        var empress = empressType.create(serverLevel);
        if (empress != null) {
            var electedId = lineage.empressId();
            if (electedId != null) {
                empress.setUUID(electedId);
            }
            empress.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                queen.getYRot(),
                0.0F
            );
            empress.finalizeSpawn(
                serverLevel,
                serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED,
                null
            );
            if (!serverLevel.addFreshEntity(empress)) {
                empress = null;
            }
        }
        if (empress == null) {
            Alien.LOGGER.warn(
                "Hive: empress emergence completion failed — empressType.spawn returned null for variant {}",
                variant
            );
            queen.setInvulnerable(false);
            queen.setNoAi(false);
            return;
        }

        // Her tracker (if any) can no longer follow a queen who no longer exists -- mark it lost so it surfaces in the
        // PDA's caution list rather than lingering on a discarded entity.
        TrackedQueenRegistry.markLostAndAnnounce(serverLevel, queen.getUUID(), TrackedQueenRegistry.REASON_EMPRESS);

        // Move the queen out of the world. The empress takes her place at the same position.
        queen.discard();

        // Wire the new empress into the lineage and apply the carried-over membership.
        lineage.setEmpressId(empress.getUUID());
        lineage.setPendingEmpressEmergence(false);
        // The body has caught up with the crown; the seat reservation has done its job.
        lineage.setPendingEmpressSeatId(null);
        materializeWaits.remove(state.lineageFactionId());

        // Hand the seat's founder pointer to the empress. She IS that hive's royal now; leaving it on the queen we
        // just discarded would leave the location naming an entity that no longer exists, which reads as "has a
        // queen" to the growth and economy tasks and as a crownable seat to the next election.
        for (var location : lineage.locationsById().values()) {
            if (state.queenId().equals(location.founderId())) {
                location.setFounderId(empress.getUUID());
            }
        }
        FactionMembershipTransfer.apply(factionSnapshot, empress);
        LocationMembership.autoJoinAtPosition(empress, serverLevel);

        Alien.LOGGER.info(
            "Hive: empress emergence completed — queen {} → empress {} (variant {}, lineage {})",
            state.queenId(),
            empress.getUUID(),
            variant,
            state.lineageFactionId()
        );
    }

    public record EmergenceState(
        UUID queenId,
        ResourceLocation lineageFactionId,
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
        long startedAtTick
    ) {}
}
