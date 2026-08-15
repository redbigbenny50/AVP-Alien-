package com.alien.common.gameplay.entity.living.alien.adolescent;

/**
 * Clip names for the reworked adolescent skeleton.
 * <p>
 * The rework replaced a 44-clip LIMB-SPLIT set (`walk.body`, `walk.head`, ... seven tracks per action) with 13
 * WHOLE-BODY clips. The dotted names below are SUB-NAMES, not limb tracks - `attack.bite` is one clip, not a bite
 * played across seven bones - so everything dispatches on {@code AzAlienAnimationUtil.BODY}.
 * <p>
 * The royal adolescent shares these names exactly: it is the same {@link Adolescent} class on the same renderer, and
 * its own animation file was authored with an identical clip set, so one dispatcher drives both.
 */
public class AdolescentAnimationRefs {

    // #####################
    // ## ANIMATION NAMES ##
    // #####################

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String WALK_ANIMATION_NAME = "walk";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String POUNCE_ANIMATION_NAME = "pounce";

    public static final String ATTACK_BITE_ANIMATION_NAME = "attack.bite";

    /** Replaces the old per-arm claw clip: the rework animates the whole swipe in one clip. */
    public static final String ATTACK_SWIPE_ANIMATION_NAME = "attack.swipe";

    /**
     * Bite variant used while swimming - the land bite reads wrong with no ground contact.
     * <p>
     * ⚠ RENAMED from {@code attack.swimbite} to match every other caste's {@code swim.attack.bite}. The constant name
     * changed with it; the old one resolved to nothing and would have failed silently on every dispatch.
     * </p>
     */
    public static final String SWIM_ATTACK_BITE_ANIMATION_NAME = "swim.attack.bite";

    // #####################
    // ## MOLT ##
    // #####################
    //
    // ⭐⭐ THE ADOLESCENT'S MOLT CLIPS ARE DESTINATION-KEYED, which no other caste's are: it does not have one
    // "wrapping up" animation, it has one PER FORM IT IS BECOMING - molt.drone.*, molt.runner.*, molt.spitter.*.
    // Same shape as the queen's source-specific emerge (molting.prae / molting.crusher), just on the other side of
    // the molt, so the existing per-entity SELECTOR seam on CocoonAnimationStateTracker carries it with no new API.
    //
    // ⚠ THE ADOLESCENT IS ONLY EVER A SOURCE. It is what other things come FROM, never what they turn into, so
    // there is no emerge clip here and none is needed: the pair is ENTER-ORIENTED (clipIsEnterOriented = true).

    private static final String MOLT_PREFIX = "molt.";

    private static final String MOLT_ENTER_SUFFIX = ".enter";

    private static final String MOLT_LOOP_SUFFIX = ".loop";

    /**
     * The forms the adolescent has authored molt clips for. ⚠ KEEP THIS IN STEP WITH THE ART - a form that is not
     * listed falls back to the drone pair rather than resolving to nothing, because an unresolved clip name fails
     * SILENTLY every frame (this entity is the one that produced 6,468 render-thread lookup failures once already).
     */
    private static final java.util.Set<String> MOLT_FORMS = java.util.Set.of(
        // adolescent.animation.json
        "drone",
        "runner",
        "spitter",
        // royal_adolescent.animation.json - THE SAME CLASS ON THE SAME ANIMATOR, only the FILE differs
        // (AdolescentAnimator.getAnimationLocation switches on isRoyal()), which is exactly why the clip names have
        // to agree across all three files and why this roster is shared rather than per-entity.
        "praetorian",
        "crusher",
        // predalien_adolescent.animation.json - a different class with its own dispatcher and animator, but it
        // reuses these names, so it reuses this roster too.
        "predalien"
    );

    private static final String MOLT_FALLBACK_FORM = "drone";

    /** Wrapping up, holding on its last frame until the loop takes over. */
    public static String moltEnterFor(String form) {
        return MOLT_PREFIX + resolveForm(form) + MOLT_ENTER_SUFFIX;
    }

    /** The in-cocoon loop, which on this caste covers the WHOLE molt - see GrowthManager's destination collapse. */
    public static String moltLoopFor(String form) {
        return MOLT_PREFIX + resolveForm(form) + MOLT_LOOP_SUFFIX;
    }

    private static String resolveForm(String form) {
        return form != null && MOLT_FORMS.contains(form) ? form : MOLT_FALLBACK_FORM;
    }
}
