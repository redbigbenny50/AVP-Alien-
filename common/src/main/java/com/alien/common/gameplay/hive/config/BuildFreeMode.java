package com.alien.common.gameplay.hive.config;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;

/**
 * ⭐⭐⭐ ONE PLACE TO ASK "IS THIS HIVE IN BUILD-FREE MODE?".
 * <p>
 * [stated] "build mode is a custom mode for adventure maps and peoples personal play styles" — a hive that claims,
 * spreads resin, lays eggs and fights, but never CARVES or STAMPS a structure. The mapmaker has already built the
 * space; the hive infests it rather than rebuilding it.
 * </p>
 * <p>
 * ⚠⚠ EVERY GATE THE MODE CHANGES ROUTES THROUGH HERE. The alternative — reading
 * {@code HiveLocationRegistry.INSTANCE.config().buildFreeModeEnabled()} at a dozen call sites — is how a mode ends up
 * half-applied: one branch checks it, the next forgets, and the hive sits in an impossible state that only shows up as
 * a field report weeks later. A single named predicate also makes it greppable, which matters because the mode touches
 * founding, eggs, vents, hosts, claims and targeting.
 * </p>
 * <p>
 * ⚠ It is a GLOBAL switch, not per-hive. A world is either in build-free mode or it is not; nothing stores the mode on
 * a location. That is deliberate — a mapmaker configures an instance, and a half-and-half world would mean two
 * incompatible sets of rules sharing one territory map.
 * </p>
 */
public final class BuildFreeMode {

    private BuildFreeMode() {}

    /** The master switch. Everything else in this class assumes it has already been consulted. */
    public static boolean isEnabled() {
        return HiveLocationRegistry.INSTANCE.config().buildFreeModeEnabled();
    }

    /**
     * ⭐⭐ HALF the slab band. In build-free mode the hive extends this far ABOVE AND BELOW the queen instead of upward
     * only.
     * <p>
     * ⚠⚠ A NORMAL HIVE'S SLAB IS UP-ONLY — {@code hiveFloorY()} returns her own Y and the band runs to +16. Put a queen
     * on the third floor of a prebuilt base under that rule and EVERY FLOOR BELOW HER IS OUTSIDE THE HIVE: no resin, no
     * spawning, no vents. Centring is what makes the reactor-room case work. End-style hives already centre their slab
     * the same way, which is where the shape is borrowed from.
     * </p>
     */
    public static int slabHalfHeight() {
        return Math.max(1, HiveLocationRegistry.INSTANCE.config().buildFreeSlabHalfHeight());
    }

    /**
     * Territory half-width in chunks for this location, empress-aware.
     * <p>
     * ⚠ Reads the location's own empress state rather than a global, because one lineage may be under an empress while
     * its neighbour is not.
     * </p>
     */
    public static int territoryRadiusChunks(HiveLocation location) {
        var config = HiveLocationRegistry.INSTANCE.config();
        return Math.max(
            1,
            isEmpressInfluenced(location)
                ? config.buildFreeEmpressTerritoryRadiusChunks()
                : config.buildFreeTerritoryRadiusChunks()
        );
    }

    /**
     * Whether the queen founds where she is placed instead of locating an anchor and digging to it.
     * <p>
     * ⚠ FORCES DAUGHTER SLOTS TO ZERO — enforced once at config load rather than here, so the clamp is visible in the
     * file the mapmaker edits instead of being an invisible runtime rule.
     * </p>
     */
    /**
     * ⭐⭐ THE DAUGHTER-HIVE CAP IN BUILD-FREE MODE. [stated] "default is 2 can be changed to 0 for none and a max of 4."
     * <p>
     * ⚠⚠ buildFreeDaughterSlots HAD NO READER AT ALL - one of eight build-free settings I wrote and never wired. The
     * cap fell through to maxDaughterHivesPerLocation, so the build-free value did nothing however it was set.
     * </p>
     * <p>
     * ⚠ CLAMPED 0..4 HERE rather than trusted from the file, because a hand-edited config is the normal way this gets
     * set and a negative or absurd value would otherwise reach the spread logic directly.
     * </p>
     * <p>
     * ⚠ FOUNDS-WHERE-PLACED STILL FORCES 0, as the field's own documentation has always said: a daughter cannot found
     * inside her mother's claim, and in that mode she has nowhere else to go.
     * </p>
     */
    public static int daughterSlots(int normalModeCap) {
        if (!isEnabled()) {
            return normalModeCap;
        }
        if (queenFoundsWherePlaced()) {
            return 0;
        }
        return Math.max(0, Math.min(4, HiveLocationRegistry.INSTANCE.config().buildFreeDaughterSlots()));
    }

    /**
     * ⭐⭐ HOW MANY BEACHHEAD CHUNKS AN INVADER MAY HOLD, CLAMPED 0..6. Zero means invasion is off.
     * <p>
     * [stated] "lowest would be 0 for not allowing them to and max i would say is 6. default should be 2."
     * </p>
     * <p>
     * ⚠ THE CLAIM PATH ITSELF IS NOT BUILT YET - the invader has to physically BE on the chunk to take it, and at
     * {@link #invasionReachChunks()} that delivery is the INVASION CONVOY, which does not exist. This accessor and its
     * clamp are here so the numbers are settled and correct when that lands; nothing reads it yet.
     * </p>
     */
    public static int invasionBeachheadChunks() {
        if (!isEnabled()) {
            return 0;
        }
        return Math.max(0, Math.min(6, HiveLocationRegistry.INSTANCE.config().buildFreeInvasionBeachheadChunks()));
    }

    /** How far beyond its own territory box a hive may reach to invade. Default 19 - see the field's own note. */
    public static int invasionReachChunks() {
        return isEnabled() ? HiveLocationRegistry.INSTANCE.config().buildFreeInvasionReachChunks() : 0;
    }

    /** Biomass the INVADER pays per beachhead chunk. [stated] the invading force pays, not the defender. */
    public static int invasionClaimBiomassCost() {
        return isEnabled() ? HiveLocationRegistry.INSTANCE.config().buildFreeInvasionClaimBiomassCost() : 0;
    }

    public static boolean queenFoundsWherePlaced() {
        return isEnabled() && HiveLocationRegistry.INSTANCE.config().buildFreeQueenFoundsWherePlaced();
    }

    private static boolean isEmpressInfluenced(HiveLocation location) {
        var faction = com.alien.Alien.MOD.factions().get(location.lineageFactionId());
        return faction != null
            && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage
            && lineage.empressId() != null;
    }
}
