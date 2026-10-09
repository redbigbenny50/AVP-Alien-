package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * ⭐⭐⭐ SITUATION 5 — A SECOND EMPRESS IS SPAWNED INTO A HIVE THAT ALREADY HAS ONE ON ITS EGGSACK.
 * <p>
 * [stated] "spawning an empress inside a hive that already has their queen replaced with an empress. wether its the
 * sovereign or not doesnt matter ... she would leave but where she goes is based on whats in that lineage."
 * </p>
 * <p>
 * ⭐⭐ SHE ALWAYS LEAVES. The three destinations, in his words: "it basically determines if she needs to make a new hive,
 * take over one, or go her own way."
 * <ol>
 * <li><b>Cap not full</b> — she founds a new hive FOR THAT LINEAGE.</li>
 * <li><b>Cap full, but some hive still has an ordinary queen</b> — she goes and takes that one.</li>
 * <li><b>Every hive already has an empress sitting on it</b> — she founds her OWN lineage and becomes a rival.</li>
 * </ol>
 * </p>
 * <p>
 * ⚠⚠ THE ORDER OF THOSE CHECKS IS THE DESIGN, NOT AN IMPLEMENTATION DETAIL. [stated] "if she had to go her own way then
 * that means the origin lineage was full. So if the lineage has room as in no 8 cap that other hive would just be
 * absorbed now." A schism is what happens when the empire has NO ROOM LEFT for her — reaching for it earlier would
 * manufacture rivals out of situations the empire could simply have accommodated.
 * </p>
 */
public final class EmpressSchism {

    private EmpressSchism() {}

    /**
     * Sends a displaced duplicate empress wherever the lineage's state says she belongs.
     */
    public static void relocate(
        ServerLevel level,
        Empress empress,
        HiveLocation occupied,
        LineageFactionData lineage
    ) {
        // !!! A DOWNED EMPRESS GOES NOWHERE. tickLoneFounding checks this; the schism did not, so a royal who was
        // incapacitated mid-descent could be sent digging again while at 0 HP - reported as "she stood up and
        // started doing the dig animation again even tho she was at 0 hp".
        //
        // ⚠ Exiled is already handled downstream, but incapacitation is the one state where she is meant to be lying
        // there waiting to be finished or rescued, and NOTHING should re-task her.
        if (empress.isIncapacitated()) {
            return;
        }

        var config = HiveLocationRegistry.INSTANCE.config();
        var cap = Math.min(config.maxLocationsPerLineage(), config.maxLocationsUnderEmpress());

        // 1. ROOM IN THE EMPIRE — she is another hive for it, not a rival to it.
        if (lineage.activeLocationCount() < cap) {
            Alien.LOGGER.info(
                "Empress {} displaced from {} - the lineage has room, so she leaves to found for it",
                empress.getUUID(),
                occupied.id()
            );
            departToFound(level, empress, false);
            return;
        }

        // 2. NO ROOM, BUT A THRONE SOMEWHERE IS STILL HELD BY AN ORDINARY QUEEN — she takes that one instead.
        //
        // ⚠ She is NOT teleported to it. She leaves, and the ordinary succession path picks her up when she reaches
        // a hive whose eggsack has no empress on it - the same tick hook situation 4 uses. Walking there is what
        // makes it visible to a player rather than an entity blinking across the map.
        for (var sibling : lineage.locationsById().values()) {
            if (!sibling.isAlive() || sibling.id().equals(occupied.id())) {
                continue;
            }
            if (!hasSittingEmpress(sibling)) {
                Alien.LOGGER.info(
                    "Empress {} displaced from {} - the lineage is at cap, but {} still has an ordinary queen",
                    empress.getUUID(),
                    occupied.id(),
                    sibling.id()
                );
                dispatchSuccessionConvoy(level, empress, occupied, sibling, lineage);
                return;
            }
        }

        // 3. EVERY THRONE TAKEN — a schism. She founds her own lineage and the empire has a rival.
        Alien.LOGGER.info(
            "Empress {} displaced from {} - every hive is held, so she breaks away as a RIVAL",
            empress.getUUID(),
            occupied.id()
        );
        departToFound(level, empress, true);
    }

