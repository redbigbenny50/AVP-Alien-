package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.location.HiveLocationSpacing;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * Pure decision function: given a queen and a candidate position, returns what {@link HiveLocationFoundingService}
 * should do — found a new lineage, add a new location to one of her existing lineages, or refuse.
 * <p>
 * Encodes the rules from {@code HIVE_REDESIGN_08_LINEAGE_SPREAD.md} § 1 (spread zone),
 * {@code HIVE_REDESIGN_03_LOCATIONS.md} § 9 (location founding), and {@code HIVE_REDESIGN_02_FACTION_LIFECYCLES.md} § 2
 * (lineage founding). The "settlement validity" sub-check (solid ground, resin-replaceable surface, Y-level) is parked
 * for a future phase — Phase 5 only enforces the spread-zone and overlap rules.
 */
public final class SpreadZoneCheck {

    /**
     * How far below the ground a hive core has to sit.
     * <p>
     * [stated] "it should be at least 17 blocks underground because the height of the structure is 16" - so it is
     * derived from HiveStampEviction.SLAB_BAND_BLOCKS rather than written as 17, and the two cannot drift.
     * </p>
     */
    private static final int MIN_BURIAL_DEPTH =
        com.alien.common.gameplay.hive.structure.HiveStampEviction.SLAB_BAND_BLOCKS + 1;

    /**
     * ⚠ Shown to players, not just logged - a queen wandering off for no visible reason is the confusing part. [stated]
     * "generate a message ... that way someone trying to found a new one without waking/knowing an exsiting legacy hive
     * is there knows why its moving or at least something is making it move."
     */
    public static final String LEGACY_SLEEPER_REASON =
        "Old echoes still linger - the hive returns to its search";

    private SpreadZoneCheck() {}

    public static SpreadZoneResult evaluate(Xenomorph queen, BlockPos position) {
        return evaluate(queen, position, false);
    }

