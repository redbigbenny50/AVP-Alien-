package com.alien.fabric.data.molting_profile;

import com.alien.common.model.lifecycle.growth.MoltPhase;

import java.util.List;

public class MoltingProfileConstants {

    // Chestbursters grow from 100% to 125% scale in 3 phases.
    //
    // MOLTING GATES GROWTH. GrowthManager will not transform an alien until its molt is finished (+20 ticks), so a
    // stage really lasts max(growthTime, moltTime). At 3 x (40s + 20s) = 3600 ticks this molt was LONGER than the
    // 3000-tick chestburster stage, which would have made the growth time meaningless - every burster would have sat
    // waiting on its skin. 3 x (25s + 10s) = 2100 ticks fits inside 3000 with room to spare.
    //
    // It is still a FLOOR, not a guarantee: molt phases only advance while the alien is calm (see
    // MoltingManager.canStartMolting), so a burster in a hive under attack will overrun its growth timer. That is
    // intended - a xenomorph does not shed its skin mid-fight.
    public static final float CHESTBURSTER_START_SCALE = 1.0F;

    public static final float CHESTBURSTER_END_SCALE = 1.25F;

    public static final List<MoltPhase> CHESTBURSTER_PHASES = uniformPhases(3, 25, 10);

    // Drones start at 90% of full size and grow to 100% over 1 minute in 3 phases.
    public static final float DRONE_START_SCALE = 0.9F;

    public static final float DRONE_END_SCALE = 1.0F;

    public static final List<MoltPhase> DRONE_PHASES = uniformPhases(3, 10, 10);

    // Adolescents grow from 100% to 125% scale in 3 phases.
    //
    // Sized to the SHORTEST branch. There is only one adolescent entity type, but its growth stage differs by host:
    // 3000 ticks to a drone, 1500 to a runner or spitter. Since molting gates growth, this molt must finish inside
    // 1500 or the fast castes are not fast at all - they would just sit waiting on the same molt as everyone else.
    // 3 x (12s + 5s) = 1020 ticks.
    public static final float ADOLESCENT_START_SCALE = 1.0F;

    public static final float ADOLESCENT_END_SCALE = 1.25F;

    public static final List<MoltPhase> ADOLESCENT_PHASES = uniformPhases(3, 12, 5);

    // Royal and predalien keep the ORIGINAL 3 x (40s + 20s) = 3600-tick molt. Their growth stages are still 6000
    // ticks, so molting was never their gate and nothing about them needs to speed up. Split out of the constants
    // above purely so that retuning the xenomorph ladder cannot silently retune their visual pacing too.
    public static final List<MoltPhase> ROYAL_CHESTBURSTER_PHASES = uniformPhases(3, 40, 20);

    public static final List<MoltPhase> ROYAL_ADOLESCENT_PHASES = uniformPhases(3, 40, 20);

    public static final List<MoltPhase> PREDALIEN_CHESTBURSTER_PHASES = uniformPhases(3, 40, 20);

    public static final List<MoltPhase> PREDALIEN_ADOLESCENT_PHASES = uniformPhases(3, 40, 20);

    // Praetorians start at 75% of full size and grow to 100% over 10 minutes in 3 phases.
    public static final float PRAETORIAN_START_SCALE = 0.66F;

    public static final float PRAETORIAN_END_SCALE = 1.0F;

    public static final List<MoltPhase> PRAETORIAN_PHASES = uniformPhases(3, 140, 60);

    // Queens start at 85% of full size and grow to 100% over 15 minutes in 3 phases.
    public static final float QUEEN_START_SCALE = 0.85F;

    public static final float QUEEN_END_SCALE = 1.0F;

    public static final List<MoltPhase> QUEEN_PHASES = uniformPhases(3, 220, 80);

    private static List<MoltPhase> uniformPhases(int count, int idleSeconds, int moltSeconds) {
        var phase = new MoltPhase(idleSeconds * 20, moltSeconds * 20);
        var phases = new java.util.ArrayList<MoltPhase>(count);

        for (int i = 0; i < count; i++) {
            phases.add(phase);
        }

        return List.copyOf(phases);
    }
}