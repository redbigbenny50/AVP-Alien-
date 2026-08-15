package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.world.InteractionHand;

/**
 * A caste's GROUND GAME - what it fights with once it has been knocked into a crawl.
 * <p>
 * [stated] "they should be the same as arm/claw attacks but maybe a bit weaker because of the posture." So these are
 * deliberately NOT a new move: same limb requirement, same mirrored art, same sound, just less damage.
 * </p>
 * <p>
 * ⚠⚠ `.crawlAttack()` DOES TWO THINGS, AND THE SECOND IS THE DANGEROUS ONE. It lets the attack through the posture gate
 * while crawling, and it flips {@code XenomorphAttackConfig}'s CRAWL PREFERENCE: the moment one usable crawl attack
 * exists in a caste's regular list, the standing attacks stop being offered while it crawls. That is the rule we want -
 * but it means a caste whose only crawl attack is unusable (both arms gone, say) has NO attack at all rather than
 * falling back. Every caste taking a crawl set wants either a second one on a different limb or an accepted answer to
 * that case.
 * </p>
 */
public final class CrawlAttack {

    /** Which limb the crawl attack needs - the mirrored arm swing, or the bite. */
    public enum Limb {
        ARM,
        HEAD
    }

    private CrawlAttack() {}

    /**
     * @param id              attack id, e.g. {@code praetorian_crawl_claw}
     * @param damageFraction  multiplier on the caste's OWN {@code ATTACK_DAMAGE} - the posture penalty
     * @param limb            what the swing needs; ARM attacks mirror, HEAD ones do not
     * @param durationInTicks swing length, matched to the authored clip
     */
    public static AttackType create(String id, float damageFraction, Limb limb, int durationInTicks) {
        var builder = AttackType.builder(id)
            .crawlAttack()
            .defaultDurationInTicks(durationInTicks)
            .sound(AlienSoundEvents.ENTITY_XENOMORPH_ATTACK)
            .damageApplicator((xenomorph, target) -> apply(xenomorph, target, damageFraction));

        return (limb == Limb.HEAD ? builder.requiresHead() : builder.requiresAnyArm()).build();
    }

    private static void apply(Xenomorph xenomorph, net.minecraft.world.entity.LivingEntity target, float damageFraction) {
        if (!ScaledDamage.canReach(xenomorph, target)) {
            return;
        }

        xenomorph.swing(InteractionHand.MAIN_HAND);
        ScaledDamage.hurtScaled(xenomorph, target, damageFraction);
    }
}
