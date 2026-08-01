package com.alien.common.gameplay.hive.economy;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;

/**
 * Per-tick jelly production. For each location:
 * <ul>
 * <li>{@code royalJellyAccumulator += queen count}. At
 * {@link com.alien.common.gameplay.hive.config.HiveConfig#royalJellyTicksPerProduction()}, grant +1 royal jelly.</li>
 * <li>{@code queenScourgeAccumulator += queen count}. At
 * {@link com.alien.common.gameplay.hive.config.HiveConfig#scourgeJellyTicksPerQueenProduction()}, grant +1 scourge
 * jelly.</li>
 * <li>{@code harbingerScourgeAccumulator += harbinger count}. At
 * {@link com.alien.common.gameplay.hive.config.HiveConfig#scourgeJellyTicksPerHarbingerProduction()}, grant +1
 * scourge.</li>
 * </ul>
 * Grants are capped by claimed chunk count; overflow is discarded (the accumulator is still subtracted so the same tick
 * budget isn't re-banked into the next minute).
 * <p>
 * Producers are currently loaded location members plus local reserves. Persisted unloaded UUIDs are intentionally
 * ignored because they can be stale.
 */
public final class JellyProduction {

    /**
     * Lineages are processed in buckets, each evaluated once per this many ticks, chosen by its own id hash so the
     * empire spreads across ticks instead of spiking on one.
     * <p>
     * SAFE BECAUSE THIS IS AN ACCUMULATOR: the production model is "add the producer count each tick, grant when the
     * accumulator crosses the threshold". An evaluation that stands for {@code LINEAGE_BUCKET_TICKS} ticks therefore
     * adds that many ticks' worth, and the output is IDENTICAL - just granted in slightly coarser steps. Counting
     * producers is the expensive part (it walks loaded members and reserves per caste) and now happens 1/20th as often.
     */
    private static final int LINEAGE_BUCKET_TICKS = 20;

    private JellyProduction() {}

    /**
     * Reused snapshot buffer for the per-tick faction scan. The scan runs only on the single server thread, so one
     * static scratch list per scan is safe; clear+addAll keeps the same iterate-a-snapshot semantics (the loop body may
     * mutate the live faction registry) while allocating nothing once the backing array has grown - this scan used to
     * build a fresh ArrayList of every faction id EVERY TICK just to run its bucket filter.
     */
    private static final java.util.List<net.minecraft.resources.ResourceLocation> SCAN_SCRATCH =
        new java.util.ArrayList<>();

    public static void scanAndProduce(MinecraftServer server) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var currentTick = server.overworld().getGameTime();

        SCAN_SCRATCH.clear();
        SCAN_SCRATCH.addAll(Alien.MOD.factions().getAllIds());
        for (var factionId : SCAN_SCRATCH) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            if (Math.floorMod(currentTick - factionId.hashCode(), LINEAGE_BUCKET_TICKS) != 0) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage) || !lineage.isAlive()) {
                continue;
            }
            for (var location : new ArrayList<>(lineage.locationsById().values())) {
                if (!location.isAlive()) {
                    continue;
                }
                tick(location, config);
            }
        }
    }

    private static void tick(
        HiveLocation location,
        com.alien.common.gameplay.hive.config.HiveConfig config
    ) {
        // A converted hive is a white dwarf: it burns what it has and takes in nothing. Its jelly is whatever the
        // royal and scourge pools merged into at conversion, and that number only ever goes down.
        if (IrradiatedHiveRules.isIrradiated(location)) {
            return;
        }

        var queenCount = countTaggedProducers(location, AlienEntityTypeTags.QUEENS);
        var harbingerCount = countTaggedProducers(location, AlienEntityTypeTags.HARBINGERS);

        if (queenCount > 0) {
            var nextRoyal = location.royalJellyAccumulator() + (long) queenCount * LINEAGE_BUCKET_TICKS;
            var royalThreshold = config.royalJellyTicksPerProduction();
            if (nextRoyal >= royalThreshold) {
                grantRoyal(location, (int) (nextRoyal / royalThreshold), royalJellyCap(location));
                nextRoyal %= royalThreshold;
            }
            location.setRoyalJellyAccumulator(nextRoyal);

            var nextQueenScourge = location.queenScourgeAccumulator() + (long) queenCount * LINEAGE_BUCKET_TICKS;
            var queenScourgeThreshold = config.scourgeJellyTicksPerQueenProduction();
            if (nextQueenScourge >= queenScourgeThreshold) {
                grantScourge(location, (int) (nextQueenScourge / queenScourgeThreshold), scourgeJellyCap(location));
                nextQueenScourge %= queenScourgeThreshold;
            }
            location.setQueenScourgeAccumulator(nextQueenScourge);
        }

        if (harbingerCount > 0) {
            var nextHarbScourge = location.harbingerScourgeAccumulator() + (long) harbingerCount * LINEAGE_BUCKET_TICKS;
            var harbScourgeThreshold = config.scourgeJellyTicksPerHarbingerProduction();
            if (nextHarbScourge >= harbScourgeThreshold) {
                grantScourge(location, (int) (nextHarbScourge / harbScourgeThreshold), scourgeJellyCap(location));
                nextHarbScourge %= harbScourgeThreshold;
            }
            location.setHarbingerScourgeAccumulator(nextHarbScourge);
        }
    }

    private static int countTaggedProducers(
        HiveLocation location,
        net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> tag
    ) {
        var count = 0;
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (entry.getKey().is(tag)) {
                count += entry.getValue().size();
            }
        }
        count += location.localReserves().getReliableCountMatching(type -> type.is(tag));
        return count;
    }

    /**
     * Territory buys jelly capacity - but never below {@code minRoyalJellyCap}.
     * <p>
     * The raw chunk count alone silently gated the two costs the royal line depends on. Crowning a successor and
     * promoting a founder queen both cost 100, so a hive holding fewer than 100 chunks could not bank the price at all
     * - not slowly, EVER. A hive that lost its queen before growing that large stayed queenless permanently, which is
     * not what the replacement firewall was written to do.
     */
    public static int royalJellyCap(HiveLocation location) {
        var floor = HiveLocationRegistry.INSTANCE.config().minRoyalJellyCap();
        return Math.max(floor, location.claimedChunks().size());
    }

    /**
     * Scourge deliberately gets NO floor. Nothing in the scourge economy costs more than a couple of units - the
     * harbinger is 1 - so the chunk count never blocked it the way it blocked royal, and scourge is meant to be the
     * scarce currency. Handing small hives a guaranteed scourge bank would loosen the tier that is supposed to be hard
     * to reach.
     */
    public static int scourgeJellyCap(HiveLocation location) {
        return location.claimedChunks().size();
    }

    private static void grantRoyal(HiveLocation location, int amount, int cap) {
        var newAmount = Math.min(cap, location.royalJelly() + amount);
        location.setRoyalJelly(newAmount);
    }

    private static void grantScourge(HiveLocation location, int amount, int cap) {
        var newAmount = Math.min(cap, location.scourgeJelly() + amount);
        location.setScourgeJelly(newAmount);
    }
}
