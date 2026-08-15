package com.alien.common.gameplay.entity.living.alien.xenomorph;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface AttackExecutor {

    Supplier<AttackExecutor> DEFAULT_FACTORY = DefaultAttackExecutor::new;

    /**
     * Total ticks the attack should occupy on the entity (windup + active). Drives {@link Xenomorph#beginAttack(int)}.
     */
    int totalDurationInTicks(AttackType attack);

    /**
     * ⭐⭐ THE SAME FIGURE, BUT ALLOWED TO SEE THE ENTITY - this is what lets a caste swing FASTER while buffed.
     * <p>
     * ⚠ ADDITIVE AND DEFAULTED ON PURPOSE. Every existing executor keeps its old behaviour untouched by inheriting
     * this, so a bespoke one (the crusher charge, the razor claw sweep) cannot silently DESYNC: if it does not scale
     * its own internal clock, it does not get a scaled outer duration either. Only executors that override BOTH this
     * and their own {@code onStart} clock actually speed up.
     * </p>
     */
    default int totalDurationInTicks(Xenomorph entity, AttackType attack) {
        return totalDurationInTicks(attack);
    }

    /**
     * Called once when the attack starts. Implementations should capture any per-invocation state here.
     */
    void onStart(Xenomorph entity, AttackType attack, @Nullable LivingEntity target);

    /**
     * Called every tick while the attack is active. Returns {@code true} if the attack should continue.
     */
    boolean onTick(Xenomorph entity, AttackType attack);

    /**
     * Called when the attack finishes (naturally or aborted). Implementations should release any held references.
     */
    default void onComplete(Xenomorph entity, AttackType attack) {}
}
