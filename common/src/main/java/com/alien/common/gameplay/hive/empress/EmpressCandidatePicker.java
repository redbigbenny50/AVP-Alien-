package com.alien.common.gameplay.hive.empress;

import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Elects which of a lineage's hives becomes the empress SEAT, deterministically and <b>without loading anything</b>.
 * <p>
 * This used to rank live {@link Queen} entities drawn from {@code loadedMembersByType}, which quietly made the player's
 * standing position decide the succession: hives sit hundreds of blocks apart, so typically zero or one candidate was
 * ever loaded at once. With one loaded there was no contest to hold, and with none loaded no empress could
 * <em>ever</em> emerge no matter how large the empire grew. Queens are also deliberately never virtualized into
 * reserves (see {@code HiveIdentityReserveUnloadHandler} - a founder queen carries the lineage and rides an ovipositor
 * that would be orphaned), so an unloaded queen is unreachable NBT rather than an object we could rank.
 * <p>
 * So the election ranks LOCATIONS on persisted state, and the body is reconciled later - the same
 * decide-abstractly-then-catch-up contract {@link com.alien.common.gameplay.hive.growth.CatchUpEngine} already uses for
 * biomass and claims. A location is a candidate when it is alive, has a seated queen ({@code founderId != null} - the
 * same "not queenless" test the growth and economy tasks already trust for unloaded hives), and clears
 * {@code config.empressCandidateMinMembers()}.
 * <p>
 * Comparators, in the design's stated priority (claims, then members, then age):
 * <ol>
 * <li>Most claimed chunks wins.</li>
 * <li>If tied, the larger hive wins ({@link CastePopulation#totalTrackedPopulation}, which counts loaded members
 * <em>plus</em> reserves - and since fungible members virtualize into reserves on unload, it reads about the same
 * whether the hive is loaded or not, which is what makes comparing across load states fair at all).</li>
 * <li>If tied, the older hive wins. This replaces the old "older queen wins" step, which needed her {@code tickCount}
 * and so needed her loaded; seat age was already the next tiebreak and is the honest proxy.</li>
 * <li>Then the lineage founder's own seat wins, then location-id lex - a last-resort total order so the result is
 * stable across restarts and identical on every server that evaluates it.</li>
 * </ol>
 */
public final class EmpressCandidatePicker {

    private EmpressCandidatePicker() {}

    /**
     * The best seat for an empress, or {@code null} if this lineage has no hive with a seated queen. Reads only
     * persisted location state - safe to call when nothing in the lineage is loaded.
     */
    /**
     * Seats that were crowned and then surrendered the crown on load, mapped to the tick they may be tried again.
     * <p>
     * WHY THIS EXISTS. Election and materialization did not agree on what "eligible" means. {@link #pickSeat} runs on
     * PERSISTED data and can only see {@code founderId != null}; {@link #resolveSeatedQueen} runs on the loaded entity
     * and additionally demands she be alive, founded (has an ovipositor), and neither contained nor inhibited. A seat
     * failing only the second set was re-elected forever: elect, load, surrender, elect again on the very next scan -
     * once every 5 seconds in Razorem's log, 80 times in 7 minutes, each cycle also firing "empress influence gained -
     * expanding and resuming construction" and the matching contraction. That churn, not the logging, is the lag.
     * <p>
     * The gap cannot be closed at election time: whether she is inhibited or holds an ovipositor lives on the entity,
     * and the seat hive is by design NOT loaded when the vote happens. So a failed seat is benched instead. Transient
     * on purpose - a restart is a fine moment to reconsider.
     */
    private static final java.util.Map<com.alien.common.gameplay.hive.id.HiveLocationId, Long> SEAT_RETRY_AT =
        new java.util.HashMap<>();

    /** Long enough that a hopeless seat costs one scan per five minutes instead of one every five seconds. */
    private static final long SEAT_RETRY_COOLDOWN_TICKS = 20L * 60L * 5L;

    /** Bench a seat that surrendered the crown, so the next scan does not immediately re-elect it. */
    public static void benchSeat(com.alien.common.gameplay.hive.id.HiveLocationId seatId, long currentTick) {
        SEAT_RETRY_AT.put(seatId, currentTick + SEAT_RETRY_COOLDOWN_TICKS);
    }

    /** Cleared the moment a seat successfully crowns, so a working seat never carries a stale bench entry. */
    public static void clearBench(com.alien.common.gameplay.hive.id.HiveLocationId seatId) {
        SEAT_RETRY_AT.remove(seatId);
    }

    public static @Nullable HiveLocation pickSeat(
        LineageFactionData lineage,
        net.minecraft.server.MinecraftServer server,
        long currentTick
    ) {
        var config = HiveLocationRegistry.INSTANCE.config();
        Candidate best = null;

        for (var location : lineage.locationsById().values()) {
            if (!location.isAlive() || location.isExiled()) {
                // An exiled remnant is alive but written off - it can never seat the next empress.
                continue;
            }
            var retryAt = SEAT_RETRY_AT.get(location.id());
            if (retryAt != null) {
                if (currentTick < retryAt) {
                    continue; // benched after surrendering the crown - see SEAT_RETRY_AT
                }
                SEAT_RETRY_AT.remove(location.id());
            }
            if (location.founderId() == null) {
                // Null founder == queenless, which is what QueenInhibitionService sets when a queen is inhibited and
                // what the growth/economy tasks already read. A hive with no seated queen has nobody to crown.
                continue;
            }

            // ASK HER DIRECTLY WHEN SHE IS LOADED. founderId is only cleared by inhibition and by death, so a
            // queen who is merely CONTAINED (four chains) or not yet founded keeps her founder link and sails
            // through every persisted test - then fails resolveSeatedQueen the instant the seat materializes.
            // That mismatch is what looped: elect, surrender, elect again on the next scan, five seconds apart,
            // each round granting and revoking empress influence (expand, resume construction, contract).
            //
            // When the entity is resolvable we can run the REAL test here and simply never elect her. When she is
            // not loaded we cannot know, and the bench above catches that case after the fact.
            var seatLevel = server.getLevel(location.dimension());
            if (
                seatLevel != null
                    && seatLevel.getEntity(location.founderId()) != null
                    && resolveSeatedQueen(seatLevel, location) == null
            ) {
                continue;
            }

            // Computed once and reused: it is both the eligibility floor AND the second comparator.
            var members = CastePopulation.totalTrackedPopulation(location);
            if (members < config.empressCandidateMinMembers()) {
                // Eligibility floor. Defaults to 0 (off) - the design gate is hive COUNT, not population.
                continue;
            }

            var candidate = new Candidate(
                location,
                location.claimedChunks().size(),
                members,
                location.ageInTicks()
            );

            if (best == null || compare(candidate, best, lineage) > 0) {
                best = candidate;
            }
        }

        return best == null ? null : best.location();
    }

    /**
     * The living, eligible queen physically seated at {@code location}, or {@code null}.
     * <p>
     * Called when an elected seat finally loads, to find the body the election already committed to. The eligibility
     * re-check matters: election happens abstractly and possibly a long time earlier, so she may have been captured or
     * inhibited in the meantime. Returning null there tells the caller to release the seat and re-elect.
     */
    public static @Nullable Queen resolveSeatedQueen(ServerLevel serverLevel, HiveLocation location) {
        var founderId = location.founderId();
        if (founderId == null) {
            return null;
        }

        var entity = serverLevel.getEntity(founderId);
        if (!(entity instanceof Queen queen) || !queen.isAlive() || queen.isRemoved()) {
            return null;
        }
        if (!queen.hasOvipositor()) {
            // Only a founded queen may be crowned - the empress pipeline assumes she already holds a hive and never
            // founds one, so a pre-founding queen would be stranded.
            return null;
        }
        if (queen.isContained() || queen.isInhibited()) {
            // Bound or inhibited queens can never become empress.
            return null;
        }

        return queen;
    }

    /** Returns &gt; 0 when {@code a} is the better seat. Walks the chain until one differentiates. */
    private static int compare(Candidate a, Candidate b, LineageFactionData lineage) {
        // 1. Most claims wins.
        var claimCmp = Integer.compare(a.claimCount(), b.claimCount());
        if (claimCmp != 0) {
            return claimCmp;
        }

        // 2. Bigger hive wins (tracked members: loaded + reserves).
        var memberCmp = Integer.compare(a.memberCount(), b.memberCount());
        if (memberCmp != 0) {
            return memberCmp;
        }

        // 3. Older hive wins.
        var seatCmp = Long.compare(a.locationAgeTicks(), b.locationAgeTicks());
        if (seatCmp != 0) {
            return seatCmp;
        }

        // 4. The lineage founder's own seat wins.
        var founderId = lineage.founderId();
        if (founderId != null) {
            var aFounder = founderId.equals(a.location().founderId());
            var bFounder = founderId.equals(b.location().founderId());
            if (aFounder != bFounder) {
                return aFounder ? 1 : -1;
            }
        }

        // 5. Location-id lex - lex-greater wins, so the order is total and stable across restarts.
        return a.location().id().value().toString().compareTo(b.location().id().value().toString());
    }

    private record Candidate(
        HiveLocation location,
        int claimCount,
        int memberCount,
        long locationAgeTicks
    ) {}
}