    /**
     * As {@link #evaluate(Xenomorph, BlockPos)}, but {@code operatorOverride} waives Rule 0 - the surface rule.
     * <p>
     * [stated] "exempt the command if someone wants to do it let them." /avp hive skip settlement exists to put a hive
     * where you are standing, and you are almost always standing on the surface when you reach for it, so the depth
     * rule would have quietly broken the one command whose whole purpose is to override placement.
     * </p>
     * <p>
     * ⚠⚠ RULE 0 ONLY. Rule 1 - somebody else's territory - still applies, because that one is not about taste. Two
     * hives sharing ground corrupts spacing, contests and lineage membership for both, and an operator saying "put it
     * here" is not saying "put it inside that other hive".
     * </p>
     * <p>
     * ⚠ AN OVERRIDDEN HIVE IS A SURFACE HIVE, with everything that implies - it will not have a dug chamber under it.
     * That is the operator's call to make, which is the point.
     * </p>
     */
    public static SpreadZoneResult evaluate(Xenomorph queen, BlockPos position, boolean operatorOverride) {
        var dimension = queen.level().dimension();
        var candidateChunk = new ChunkPos(position);

        // ⭐⭐ RULE 0, THE PARKED SETTLEMENT-VALIDITY CHECK: A HIVE IS NOT BUILT IN THE OPEN.
        //
        // [stated] "she made it on the surface but never actually built." Nothing on this path had ever looked at Y.
        // The ONLY thing that ever put a queen underground was the LOCATION dig phase, so any route that reached
        // settlement without one - a queen who lost her hive, an interrupted founding - founded wherever she stood,
        // and a queen standing on a beach raised a claim over open water with no chamber under it. Every downstream
        // symptom followed from that: 0 egg chambers, eggs hauled nowhere and banked, a queen_chamber_3x3 carve site
        // commissioned at sea level that can never complete.
        //
        // Blocked is the RIGHT answer rather than a silent shove: HiveManager answers a Blocked result with
        // restartLocationPhase(), which re-picks a weighted-Y anchor and sends her digging. So this reads as "not
        // here - go underground first", and it is self-healing.
        //
        // ⚠ END-STYLE IS EXEMPT AND MUST STAY EXEMPT. There is only void below the island, so those hives found
        // ON the surface by design - enterLocation() has the same carve-out. Anything with a ceiling (the Nether)
        // measures "open" as a shelf with clear air above it rather than sky, which DimensionHiveProfiles already
        // handles; this just asks it the question.
        if (
            queen.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                && !com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.isEndStyle(serverLevel)
        ) {
            var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(serverLevel);
            // !!! SKY IS ONLY ONE OF THE TWO TESTS, AND ON ITS OWN IT MISSED THE COMMONEST CASE.
            // isOpenToWorld resolves to level.canSeeSky() in a sky dimension, and canSeeSky reads the MOTION_BLOCKING
            // heightmap - WHICH LEAVES AND FLUIDS BOTH BLOCK. So a queen on a forest floor and a queen on an ocean
            // floor were both "not open to the world" and both founded on the surface, which is exactly the pair of
            // reports this fixes: "instead of digging down they make a hive on the surface" under a canopy, and a
            // hive raised on a seabed. One line, two bug reports.
            //
            // DEPTH IS THE REAL QUESTION. [stated] "depth is the way to gauge it though you are correct no sky is
            // just one check it should have" - so both are asked, and either one refuses her.
            // !!! A FLAT WORLD IS EXEMPT FROM BOTH GATES, NOT WALKED THROUGH THEM.
            // [stated] "cant the system detect if its a superflat world and adjust those gates acordingly? like the
            // sky rule obviously shouldnt apply if its superflat or the depth rule." A default flat world is grass at
            // y=-60 over bedrock at y=-64: every position in it is open to the sky AND above sea level, so both gates
            // refuse everywhere, forever. The two-failed-attempts grace made her wander twice first for no reason.
            // Detecting the generator answers it once, up front, and cheaply.
            //
            // ⚠ THIS DOES NOT LET HER SETTLE IN SOMEBODY ELSE'S GROUND - Rule 1 is below and still runs.
            // ⚠ THE CONFIG FLAG WAIVES RULE 0 TOO, or turning it on would produce queens who commit to founding
            // where they stand and are then refused for standing there. A server that enables it is asking for
            // surface hives. Rule 1 below still runs, so this cannot overlap territory.
            // 🚨 Oct 1: BUILD-FREE FOUND-IN-PLACE WAIVES IT TOO. Only the ordinary flag did, so a queen a mapmaker set
            // down
            // in a surface building (the Hadley's Hope case this mode exists for) was refused as "open to the world" or
            // "too shallow" - and, having nowhere else she may found, halted for good. Rule 1 below still runs.
            var foundsWhereStanding = com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhaseManager
                .foundsWhereStanding()
                || com.alien.common.gameplay.hive.config.BuildFreeMode.queenFoundsWherePlaced();

            if (
                !operatorOverride && !foundsWhereStanding && !isFlatWorld(serverLevel)
                    && (com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.isOpenToWorld(serverLevel, profile, position)
                        || isTooShallow(serverLevel, profile, position))
            ) {
                // ⭐⭐⭐ A WORLD WITH NO COVER ANYWHERE MUST NOT MAKE HER WANDER FOREVER.
                //
                // ⚠⚠ A LIVE SUPERFLAT LOG IS WHY THIS EXISTS. Rule 0 refused her, HiveManager re-picked an anchor,
                // she walked, slept, woke, and was refused again - drifting further from spawn every cycle
                // (z 664 -> 936 -> 1096) because there is no rock overhead ANYWHERE in that world to find. The rule
                // was working perfectly and the outcome was still a queen who could never settle.
                //
                // ⚠ TWO conditions, and BOTH are required. [stated] "detect if its a superlative world with no cover
                // because people like to test" AND [stated] "change it to 2 failed attempts". The counter alone would
                // eventually surface-found in an ordinary world where she simply had bad luck; the cover check alone
                // would surface-found instantly on the first try in a flat world and skip the search entirely.
                //
                // ⚠⚠ THIS DOES NOT LET HER SETTLE IN SOMEBODY ELSE'S TERRITORY. Rule 1 is BELOW this and still runs -
                // [stated] "as long as its not in another's hive area/ territory". Relaxing Rule 0 relaxes only the
                // "must be underground" requirement, never the "must be unclaimed" one.
                if (
                    !(queen.getSurfaceFoundingAttempts() >= SURFACE_FOUNDING_GRACE_ATTEMPTS
                        && worldHasNoCover(serverLevel, position))
                ) {
                    queen.recordSurfaceFoundingRefusal();
                    return new SpreadZoneResult.Blocked(
                        "position " + position
                            + " is open to the world - a hive is founded underground, not on the surface"
                            + " (attempt " + queen.getSurfaceFoundingAttempts()
                            + " of " + SURFACE_FOUNDING_GRACE_ATTEMPTS + ")"
                    );
                }

                // ⚠⚠ ONCE PER QUEEN, NOT ONCE PER ATTEMPT. The same queen logged this 152 TIMES in one session at 152
                // different positions - she is allowed, something else refuses her, she re-picks, and it repeats. The
                // message is worth having once; 152 copies bury everything else in the log, which is exactly how the
                // real faults in this file went unnoticed for so long.
                if (SURFACE_FOUNDING_ANNOUNCED.add(queen.getUUID())) {
                    com.alien.Alien.LOGGER.info(
                        "Hive: {} has been refused {} times and this world has no cover - allowing a surface founding at {}",
                        queen.getUUID(),
                        queen.getSurfaceFoundingAttempts(),
                        position
                    );
                }
            }
        }

        // Rule 1: never settle on top of an existing location's territory (any lineage, any variant).
        var occupant = HiveLocationRegistry.INSTANCE.getByChunk(dimension, candidateChunk);
        if (occupant != null) {
            return new SpreadZoneResult.Blocked(
                "chunk " + candidateChunk + " is already claimed by location " + occupant.id()
            );
        }

        var queenVariant = queen.getVariant();
        var config = HiveLocationRegistry.INSTANCE.config();
        if (
            !HiveLocationSpacing.isFarEnoughFromExistingLocations(
                dimension,
                candidateChunk,
                config.minimumHiveLocationDistanceChunks()
            )
        ) {
            return new SpreadZoneResult.Blocked(
                "chunk " + candidateChunk + " is too close to an existing hive location"
            );
        }

        // ⭐⭐⭐ A SLEEPING LEGACY QUEEN RESERVES HER GROUND EVEN THOUGH SHE OWNS NO LOCATION YET.
        //
        // [stated] "mark the spot shes in as a territory so even if shes still asleep a new hive trying to found
        // still cant ... then its up to the player to kill the old queen, wake her up with a command, or inhibit
        // her."
        //
        // ⚠⚠ THIS IS THE HOLE THAT LET TWO HIVES SHARE GROUND. The spacing test above walks REGISTERED locations,
        // and a dormant legacy queen has none until recovery restores hers - so her ground looked empty, a new queen
        // founded on it, and when her hive was finally restored the two overlapped. Neither had done anything wrong.
        //
        // ⭐ RESERVING HER SPOT IS THE RIGHT SHAPE because it needs no new state: she is already an entity in the
        // world, already flagged dormant, and already findable. Blocking here means the arriving queen simply keeps
        // looking, which is behaviour the founding path already handles.
        // 🚨🚨 A PURGED WORLD HAS NO SLEEPERS, WHATEVER THE ENTITY FLAG SAYS.
        //
        // ⚠⚠ THIS IS THE CHECK THAT ACTUALLY ENDS IT, AND I PUT IT IN THE WRONG PLACE TWICE. `legacyDormant` is a
        // field ON THE QUEEN ENTITY, so discarding the sleepers a player can see does not help: one sitting in an
        // UNLOADED chunk is invisible to any sweep, then loads later and refuses a founding on a world the player
        // was told was empty. Clearing the save data does not reach her either - she already exists.
        //
        // ⭐ Asking the world whether legacy recovery has been purged covers every one of them at once, loaded or
        // not, now or in an hour. A player who has retired this world's legacy data has said what he wants.
        var legacyRetired = queen.level().getServer() != null
            && com.alien.common.gameplay.hive.migration.LegacyHiveRecoveryData
                .getOrCreate(queen.level().getServer())
                .isSomeAnd(com.alien.common.gameplay.hive.migration.LegacyHiveRecoveryData::legacyPurged);

        if (!legacyRetired && queen.level() instanceof net.minecraft.server.level.ServerLevel sleeperLevel) {
            var reserveRadius = config.minimumHiveLocationDistanceChunks() * 16.0;
            var sleeper = com.alien.common.gameplay.hive.migration.LegacyHiveRecovery.dormantLegacyQueensIn(
                sleeperLevel,
                new net.minecraft.world.phys.AABB(candidateChunk.getMiddleBlockPosition(queen.blockPosition().getY()))
                    .inflate(reserveRadius)
            );
            if (!sleeper.isEmpty()) {
                return new SpreadZoneResult.Blocked(LEGACY_SLEEPER_REASON);
            }
        }

        var maxSpread = config.maxLineageSpreadChunks();

        // Rule 2: collect this queen's lineage memberships (filtered to lineages of her variant + dimension).
        ResourceLocation ownLineageInRange = null;
        for (var factionId : Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (lineage.variant() != queenVariant || !lineage.dimension().equals(dimension)) {
                continue;
            }
            if (isWithinSpreadZone(lineage, candidateChunk, maxSpread)) {
                // Location cap - a lineage holds at most maxLocationsPerLineage member hives (8 by default); with
                // an empress the tighter of the two caps applies. When her own lineage is FULL she does not just
                // get blocked (which would shove her outside the spread zone to found a new lineage by geometric
                // accident, overshooting the 17-chunk daughter distance). Instead she founds a NEW LINEAGE right
                // here: overflow deliberately spawns a fresh lineage at the same daughter distance rather than a
                // 9th hive in this one. Under cap, she founds a daughter LOCATION in this lineage as normal.
                var cap = lineage.empressId() != null
                    ? Math.min(config.maxLocationsUnderEmpress(), config.maxLocationsPerLineage())
                    : config.maxLocationsPerLineage();
                if (lineage.activeLocationCount() >= cap) {
                    return new SpreadZoneResult.NewLineage();
                }
                ownLineageInRange = factionId;
                break;
            }
        }

        if (ownLineageInRange != null) {
            return new SpreadZoneResult.NewLocation(ownLineageInRange);
        }

        // Rule 3: she is NOT within any of her own lineages' spread zones (or has no lineage). Territory now
        // belongs to whoever's spread zone she is standing in:
        // - Inside another same-variant lineage M's zone, M UNDER its 8-cap -> she is ADOPTED into M and
        // founds a daughter location of M (foundNewLocation sheds her old lineage). She is an M queen now,
        // and so is the hive she raises - allegiance fully transfers.
        // - Inside another same-variant lineage M's zone, but every covering lineage is FULL -> she founds a
        // NEW rival lineage right here as an INVADER (only the 17-chunk spacing gate applies, not the spread
        // repulsion - she is allowed inside the 32-chunk zone).
        // - Inside nobody's zone -> fresh new lineage.
        // Among covering lineages, adoption is preferred over invasion: she joins the NEAREST under-cap lineage;
        // only when no covering lineage has room does she invade.
        ResourceLocation nearestAdopter = null;
        var nearestAdopterDistance = Integer.MAX_VALUE;
        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (lineage.variant() != queenVariant || !lineage.dimension().equals(dimension)) {
                continue;
            }
            if (!isWithinSpreadZone(lineage, candidateChunk, maxSpread)) {
                continue;
            }
            var cap = lineage.empressId() != null
                ? Math.min(config.maxLocationsUnderEmpress(), config.maxLocationsPerLineage())
                : config.maxLocationsPerLineage();
            if (lineage.activeLocationCount() >= cap) {
                continue; // this lineage is full - can't adopt her; she'd invade unless another has room
            }
            var distance = nearestLocationDistance(lineage, candidateChunk);
            if (distance < nearestAdopterDistance) {
                nearestAdopterDistance = distance;
                nearestAdopter = factionId;
            }
        }

