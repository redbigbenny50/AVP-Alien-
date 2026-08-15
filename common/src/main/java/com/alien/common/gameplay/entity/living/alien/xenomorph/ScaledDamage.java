package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.AlienResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Lands a hit at a FRACTION of the attacker's normal damage.
 * <p>
 * ⚠⚠ WHY IT IS DONE THIS WAY, AND NOT WITH {@code hurt()} AND A RAW NUMBER: {@code doHurtTarget} is what carries this
 * mod's own plumbing - acid blood, the gun/limb damage bridge, aggro bookkeeping. Computing a damage number and calling
 * {@code hurt()} directly would land the right number and silently drop all of it. Scaling the ATTRIBUTE with a
 * TRANSIENT modifier around the ordinary call keeps every hook and still softens the blow.
 * </p>
 * <p>
 * ⚠⚠ THE MODIFIER IS REMOVED IN A {@code finally}. A leaked one permanently rescales EVERY later attack the entity
 * makes, and the symptom - "praetorians hit soft after a backhand" - points nowhere near the cause.
 * </p>
 * <p>
 * Shared by the backhand and the crawl attacks. Any future "this attack hits for less" wants this, not its own copy.
 * </p>
 */
public final class ScaledDamage {

    private static final ResourceLocation SCALE_ID = AlienResources.location("scaled_attack_damage");

    private ScaledDamage() {}

    /**
     * Runs {@code doHurtTarget} with attack damage multiplied by {@code fraction}.
     *
     * @return whether the hit actually landed - false for blocked, immune, or still in i-frames
     */
    public static boolean hurtScaled(Xenomorph xenomorph, LivingEntity target, float fraction) {
        var attribute = xenomorph.getAttribute(Attributes.ATTACK_DAMAGE);

        if (attribute == null) {
            return xenomorph.doHurtTarget(target);
        }

        // ADD_MULTIPLIED_BASE with (fraction - 1) scales the base value: 0.8 -> -0.2 -> 80% of normal.
        var modifier = new AttributeModifier(SCALE_ID, fraction - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        attribute.addTransientModifier(modifier);

        try {
            return xenomorph.doHurtTarget(target);
        } finally {
            attribute.removeModifier(SCALE_ID);
        }
    }

    /** The ordinary reach + line-of-sight check every melee applicator shares. */
    public static boolean canReach(Xenomorph xenomorph, LivingEntity target) {
        var attackRange = xenomorph.getBbWidth() + 1.0;
        return xenomorph.distanceTo(target) <= attackRange && xenomorph.getSensing().hasLineOfSight(target);
    }
}