    /**
     * ⭐⭐⭐ THE SUCCESSION CONVOY — she travels to the sibling throne through ABSTRACT SPACE.
     * <p>
     * ⚠⚠ SHE CANNOT WALK THERE AND THIS IS NOT A DETAIL. A sibling hive is typically hundreds of blocks away in chunks
     * nobody has loaded; an entity on foot would wander near her old hive until something killed her. Convoys are the
     * mod's answer to exactly this — they move between hives with NEITHER END LOADED.
     * </p>
     * <p>
     * ⭐ REUSES {@code Convoy.Migration}, which already carries an empress ({@code carriesEmpress}) and already lands a
     * royal at a destination hive. A parallel convoy type would duplicate arrival, codec and tracking for a journey
     * that is mechanically identical — this is a new REASON to dispatch, not a new kind of travel.
     * </p>
     * <p>
     * ⚠ The composition is EMPTY. She takes no garrison with her: the hive she is leaving keeps everything, which is
     * the whole point of a succession rather than a migration. Only she moves.
     * </p>
     */
    private static void dispatchSuccessionConvoy(
        ServerLevel level,
        Empress empress,
        HiveLocation from,
        HiveLocation to,
        LineageFactionData lineage
    ) {
        empress.getEmpressOvipositorManager().abandonOvipositor();

        var convoy = new com.alien.common.gameplay.hive.convoy.Convoy.Migration(
            com.alien.common.gameplay.hive.convoy.ConvoyId.fresh(),
            lineage.factionId(),
            from.dimension(),
            from.id(),
            to.id(),
            new net.minecraft.world.phys.Vec3(
                from.centerPos().getX() + 0.5,
                from.centerPos().getY() + 0.5,
                from.centerPos().getZ() + 0.5
            ),
            to.centerPos(),
            new com.blib.api.common.entity.v1.EntityReserves(),
            0,
            true,
            level.getGameTime()
        );
        lineage.convoys().add(convoy);
        lineage.markDirty();

        // ⚠ She is REMOVED here, not left standing. The convoy IS her now - leaving the entity behind would give the
        // lineage two of her, one walking about and one arriving at the far hive.
        empress.discard();
    }

    /**
     * Turns her out of the hive so the ordinary founding path can take over.
     * <p>
     * ⚠ SHE CLEARS THE CLAIM ON FOOT, exactly like a daughter — no exemption from the spread-zone rule. Where she ends
     * up is the founding system's problem, and it already refuses to settle on top of an existing territory.
     * </p>
     * <p>
     * ⚠ A SCHISM IS MARKED so the grace period can protect her. Without it she founds one hive beside an empire of
     * eight, the absorption roll floors her defence at ten percent, and she rejoins the empire she just left within
     * minutes - the player who spawned her would see nothing happen at all.
     * </p>
     */
    private static void departToFound(ServerLevel level, Empress empress, boolean schism) {
        // ⚠ She gives up any sack she is carrying before she goes - it belongs to the hive she is leaving.
        empress.getEmpressOvipositorManager().abandonOvipositor();

        // ⭐⭐⭐ AND THEN SHE ACTUALLY FOUNDS. THIS METHOD PREVIOUSLY DID NOTHING ELSE.
        //
        // ⚠⚠ IT DROPPED HER SACK AND RETURNED. No anchor, no descent, no hive - a displaced empress simply lost her
        // eggsack and stood there, while the comments around it described a departure that never happened. Situation
        // 5 branches 1 and 3 were dead.
        //
        // ⭐ AN EMPRESS CAN FOUND NOW because HiveLocationFoundingService and SpreadZoneCheck were widened from Queen
        // to Xenomorph - they only ever used getUUID, getVariant, level and blockPosition, all of which she has. That
        // is what makes her digging clips reachable at all: she is already an empress by this point, so the queen's
        // founding path was never available to her.
        var anchor = pickDepartureAnchor(level, empress);
        if (anchor == null) {
            Alien.LOGGER.info(
                "Empress {} could not find anywhere to found - she remains displaced",
                empress.getUUID()
            );
            return;
        }

        if (schism) {
            SCHISM_PENDING.add(empress.getUUID());
        }

        empress.beginForcedDescent(anchor);
    }

