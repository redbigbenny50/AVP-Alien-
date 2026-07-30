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
    public static @Nullable HiveLocation pickSeat(LineageFactionData lineage) {
        var config = HiveLocationRegistry.INSTANCE.config();
        Candidate best = null;

        for (var location : lineage.locationsById().values()) {
            if (!location.isAlive() || location.isExiled()) {
                // An exiled remnant is alive but written off - it can never seat the next empress.
                continue;
            }
            if (location.founderId() == null) {
                // Null founder == queenless, which is what QueenInhibitionService sets when a queen is inhibited and
                // what the growth/economy tasks already read. A hive with no seated queen has nobody to crown.
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
