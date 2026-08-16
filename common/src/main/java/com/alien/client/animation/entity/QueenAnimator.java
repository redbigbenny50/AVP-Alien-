package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonSourceForm;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonState;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenBindManager;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.keyframe.AzKeyframeCallbacks;
import com.blib.api.client.animation.v1.keyframe.event.AzSoundKeyframeEvent;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.blib.api.common.dismemberment.v1.DismembermentManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class QueenAnimator extends AzEntityAnimator<Queen> {

    private static final String NAME = "queen";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private static final ResourceLocation LEFT_ARM_LIMB_ID = AlienResources.location("queen_left_arm");

    private static final ResourceLocation QUEEN_TAIL_LIMB_ID = AlienResources.location("queen_tail");

    private static final ResourceLocation RIGHT_ARM_LIMB_ID = AlienResources.location("queen_right_arm");

    private int previousAttackId = Integer.MIN_VALUE;

    /**
     * Crawl-edge tracker for the posture transitions. Null until first observed so a mid-crawl load doesn't replay a
     * drop.
     */
    private Boolean previousCrawling;

    /** Edge-detects the digging state so digdown/digup one-shots fire once on start/stop. */
    /**
     * Ticks a dig one-shot still needs before anything else may dispatch. Both dig triptychs fire a short PLAY_ONCE
     * clip on an edge and then, on the very NEXT tick, the following block dispatched a different animation - which
     * replaced the one-shot after a single tick, so the plant and the pull-out were never actually seen. Holding
     * dispatch for the clip's length lets each one play in full.
     */
    private int digOneShotHoldTicks = 0;

    /** Lengths of the dig one-shots, in ticks (see queen.animation.json). */
    private static final int DIG_DOWN_TICKS = 7;

    private static final int DIG_UP_TICKS = 15;

    private static final int DIG_STAND_START_TICKS = 5;

    private static final int DIG_STAND_STOP_TICKS = 5;

    private boolean previousDigging = false;

    private boolean previousStandDigging = false;

    private boolean previousIncapacitated = false;

    /**
     * ⭐⭐ THE QUEEN IS THE ONLY CASTE THAT IS BOTH A MOLT DESTINATION AND A MOLT SOURCE.
     * <p>
     * She ARRIVES from a praetorian or a crusher and she LEAVES to become an empress. BOTH directions are served by the
     * EMERGE-ORIENTED two-selector form, which is what makes that possible at all: it plays the selected clip FORWARDS
     * when emerging and BACKWARDS when cocooning in. So the arrival clips play forwards as she hatches, and the empress
     * clip plays reversed as she wraps up.
     * </p>
     * <p>
     * ⚠⚠ DO NOT PASS AN ENTER SELECTOR HERE. [stated] "the molt.emerge.empress needs to be played in reverse like the
     * other emerges that were entering molts to new forms." Supplying a third selector switches the tracker to "both
     * halves authored" mode, where NOTHING is ever reversed - which is the opposite of what her empress transition
     * wants. The reversal IS the mechanism here, not a fallback.
     * </p>
     */
    private final CocoonAnimationStateTracker<Queen> cocoonAnimationStateTracker =
        new CocoonAnimationStateTracker<>(QueenAnimator::selectLoopAnimation, QueenAnimator::selectEmergeAnimation);

    public QueenAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Queen> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .setKeyframeCallbacks(
                    AzKeyframeCallbacks.<Queen>builder()
                        .setSoundKeyframeHandler(QueenAnimator::onSoundKeyframe)
                        .build()
                )
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Queen animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Queen animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);

        var bakedModel = context().boneCache().getBakedModel();
        var eggSack = bakedModel.getBoneOrNull("root2");

        if (eggSack != null) {
            eggSack.setHidden(true);
        }

        // Shackle bones must keep tracking their world matrices so the ShackleAnchorLayer can publish live anchor
        // points for the chain render. VISIBILITY (which of them to show) is NOT decided here - it lives on the
        // renderer's bone-visibility filter, because BLib resets each bone's hidden flag every frame and would
        // wipe a setHidden() set here before the bone is drawn.
        for (var shackleBone : new String[] { "gLeftArmShackle", "gRightArmShackle", "gNeckShackle" }) {
            var bone = bakedModel.getBoneOrNull(shackleBone);
            if (bone != null) {
                bone.setTrackingMatrices(true);
            }
        }
    }

    /**
     * The in-cocoon loop, which depends on WHICH WAY she is moulting.
     * <p>
     * ⚠ SOURCE_COCOONING IS HER BECOMING AN EMPRESS - a different loop entirely from the two she plays while ARRIVING
     * as a queen. Reading only {@code cocoonSourceForm} would have had her lying in a praetorian's cocoon pose for the
     * whole empress transformation.
     * </p>
     */
    private static String selectLoopAnimation(Queen queen) {
        if (queen.cocoonState.get() == CocoonState.SOURCE_COCOONING) {
            return QueenAnimationRefs.MOLT_LOOP_EMPRESS_ANIMATION_NAME;
        }

        return queen.cocoonSourceForm.get() == CocoonSourceForm.CRUSHER
            ? QueenAnimationRefs.MOLTING_CRUSHER_ANIMATION_NAME
            : QueenAnimationRefs.MOLTING_PRAE_ANIMATION_NAME;
    }

    /**
     * The clip for whichever transformation she is in the middle of.
     * <p>
     * ⚠ THE TRACKER REVERSES WHATEVER THIS RETURNS WHEN SHE IS COCOONING IN, so the empress clip is selected here
     * rather than through an enter selector - handing it over as an "enter" would make it play forwards and she would
     * unwrap herself into a cocoon.
     * </p>
     * <p>
     * SOURCE_COCOONING is her becoming an EMPRESS; anything else is her ARRIVING as a queen, from a crusher or a
     * praetorian.
     * </p>
     */
    private static String selectEmergeAnimation(Queen queen) {
        if (queen.cocoonState.get() == CocoonState.SOURCE_COCOONING) {
            return QueenAnimationRefs.MOLT_EMERGE_EMPRESS_ANIMATION_NAME;
        }

        return queen.cocoonSourceForm.get() == CocoonSourceForm.CRUSHER
            ? QueenAnimationRefs.EMERGE_CRUSHER_ANIMATION_NAME
            : QueenAnimationRefs.EMERGE_PRAE_ANIMATION_NAME;
    }

    private void runPassiveAnimations(Queen queen) {
        var dispatcher = queen.getAnimationDispatcher();

        // Front-end Stage 3: while hibernating she holds the curled sleep pose, overriding idle/walk/run. Driven off
        // the
        // synced flag because the lifecycle phase is server-only state — animation dispatch must happen client-side.
        // Incapacitated (involuntary defeat collapse) outranks everything, including hibernation: she did not
        // choose this. Three-part machine driven off the synced flag - a one-shot DROP on the rising edge, the
        // loop while she is down, and a one-shot RISE on the falling edge as she gets back up. Both one-shots
        // hold on their last frame, so each hands off cleanly to whatever comes next.
        boolean incapacitatedNow = queen.isIncapacitated();
        if (incapacitatedNow && !previousIncapacitated) {
            dispatcher.incapacitatedDrop();
            previousIncapacitated = true;
            return;
        }
        if (!incapacitatedNow && previousIncapacitated) {
            dispatcher.incapacitatedRise();
            previousIncapacitated = false;
            return;
        }
        if (incapacitatedNow) {
            dispatcher.incapacitated();
            return;
        }

        // Straining against the chains. ONLY during the window where she is fully bound (4+ chains) but not yet
        // subdued: an inhibited queen is pacified, and one riding her chained eggsack is a settled captive
        // breeder. Neither strains. This is the fight she puts up in between.
        if (
            queen.bindChainCount.get() >= QueenBindManager.FULLY_BOUND_CHAINS
                && !queen.isInhibited()
                && !queen.isRidingOvipositor()
        ) {
            dispatcher.boundStruggle();
            return;
        }

        if (queen.isHibernating.get()) {
            dispatcher.hibernate();
            return;
        }

        // Let a dig one-shot finish before anything else takes the track. Higher-priority states (incapacitated,
        // bound struggle, hibernate) sit above this and still interrupt.
        if (digOneShotHoldTicks > 0) {
            digOneShotHoldTicks--;
            return;
        }

        // Vertical dig (Stage 2b clip-dig to anchor): digdown one-shot on start, digging loop while descending, digup
        // one-shot on stop. Driven off the synced flag since the digging state is server-only. The one-shots fire on
        // the rising/falling edge; the loop holds in between.
        boolean diggingNow = queen.isDiggingSynced.get();
        if (diggingNow && !previousDigging) {
            dispatcher.digDown();
            previousDigging = true;
            digOneShotHoldTicks = DIG_DOWN_TICKS;
            return;
        }
        if (!diggingNow && previousDigging) {
            dispatcher.digUp();
            previousDigging = false;
            digOneShotHoldTicks = DIG_UP_TICKS;
            return;
        }
        if (diggingNow) {
            dispatcher.digging();
            return;
        }

        // Founding stand-dig (construction economy step 6): she carves her own chamber. Same edge-driven triptych as
        // the vertical dig, off its own synced flag - a one-shot plant on the rising edge, the stand-dig loop while
        // the carve runs, a one-shot pull-out on the falling edge.
        boolean standDiggingNow = queen.standDiggingSynced.get();
        if (standDiggingNow && !previousStandDigging) {
            dispatcher.digStandStart();
            previousStandDigging = true;
            digOneShotHoldTicks = DIG_STAND_START_TICKS;
            return;
        }
        if (!standDiggingNow && previousStandDigging) {
            dispatcher.digStandStop();
            previousStandDigging = false;
            digOneShotHoldTicks = DIG_STAND_STOP_TICKS;
            return;
        }
        if (standDiggingNow) {
            dispatcher.standDigging();
            return;
        }

        // Crawl posture transitions - same edge-driven shape as the dig triptychs above, keyed off the synced
        // crawl flag. The one-shot fires on the edge and holds the track for the clip's ticks; a leg-loss collapse
        // plays the SAME drop clip at double speed with half the hold ([stated]). Placed above the attack block so
        // a posture change visually pre-empts a swing; the server blocks NEW attacks for the same window.
        boolean crawlingNow = queen.getCrawlingManager().isCrawling();
        if (previousCrawling == null) {
            previousCrawling = crawlingNow;
        } else if (crawlingNow != previousCrawling) {
            previousCrawling = crawlingNow;
            if (crawlingNow) {
                var collapse = queen.getCrawlingManager().isLegForcedCrawl();
                dispatcher.crawlDrop(collapse ? 2.0F : 1.0F);
                digOneShotHoldTicks = collapse
                    ? Math.max(1, QueenAnimationRefs.CRAWL_DROP_TICKS / 2)
                    : QueenAnimationRefs.CRAWL_DROP_TICKS;
            } else {
                dispatcher.crawlRise();
                digOneShotHoldTicks = QueenAnimationRefs.CRAWL_RISE_TICKS;
            }
            return;
        }

        var attackType = queen.attackType.get();
        var attackId = queen.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var animationName = selectAttackAnimation(queen, attackType, attackId);
                var speed = calculateAttackSpeed(queen, animationName);

                if (animationName == null) {
                    previousAttackId = attackId;
                    return;
                    // ⭐ POSTURE FIRST, as on every other caste: underwater she has one authored bite and the
                    // standing limb clips would swing her through water she cannot brace against.
                } else if (queen.isUnderWater()) {
                    dispatcher.swimAttack();
                } else if (attackType == Queen.SWIPE_DOWN) {
                    dispatcher.swipeDownAttack(animationName, speed);
                } else if (attackType == Queen.BACKHAND) {
                    dispatcher.backhandAttack(animationName, speed);
                } else if (attackType == Queen.TAIL_STRIKE) {
                    dispatcher.tailStrikeAttack(animationName, speed);
                } else if (attackType == Queen.CRAWL_ATTACK) {
                    dispatcher.crawlAttack(speed);
                } else if (attackType == Queen.HEAD_RAM) {
                    dispatcher.headRamAttack(speed);
                } else if (attackType == Queen.CRAWL_BITE) {
                    dispatcher.crawlBiteAttack();
                } else if (attackType == Queen.BITE) {
                    dispatcher.biteAttack(speed);
                }

                previousAttackId = attackId;
            }
            return;
        }

        var isMovingOnGround = queen.isMovingHorizontally.get() && queen.onGround();
        var isCrawling = queen.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (queen.getOvipositorManager().hasOvipositor()) {
            animFunction = dispatcher::sitOnOvipositor;
        } else if (queen.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(queen));
            } else if (queen.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlIdle : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Queen queen, String animationName) {
        var durationInTicks = queen.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(queen, animationName);

        // ⚠⚠ A MISSING CLIP MUST NEVER NPE THE RENDER THREAD. getAnimation returns NULL for a clip the loader
        // threw away - and GeckoLib discards an ENTIRE clip when one Molang expression fails to parse, so a
        // single bad keyframe in the art turns this line into a client crash the moment that animation is
        // selected. That is exactly what killed the game when a queen lost a leg: the forced crawl asked for
        // crawl.attack.*, which had been discarded, and this dereferenced null.
        if (animation == null) {
            return 1.0f;
        }

        return (float) (animation.length() / durationInTicks);
    }

    private String selectAttackAnimation(Queen queen, AttackType attackType, int attackId) {
        if (attackType == Queen.CRAWL_ATTACK) {
            // Mirrored pair, same length - the right clip stands in for both when measuring.
            return QueenAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;
        }

        if (attackType == Queen.HEAD_RAM) {
            return QueenAnimationRefs.ATTACK_HEADRAM_ANIMATION_NAME;
        }

        if (attackType == Queen.BITE) {
            return QueenAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        }

        if (attackType == Queen.CRAWL_BITE) {
            return QueenAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME;
        }
        if (attackType == Queen.SWIPE_DOWN) {
            return chooseArmAnimation(
                queen,
                attackId,
                QueenAnimationRefs.LEFT_SWIPE_DOWN_ANIMATION_NAME,
                QueenAnimationRefs.RIGHT_SWIPE_DOWN_ANIMATION_NAME
            );
        } else if (attackType == Queen.BACKHAND) {
            return chooseArmAnimation(
                queen,
                attackId,
                QueenAnimationRefs.LEFT_BACKHAND_ANIMATION_NAME,
                QueenAnimationRefs.RIGHT_BACKHAND_ANIMATION_NAME
            );
        } else if (attackType == Queen.TAIL_STRIKE) {
            return chooseTailAnimation(
                queen,
                attackId,
                QueenAnimationRefs.LEFT_TAIL_STRIKE_ANIMATION_NAME,
                QueenAnimationRefs.RIGHT_TAIL_STRIKE_ANIMATION_NAME
            );
        }

        return null;
    }

    private String chooseArmAnimation(Queen queen, int attackId, String leftAnimationName, String rightAnimationName) {
        var leftArmAttached = !DismembermentManager.isDetached(queen, LEFT_ARM_LIMB_ID);
        var rightArmAttached = !DismembermentManager.isDetached(queen, RIGHT_ARM_LIMB_ID);

        if (leftArmAttached && !rightArmAttached) {
            return leftAnimationName;
        }

        if (rightArmAttached && !leftArmAttached) {
            return rightAnimationName;
        }

        if (!leftArmAttached) {
            return null;
        }

        return chooseAlternatingAnimation(attackId, leftAnimationName, rightAnimationName);
    }

    private String chooseTailAnimation(Queen queen, int attackId, String leftAnimationName, String rightAnimationName) {
        if (DismembermentManager.isDetached(queen, QUEEN_TAIL_LIMB_ID)) {
            return null;
        }

        return chooseAlternatingAnimation(attackId, leftAnimationName, rightAnimationName);
    }

    private String chooseAlternatingAnimation(int attackId, String leftAnimationName, String rightAnimationName) {
        return (attackId & 1) == 0 ? leftAnimationName : rightAnimationName;
    }

    // ---- Chain-strain audio ---------------------------------------------------------------------------------

    /** Keyframe effect name in bound_struggle; see queen.animation.json. */
    private static final String CHAIN_STRUGGLE_KEYFRAME = "queen_chain_struggle";

    /**
     * Which of the two chain clips plays next, per queen. Keyed weakly so a despawned queen drops out on its own.
     * Per-entity rather than a single flag because one animator instance serves every queen on screen.
     */
    private static final Map<Queen, Boolean> CHAIN_SOUND_TOGGLE = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Fires from the sound keyframe at tick 0 of bound_struggle, so it lands exactly on every loop restart - the two
     * clips alternate as she keeps straining, and retiming the animation retimes the audio for free. Keyframes run
     * client-side, so this plays a LOCAL sound: every client showing her runs the same animation and hears its own copy
     * positioned at her.
     */
    private static void onSoundKeyframe(AzSoundKeyframeEvent<Queen> event) {
        if (!CHAIN_STRUGGLE_KEYFRAME.equals(event.getKeyframeData().getSound())) {
            return;
        }
        var queen = event.getAnimatable();
        var level = queen.level();
        if (!level.isClientSide) {
            return;
        }
        var playFirst = !Boolean.TRUE.equals(CHAIN_SOUND_TOGGLE.get(queen));
        CHAIN_SOUND_TOGGLE.put(queen, playFirst);
        var sound = playFirst
            ? AlienSoundEvents.ENTITY_QUEEN_CHAIN_STRUGGLE_1.get()
            : AlienSoundEvents.ENTITY_QUEEN_CHAIN_STRUGGLE_2.get();
        level.playLocalSound(
            queen.getX(),
            queen.getY(),
            queen.getZ(),
            sound,
            SoundSource.HOSTILE,
            1.0F,
            1.0F,
            false
        );
    }
}
