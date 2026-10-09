package com.alien.common.gameplay.hive.growth;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;

/**
 * THE THREE MOMENTS IN A DAUGHTER HIVE'S LIFE THAT A PLAYER IS ALLOWED TO HEAR.
 * <p>
 * [stated] "when a hive makes a daughter queen give the players nearby a message because they have no idea it happens",
 * and then [stated] "players kept being confused if their hives made daughters or not", and finally [stated] "for the
 * announce on arrival can you also announce if she is killed and it fails?"
 * </p>
 * <p>
 * ⭐⭐ THE THREE READ AS ONE STORY, WHICH IS THE WHOLE POINT: <b>raised → arrived → or died on the way</b>. A player who
 * hears the first line and then nothing has no idea whether the daughter exists; a player who hears the first and the
 * third knows they stopped it. The middle line is the one that closes the confusion, because it is the only one that
 * fires on BOTH halves of hive spread.
 * </p>
 * <p>
 * ⚠ EVERY LINE IS THE LOUD TIER — queen scream plus text, in the strain's own chat colour, sent to the same audience so
 * nobody ever gets half of it. Same pairing the world's first wild queen announces herself with.
 * </p>
 */
public final class DaughterQueenAnnouncements {

    /** Audience for every line here. Matches the wild-queen broadcast range so all the loud tiers read alike. */
    private static final int ANNOUNCE_RANGE_BLOCKS = 256;

    private DaughterQueenAnnouncements() {}

    /**
     * RAISED: a heavy has just gone into the royal cocoon. Fires ~30s before she steps out, so it reads as "the hive is
     * making something" rather than "a queen exists now".
     * <p>
     * ⚠ THE VISIBLE HALF ONLY. {@code AbstractSpreadAttempt} mints a daughter for hives nobody is near and stays silent
     * — there is nobody within range to tell. {@link #announceArrival} is what covers that half.
     * </p>
     */
    public static void announceRaised(ServerLevel level, Alien candidate) {
        broadcast(level, candidate, "The hive's influence grows... new terrors enter the world");
    }

    /**
     * ARRIVED: she has founded. ⭐⭐ THIS IS THE LINE THAT ACTUALLY ANSWERS "DID MY HIVE MAKE A DAUGHTER OR NOT" — every
     * daughter founds eventually, by either half of hive spread, so unlike {@link #announceRaised} it cannot be missed
     * by wandering off while the hive works.
     */
    public static void announceArrival(ServerLevel level, Alien queen) {
        broadcast(level, queen, "A new hive takes root... her daughters have found their ground");
    }

    /**
     * ADOPTED: a lineage has taken in a queen it did not raise — [stated] "also let it announce if a lineage adopts a
     * hive especially if its one of the generated hibernated ones."
     * <p>
     * ⭐⭐ THE HIBERNATING WILD QUEENS ARE THE POINT. One spawns per chunk-roll, sleeps underground, and is adopted the
     * moment a same-strain lineage's claims reach within 32 chunks of her — which is a hive GAINING A WHOLE LOCATION
     * out of nowhere, with a queen nobody watched being raised. It is the single least visible way an empire grows, and
     * until now it happened in total silence.
     * </p>
     * <p>
     * Also covers the rescue-aftermath adoption (a freed captive joining her rescuers' lineage) and a queen who settles
     * inside ANOTHER lineage's spread zone and is taken in by it rather than founding as a rival.
     * </p>
     */
    public static void announceAdopted(ServerLevel level, Alien queen) {
        broadcast(level, queen, "The hive has discovered a lost daughter and welcomes her, growing their influence");
    }

    /**
     * ADOPTED, AT A PLACE RATHER THAN AT A QUEEN — used when an EMPRESS NETWORK absorbs a whole lineage and there is no
     * single queen the event belongs to. Same line, same scream; the audience is measured from the absorbed hive.
     * <p>
     * ⚠ Falls back to the NORMAL variant colour because a position has no strain of its own. The colour is flavour
     * here, not information — every other cue in the line already says whose hive it is.
     * </p>
     */
    public static void announceAdoptedHive(ServerLevel level, net.minecraft.core.BlockPos at) {
        var message = Component
            .literal("The hive has discovered a lost daughter and welcomes her, growing their influence")
            .withStyle(AlienVariantTypes.NORMAL.chatColor());
        var rangeSqr = (double) ANNOUNCE_RANGE_BLOCKS * ANNOUNCE_RANGE_BLOCKS;

        for (var player : level.players()) {
            if (player.blockPosition().distSqr(at) > rangeSqr) {
                continue;
            }
            player.playNotifySound(AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(), SoundSource.MASTER, 1, 1);
            player.sendSystemMessage(message);
        }
    }

