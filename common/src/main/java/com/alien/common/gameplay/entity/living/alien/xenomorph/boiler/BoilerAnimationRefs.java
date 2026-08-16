package com.alien.common.gameplay.entity.living.alien.xenomorph.boiler;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.blib.api.client.animation.v1.track.AzTrackHandle;

public class BoilerAnimationRefs {

    public static final AzTrackHandle<Alien> FULL_BODY = AzTrackHandle.declare("full_body");

    /**
     * ⭐ THE BOILER HAS NO ATTACK CLIPS AND NEEDS NONE. [stated] "the boiler as you can tell from code doesnt have any
     * attacks it tracks a player by sound and line of sight once triggered." Its ATTACK_CLAW / ATTACK_TAIL / LUNGE
     * constants were removed rather than renamed - nothing ever referenced them, and the art has never had a matching
     * clip. A constant with no clip and no caller is a trap for whoever reads this next.
     */
    public static final String CRAWL_ANIMATION_NAME = "crawl";

    public static final String JUMP_ANIMATION_NAME = "jump";

    public static final String LAND_ANIMATION_NAME = "land";

    /** ⚠ Enter only - the boiler has NO molt.loop and NO molt.emerge, so the tracker reverses this for the exit. */
    public static final String MOLT_ENTER_ANIMATION_NAME = "molt.enter";

    public static final String IDLE_ANIMATION_NAME = "idle";

    public static final String RUN_ANIMATION_NAME = "run";

    public static final String SWIM_ANIMATION_NAME = "swim";

    public static final String WALK_ANIMATION_NAME = "walk";
}
