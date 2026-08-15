package com.alien.common.gameplay.entity.dismemberment;

import com.alien.common.gameplay.entity.living.alien.xenomorph.chrysalis.Chrysalis;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.Harbinger;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * How much of a bullet a xenomorph's plating turns aside, by CALIBER and by where it landed.
 * <h2>Why this is a string-and-primitive API</h2> The caliber is known only on the gun side and the armour rule only
 * here, so ONE of the two mods has to call the other. This takes a limb id path and a caliber NAME rather than any
 * avp_human type, so avp_alien gains no compile-time or runtime dependency on avp_human - the call is made from
 * avp_human's own guarded compatibility class, exactly like {@code HumanAlienBlood} does in the other direction. If
 * avp_human is absent nothing calls this.
 * <h2>⚠ This is the ONLY caliber-aware armour rule in the mod</h2> Everything else scales off the flat
 * {@code healthDamageMultiplier} authored per limb volume, which cannot tell a pistol from a sniper. Add new castes
 * here rather than trying to express caliber in the hitbox profile.
 */
public final class BulletResistance {

    /** Ordinary pistol and light rifle rounds. */
    public static final String CALIBER_SMALL = "small";

    /** Battle-rifle class rounds - and, since the drum change, Old Painless. */
    public static final String CALIBER_MEDIUM = "medium";

    /** Pulse rifle and smartgun rounds. */
    public static final String CALIBER_CASELESS = "caseless";

    /** ⚠ THE STANDARD REDUCTION, one constant so the three tiers below cannot drift to 0.75/0.8/0.7 by accident. */
    private static final float QUARTER_REDUCTION = 0.75F;

    private static final float IMMUNE = 0.0F;

    private static final float UNAFFECTED = 1.0F;

    private BulletResistance() {}

    /**
     * @param target   whatever the bullet struck
     * @param limbPath the path of the limb volume's id ({@code crusher_head}, {@code queen_left_arm}, ...), or null for
     *                 a plain body hit
     * @param caliber  one of the {@code CALIBER_*} names above, or anything else for calibers with no special rule
     * @return a multiplier to apply to the damage; 1.0 for no rule, 0.0 for outright immunity
     */
    public static float damageMultiplier(LivingEntity target, @Nullable String limbPath, String caliber) {
        if (limbPath == null) {
            return UNAFFECTED; // a plain body hit is never plated
        }

        // ⚠ THE CRUSHER IS HEAD-ONLY. Its arms and legs take everything normally - his rule was specifically about the
        // crested head, and armouring the whole animal against pistols is a different and much larger change.
        if (target instanceof Crusher) {
            return limbPath.endsWith("_head") ? smallImmuneMediumReduced(caliber) : UNAFFECTED;
        }

        // ⚠ THE CHRYSALIS PLATES ITS ARMS AS WELL AS ITS HEAD - it curls behind them, so they are the surface a shooter
        // actually hits. Legs and tail are not plated.
        if (target instanceof Chrysalis) {
            return isArmOrHead(limbPath) ? smallImmuneMediumReduced(caliber) : UNAFFECTED;
        }

        // ⭐⭐ THE ROYALS ARE A TIER ABOVE, BUT HEAD ONLY. [stated] "queens, empress and harbinger have the same
        // immunities except it includes medium with 25% damage reduced to caseless", then the correction: "the
        // royals dont include the arms for the immunity just their heads only the chrysyls has the immunity on its
        // arms because they are armored."
        //
        // ⚠ SO ONLY THE CHRYSALIS PLATES ITS ARMS, of every caste in the mod. Its arms are armour it curls behind;
        // a royal's are working limbs and shoot off like anyone else's. What the royals get instead is a HIGHER
        // CALIBER TIER on the head - their immunity swallows medium and the quarter-reduction lands on caseless.
        if (target instanceof Queen || target instanceof Empress || target instanceof Harbinger) {
            return limbPath.endsWith("_head") ? mediumImmuneCaselessReduced(caliber) : UNAFFECTED;
        }

        return UNAFFECTED;
    }

    /** Crusher and chrysalis: immune to small, a quarter off medium. */
    private static float smallImmuneMediumReduced(String caliber) {
        if (CALIBER_SMALL.equals(caliber)) {
            return IMMUNE;
        }

        if (CALIBER_MEDIUM.equals(caliber)) {
            return QUARTER_REDUCTION;
        }

        return UNAFFECTED;
    }

    /** Queen, empress and harbinger: immune to small AND medium, a quarter off caseless. */
    private static float mediumImmuneCaselessReduced(String caliber) {
        if (CALIBER_SMALL.equals(caliber) || CALIBER_MEDIUM.equals(caliber)) {
            return IMMUNE;
        }

        if (CALIBER_CASELESS.equals(caliber)) {
            return QUARTER_REDUCTION;
        }

        return UNAFFECTED;
    }

    /**
     * ⚠ MATCHES THE LIMB ID SUFFIXES THE HITBOX PROFILE AUTHORS, not a list of caste-specific names: every profile
     * builds its ids as {@code <prefix>_head}, {@code <prefix>_left_arm}, {@code <prefix>_right_arm}. Keying on the
     * suffix means a caste added later needs no change here beyond its own line in {@code damageMultiplier}.
     * <p>
     * ⚠ ONLY THE CHRYSALIS USES THIS. Every other plated caste is head-only; its arms are armour it curls behind rather
     * than working limbs, which is the whole reason it is the exception.
     * </p>
     */
    private static boolean isArmOrHead(String limbPath) {
        return limbPath.endsWith("_head") || limbPath.endsWith("_arm");
    }
}