    /**
     * SEVERED: a corridor to an empress network has been cut and a lineage has fallen out of it — [stated] "make a line
     * something like '<playername> efforts have severed a key supply line for the hive'".
     * <p>
     * ⭐⭐ THIS IS FEEDBACK ON A DESIGNED LEVER. Take a hive in the middle and everything beyond it is severed from her —
     * but until now that happened in total silence, so a player could pull off the single most effective move against
     * an empire and never learn it had worked.
     * </p>
     * <p>
     * ⚠ THE NAME IS A HEURISTIC AND HAS TO BE. Severance is detected by a corridor RECONCILE that recomputes
     * connectivity from scratch; nothing anywhere records who caused it, and the cut may be several minutes and several
     * deaths old. The nearest player to the severed hive is credited, which in practice is whoever has been fighting
     * there. When nobody is in range the line is skipped entirely — there is no audience, so there is nothing to
     * attribute.
     * </p>
     * <p>
     * ⚠⚠ Announced AT THE SEVERED HIVE, never at her seat — same no-leak rule as {@link #announceAdoptedHive}.
     * </p>
     */
    public static void announceCorridorSevered(ServerLevel level, net.minecraft.core.BlockPos at) {
        var rangeSqr = (double) ANNOUNCE_RANGE_BLOCKS * ANNOUNCE_RANGE_BLOCKS;

        net.minecraft.server.level.ServerPlayer nearest = null;
        var nearestSqr = Double.MAX_VALUE;
        for (var player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            var distSqr = player.blockPosition().distSqr(at);
            if (distSqr <= rangeSqr && distSqr < nearestSqr) {
                nearest = player;
                nearestSqr = distSqr;
            }
        }
        if (nearest == null) {
            return;
        }

        var message = Component
            .literal(nearest.getGameProfile().getName() + "'s efforts have severed a key supply line for the hive")
            .withStyle(AlienVariantTypes.NORMAL.chatColor());

        for (var player : level.players()) {
            if (player.blockPosition().distSqr(at) > rangeSqr) {
                continue;
            }
            player.playNotifySound(AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(), SoundSource.MASTER, 1, 1);
            player.sendSystemMessage(message);
        }
    }

    /**
     * FAILED: a landless daughter died before she ever founded. Her mother has already paid — the jelly, the
     * praetorian, and one of her two lifetime daughter slots were all spent when the molt STARTED — so this is a real
     * loss for the hive and a real win for whoever caused it, and it deserves to be heard.
     */
    public static void announceFailed(ServerLevel level, Alien queen) {
        broadcast(level, queen, "The young queen falls... the hive's reach is cut short");
    }

    /**
     * ⭐⭐ WAS THIS A DAUGHTER STILL ON HER WAY? Two conditions, and BOTH are needed.
     * <p>
     * <b>She belongs to a lineage</b> — a hive raised her. A wild queen dug up in a cave belongs to nothing and her
     * death is not a hive's loss to mourn. <br>
     * <b>She founds nothing</b> — no registered live location names her as founder. A seated queen dying is an entirely
     * different event, already covered by the revenge raid and the succession machinery.
     * </p>
     * <p>
     * ⚠⚠ CALL THIS <b>BEFORE</b> {@code onRoyalDiedClearFounder()}. That method surrenders the founder pointer on every
     * hive she was seated at, so one line later a reigning queen of thirty hive-years reads as landless and would
     * announce as a failed daughter. The ordering is the whole correctness of this check.
     * </p>
     */
    public static boolean isLandlessDaughter(Alien queen) {
        if (!queen.getType().is(AlienEntityTypeTags.QUEENS)) {
            return false;
        }

        var hasLineage = false;
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (LineageIds.isLineageId(factionId)) {
                hasLineage = true;
                break;
            }
        }
        if (!hasLineage) {
            return false;
        }

        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (location.isAlive() && queen.getUUID().equals(location.founderId())) {
                return false;
            }
        }
        return true;
    }

    private static void broadcast(ServerLevel level, Alien royal, String text) {
        var message = Component.literal(text).withStyle(AlienVariantTypes.getFor(royal).chatColor());
        var rangeSqr = (double) ANNOUNCE_RANGE_BLOCKS * ANNOUNCE_RANGE_BLOCKS;

        for (var player : level.players()) {
            if (player.distanceToSqr(royal) > rangeSqr) {
                continue;
            }
            player.playNotifySound(AlienSoundEvents.ENTITY_QUEEN_SCREAM.get(), SoundSource.MASTER, 1, 1);
            player.sendSystemMessage(message);
        }
    }
}
