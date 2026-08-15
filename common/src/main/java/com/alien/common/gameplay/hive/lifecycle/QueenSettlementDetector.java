package com.alien.common.gameplay.hive.lifecycle;

import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-queen settlement timer. Accumulates how long each queen has stood ready to found while <em>out of combat</em>;
 * returns a {@link BlockPos} once {@link com.alien.common.gameplay.hive.config.HiveConfig#settlementTicks()} of
 * out-of-combat time have banked, at which point the caller should evaluate {@link SpreadZoneCheck} and (if permitted)
 * hand the position to {@link HiveLocationFoundingService}.
 * <p>
 * Combat <em>pauses</em> the timer rather than resetting it. While she has a target, was recently hurt, or has a
 * last-hurt-by attacker, no progress accrues — but the progress already banked is preserved and resumes when the fight
 * ends. Chunk crossings do not affect the timer; wandering during settlement is fine, and she founds at whichever chunk
 * she is standing in when the timer fills.
 * <p>
 * State is in-memory only — a server stop or world reload starts every queen fresh. That's the design intent;
 * settlements are rare events and persistence isn't worth the complexity. A large gap between observations (chunk
 * unload/reload) restarts accrual rather than crediting the whole gap at once.
 */
public final class QueenSettlementDetector {

    /**
     * Largest gap (ticks) between two observations that still counts as continuous presence. The driver observes every
     * tick, so the real delta is 1; anything beyond this means she was unloaded / not ticked, so accrual restarts
     * instead of dumping the whole gap into the timer.
     */
    private static final long MAX_OBSERVATION_GAP_TICKS = 40L;

    private static final Map<UUID, AnchorState> states = new HashMap<>();

    private QueenSettlementDetector() {}

    /**
     * Per-tick observation. Banks out-of-combat time toward settlement and returns the settlement block-position (the
     * current chunk's middle) once enough has accrued. Returns {@code null} while still banking or paused by combat.
     */
    public static @Nullable BlockPos observe(Queen queen, long currentGameTime) {
        var uuid = queen.getUUID();
        var existing = states.get(uuid);

        if (existing == null) {
            // First observation — start banking from zero (whether or not she is currently in combat).
            states.put(
                uuid,
                new AnchorState(new ChunkPos(queen.blockPosition()), 0L, currentGameTime, queen.blockPosition())
            );
            return null;
        }

        var delta = currentGameTime - existing.lastObservedTick();
        if (delta < 0L || delta > MAX_OBSERVATION_GAP_TICKS) {
            // Discontinuous observation (unload/reload/time anomaly) — restart accrual rather than crediting the gap.
            states.put(
                uuid,
                new AnchorState(new ChunkPos(queen.blockPosition()), 0L, currentGameTime, queen.blockPosition())
            );
            return null;
        }

        // Combat pauses (accrue nothing) but preserves banked progress; out of combat, bank the elapsed ticks.
        var accumulated = existing.accumulatedTicks() + (isInCombat(queen) ? 0L : delta);
        var settlementTicks = HiveLocationRegistry.INSTANCE.config().settlementTicks();

        if (accumulated >= settlementTicks) {
            states.remove(uuid);
            return new ChunkPos(queen.blockPosition()).getMiddleBlockPosition(queen.blockPosition().getY());
        }

        states.put(uuid, new AnchorState(existing.chunk(), accumulated, currentGameTime, queen.blockPosition()));
        return null;
    }

    /** True while this queen is mid-settlement (banking or paused by combat) — the "actively founding" window. */
    public static boolean isSettling(UUID queenId) {
        return states.containsKey(queenId);
    }

    /** Drops the queen's anchor without firing settlement. Use when she dies, despawns, or is otherwise removed. */
    public static void forget(UUID queenId) {
        states.remove(queenId);
    }

    /** For debug commands — read-only snapshot of the current per-queen anchors. */
    public static Map<UUID, AnchorState> snapshot() {
        return Collections.unmodifiableMap(new HashMap<>(states));
    }

    /** Called from server-stop. Prevents per-UUID state from leaking between worlds. */
    public static void clear() {
        states.clear();
    }

    private static boolean isInCombat(Queen queen) {
        if (queen.getTarget() != null) {
            return true;
        }
        if (queen.hurtTime > 0) {
            return true;
        }
        return queen.getLastHurtByMob() != null;
    }

    /**
     * Per-queen settlement progress. {@code accumulatedTicks} is the banked out-of-combat time toward
     * {@code settlementTicks}; {@code lastObservedTick} is the game tick of the most recent observation (used to
     * compute the per-tick delta and detect unload gaps).
     */
    public record AnchorState(
        ChunkPos chunk,
        long accumulatedTicks,
        long lastObservedTick,
        BlockPos lastSeenPos
    ) {}
}
