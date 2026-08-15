package com.alien.client.animation.entity.cocoon;

import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonState;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.util.AzAlienAnimationUtil;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;

import java.util.function.Function;

public class CocoonAnimationStateTracker<T extends Xenomorph> {

    /**
     * The authored molt clip: an entity wrapping itself up to become something else (a prowler cocooning into a
     * crusher, say). Emerging is THIS clip played backwards - there is no separately authored emerge animation, so the
     * code produces it by reversing the enter.
     */
    private static final String MOLT_ANIMATION_NAME = "molt.enter";

    private static final String COCOON_LOOP_ANIMATION_NAME = "molting";

    /**
     * The shared in-cocoon loop name, for castes that need a CUSTOM emerge but the ORDINARY loop.
     * <p>
     * Exposed so such a caste can pass it explicitly instead of repeating the literal - the spitter needs the
     * emerge-oriented constructor (it has no enter clip) while its art ships no loop of its own.
     * </p>
     */
    public static String defaultLoopAnimationName() {
        return COCOON_LOOP_ANIMATION_NAME;
    }

    private static final int TRANSITION_ANIMATION_TICKS = 20;

    /**
     * Resolves the looping in-cocoon animation per entity. Defaults to the shared {@code molting}; the queen passes a
     * selector that picks a source-specific loop ({@code molting.prae} / {@code molting.crusher}).
     */
    private final Function<T, String> loopAnimationSelector;

    /**
     * Resolves the one-shot emerge animation per entity. Defaults to the shared {@code molt.emerge}; the queen passes a
     * selector that picks a source-specific emerge ({@code emerge.prae} / {@code emerge.crusher}).
     */
    private final Function<T, String> emergeAnimationSelector;

    /**
     * Which way round the authored clip runs.
     * <p>
     * Most castes ship an ENTER clip ({@code molt.enter}) - the thing wrapping itself up - so cocooning in plays it
     * forwards and emerging plays it backwards. The queen is the other way round: she is only ever a molt DESTINATION,
     * and ships authored emerge clips ({@code emerge.prae} / {@code emerge.crusher}), so hers play forwards to emerge
     * and backwards to cocoon in. Getting this the wrong way round runs the whole molt in reverse, which is why it is
     * explicit rather than assumed.
     */
    private final boolean clipIsEnterOriented;

    /**
     * ⭐⭐ A SEPARATELY AUTHORED ENTER CLIP, when the caste ships BOTH halves. Null for everyone else.
     * <p>
     * The two older constructors assume ONE authored clip and produce the other half by reversing it - fine when a
     * caste ships only an enter (most) or only an emerge (queen, spitter). The praetorian is the first to ship
     * {@code molt.enter}, {@code molt.loop} AND {@code molt.emerge}, and with only those constructors one of its two
     * authored clips would have been thrown away and replaced by a reversal of the other.
     * </p>
     * <p>
     * When this is set BOTH clips play FORWARDS and {@code clipIsEnterOriented} stops mattering.
     * </p>
     */
    private final Function<T, String> enterAnimationSelector;

    private int previousAnimationId = Integer.MIN_VALUE;

    private CocoonState previousState = CocoonState.NONE;

    private int transitionAnimationStartTick = Integer.MIN_VALUE;

    /** Whether the in-cocoon loop has already been started for the current cocooning, so it isn't re-sent per frame. */
    private boolean loopStarted = false;

    /** Default: the shared {@code molt.enter} / {@code molting} pair, authored enter-first. */
    public CocoonAnimationStateTracker() {
        this(xenomorph -> COCOON_LOOP_ANIMATION_NAME, xenomorph -> MOLT_ANIMATION_NAME, true);
    }

    /** Custom clips authored EMERGE-first (the queen's {@code emerge.prae} / {@code emerge.crusher}). */
    public CocoonAnimationStateTracker(
        Function<T, String> loopAnimationSelector,
        Function<T, String> emergeAnimationSelector
    ) {
        this(loopAnimationSelector, emergeAnimationSelector, false);
    }

    public CocoonAnimationStateTracker(
        Function<T, String> loopAnimationSelector,
        Function<T, String> emergeAnimationSelector,
        boolean clipIsEnterOriented
    ) {
        this(loopAnimationSelector, emergeAnimationSelector, clipIsEnterOriented, null);
    }

    /**
     * BOTH halves authored - no reversal anywhere. Pass the enter clip as well and each plays forwards in its own
     * state. See {@link #enterAnimationSelector}.
     */
    public CocoonAnimationStateTracker(
        Function<T, String> loopAnimationSelector,
        Function<T, String> emergeAnimationSelector,
        Function<T, String> enterAnimationSelector
    ) {
        this(loopAnimationSelector, emergeAnimationSelector, true, enterAnimationSelector);
    }

