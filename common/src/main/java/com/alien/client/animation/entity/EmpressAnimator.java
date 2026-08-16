package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.EmpressAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenScreamDefense;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class EmpressAnimator extends AzEntityAnimator<Empress> {

    private static final String NAME = "empress";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /**
     * Crawl-edge tracker for the posture transitions. Null until first observed so a mid-crawl load doesn't replay a
     * drop.
     */
    private Boolean previousCrawling;

    /** How long the sit/getoff one-shots own the track. Matched to the authored clips. */
    private static final int SACK_TRANSITION_TICKS = 20;

    /** Ticks the current crawl transition one-shot still owns the track. */
    private int crawlOneShotHoldTicks;

    /**
     * ⚠⚠ EXPLICIT SELECTORS. The no-arg tracker hardcodes "molting" + "molt.enter", and this art has NEVER contained a
     * clip called "molting" - so the loop half of every queen→empress molt was bind-posing SILENTLY. She was the LAST
     * caste still on the default; every other one has now been caught by the same bug.
     * <p>
     * Emerge-oriented: she is a molt DESTINATION only, so there is no enter clip and the tracker makes the cocooning
     * half by reversing the emerge.
     * </p>
     */
    private final CocoonAnimationStateTracker<Empress> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>(
        empress -> EmpressAnimationRefs.MOLT_LOOP_ANIMATION_NAME,
        empress -> EmpressAnimationRefs.MOLT_EMERGE_ANIMATION_NAME
    );

    /** Edge detection for the scream - the id is bumped server-side each time she screams. */
    private int previousScreamId = -1;

    private int screamHoldTicks;

    /**
     * ⭐⭐ THE SACK IS A THREE-STAGE SEQUENCE FOR HER, WHERE THE QUEEN HAS ONE LOOP. [stated] "she has a sit that
     * preludes her being on it or when the sack is first summoned. then the loop which plays while she is sitting on it
     * then if she gets off of it due to attack it plays the getoff animation."
     * <p>
     * ⚠ EDGE-DETECTED ON hasOvipositor, not polled: sit fires ONCE as the sack appears and holds its last frame into
     * the loop, and getoff fires ONCE as it goes. Dispatching either every tick would restart it on frame 0 forever.
     * </p>
     */
    private Boolean previousHasOvipositor;

    private int sackOneShotHoldTicks;

    public EmpressAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Empress> animationTrackContainer) {
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Empress animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Empress animatable, float partialTicks) {
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
    }

    private void runPassiveAnimations(Empress empress) {
        var dispatcher = empress.getAnimationDispatcher();

        // ⭐ THE SCREAM OUTRANKS EVERYTHING - it is a scripted panic, and the stun it applies lasts exactly as long
        // as the clip, so nothing she was doing may animate over it.
        var screamId = empress.screamId.get();

        if (screamId != previousScreamId) {
            previousScreamId = screamId;

            if (screamId > 0) {
                dispatcher.screamAttack();
                screamHoldTicks = QueenScreamDefense.SCREAM_DURATION_TICKS;
                return;
            }
        }

        if (screamHoldTicks > 0) {
            screamHoldTicks--;
            return;
        }

        // ⭐ THE SACK TRANSITIONS. Blocking one-shots, like the crawl pair below.
        if (sackOneShotHoldTicks > 0) {
            sackOneShotHoldTicks--;
            return;
        }

        var hasOvipositor = empress.getEmpressOvipositorManager().hasOvipositor();

        if (previousHasOvipositor == null) {
            previousHasOvipositor = hasOvipositor;
        } else if (previousHasOvipositor != hasOvipositor) {
            previousHasOvipositor = hasOvipositor;

            if (hasOvipositor) {
                dispatcher.sitOntoOvipositor();
            } else {
                dispatcher.getOffOvipositor();
            }

            sackOneShotHoldTicks = SACK_TRANSITION_TICKS;
            return;
        }

        // Crawl posture transitions - edge-driven one-shots off the synced crawl flag; a leg-loss collapse plays
        // the same drop clip at double speed with half the hold ([stated]). Above the attack block so a posture
        // change visually pre-empts a swing; the server blocks NEW attacks for the same window.
        if (crawlOneShotHoldTicks > 0) {
            crawlOneShotHoldTicks--;
            return;
        }
        boolean crawlingNow = empress.getCrawlingManager().isCrawling();
        if (previousCrawling == null) {
            previousCrawling = crawlingNow;
        } else if (crawlingNow != previousCrawling) {
            previousCrawling = crawlingNow;
            if (crawlingNow) {
                var collapse = empress.getCrawlingManager().isLegForcedCrawl();
                dispatcher.crawlDrop(collapse ? 2.0F : 1.0F);
                crawlOneShotHoldTicks = collapse
                    ? Math.max(1, EmpressAnimationRefs.CRAWL_DROP_TICKS / 2)
                    : EmpressAnimationRefs.CRAWL_DROP_TICKS;
            } else {
                dispatcher.crawlRise();
                crawlOneShotHoldTicks = EmpressAnimationRefs.CRAWL_RISE_TICKS;
            }
            return;
        }

        var attackType = empress.attackType.get();
        var attackId = empress.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(empress, attackType);

                // ⚠ POSTURE FIRST. Underwater the claws are one both-arms clip and the bite is its fallback;
                // crawling uses the crawl clips or the body snaps upright for the swing.
                if (attackType == Empress.SWIM_CLAWS)
                    dispatcher.swimClawsAttack();
                else if (empress.isUnderWater())
                    dispatcher.swimBiteAttack();
                else if (attackType == Empress.CRAWL_BITE)
                    dispatcher.crawlBiteAttack();
                else if (attackType == Empress.CRAWL_ATTACK)
                    dispatcher.crawlAttack(speed);
                else if (attackType == Empress.HEAD_RAM)
                    dispatcher.headRamAttack(speed);
                else if (attackType == Empress.BITE)
                    dispatcher.biteAttack(speed);
                else if (attackType == Empress.SWIPE_DOWN)
                    dispatcher.swipeDownAttack(speed);
                else if (attackType == Empress.BACKHAND)
                    dispatcher.backhandAttack(speed);
                else if (attackType == Empress.TAIL_STRIKE)
                    dispatcher.tailStrikeAttack(speed);

                previousAttackId = attackId;
            }
            return;
        }

        var isMovingOnGround = empress.isMovingHorizontally.get() && empress.onGround();
        var isCrawling = empress.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (hasOvipositor) {
            animFunction = dispatcher::sitOnOvipositor;
        } else if (empress.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(empress));
            } else if (empress.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlHold : dispatcher::idle;
        }

        animFunction.run();
    }

    private float calculateAttackSpeed(Empress empress, AttackType attackType) {
        String animationName = null;

        if (attackType == Empress.SWIPE_DOWN)
            // Mirrored pair, same length - the right clip stands in for both when measuring.
            animationName = EmpressAnimationRefs.SWIPEDOWN_RIGHT_ANIMATION_NAME;
        else if (attackType == Empress.BACKHAND)
            animationName = EmpressAnimationRefs.BACKHAND_RIGHT_ANIMATION_NAME;
        else if (attackType == Empress.TAIL_STRIKE)
            animationName = EmpressAnimationRefs.TAILSTRIKE_RIGHT_ANIMATION_NAME;

        else if (attackType == Empress.BITE)
            animationName = EmpressAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == Empress.CRAWL_BITE)
            animationName = EmpressAnimationRefs.CRAWL_ATTACK_BITE_ANIMATION_NAME;
        else if (attackType == Empress.CRAWL_ATTACK)
            animationName = EmpressAnimationRefs.CRAWL_ATTACK_RIGHT_ANIMATION_NAME;
        else if (attackType == Empress.HEAD_RAM)
            animationName = EmpressAnimationRefs.ATTACK_HEADRAM_ANIMATION_NAME;
        else if (attackType == Empress.SWIM_CLAWS)
            animationName = EmpressAnimationRefs.SWIM_ATTACK_CLAWS_ANIMATION_NAME;

        var durationInTicks = empress.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(empress, animationName);

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
}
