package com.alien.common.gameplay.entity.living.alien.chestburster;

import com.blib.api.client.animation.v1.command.AzCommand;
import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;

public class ChestbursterAnimationDispatcher {

    private static final AzCommand<Chestburster> IDLE_HEAD = AzCommand.<Chestburster>idempotent()
            .play(ChestbursterAnimationRefs.HEAD, ChestbursterAnimationRefs.IDLE_HEAD_ANIMATION_NAME, AzPlayBehaviors.LOOP)
            .build();

    private static final AzCommand<Chestburster> IDLE_TAIL = AzCommand.<Chestburster>builder()
            .cancel(ChestbursterAnimationRefs.TAIL)
            .build();

    private static final AzCommand<Chestburster> SLITHER_TAIL = AzCommand.<Chestburster>idempotent()
            .play(ChestbursterAnimationRefs.TAIL, ChestbursterAnimationRefs.SLITHER_TAIL_ANIMATION_NAME, AzPlayBehaviors.LOOP)
            .build();

    // MUST be replay(), not builder(). A play action needs a dispatch mode; builder() has none, so the AzCommand
    // constructor throws - and because these are STATIC fields, that throw is an ExceptionInInitializerError that
    // takes the whole class down. Every chestburster spawn then crashed the server on construction. (IDLE_TAIL gets
    // away with builder() only because .cancel() needs no dispatch mode.) replay() is what every other one-shot
    // attack in the mod uses - a bite should fire fresh each time, not be deduplicated.
    private static final AzCommand<Chestburster> BITE_HEAD = AzCommand.<Chestburster>replay()
            .play(ChestbursterAnimationRefs.HEAD, ChestbursterAnimationRefs.BITE_HEAD_ANIMATION_NAME, AzPlayBehaviors.PLAY_ONCE)
            .build();

    private static final AzCommand<Chestburster> IDLE = AzCommand.compose(IDLE_HEAD, IDLE_TAIL);

    private static final AzCommand<Chestburster> SLOW_SLITHER = AzCommand.compose(IDLE_HEAD, SLITHER_TAIL);

    private final Chestburster chestburster;

    public ChestbursterAnimationDispatcher(Chestburster chestburster) {
        this.chestburster = chestburster;
    }

    public void idle() {
        IDLE.dispatchForEntity(chestburster);
    }

    public void slowSlither() {
        SLOW_SLITHER.dispatchForEntity(chestburster);
    }

    /** Snap of the head - used when eating a spent egg / facehugger. */
    public void biteAttack() {
        BITE_HEAD.dispatchForEntity(chestburster);
    }
}