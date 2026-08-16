package com.alien.common.gameplay.entity.living.alien.ai;

import com.alien.common.gameplay.entity.living.alien.Alien;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * ⭐⭐ STAND STILL AND CHANGE. The goal that makes juveniles grow up again.
 * <p>
 * {@code MoltingManager.canStartMolting()} holds a molt phase at its boundary until the alien is calm: not aggroed, not
 * moving, no active path, hive not tracking players. For an ADULT that is nearly always true within seconds. For a
 * JUVENILE it was very nearly never true - the Aug 11 work gave adolescents their own hunting and wandering AI, and
 * reparenting them onto {@code Xenomorph} switched on the {@code hasActiveBLibPath()} clause that had returned false
 * for them unconditionally beforehand. They never stood still, never advanced a phase, never fully matured, and
 * {@code GrowthManager} refuses to grow anything that has not fully matured. Hence [stated] "theres a bunch of adols
 * running around that seem to just not grow at all".
 * </p>
 * <p>
 * ⚠ THIS GOAL DOES NOT PERFORM THE MOLT - it only creates the conditions the molt was already waiting for. It stops the
 * navigation, and {@code MoltingManager.tick()} does the rest on its own. That keeps the calm-molt design intact rather
 * than carving an exception through it, and it gives the authored molt clips somewhere to actually play instead of
 * firing mid-sprint.
 * </p>
 * <p>
 * ⚠ IT DELIBERATELY DOES NOT OUTRANK COMBAT OR FLEEING. Registered BELOW the melee and avoid goals, so a juvenile being
 * chased still runs; it settles when nothing more urgent is happening. The bounded valve in {@code MoltingManager}
 * (MOLT_FORCE_TICKS) is what covers the case where calm never comes at all.
 * </p>
 */
public class SettleToMoltGoal extends Goal {

    private final Alien alien;

    public SettleToMoltGoal(Alien alien) {
        this.alien = alien;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return alien.getMoltingManager().isWaitingToMolt();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        alien.getNavigation().stop();
    }

    @Override
    public void tick() {
        // Held every tick, not just on start: a re-plan elsewhere can hand the navigation a fresh path, and
        // canStartMolting() reads the path, not the intent.
        alien.getNavigation().stop();
        alien.setDeltaMovement(alien.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
    }

    @Override
    public void stop() {
        alien.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
