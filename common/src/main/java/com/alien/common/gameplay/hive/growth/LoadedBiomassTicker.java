package com.alien.common.gameplay.hive.growth;

import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;

/**
 * Per-second loaded-biomass income for locations with a player nearby. Fires once every {@link #INTERVAL_TICKS} (1
 * second) so the math stays integer-friendly — at the design's default rates ({@code +1/sec/loaded-xeno},
 * {@code +0.5/sec/ovomorph}, {@code +5/sec} empress bonus, {@code +0.1/sec} idle) one second is enough granularity to
 * round to a whole biomass value.
 * <p>
 * Discrete event income (per-kill, per-resin-block) lives elsewhere — this ticker only handles the periodic portion.
 * Bumps {@code lastGrowthTick} so {@link CatchUpEngine}'s slow-path sampling doesn't double-count the same elapsed
 * period using the (lower) unloaded formula.
 * <p>
 * Does NOT attempt claims — those are handled by {@link CatchUpEngine}'s claim loop, which the loaded path also drives
 * via {@link com.alien.common.gameplay.hive.tick.HiveLocationLoadedTickTask}.
 */
public final class LoadedBiomassTicker {

    private static final long INTERVAL_TICKS = 20L;

    private LoadedBiomassTicker() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    /**
     * Adds one second's worth of loaded biomass income to {@code location}, rounded to an integer. Bumps
     * {@code lastGrowthTick} forward to {@code currentTick} so the elapsed time isn't double-counted by the scan task
     * using the (lower) unloaded formula.
     */
    public static void run(HiveLocation location, LineageFactionData lineage, long currentTick) {
        var config = HiveLocationRegistry.INSTANCE.config();
        var perSec = BiomassIncome.loadedPerSecond(location, lineage, config);
        var income = (int) Math.round(perSec);

        if (income > 0) {
            // Oct 8: never lowers a pool banked while unloaded.
            BiomassIncome.addUpToCap(location, income, BiomassIncome.biomassCap(location, config));
        }

        // Even if income is 0 this tick (no loaded entities), bump the growth tick so the scan task's elapsed
        // window doesn't grow into "should have been an unloaded period."
        location.setLastGrowthTick(currentTick);
    }
}
