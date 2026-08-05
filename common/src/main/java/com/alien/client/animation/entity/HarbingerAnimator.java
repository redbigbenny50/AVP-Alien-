package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.Harbinger;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.HarbingerAnimationRefs;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.HarbingerBackhandAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.HarbingerFrontKickAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.HarbingerGroundSlamAttack;
import com.alien.common.gameplay.entity.living.alien.xenomorph.harbinger.HarbingerKickAttack;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class HarbingerAnimator extends AzEntityAnimator<Harbinger> {

    private static final String NAME = "harbinger";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private int previousAttackId = Integer.MIN_VALUE;

    /**
     * Crawl-edge tracker for the posture transitions. Null until first observed so a mid-crawl load doesn't replay a
     * drop.
     */
    private Boolean previousCrawling;

    /** Ticks the current crawl transition one-shot still owns the track. */
    private int crawlOneShotHoldTicks;

    private final CocoonAnimationStateTracker<Harbinger> cocoonAnimationStateTracker = new CocoonAnimationStateTracker<>();

    public HarbingerAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<Harbinger> animationTrackContainer) {
        // The back whips are a PARALLEL layer, not states of the body machine: their bones
        // (gLeftWhip.. / gRightWhip..) appear in no body clip, so nothing drove them while BODY was the
        // only track. One track per side, since both whips must move at the same time and a track only
        // ever plays one clip.
        animationTrackContainer.add(
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.BODY)
                .setTransitionLength(5)
                .build(),
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.LEFT_WHIP)
                .setTransitionLength(5)
                .build(),
            AzAnimationTrack.builder(this, AzAlienAnimationUtil.RIGHT_WHIP)
                .setTransitionLength(5)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(Harbinger animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(Harbinger animatable, float partialTicks) {
        super.setCustomAnimations(animatable, partialTicks);

        if (cocoonAnimationStateTracker.run(animatable)) {
            return;
        }

        AzAlienHeadAnimationUtil.applyHeadLookFromBindPose(animatable, context(), partialTicks, "gNeck");

        runPassiveAnimations(animatable);
        runWhipAnimations(animatable);
    }

    private void runPassiveAnimations(Harbinger harbinger) {
        var dispatcher = harbinger.getAnimationDispatcher();

        // Crawl posture transitions - edge-driven one-shots off the synced crawl flag; a leg-loss collapse plays
        // the same drop clip at double speed with half the hold ([stated]). Above the attack block so a posture
        // change visually pre-empts a swing; the server blocks NEW attacks for the same window.
        if (crawlOneShotHoldTicks > 0) {
            crawlOneShotHoldTicks--;
            return;
        }
        boolean crawlingNow = harbinger.getCrawlingManager().isCrawling();
        if (previousCrawling == null) {
            previousCrawling = crawlingNow;
        } else if (crawlingNow != previousCrawling) {
            previousCrawling = crawlingNow;
            if (crawlingNow) {
                var collapse = harbinger.getCrawlingManager().isLegForcedCrawl();
                dispatcher.crawlDown(collapse ? 2.0F : 1.0F);
                crawlOneShotHoldTicks = collapse
                    ? Math.max(1, HarbingerAnimationRefs.CRAWL_DOWN_TICKS / 2)
                    : HarbingerAnimationRefs.CRAWL_DOWN_TICKS;
            } else {
                dispatcher.crawlUp();
                crawlOneShotHoldTicks = HarbingerAnimationRefs.CRAWL_UP_TICKS;
            }
            return;
        }

        var attackType = harbinger.attackType.get();
        var attackId = harbinger.attackId.get();

        if (!attackType.isNone()) {
            if (attackId != previousAttackId) {
                var speed = calculateAttackSpeed(harbinger, attackType);

                if (attackType == Harbinger.BITE) {
                    dispatcher.biteAttack(speed);
                } else if (attackType == Harbinger.CLAW) {
                    dispatcher.rightClawAttack(speed);
                } else if (attackType == Harbinger.TAIL) {
                    dispatcher.tailAttack(speed);
                } else if (attackType == Harbinger.CRAWL_BITE) {
                    dispatcher.biteAttack(speed);
                } else if (attackType == Harbinger.CRAWL_WHIPSTAB_LEFT) {
                    dispatcher.leftWhipstabAttack(speed);
                } else if (attackType == Harbinger.CRAWL_WHIPSTAB_RIGHT) {
                    dispatcher.rightWhipstabAttack(speed);
                } else if (attackType == HarbingerGroundSlamAttack.ATTACK) {
                    dispatcher.groundSlamAttack(speed);
                } else if (attackType == HarbingerBackhandAttack.ATTACK) {
                    dispatcher.backhandAttack(speed);
                } else if (attackType == HarbingerKickAttack.ATTACK) {
                    dispatcher.kickAttack(speed);
                } else if (attackType == HarbingerFrontKickAttack.ATTACK) {
                    dispatcher.frontKickAttack(speed);
                }

                previousAttackId = attackId;
            }

            // The crawl whipstabs play on their own whip track now, and their clips move nothing but that
            // side's whip - so the body is free to keep crawling underneath instead of holding on a clip
            // that never touches it. Every other attack still owns the body.
            var isWhipOnlyAttack = attackType == Harbinger.CRAWL_WHIPSTAB_LEFT
                || attackType == Harbinger.CRAWL_WHIPSTAB_RIGHT;

            if (!isWhipOnlyAttack) {
                return;
            }
        }

        var isMovingOnGround = harbinger.isMovingHorizontally.get() && harbinger.onGround();
        var isCrawling = harbinger.getCrawlingManager().isCrawling();
        Runnable animFunction;

        if (harbinger.isUnderWater()) {
            animFunction = dispatcher::swim;
        } else if (isMovingOnGround) {
            if (isCrawling) {
                animFunction = () -> dispatcher.crawl(AzAlienAnimationUtil.crawlAnimationSpeed(harbinger));
            } else if (harbinger.isMovingQuickly.get()) {
                animFunction = dispatcher::run;
            } else {
                animFunction = dispatcher::walk;
            }
        } else {
            animFunction = isCrawling ? dispatcher::crawlIdle : dispatcher::idle;
        }

        animFunction.run();
    }

    /**
     * Drives the two whip tracks. This runs every frame regardless of what the body is doing - that is the whole point
     * of the layer, the whips keep moving through idles, walks and body attacks alike.
     * <p>
     * The idle and movement clips are procedural: a single Molang keyframe and no baked motion, so the sway only exists
     * while the track is actively playing them. The attack pair is authored keyframes and covers both crawling and
     * attacking. A whipstab owns its own side for the duration of the strike; the opposite whip carries on with the
     * braced attack loop.
     */
    private void runWhipAnimations(Harbinger harbinger) {
        var dispatcher = harbinger.getAnimationDispatcher();
        var attackType = harbinger.attackType.get();
        var isRunning = harbinger.isMovingHorizontally.get()
            && harbinger.onGround()
            && harbinger.isMovingQuickly.get();

        Runnable leftWhipFunction;
        Runnable rightWhipFunction;

        if (!attackType.isNone()) {
            // Attacking outranks stance: the whips brace on the authored attack loop whatever the body is
            // doing, so a swing never reads as a lazy idle sway. The striking side is skipped below.
            leftWhipFunction = dispatcher::leftWhipAttackIdle;
            rightWhipFunction = dispatcher::rightWhipAttackIdle;
        } else if (harbinger.isUnderWater() || isRunning) {
            // Running and swimming share the harder trailing sway.
            leftWhipFunction = dispatcher::leftWhipMovement;
            rightWhipFunction = dispatcher::rightWhipMovement;
        } else if (harbinger.getCrawlingManager().isCrawling()) {
            // Same braced loop the attacks use.
            leftWhipFunction = dispatcher::leftWhipAttackIdle;
            rightWhipFunction = dispatcher::rightWhipAttackIdle;
        } else {
            // Idle and walking both use the slow sway.
            leftWhipFunction = dispatcher::leftWhipIdle;
            rightWhipFunction = dispatcher::rightWhipIdle;
        }

        if (attackType != Harbinger.CRAWL_WHIPSTAB_LEFT) {
            leftWhipFunction.run();
        }

        if (attackType != Harbinger.CRAWL_WHIPSTAB_RIGHT) {
            rightWhipFunction.run();
        }
    }

    private float calculateAttackSpeed(Harbinger harbinger, AttackType attackType) {
        String animationName;

        if (attackType == Harbinger.BITE) {
            animationName = HarbingerAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Harbinger.CLAW) {
            animationName = HarbingerAnimationRefs.ATTACK_CLAW_ANIMATION_NAME;
        } else if (attackType == Harbinger.TAIL) {
            animationName = HarbingerAnimationRefs.ATTACK_TAIL_ANIMATION_NAME;
        } else if (attackType == Harbinger.CRAWL_BITE) {
            animationName = HarbingerAnimationRefs.ATTACK_BITE_ANIMATION_NAME;
        } else if (attackType == Harbinger.CRAWL_WHIPSTAB_LEFT) {
            animationName = HarbingerAnimationRefs.ATTACKCRAWL_LEFT_WHIPSTAB_ANIMATION_NAME;
        } else if (attackType == Harbinger.CRAWL_WHIPSTAB_RIGHT) {
            animationName = HarbingerAnimationRefs.ATTACKCRAWL_RIGHT_WHIPSTAB_ANIMATION_NAME;
        } else if (attackType == HarbingerGroundSlamAttack.ATTACK) {
            animationName = HarbingerAnimationRefs.ATTACK_GROUND_SLAM_ANIMATION_NAME;
        } else if (attackType == HarbingerBackhandAttack.ATTACK) {
            animationName = HarbingerAnimationRefs.ATTACK_BACKHAND_ANIMATION_NAME;
        } else if (attackType == HarbingerKickAttack.ATTACK) {
            animationName = HarbingerAnimationRefs.ATTACK_KICK_ANIMATION_NAME;
        } else if (attackType == HarbingerFrontKickAttack.ATTACK) {
            animationName = HarbingerAnimationRefs.ATTACK_FRONT_KICK_ANIMATION_NAME;
        } else {
            animationName = null;
        }

        var durationInTicks = harbinger.attackDurationInTicks.get();

        if (animationName == null || durationInTicks <= 0) {
            return 1.0f;
        }

        var animation = getAnimation(harbinger, animationName);

        return (float) (animation.length() / durationInTicks);
    }
}
