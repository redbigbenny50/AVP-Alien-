package com.alien.common.gameplay.entity.living.alien.adolescent.ai;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;

/**
 * ⭐⭐ WHAT A CHILD IS ALLOWED TO EAT. [stated] "They will attack and chase small passive mobs chickens, cats, axolotls,
 * baby animals, a fox is the largest thing they would probably try to eat. everything else they would run from unless
 * they can no longer escape inwhich they would turn to fight."
 * <p>
 * TWO WAYS IN, and they are deliberately different in kind. The {@code juvenile_prey} TAG is the species list, so it
 * retunes from data without a code change. The BABY rule is a rule rather than a list precisely because it has to hold
 * for animals that are far too big as adults - a calf is prey, the cow it grows into is not.
 * </p>
 * <p>
 * ⚠ THE CEILING IS THE FOX, and it is a judgement about SIZE, not about hostility. Nothing is admitted here for being
 * weak or for having attacked first; a zombie is not prey however easy it looks, and neither is a wolf that a child
 * could technically beat. Self-defence is a separate question answered by {@link CorneredTracker}.
 * </p>
 */
public final class JuvenilePrey {

    private JuvenilePrey() {}

    public static boolean isPrey(LivingEntity candidate) {
        if (!candidate.isAlive() || candidate instanceof Player) {
            return false;
        }

        // ⚠ Never its own kind, whatever the tag says. Hive members are family, and an eggsack or a facehugger sitting
        // still is not a meal - this also keeps a child from eating the hive it lives in.
        if (candidate instanceof Alien) {
            return false;
        }

        if (candidate.getType().is(AlienEntityTypeTags.JUVENILE_PREY)) {
            return true;
        }

        // Any baby ANIMAL - the passive breeding kind. Deliberately not "anything with isBaby", which would admit a
        // baby zombie and a hoglin calf that fights back harder than its parent.
        return candidate instanceof Animal animal && animal.isBaby();
    }
}