    /**
     * Somewhere legal for a displaced empress to dig to.
     * <p>
     * ⚠ Runs the SAME spread checks an ordinary founding does, so she cannot plant herself on another hive's ground -
     * [stated] "as long as its not in another's hive area/ territory". A refusal here means she stays put rather than
     * founding somewhere she should not.
     * </p>
     */
    /** A depth in the empress band, clamped into this dimension so the Nether does not aim below its own floor. */
    /**
     * How far below the SURFACE OF THIS COLUMN the anchor should sit.
     * <p>
     * !!! THE BAND ALONE IS NOT AN ANCHOR. This used to roll y 26-40 and clamp only to build height, which assumes a
     * world whose ground happens to be above y 40. On a SUPERFLAT world the surface is around y 4, so every roll landed
     * in OPEN SKY - the empress founded in mid-air and the hive grew downward on legs. Reported with a screenshot of
     * exactly that.
     * </p>
     * <p>
     * ⭐ The band is now a PREFERENCE, and the terrain is the authority: whatever is rolled, the anchor is pushed down
     * to sit at least MIN_COVER blocks under the actual surface of the column she is aiming at. A deep world behaves
     * exactly as before; a shallow one gets the deepest legal spot instead of a tower.
     * </p>
     */
    private static final int MIN_COVER_BLOCKS = 3;

    private static int anchorDepth(ServerLevel level, Empress empress, ChunkPos candidate) {
        var rolled = EMPRESS_ANCHOR_Y_MIN
            + empress.getRandom().nextInt(EMPRESS_ANCHOR_Y_MAX - EMPRESS_ANCHOR_Y_MIN + 1);

        var profile = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles.get(level);
        var remapped = com.alien.common.gameplay.hive.dimension.DimensionHiveProfiles
            .remapFromOverworldBand(rolled, profile);

        // ⚠ WORLD_SURFACE, not MOTION_BLOCKING: leaves and fluids must not read as ground, which is the mistake that
        // let queens found on top of forests - see SpreadZoneCheck.isTooShallow.
        var middle = candidate.getMiddleBlockPosition(0);
        var surface = level.getHeight(
            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
            middle.getX(),
            middle.getZ()
        );

        var deepestAllowed = surface - MIN_COVER_BLOCKS;
        var floor = level.getMinBuildHeight() + 1;

        // A world with no room at all - superflat with a couple of layers - still gets a legal anchor rather than a
        // tower: the lowest buildable block.
        if (deepestAllowed < floor) {
            return floor;
        }

        return Math.max(floor, Math.min(remapped, deepestAllowed));
    }

    /**
     * Sends a lone empress - no lineage, no hive - off to dig her own.
     * <p>
     * [stated] she should "do the queens process with no hibernation". This is that process: an anchor in the depth
     * band, a descent, and a founding on arrival. The waiting half is the queen's alone.
     * </p>
     */
    public static void foundAlone(ServerLevel level, Empress empress) {
        departToFound(level, empress, false);
    }

