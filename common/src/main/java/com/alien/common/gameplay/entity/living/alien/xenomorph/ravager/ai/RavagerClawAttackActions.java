package com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.ai;

import com.alien.common.gameplay.entity.dismemberment.MirroredAttackSide;
import com.alien.common.gameplay.entity.dismemberment.RavagerHeadDismemberment;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ravager.Ravager;
import com.alien.common.registry.key.AlienDamageTypeKeys;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.LimbDismemberer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

public class RavagerClawAttackActions {

    /**
     * Floor chance for the double-claw strike to rip off an arm. Always applied if the target is smaller than the
     * ravager — additional bonuses scale on top of this.
     */
    private static final float ARM_BASE_CHANCE = 0.40F;

    /**
     * Bonus added when the target is at zero health, scaled linearly by {@code 1 - currentHp / maxHp}. A near-dead
     * target picks up the full bonus; a target at full health gets none of it.
     */
    private static final float ARM_LOW_HEALTH_BONUS = 0.40F;

    /**
     * Bonus added based on how much of the target's max health was consumed by this single strike, capped at 1×. A hit
     * that dealt 100% of max health adds the full bonus; a glancing hit adds proportionally less.
     */
    private static final float ARM_DAMAGE_BONUS = 0.20F;

    /**
     * Single-claw AOE strike. Decapitates any smaller-than-ravager target that the strike kills, leaving larger targets
     * untouched (they still take normal damage).
     */
    public static void singleClaw(Ravager ravager) {
        var damageSource = ravager.damageSources().source(AlienDamageTypeKeys.RAVAGER_CLAW, ravager);
        var damage = (float) ravager.getAttributeValue(Attributes.ATTACK_DAMAGE);

        for (var target : targetsInFront(ravager)) {
            if (target.isInvulnerableTo(damageSource)) {
                continue;
            }

            target.hurt(damageSource, damage);

            if (target.isDeadOrDying() && RavagerAreaAttackUtil.isSmallerThanRavager(ravager, target)) {
                RavagerHeadDismemberment.tryDismemberHead(
                    target,
                    RavagerHeadDismemberment.knockbackAwayFrom(ravager)
                );
            }
        }
    }

    /**
     * Double-claw AOE strike. On top of normal damage, rolls a per-target arm dismemberment chance whose probability
     * scales with how close the target is to death and how heavy the strike was. The target does not have to die for
     * the dismemberment to land.
     */
    /**
     * ⭐⭐ HALF DAMAGE ON ONE MISSING ARM. [stated] "if one arm is missing it will still play at 50% damage and wont play
     * at all with no arms."
     * <p>
     * ⚠ THE TWO HALVES OF THAT RULE LIVE IN DIFFERENT PLACES, and neither alone expresses it: the "not at all with no
     * arms" half is `requiresAnyArm()` on the AttackType, which refuses the swing outright; the "50%" half is here,
     * priced into the damage. This attack used to carry `requiresBothArms()`, which collapsed both halves into one and
     * meant a one-armed ravager simply never used it.
     * </p>
     */
    public static void doubleClaw(Ravager ravager) {
        var damageSource = ravager.damageSources().source(AlienDamageTypeKeys.RAVAGER_CLAW, ravager);
        var bothArms = !MirroredAttackSide.isArmDetached(ravager, true)
            && !MirroredAttackSide.isArmDetached(ravager, false);
        var damage = (float) ravager.getAttributeValue(Attributes.ATTACK_DAMAGE) * (bothArms ? 1.0F : 0.5F);

        for (var target : targetsInFront(ravager)) {
            if (target.isInvulnerableTo(damageSource)) {
                continue;
            }

            var healthBefore = target.getHealth();
            target.hurt(damageSource, damage);
            var damageDealt = Math.max(0F, healthBefore - target.getHealth());

            if (!RavagerAreaAttackUtil.isSmallerThanRavager(ravager, target)) {
                continue;
            }

            var chance = armDismemberChance(target, damageDealt);

            if (ravager.getRandom().nextFloat() < chance) {
                LimbDismemberer.detachFirstOfCategory(target, LimbCategories.ARM, null);
            }
        }
    }

    private static float armDismemberChance(LivingEntity target, float damageDealt) {
        var maxHealth = target.getMaxHealth();

        if (maxHealth <= 0F) {
            return ARM_BASE_CHANCE;
        }

        var healthRatio = Mth.clamp(target.getHealth() / maxHealth, 0F, 1F);
        var damageRatio = Mth.clamp(damageDealt / maxHealth, 0F, 1F);

        var chance = ARM_BASE_CHANCE
            + ARM_LOW_HEALTH_BONUS * (1F - healthRatio)
            + ARM_DAMAGE_BONUS * damageRatio;

        return Mth.clamp(chance, 0F, 1F);
    }

    private static List<LivingEntity> targetsInFront(Ravager ravager) {
        return RavagerAreaAttackUtil.getEntitiesInFront(
            ravager,
            Ravager.FRONT_AOE_RANGE_IN_BLOCKS,
            Ravager.FRONT_AOE_CONE_ANGLE_DEGREES
        );
    }

    private RavagerClawAttackActions() {
        throw new UnsupportedOperationException();
    }
}
