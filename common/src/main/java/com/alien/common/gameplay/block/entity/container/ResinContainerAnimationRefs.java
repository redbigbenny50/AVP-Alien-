package com.alien.common.gameplay.block.entity.container;

import com.blib.api.client.animation.v1.track.AzTrackHandle;

/**
 * The container's four clips.
 * <p>
 * ⚠ A TWO-STATE MACHINE WITH TWO TRANSITIONS, not four independent animations: {@code closed} and {@code open} are the
 * resting loops, {@code opening} and {@code closing} are the one-shots between them. Both transitions hold their last
 * frame, so a lid caught mid-swing by a chunk unload settles on the pose it was heading for rather than snapping.
 * </p>
 */
public final class ResinContainerAnimationRefs {

    /**
     * ⚠ ITS OWN TRACK HANDLE. {@code AzAlienAnimationUtil.BODY} is declared as {@code AzTrackHandle<Alien>} - it is
     * typed to the ENTITY hierarchy and will not accept a block entity, so the container declares its own rather than
     * loosening a type that fifteen animators depend on.
     */
    public static final AzTrackHandle<ResinContainerBlockEntity> BODY = AzTrackHandle.declare("body");

    public static final String OPENING = "opening";

    public static final String OPEN = "open";

    public static final String CLOSING = "closing";

    public static final String CLOSED = "closed";

    /** Both transition clips are 0.5s in the art. */
    public static final int TRANSITION_TICKS = 10;

    private ResinContainerAnimationRefs() {}
}
