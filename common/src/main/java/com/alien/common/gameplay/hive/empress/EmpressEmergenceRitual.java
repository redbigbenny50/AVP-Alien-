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

    private EmpressEmergenceRitual() {}

    public static void clear() {
        states.clear();
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
        var empress = empressType.spawn(serverLevel, spawnPos, MobSpawnType.MOB_SUMMONED);
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
