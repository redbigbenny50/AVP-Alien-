package com.alien.common.gameplay.entity.dismemberment;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;

/**
 * Picks which arm a mirrored attack should swing with.
 * <p>
 * [stated] "if say the full attack used the left arm and the left arm is missing you would use the right arm attack...
 * this also means that if they still have both arms they can alternate between the two for variety."
 * </p>
 * <p>
 * WHY THIS IS A RUNTIME CHOICE AND NOT A RENDER TRICK. There is no mirror operation at dispatch - BLib cannot flip a
 * clip on the way out - so a left swing and a right swing have to be two separate authored animations. That made this
 * rule unbuildable until the mirrored art existed. It does now, so the only thing missing was something to decide which
 * of the pair to play, which is all this is.
 * </p>
 * <p>
 * The side is read from the DISMEMBERMENT state rather than tracked separately, so it cannot drift out of sync with
 * what the model is actually showing: if the left arm is on the floor, the left clip is never chosen again.
 * </p>
 */
public final class MirroredAttackSide {

    private MirroredAttackSide() {
        throw new UnsupportedOperationException();
    }

    /**
     * True when this attack should play its LEFT-arm clip.
     * <p>
     * One arm gone forces the survivor. With both arms intact it alternates on the entity's own tick count, which gives
     * the variety asked for without storing any state: consecutive swings land on different ticks, so they naturally
     * fall on different sides.
     * </p>
     * <p>
     * If BOTH arms are gone the answer is arbitrary and does not matter - an alien in that state should not be
     * attacking at all, which is the posture gate's job, not this one's.
     * </p>
     */
    public static boolean useLeftArm(Alien alien) {
        var leftGone = isArmDetached(alien, true);
        var rightGone = isArmDetached(alien, false);

        if (leftGone && !rightGone) {
            return false;
        }

        if (rightGone && !leftGone) {
            return true;
        }

        return (alternationSeed(alien) & 1) == 0;
    }

    /**
     * The parity source for alternation - STABLE FOR A WHOLE SWING, which is the important part.
     * <p>
     * ⚠ IT CANNOT BE `tickCount`. This method is not asked once: the dispatcher asks at the START of a swing, but
     * {@code DroneLimbHitboxes.animationFor} asks again EVERY TICK while the attack runs, because the server replays
     * the same clip to work out where the limbs are for hit detection. A per-tick parity would flip mid-swing, and the
     * arm hitboxes would jump to the opposite side of the body from the arm the player can see moving.
     * </p>
     * <p>
     * The attack's start time is synced, and constant for the swing's duration, so client and server pick the same clip
     * from first frame to last - while consecutive swings still land on different sides.
     * </p>
     */
    private static int alternationSeed(Alien alien) {
        if (alien instanceof Xenomorph xenomorph) {
            return xenomorph.attackStartedAtGameTime.get();
        }
        return alien.tickCount;
    }

    /** Whether the named arm has been torn off. */
    public static boolean isArmDetached(Alien alien, boolean left) {
        if (!(alien instanceof Dismemberable dismemberable)) {
            return false;
        }

        var manager = dismemberable.getDismembermentManager();

        // hasAnyDetached() first: an intact alien is the overwhelmingly common case and this skips the whole walk.
        if (manager == null || !manager.hasAnyDetached()) {
            return false;
        }

        var side = left ? "left_arm" : "right_arm";

        for (var definition : LimbDefinitionRegistry.getDefinitions(alien.getType())) {
            // Category identifies it as an ARM; the SIDE only exists in the id (e.g. avp_alien:drone_left_arm), which
            // is why this matches on the suffix rather than on anything structured.
            if (
                definition.category().equals(LimbCategories.ARM)
                    && definition.id().getPath().endsWith(side)
                    && manager.isDetached(definition)
            ) {
                return true;
            }
        }

        return false;
    }

    /** True when neither arm remains - the caller should not be swinging at all. */
    public static boolean hasNoArms(Alien alien) {
        return isArmDetached(alien, true) && isArmDetached(alien, false);
    }
}