        if (nearestAdopter != null) {
            return new SpreadZoneResult.NewLocation(nearestAdopter); // adopted into M as a daughter
        }

        // Either covered only by FULL lineages (invader) or covered by none (fresh ground): both found a new
        // lineage here. The 17-chunk spacing gate above already ran, so an invader still can't overlap a hive.
        return new SpreadZoneResult.NewLineage();
    }

    /**
     * Whether {@link #evaluate} would permit founding in {@code chunk} for this queen (identical spacing + spread-zone
     * rules). The queen's anchor picker uses this so it only proposes anchors founding will actually accept: otherwise
     * it can pick a spot that clears the minimum LOCATION spacing yet sits inside another lineage's larger SPREAD zone,
     * whereupon founding blocks, she restarts LOCATION, re-picks the very same spot, and loops forever. Y is irrelevant
     * to the spacing/spread rules, so the chunk centre at the queen's Y is used.
     */
    /** As {@link #wouldAllow(Xenomorph, ChunkPos)}, but probes an EXPLICIT position rather than the actor's own Y. */
    public static boolean wouldAllow(Xenomorph queen, BlockPos probe) {
        return !(evaluate(queen, probe) instanceof SpreadZoneResult.Blocked);
    }

    public static boolean wouldAllow(Xenomorph queen, ChunkPos chunk) {
        var probe = chunk.getMiddleBlockPosition(queen.blockPosition().getY());
        return !(evaluate(queen, probe) instanceof SpreadZoneResult.Blocked);
    }

    /** Chebyshev chunk distance from {@code candidate} to the NEAREST location centre in {@code lineage}. */
    private static int nearestLocationDistance(LineageFactionData lineage, ChunkPos candidate) {
        var best = Integer.MAX_VALUE;
        for (var location : lineage.locationsById().values()) {
            best = Math.min(best, chunkDistance(location, candidate));
        }
        return best;
    }

    private static boolean isWithinSpreadZone(LineageFactionData lineage, ChunkPos candidate, int maxSpread) {
        for (var location : lineage.locationsById().values()) {
            if (chunkDistance(location, candidate) <= maxSpread) {
                return true;
            }
        }
        return false;
    }

    private static int chunkDistance(HiveLocation location, ChunkPos other) {
        var locationChunk = new ChunkPos(location.centerPos());
        var dx = Math.abs(locationChunk.x - other.x);
        var dz = Math.abs(locationChunk.z - other.z);
        // Chebyshev distance — the spread zone is a square, not a circle, matching how chunk distances feel in-game.
        return Math.max(dx, dz);
    }

    /**
     * ⭐ How many surface refusals she tolerates before a coverless world is allowed to have a surface hive.
     * <p>
     * [stated] "change it to 2 failed attempts". Low on purpose - in a world that HAS cover she will have found it long
     * before two refusals, so this only ever fires where the search is hopeless.
     * </p>
     */
    private static final int SURFACE_FOUNDING_GRACE_ATTEMPTS = 2;

    /** How far up from the candidate we look for anything solid before calling the world coverless. */
    /** Queens that have already announced a surface founding. ⚠ Not persisted - one line per queen per run. */
    private static final java.util.Set<java.util.UUID> SURFACE_FOUNDING_ANNOUNCED =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final int COVER_PROBE_HEIGHT = 48;

    /** How far out we sample, so one open clearing in a normal world cannot pass for a flat world. */
    private static final int COVER_PROBE_RADIUS = 32;

    /**
     * ⭐⭐ IS THERE ANY ROCK OVERHEAD ANYWHERE NEAR HER? [stated] "detect if its a superlative world with no cover".
     * <p>
     * ⚠ SAMPLED WIDE, NOT JUST OVERHEAD. A queen standing in a meadow in an ordinary world has nothing directly above
     * her either - probing only her own column would call the overworld coverless. Sampling a spread of columns around
     * her means a normal world nearly always finds a hill, a tree-covered rise or a cave roof somewhere, while a
     * superflat finds nothing however far it looks.
     * </p>
     * <p>
     * ⚠ ONLY EVER REACHED AFTER TWO REFUSALS, so this is not on any hot path - it runs at most twice per queen per
     * settlement attempt.
     * </p>
     */
    /**
     * ⭐⭐⭐ CACHED PER DIMENSION. "Does this world have cover anywhere" IS A PROPERTY OF THE WORLD, NOT THE POSITION.
     * <p>
     * ⚠⚠ MY OWN COMMENT CLAIMED THIS RAN "ON THE FIRST REFUSAL" AND IT DOES NOT. A live log caught ONE queen firing it
     * 152 times in a single session, at 152 different positions - she is allowed a surface founding, something else
     * refuses her, she re-picks, and the whole probe runs again. At 81 columns x 48 blocks that is ~590,000 BLOCK READS
     * for a question whose answer cannot change.
     * </p>
     * <p>
     * ⚠ Keyed on the DIMENSION, not the position: a superflat has no cover anywhere, an ordinary world has it
     * somewhere, and that is exactly the distinction the probe exists to draw. Sampling one place is enough.
     * </p>
     * <p>
     * ⚠ NOT PERSISTED - it is re-derived once per dimension per server run, which costs a single probe and cannot go
     * stale across a world edit.
     * </p>
     */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Boolean> NO_COVER_BY_DIMENSION =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Whether this position is too near the top of the world to bury a hive under.
     * <p>
     * !!! MEASURED FROM SEA LEVEL, NOT FROM THE GROUND SHE IS STANDING ON. [stated] "depth should base itself on sea
     * level not just the ground shes standing on. theres a reason the empress founding started at y40 and not higher."
     * </p>
     * <p>
     * ⚠⚠ GROUND-RELATIVE DEPTH LOOKED RIGHT AND WAS NOT. It would have passed a hive dug seventeen blocks into a
     * mountainside at y=140 - buried by the letter of the rule, nowhere near the band the hive economy is built around,
     * and still up in the terrain a player walks over. An absolute ceiling is also immune to every heightmap edge case
     * that made the sky test wrong in the first place: leaves, water and overhangs cannot move sea level.
     * </p>
     * <p>
     * At vanilla sea level (63) this puts the ceiling at y=46, comfortably above the y=40 the anchor band already
     * targets, so an ordinary dug hive clears it and a surface hive never does.
     * </p>
     */
    private static boolean isTooShallow(
        net.minecraft.server.level.ServerLevel level,
        com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.Profile profile,
        BlockPos position
    ) {
        if (profile.surfaceMode() != com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.SurfaceMode.SKY_SURFACE) {
            return false; // a ceiling dimension has no meaningful sea level; the shelf test covers it
        }

        return position.getY() > level.getSeaLevel() - MIN_BURIAL_DEPTH;
    }

    /**
     * \u2b50 Oct 3 - THE FOUNDING SITE RULE (Rule 0), asked by a position alone. A daughter hive delivered by convoy
     * never walked through {@link #evaluate}, so its site was never held to the rule every queen on foot is: not open
     * to the world, and buried below the sea-level depth line. {@code DaughterHiveSiting} uses this so both paths
     * answer with the SAME test. Flat worlds and found-where-standing modes are the caller's to exempt, exactly as
     * {@code evaluate} does.
     *
     * @param level    the level
     * @param position the candidate hive centre
     * @return true if a hive must not be founded here
     */
    public static boolean isUnfitFoundingSite(net.minecraft.server.level.ServerLevel level, BlockPos position) {
        var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(level);
        return com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.isOpenToWorld(level, profile, position)
            || isTooShallow(level, profile, position);
    }

    /**
     * Whether this dimension is generated flat.
     * <p>
     * Deliberately uncached - see the note in the body.
     * </p>
     */
    public static boolean isFlatWorld(net.minecraft.server.level.ServerLevel level) {
        // ⚠⚠ NOT CACHED ANY MORE - THE CACHE WAS THE BUG. It was a static map keyed by dimension id only, so it
        // outlived the world it was computed in. Open a normal world, quit to the title screen, open a SUPERFLAT one
        // in the same session: "minecraft:overworld" was already answered false and the flat world was treated as
        // normal terrain. Every queen then took the surface-refusal path and dug to bedrock. A field log of Sep 23
        // shows exactly that sequence (two worlds, one session). The instanceof is one field read; nothing to save.
        return level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.FlatLevelSource;
    }

    private static boolean worldHasNoCover(net.minecraft.server.level.ServerLevel level, BlockPos around) {
        var cached = NO_COVER_BY_DIMENSION.get(level.dimension());
        if (cached != null) {
            return cached;
        }
        var answer = probeForCover(level, around);
        NO_COVER_BY_DIMENSION.put(level.dimension(), answer);
        return answer;
    }

    private static boolean probeForCover(net.minecraft.server.level.ServerLevel level, BlockPos around) {
        // !! NO ROOM TO DIG IS ALSO "NO COVER", AND THE OVERHEAD PROBE ALONE WOULD HAVE MISSED IT.
        // The escape hatch exists so a queen in a coverless world can settle instead of wandering forever, and now
        // that Rule 0 measures DEPTH, a world can refuse her for a second reason the probe never looked at: a floor
        // with nothing under it. A default flat world puts grass at y=-60 over bedrock at y=-64, so there is not a
        // seventeen-block column anywhere in it - she would be refused for being too shallow, pass the overhead
        // probe only if it happened to find no roof, and otherwise loop exactly as she did before.
        if (around.getY() - level.getMinBuildHeight() < MIN_BURIAL_DEPTH) {
            return true;
        }

        var step = 8;
        for (var dx = -COVER_PROBE_RADIUS; dx <= COVER_PROBE_RADIUS; dx += step) {
            for (var dz = -COVER_PROBE_RADIUS; dz <= COVER_PROBE_RADIUS; dz += step) {
                var probe = new BlockPos.MutableBlockPos();
                for (var dy = 1; dy <= COVER_PROBE_HEIGHT; dy++) {
                    probe.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                    if (level.isOutsideBuildHeight(probe.getY())) {
                        break;
                    }
                    if (!level.getChunkSource().hasChunk(probe.getX() >> 4, probe.getZ() >> 4)) {
                        break; // never force-load a chunk just to answer this
                    }
                    if (level.getBlockState(probe).isSolidRender(level, probe)) {
                        return false; // something up there - this world has cover
                    }
                }
            }
        }
        return true;
    }
}
