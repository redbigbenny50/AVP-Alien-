package com.alien.common.gameplay.hive.war;

import com.alien.Alien;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.util.RandomSource;

/**
 * The war nobody is watching. [stated] "when the chunks unload the war continues simulated until a victor is decided
 * this can be calculated wargame style using stats numbers and random rolls."
 * <p>
 * This is what stops an unloaded war from hanging forever - the loaded end condition cannot judge a hive whose members
 * are not ticking, so instead of guessing it, the two sides trade abstract casualties on a slow clock until one of them
 * has nothing left. Deliberately coarse: strength is population plus bank plus a queen, tilted by the last stand, and
 * each round takes a bite proportional to the other side's strength with a roll on top. It is not a battle model, it is
 * a fair way to end a fight the player never saw.
 */
public final class WarSimulation {

    /** One exchange per this many ticks of war. Slow on purpose - an unwatched war should take real time. */
    private static final long ROUND_INTERVAL_TICKS = 600L;

    /** A queen in the hive is worth this much strength on top of the headcount. */
    private static final int QUEEN_STRENGTH = 6;

    /** [stated] the last stand's 25%, applied to the whole side's strength. */
    private static final double LAST_STAND_MULTIPLIER = 1.25D;

    /** Casualties per round, as a fraction of the enemy's strength, before the roll. */
    private static final double BITE_FRACTION = 0.12D;

    private WarSimulation() {}

    /** Game time of the last round fought anywhere. Rounds are global: one sweep resolves every open war at once. */
    private static long lastRoundTick = Long.MIN_VALUE;

    /**
     * Claims this sweep's round, at most one per interval, and reports whether it is due.
     * <p>
     * MUST NOT be a modulo of the game time. The war sweep runs on the contest cadence (60s by default), so a `gameTime
     * % 600` test only ever fired because 1200 happens to divide by 600 - retune contestTickWindow to, say, 500 and
     * simulated wars would have run a round every 3000 ticks instead of every 600, or with an odd value, never. Elapsed
     * time cannot drift like that.
     */
    public static boolean claimRound(long gameTime) {
        if (gameTime - lastRoundTick < ROUND_INTERVAL_TICKS) {
            return false;
        }
        lastRoundTick = gameTime;
        return true;
    }

    /**
     * Trades one round of casualties between two hives. Returns true if either side was emptied by it - the caller then
     * concludes the war exactly as it would for a fight that played out on loaded chunks.
     */
    public static boolean fightRound(RandomSource random, HiveLocation first, HiveLocation second) {
        int firstStrength = strengthOf(first);
        int secondStrength = strengthOf(second);
        if (firstStrength <= 0 || secondStrength <= 0) {
            return true; // somebody was already finished before the round began
        }

        int firstLosses = casualties(random, secondStrength);
        int secondLosses = casualties(random, firstStrength);

        // Simultaneous: both sides take the round's damage, so a war can end in the mutual bleed-out the loaded path
        // already handles. Losses come out of the bank because an unloaded hive's members ARE its bank.
        int firstTaken = applyCasualties(first, firstLosses);
        int secondTaken = applyCasualties(second, secondLosses);

        if (firstTaken > 0 || secondTaken > 0) {
            Alien.LOGGER.debug(
                "War round (simulated): {} lost {}, {} lost {} — strength {} vs {}.",
                first.id(),
                firstTaken,
                second.id(),
                secondTaken,
                firstStrength,
                secondStrength
            );
        }

        return strengthOf(first) <= 0 || strengthOf(second) <= 0;
    }

    /** Everything a hive can bring: bodies it is tracking, bodies in the bank, and its queen. */
    private static int strengthOf(HiveLocation location) {
        if (!location.isAlive()) {
            return 0;
        }
        int strength = CastePopulation.totalTrackedPopulation(location) + location.localReserves().getReliableCount();
        if (CastePopulation.countCaste(location, com.alien.common.registry.tag.AlienEntityTypeTags.QUEENS) > 0) {
            strength += QUEEN_STRENGTH;
        }
        return location.isInLastStand() ? (int) Math.round(strength * LAST_STAND_MULTIPLIER) : strength;
    }

    /** A bite of the enemy's strength, with a roll so identical hives do not grind to a perfect draw every time. */
    private static int casualties(RandomSource random, int enemyStrength) {
        int base = (int) Math.floor(enemyStrength * BITE_FRACTION);
        return Math.max(1, base + random.nextInt(3) - 1);
    }

    /** Spends losses out of the reserve bank, heaviest-available first. Returns how many actually fell. */
    private static int applyCasualties(HiveLocation location, int losses) {
        int taken = 0;
        while (taken < losses) {
            var available = location.localReserves().getReliableAvailableEntityTypes();
            if (available.isEmpty() || !location.localReserves().trySpawn(available.get(0))) {
                break; // nothing left to lose - the strength check below will call it
            }
            taken++;
        }
        if (taken > 0) {
            markLineageDirty(location);
        }
        return taken;
    }

    private static void markLineageDirty(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (
            faction != null
                && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage
        ) {
            lineage.markDirty();
        }
    }
}
