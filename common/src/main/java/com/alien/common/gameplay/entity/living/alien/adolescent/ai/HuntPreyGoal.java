package com.alien.common.gameplay.entity.living.alien.adolescent.ai;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * ⭐ HUNTING FOR FOOD, which is the only reason a child picks a fight on purpose.
 * <p>
 * Deliberately NOT {@code NearestAttackableTargetGoal}: that one acquires by class and hostility, and would have a
 * juvenile lock onto anything it is allowed to hate. The whole point of {@link JuvenilePrey} is that eligibility is
 * about SIZE and edibility, so the search is written around that predicate instead.
 * </p>
 * <p>
 * ⚠ IT YIELDS TO SELF-DEFENCE. If something is actively hurting the child, hunting stops: a cornered juvenile has a
 * more pressing target than lunch, and letting a hunt hold the target slot would keep it chewing a chicken while a
 * marine shot it in the back.
 * </p>
 */
public class HuntPreyGoal extends Goal {

    /** ⭐ How far a child will look for a meal. Kept short - it is grazing, not patrolling. */
    private static final double SEARCH_RANGE = 12.0D;

    /** How often it looks. A hunt is not urgent and this runs on every juvenile in the world. */
    private static final int SEARCH_INTERVAL_TICKS = 40;

    private final Xenomorph xenomorph;

    private final TargetingConditions conditions;

    private int nextSearchTick;

    private @Nullable LivingEntity prey;

    public HuntPreyGoal(Xenomorph xenomorph) {
        this.xenomorph = xenomorph;
        this.conditions = TargetingConditions.forCombat()
            .range(SEARCH_RANGE)
            .selector(JuvenilePrey::isPrey);
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // Being hurt outranks being hungry.
        if (CorneredTracker.threatOf(xenomorph) != null) {
            return false;
        }

        if (xenomorph.tickCount < nextSearchTick) {
            return false;
        }

        nextSearchTick = xenomorph.tickCount + SEARCH_INTERVAL_TICKS;
        prey = findPrey();

        return prey != null;
    }

    @Override
    public boolean canContinueToUse() {
        var target = xenomorph.getTarget();

        return target != null
            && target.isAlive()
            && JuvenilePrey.isPrey(target)
            && CorneredTracker.threatOf(xenomorph) == null;
    }

    @Override
    public void start() {
        xenomorph.setTarget(prey);
    }

    @Override
    public void stop() {
        prey = null;

        if (xenomorph.getTarget() != null && JuvenilePrey.isPrey(xenomorph.getTarget())) {
            xenomorph.setTarget(null);
        }
    }

    private @Nullable LivingEntity findPrey() {
        return xenomorph.level()
            .getNearestEntity(
                xenomorph.level()
                    .getEntitiesOfClass(
                        LivingEntity.class,
                        xenomorph.getBoundingBox().inflate(SEARCH_RANGE),
                        JuvenilePrey::isPrey
                    ),
                conditions,
                xenomorph,
                xenomorph.getX(),
                xenomorph.getEyeY(),
                xenomorph.getZ()
            );
    }
}