    private static @org.jetbrains.annotations.Nullable net.minecraft.core.BlockPos pickDepartureAnchor(
        ServerLevel level,
        Empress empress
    ) {
        var here = empress.chunkPosition();
        for (var radius = MIN_DEPARTURE_CHUNKS; radius <= MAX_DEPARTURE_CHUNKS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue; // ring walk - nearest legal ground first
                    }
                    var candidate = new net.minecraft.world.level.ChunkPos(here.x + dx, here.z + dz);
                    if (!level.getChunkSource().hasChunk(candidate.x, candidate.z)) {
                        continue;
                    }
                    if (
                        com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE
                            .getByChunk(level.dimension(), candidate) != null
                    ) {
                        continue;
                    }
                    // ⚠ PROBE AT THE DEPTH SHE WILL ACTUALLY FOUND AT, not at the height she is standing at now.
                    // The arrival check runs at the anchor, so probing anywhere else just means being refused later.
                    var anchorY = anchorDepth(level, empress, candidate);
                    var probe = candidate.getMiddleBlockPosition(anchorY);
                    if (com.alien.common.gameplay.hive.lifecycle.SpreadZoneCheck.wouldAllow(empress, probe)) {
                        return probe;
                    }
                }
            }
        }
        return null;
    }

    /** Far enough that she is not founding on her old hive's doorstep. */
    /**
     * The depth band a founding empress digs to.
     * <p>
     * [stated] "what should happen is she digs to between y 40-26 and does the queens process with no hibernation."
     * </p>
     * <p>
     * !!! THE ANCHOR USED TO KEEP HER CURRENT Y - getMiddleBlockPosition(empress.blockPosition().getY()) - so she was
     * sent to "dig down" to a block at the height she was already standing at. The descent had nowhere to go, she
     * arrived instantly, and above sea level the surface rule then refused the founding, which is the dig/idle bounce
     * that was reported. A real depth is what makes the descent mean anything.
     * </p>
     * <p>
     * * COMFORTABLY BELOW THE SURFACE RULE's ceiling of seaLevel - 17 (y=46 in the overworld), so a legal anchor is
     * never rolled into an illegal founding.
     * </p>
     */
    private static final int EMPRESS_ANCHOR_Y_MIN = 26;

    private static final int EMPRESS_ANCHOR_Y_MAX = 40;

    private static final int MIN_DEPARTURE_CHUNKS = 4;

    /** Beyond this she is not displaced, she is lost. */
    private static final int MAX_DEPARTURE_CHUNKS = 12;

    /**
     * ⭐⭐ EMPRESSES WHO LEFT AS RIVALS AND HAVE NOT YET FOUNDED.
     * <p>
     * ⚠ The grace period cannot be stamped when she DEPARTS, because the lineage she will be protected in does not
     * exist yet - she has to walk out and found it first. This remembers the intent across that gap, and
     * {@link #onFounded} spends it the moment her new lineage appears.
     * </p>
     * <p>
     * ⚠ IN MEMORY ONLY, on purpose. If the server restarts while she is still walking she simply founds without the
     * grace - which fails toward the ordinary behaviour rather than toward a permanently unabsorbable lineage.
     * </p>
     */
    private static final java.util.Set<java.util.UUID> SCHISM_PENDING =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Stamps the grace period if this founder left as a rival. Call when a new lineage is founded.
     */
    public static void onFounded(net.minecraft.world.level.Level level, java.util.UUID founderId, LineageFactionData lineage) {
        if (!SCHISM_PENDING.remove(founderId)) {
            return;
        }
        lineage.beginSchismGrace(level.getGameTime() + SCHISM_GRACE_TICKS);
        Alien.LOGGER.info("Empress {} founded a breakaway lineage - 3 day grace period begins", founderId);
    }

    /** [stated] "the grace period should be a time of 3 minecraft days". */
    private static final long SCHISM_GRACE_TICKS = 3L * 24000L;

    /**
     * ⚠⚠ READS THE PERSISTED SEAT, NEVER THE WORLD. Branch 2 walks EVERY hive in the lineage, and in a real game almost
     * none of them are loaded — an entity scan would report "no empress" for each one and send her to a throne that is
     * already occupied. This is exactly why the seat had to become persisted state.
     */
    private static boolean hasSittingEmpress(HiveLocation location) {
        return location.hasSittingEmpress();
    }
}