    private CocoonAnimationStateTracker(
        Function<T, String> loopAnimationSelector,
        Function<T, String> emergeAnimationSelector,
        boolean clipIsEnterOriented,
        Function<T, String> enterAnimationSelector
    ) {
        this.loopAnimationSelector = loopAnimationSelector;
        this.emergeAnimationSelector = emergeAnimationSelector;
        this.clipIsEnterOriented = clipIsEnterOriented;
        this.enterAnimationSelector = enterAnimationSelector;
    }

    public boolean run(T xenomorph) {
        var state = xenomorph.getCocoonManager().getState();
        var animationId = xenomorph.getCocoonManager().getAnimationId();

        return switch (state) {
            case NONE, PENDING -> {
                previousState = state;
                loopStarted = false;
                yield false;
            }
            case SOURCE_COCOONING -> {
                if (previousState != state || previousAnimationId != animationId) {
                    playEnter(xenomorph);
                    transitionAnimationStartTick = xenomorph.tickCount;
                    previousAnimationId = animationId;
                    loopStarted = false;
                } else if (!loopStarted && hasTransitionAnimationFinished(xenomorph)) {
                    // Hand off to the loop ONCE. Without the latch this re-dispatched every render frame for the
                    // whole time the entity sat in its cocoon - the same per-frame cost the case below had.
                    playLoop(xenomorph);
                    loopStarted = true;
                }

                previousState = state;
                yield true;
            }
            case DESTINATION_COCOONING -> {
                // Dispatch only on a real state change, exactly like the cases around it. This ran EVERY CALL, and
                // run() is driven from setCustomAnimations - i.e. once per render FRAME, not per tick. The loop is
                // idempotent so it never restarted, but every frame still built a fresh AzCommand and re-resolved
                // the animation; when the model has no such animation (only the queen ships molt clips - the
                // empress and prowler have none) each frame also logged a lookup failure. A single cocooning
                // entity on screen was enough to hammer the render thread, which is the stall players hit walking
                // back to a cocooning empress.
                if (previousState != state || previousAnimationId != animationId) {
                    playLoop(xenomorph);
                    loopStarted = true;
                }
                previousState = state;
                previousAnimationId = animationId;
                yield true;
            }
            case EMERGING -> {
                if (previousState != state || previousAnimationId != animationId) {
                    playEmerge(xenomorph);
                    transitionAnimationStartTick = xenomorph.tickCount;
                    previousAnimationId = animationId;
                }

                previousState = state;
                yield true;
            }
        };
    }

    private boolean hasTransitionAnimationFinished(Xenomorph xenomorph) {
        return xenomorph.tickCount - transitionAnimationStartTick >= TRANSITION_ANIMATION_TICKS;
    }

    /** Cocooning IN: forwards for an enter-authored clip, backwards for an emerge-authored one. */
    private void playEnter(T xenomorph) {
        // Both halves authored: play the real enter clip forwards and never reverse anything.
        if (enterAnimationSelector != null) {
            AzCommand.<Xenomorph>replay()
                .play(AzAlienAnimationUtil.BODY, enterAnimationSelector.apply(xenomorph), AzPlayBehaviors.PLAY_ONCE)
                .build()
                .dispatchForEntity(xenomorph);
            return;
        }

        AzCommand.<Xenomorph>replay()
            .play(AzAlienAnimationUtil.BODY, emergeAnimationSelector.apply(xenomorph), AzPlayBehaviors.PLAY_ONCE)
            .setReverseAnimation(AzAlienAnimationUtil.BODY, !clipIsEnterOriented)
            .build()
            .dispatchForEntity(xenomorph);
    }

    private void playLoop(T xenomorph) {
        AzCommand.<Xenomorph>idempotent()
            .play(AzAlienAnimationUtil.BODY, loopAnimationSelector.apply(xenomorph), AzPlayBehaviors.LOOP)
            .build()
            .dispatchForEntity(xenomorph);
    }

    /** Emerging: the mirror of {@link #playEnter} - the entity unwrapping itself. */
    private void playEmerge(T xenomorph) {
        AzCommand.<Xenomorph>replay()
            .play(AzAlienAnimationUtil.BODY, emergeAnimationSelector.apply(xenomorph), AzPlayBehaviors.PLAY_ONCE)
            .setReverseAnimation(AzAlienAnimationUtil.BODY, enterAnimationSelector == null && clipIsEnterOriented)
            .build()
            .dispatchForEntity(xenomorph);
    }
}
