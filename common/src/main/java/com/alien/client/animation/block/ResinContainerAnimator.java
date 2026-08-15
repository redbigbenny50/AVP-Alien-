package com.alien.client.animation.block;

import com.alien.common.gameplay.block.entity.container.ResinContainerAnimationRefs;
import com.alien.common.gameplay.block.entity.container.ResinContainerBlockEntity;
import com.blib.api.client.animation.v1.animator.AzAnimatorConfig;
import com.blib.api.client.animation.v1.animator.AzBlockAnimator;
import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.blib.api.client.animation.v1.track.AzAnimationTrack;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Drives the container's ribs: closed, opening, open, closing.
 * <h2>⚠⚠ EDGE-DETECTED, NOT POLLED</h2> The transitions are one-shots. Dispatching either every tick would restart it
 * on frame 0 forever and the ribs would quiver instead of swinging - the same trap the castes' jump clips have. This
 * tracks the previous open state and only acts when it flips, then holds the track for the clip's length so the resting
 * loop cannot cut it short.
 * <h2>⚠ FIRST OBSERVATION SETS THE RESTING POSE</h2> A container loaded from disk with someone already inside it must
 * NOT play the opening clip - nobody just opened it. The null check on {@code previousOpen} is what makes a chunk load
 * settle straight into the right loop.
 */
public class ResinContainerAnimator extends AzBlockAnimator<ResinContainerBlockEntity> {

    private static final ResourceLocation ANIMATION =
        ResourceLocation.fromNamespaceAndPath("avp_alien", "animations/block/resin_container.animation.json");

    private static final AzCommand<ResinContainerBlockEntity> OPENING = AzCommand
        .<ResinContainerBlockEntity>replay()
        .play(ResinContainerAnimationRefs.BODY, ResinContainerAnimationRefs.OPENING, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<ResinContainerBlockEntity> OPEN = AzCommand
        .<ResinContainerBlockEntity>idempotent()
        .play(ResinContainerAnimationRefs.BODY, ResinContainerAnimationRefs.OPEN, AzPlayBehaviors.LOOP)
        .build();

    private static final AzCommand<ResinContainerBlockEntity> CLOSING = AzCommand
        .<ResinContainerBlockEntity>replay()
        .play(ResinContainerAnimationRefs.BODY, ResinContainerAnimationRefs.CLOSING, AzPlayBehaviors.HOLD_ON_LAST_FRAME)
        .build();

    private static final AzCommand<ResinContainerBlockEntity> CLOSED = AzCommand
        .<ResinContainerBlockEntity>idempotent()
        .play(ResinContainerAnimationRefs.BODY, ResinContainerAnimationRefs.CLOSED, AzPlayBehaviors.LOOP)
        .build();

    private Boolean previousOpen;

    private int transitionHoldTicks;

    public ResinContainerAnimator() {
        super(AzAnimatorConfig.defaultConfig());
    }

    @Override
    public void registerTracks(AzAnimationTrackContainer<ResinContainerBlockEntity> trackContainer) {
        trackContainer.add(
            AzAnimationTrack.builder(this, ResinContainerAnimationRefs.BODY)
                .setTransitionLength(3)
                .build()
        );
    }

    @Override
    public @NotNull ResourceLocation getAnimationLocation(ResinContainerBlockEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(ResinContainerBlockEntity container, float partialTicks) {
        super.setCustomAnimations(container, partialTicks);

        var open = container.isOpen();

        if (previousOpen == null) {
            // Loaded from disk, or seen for the first time: settle into the resting pose without a transition.
            previousOpen = open;
            (open ? OPEN : CLOSED).dispatchForBlockEntity(container);
            return;
        }

        if (transitionHoldTicks > 0) {
            transitionHoldTicks--;
            return;
        }

        if (previousOpen != open) {
            previousOpen = open;
            (open ? OPENING : CLOSING).dispatchForBlockEntity(container);
            transitionHoldTicks = ResinContainerAnimationRefs.TRANSITION_TICKS;
            return;
        }

        (open ? OPEN : CLOSED).dispatchForBlockEntity(container);
    }
}
