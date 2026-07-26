package com.alien.common.gameplay.hive.empress;

import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Picks which queen of a lineage should become its empress, deterministically.
 * <p>
 * A queen is only a <b>candidate</b> at all if she is <b>unbound</b> (not {@link Queen#isContained()}),
 * <b>uninhibited</b> (not {@link Queen#isInhibited()}), has an established hive — a location, a placed slab, and an
 * eggsack (i.e., she has founded: {@link Queen#hasOvipositor()}) — and her hive's tracked member count is at least
 * {@code config.empressCandidateMinMembers()} (a pass/fail eligibility floor, not a ranking factor).
 * <p>
 * Among eligible candidates, comparators apply in order:
 * <ol>
 * <li>Most claimed chunks on her hive location wins.</li>
 * <li>If tied, older queen wins (compare entity {@code tickCount}).</li>
 * <li>If still tied — queen at the older location wins (seat location's {@code ageInTicks}), then founder tiebreak,
 * then UUID lex compare — kept as a last-resort total order so the result is stable across restarts.</li>
 * </ol>
 * <p>
 * Returns {@code null} when no eligible loaded queens exist in any of the lineage's locations. Phase 10 calls this from
 * {@link EmpressEmergenceTask}.
 */
public final class EmpressCandidatePicker {

    private EmpressCandidatePicker() {}

    public static @Nullable Queen pick(MinecraftServer server, LineageFactionData lineage) {
        var serverLevel = server.getLevel(lineage.dimension());
        if (serverLevel == null) {
            return null;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        Candidate best = null;

        for (var location : lineage.locationsById().values()) {
            for (var entry : location.loadedMembersByType().entrySet()) {
                if (!entry.getKey().is(AlienEntityTypeTags.QUEENS)) {
                    continue;
                }
                for (var uuid : entry.getValue()) {
                    var entity = serverLevel.getEntity(uuid);
                    if (!(entity instanceof Queen queen) || !queen.isAlive()) {
                        continue;
                    }
                    if (!queen.hasOvipositor()) {
                        // Only a founded queen (one who has laid her ovipositor/eggsack) may become an empress. A
                        // pre-founding queen merely wandering in claimed territory would be stranded, since the empress
                        // pipeline assumes she already has a hive and never founds one.
                        continue;
                    }
                    if (queen.isContained()) {
                        // Bound (chained/enclosed) queens can never become empress — she must be unbound.
                        continue;
                    }
                    if (queen.isInhibited()) {
                        // Inhibited queens can never become empress — she must be uninhibited.
                        continue;
                    }
                    if (CastePopulation.totalTrackedPopulation(location) < config.empressCandidateMinMembers()) {
                        // Eligibility floor, not a ranking factor — her hive must be at least this populous to be
                        // considered at all.
                        continue;
                    }

                    var candidate = new Candidate(queen, location.claimedChunks().size(), location.ageInTicks());

                    if (best == null || compare(candidate, best, lineage) > 0) {
                        best = candidate;
                    }
                }
            }
        }

        return best == null ? null : best.queen();
    }

    /** Returns > 0 when {@code a} is preferred over {@code b}. Walks the comparator chain until one differentiates. */
    private static int compare(Candidate a, Candidate b, LineageFactionData lineage) {
        // 1. Most claims wins.
        var claimCmp = Integer.compare(a.claimCount(), b.claimCount());
        if (claimCmp != 0) {
            return claimCmp;
        }

        // 2. Older queen wins.
        var tickCmp = Integer.compare(a.queen().tickCount, b.queen().tickCount);
        if (tickCmp != 0) {
            return tickCmp;
        }

        // 3. Queen at the older location wins (location ageInTicks).
        var seatCmp = Long.compare(a.locationAgeTicks(), b.locationAgeTicks());
        if (seatCmp != 0) {
            return seatCmp;
        }

        // 4. Founder match wins.
        var founderId = lineage.founderId();
        if (founderId != null) {
            if (founderId.equals(a.queen().getUUID()) && !founderId.equals(b.queen().getUUID())) {
                return 1;
            }
            if (founderId.equals(b.queen().getUUID()) && !founderId.equals(a.queen().getUUID())) {
                return -1;
            }
        }

        // 5. UUID lex compare — lex-greater wins so smaller UUIDs lose. Stable across restarts.
        return uuidLex(a.queen().getUUID(), b.queen().getUUID());
    }

    private static int uuidLex(UUID a, UUID b) {
        var hi = Long.compare(a.getMostSignificantBits(), b.getMostSignificantBits());
        if (hi != 0) {
            return hi;
        }
        return Long.compare(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    /** Cached per-candidate metrics so comparisons don't repeatedly re-walk {@link HiveLocation} lookups. */
    private record Candidate(
        Queen queen,
        int claimCount,
        long locationAgeTicks
    ) {}
}
