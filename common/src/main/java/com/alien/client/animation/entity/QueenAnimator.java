package com.alien.client.animation.entity;

import com.alien.AlienResources;
import com.alien.client.animation.entity.cocoon.CocoonAnimationStateTracker;
import com.alien.common.gameplay.entity.living.alien.xenomorph.AttackType;
import com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonSourceForm;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenAnimationRefs;
import com.alien.common.util.AzAlienAnimationUtil;
import com.alien.common.util.AzAlienHeadAnimationUtil;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.blib.api.common.dismemberment.v1.DismembermentManager;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class QueenAnimator extends AzEntityAnimator<Queen> {

    private static final String NAME = "queen";

    private static final ResourceLocation ANIMATION = AlienResources.entityAnimationLocation(NAME);

    private static final ResourceLocation LEFT_ARM_LIMB_ID = AlienResources.location("queen_left_arm");

    private static final ResourceLocation QUEEN_TAIL_LIMB_ID = AlienResources.location("queen_tail");

    private static final ResourceLocation RIGHT_ARM_LIMB_ID = AlienResources.location("queen_right_arm");

    private int previousAttackId = Integer.MIN_VALUE;

    /** Edge-detects the digging state so digdown/digup one-shots fire once on start/stop. */
    private boolean previousDigging = false;

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

        // Layer 2: reveal each shackle once its chain is attached (chain 1 -> left arm, 2 -> right arm, 3+ -> neck;
        // chains 5-8 reuse those same bones). Tracker/inhibitor attachment points stay hidden until their slices land.
        int chainCount = animatable.bindChainCount.get();

        var leftArmShackle = bakedModel.getBoneOrNull("gLeftArmShackle");
        if (leftArmShackle != null) {
            leftArmShackle.setHidden(chainCount < 1);
            leftArmShackle.setTrackingMatrices(true);
        }

        var rightArmShackle = bakedModel.getBoneOrNull("gRightArmShackle");
        if (rightArmShackle != null) {
            rightArmShackle.setHidden(chainCount < 2);
            rightArmShackle.setTrackingMatrices(true);
        }

        var neckShackle = bakedModel.getBoneOrNull("gNeckShackle");
        if (neckShackle != null) {
            neckShackle.setHidden(chainCount < 3);
            neckShackle.setTrackingMatrices(true);
        }

        var tracker = bakedModel.getBoneOrNull("gTracker");
        if (tracker != null) {
            tracker.setHidden(!animatable.isTracked());
        }

        var inhibitor = bakedModel.getBoneOrNull("gInhibitor");
        if (inhibitor != null) {
            inhibitor.setHidden(!animatable.isInhibited());
        }
    }

    /** Source-specific in-cocoon loop: from a crusher she plays molting.crusher, otherwise molting.prae. */
    private static String selectLoopAnimation(Queen queen) {
        return queen.cocoonSourceForm.get() == CocoonSourceForm.CRUSHER
            ? QueenAnimationRefs.MOLTING_CRUSHER_ANIMATION_NAME
            : QueenAnimationRefs.MOLTING_PRAE_ANIMATION_NAME;
    }

    /** Source-specific emerge burst: from a crusher she plays emerge.crusher, otherwise emerge.prae. */
    private static String selectEmergeAnimation(Queen queen) {
        return queen.cocoonSourceForm.get() == CocoonSourceForm.CRUSHER
            ? QueenAnimationRefs.EMERGE_CRUSHER_ANIMATION_NAME
            : QueenAnimationRefs.EMERGE_PRAE_ANIMATION_NAME;
    }

    private void runPassiveAnimations(Queen queen) {
        var dispatcher = queen.getAnimationDispatcher();

        // Front-end Stage 3: while hibernating she holds the curled sleep pose, overriding idle/walk/run. Driven off
        // the
        // synced flag because the lifecycle phase is server-only state — animation dispatch must happen client-side.
        if (queen.isHibernating.get()) {
            dispatcher.hibernate();
            return;
        }

        // Vertical dig (Stage 2b clip-dig to anchor): digdown one-shot on start, digging loop while descending, digup
        // one-shot on stop. Driven off the synced flag since the digging state is server-only. The one-shots fire on
        // the rising/falling edge; the loop holds in between.
        boolean diggingNow = queen.isDiggingSynced.get();
        if (diggingNow && !previousDigging) {
            dispatcher.digDown();
            previousDigging = true;
            return;
        }
        if (!diggingNow && previousDigging) {
            dispatcher.digUp();
            previousDigging = false;
            return;
        }
        if (diggingNow) {
            dispatcher.digging();
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
                } else if (attackType == Queen.SWIPE_DOWN) {
                    dispatcher.swipeDownAttack(animationName, speed);
                } else if (attackType == Queen.BACKHAND) {
                    dispatcher.backhandAttack(animationName, speed);
                } else if (attackType == Queen.TAIL_STRIKE) {
                    dispatcher.tailStrikeAttack(animationName, speed);
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

        return (float) (animation.length() / durationInTicks);
    }

    private String selectAttackAnimation(Queen queen, AttackType attackType, int attackId) {
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
}
