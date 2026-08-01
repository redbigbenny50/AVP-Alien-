package com.alien.common.gameplay.entity;

/**
 * Implemented by entities whose caste has authored crawl ENTER/EXIT transition clips (queen, harbinger, ravager,
 * empress as of Aug 1). {@link CrawlingManager} calls this on the SERVER at the exact tick the crawl state flips and
 * blocks navigation and new attacks for the returned number of ticks - [stated] the transitions are "blocking while
 * they play". The VISUAL half lives entirely client-side: each caste's animator watches the same synced crawl flag,
 * fires the drop/rise one-shot on the edge, and holds the track for the same tick counts (shared as constants in the
 * caste's AnimationRefs so the two sides can never disagree).
 * <p>
 * A caste that does not implement this keeps today's behaviour exactly: the posture snaps and nothing blocks -
 * which is correct, because with no clip there is nothing to wait for.
 * <p>
 * LEG-LOSS COLLAPSE is handled by the CALLER, not here: [stated] "a leg-loss collapse plays the same drop clip
 * FASTER" - CrawlingManager halves the returned enter duration when the flip was forced by a detached leg, and the
 * animators play the same drop clip at double speed off the same detection.
 */
public interface CrawlPostureTransitionListener {

    /**
     * Duration in ticks of this caste's transition clip for the given direction - enter = the drop clip, exit = the
     * rise clip. Return 0 for a direction with no clip.
     */
    int crawlPostureTransitionTicks(boolean enteringCrawl);
}
