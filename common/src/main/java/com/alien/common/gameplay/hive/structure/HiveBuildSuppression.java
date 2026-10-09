package com.alien.common.gameplay.hive.structure;

/**
 * Suppresses breach flagging while the hive itself is placing blocks.
 * <p>
 * 🚨🚨 THE REPAIR CREW WAS CAUSING THE NEXT BREACH. A breach is flagged whenever a RESIN block becomes air - see
 * MixinBlockBehaviour_HiveBreachFlag - and repairing one re-stamps the structure template over the site. A stamp
 * replaces blocks, so any resin that goes to air during it fires the same hook and flags a NEW breach at once. The crew
 * repaired, tripped itself, and repaired again indefinitely.
 * </p>
 * <p>
 * ⚠⚠ VISIBLE IN A LIVE LOG as "breach at [2, -1] repaired by drone crew" repeating over and over for one chunk, while
 * the player's actual hole in a different chunk was never touched - every repair drone was busy chasing a phantom the
 * previous repair had created.
 * </p>
 * <p>
 * ⚠ SERVER THREAD ONLY, and deliberately not a counter shared across threads: hive building happens on the server
 * thread, and a plain flag with a finally-guard cannot leak.
 * </p>
 */
public final class HiveBuildSuppression {

    private static int depth;

    private HiveBuildSuppression() {}

    /** Whether the hive is currently placing blocks itself. */
    public static boolean isSuppressed() {
        return depth > 0;
    }

    /**
     * Runs hive block placement with breach flagging suppressed.
     * <p>
     * ⚠ Re-entrant on purpose - a stamp can nest inside upkeep inside a repair - and the count is unwound in a finally
     * block so a throw part-way through a stamp cannot leave flagging disabled for the rest of the session.
     * </p>
     */
    public static <T> T without(java.util.function.Supplier<T> placement) {
        depth++;

        try {
            return placement.get();
        } finally {
            depth--;
        }
    }
}
