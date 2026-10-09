package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhase;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Forces a young queen to found her hive where she is standing, in one action.
 * <p>
 * [stated] "lets make a way for survival to force this too... using this block on a new queen forces her to make a hive
 * where standing." The operator command {@code /avp hive skip settlement} already did this, but it is op-only, so
 * survival players had no lever on founding placement at all.
 * </p>
 * <p>
 * ⭐⭐ ONE ACTION, NOT TWO, AND THAT IS THE WHOLE POINT. The lifecycle runs DEVELOPING -> LOCATION -> HIBERNATION ->
 * FOUNDING_HANDOFF, and the two skip commands do NOT chain: {@code skip hibernation} only works while she is IN
 * hibernation, {@code skip settlement} only works once she is ALREADY in the handoff, and skipping hibernation founds
 * her at her ANCHOR - wherever she dug to - rather than where the player is standing. So neither one, nor both, does
 * what the block promises. This drops her straight to the handoff at her CURRENT position from any earlier phase.
 * </p>
 * <p>
 * ⚠⚠ THE TERRITORY GUARD IS NOT OPTIONAL. [stated] "it still shouldnt cause hives to overlap ... otherwise this would
 * let people wreck the entire gap system." So Rule 0 (the surface rule) is waived - founding where you stand is the
 * feature - and Rule 1 (somebody else's ground) is enforced exactly as it is for a natural queen. Two hives sharing
 * territory corrupts spacing, contests and lineage membership for both.
 * </p>
 */
public final class ForcedQueenSettlement {

    private ForcedQueenSettlement() {}

    /**
     * The message shown when the spot is inside another hive's reach.
     * <p>
     * [stated] verbatim. A player holding a consumed block needs to know it was refused and why, or a working guard
     * reads as a broken item.
     * </p>
     */
    public static final String TERRITORY_REFUSAL =
        "Hive influence detected - clear the other hive or move this queen's location";

    /**
     * Whether this queen can still be forced.
     * <p>
     * ⚠ "NEW QUEEN" MEANS PRE-HANDOFF. Once she reaches FOUNDING_HANDOFF the founding systems own her and there is
     * nothing left to force - she either already has a hive or is about to make one on her own.
     * </p>
     */
    /**
     * Whether this royal can still be forced to found where she stands.
     * <p>
     * !!! TAKES A Xenomorph, NOT A Queen, AND THAT WAS A REAL BUG. Empress extends Xenomorph, NOT Queen, so a
     * Queen-typed check could never match her - the poison jelly block silently did nothing on an empress. And she is
     * the caste that needs it most: a lone empress has no develop/locate/hibernate lifecycle to fall back on.
     * </p>
     */
    public static boolean isForceable(Xenomorph queen) {
        // ⚠ THESE TWO LIVE ON THE CONCRETE ROYALS, NOT ON Xenomorph, so they are asked per type. A captive royal is
        // held, not homeless, and a downed one is in no state to be founding anything.
        // Oct 3: and chained, held, or in her release grace outside a slab - the same rule the founding service
        // applies,
        // asked up front so a refused queen is never offered the settlement in the first place.
        if (queen instanceof Queen realQueen && !QueenCaptivity.mayFound(realQueen)) {
            return false;
        }

        if (
            queen instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress empress
                && (empress.isExiled() || empress.isIncapacitated())
        ) {
            return false;
        }

        // Same test HiveManager makes privately: does any location already name her as its founder.
        var uuid = queen.getUUID();
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (uuid.equals(location.founderId())) {
                return false;
            }
        }

        // An empress has no phase machine at all - her founding runs through a descent instead - so there is no
        // "already past it" state to exclude. If she has no hive, she can be forced.
        if (!(queen instanceof Queen realQueen)) {
            return true;
        }

        return realQueen.getLifecyclePhaseManager().getPhase() != QueenLifecyclePhase.FOUNDING_HANDOFF;
    }

    /**
     * Drops her into the handoff where she stands and founds there.
     *
     * @return null on success, or the reason to show the player.
     */
    public static @Nullable String force(ServerLevel level, Xenomorph queen, @Nullable Player player) {
        if (!isForceable(queen)) {
            return "She is past the point where her hive site can be chosen";
        }

        var pos = queen.blockPosition();

        // ⚠ THE CHEAP, CERTAIN CASE FIRST. Standing inside a live claim is by far the likeliest refusal, and
        // SpreadZoneCheck would report it in its own words - this answers in his.
        var standingIn = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), queen.chunkPosition());
        if (standingIn != null && standingIn.isAlive()) {
            return TERRITORY_REFUSAL;
        }

        // Rule 0 waived, Rule 1 enforced. Spacing, rival lineages and existing claims all still answer here.
        var result = SpreadZoneCheck.evaluate(queen, pos, true);
        if (result instanceof SpreadZoneResult.Blocked) {
            return TERRITORY_REFUSAL;
        }

        // ⚠ ONLY NOW is the phase machine touched. Everything above can refuse without leaving her half-converted;
        // past this line she is committed to the handoff, so the founding call must not be allowed to fail silently.
        // ⚠ TWO DIFFERENT MACHINES. A queen is dropped into the terminal phase of her lifecycle; an empress has no
        // lifecycle, so all that is needed is to abandon any descent she is mid-way through before founding here.
        if (queen instanceof Queen realQueen) {
            realQueen.getLifecyclePhaseManager().forceHandoffWhereStanding();
        } else if (queen instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress empress) {
            empress.clearDescent();
        }

        var locationId = HiveLocationFoundingService.foundFromResult(queen, pos, result);
        if (locationId == null) {
            Alien.LOGGER.warn(
                "Forced settlement: queen {} was committed to the handoff at {} but founding returned no location",
                queen.getUUID(),
                pos
            );
            return "She takes the jelly, but something went wrong founding here (see the server log)";
        }

        Alien.LOGGER.info(
            "Forced settlement: queen {} founded location {} at {} - forced by {}",
            queen.getUUID(),
            locationId,
            pos,
            player == null ? "unknown" : player.getName().getString()
        );

        if (player != null) {
            // Chat as well, to match the refusals - the two outcomes of the same click should not land in two
            // different places, or a player who missed the action bar cannot tell whether anything happened.
            player.displayClientMessage(
                Component.literal("She settles here. The hive begins.")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
                false
            );
        }

        return null;
    }
}
