package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.util.RandomSource;

import java.util.UUID;

/**
 * ⭐⭐⭐ TWO EMPRESSES MEET. ONE TRIES TO SWALLOW THE OTHER, AND THE LOSER'S EMPIRE CHANGES HANDS.
 * <p>
 * [stated] "She should try to absorb it and if the other empress fails the roll then it gets lost. However if the other
 * empress succeeds the roll then it becomes an empress proxy war which if i remember she sends that hive reinforcements
 * and a war breaks out until one of the touching hives is dead."
 * </p>
 * <p>
 * ⚠⚠ THIS FIXES A PERMANENT STALEMATE, NOT JUST A MISSING FEATURE. {@code empressRestraintHolds} declines war between
 * two same-strain hives while either empire still has a free seat — and the network only ever adopts lineages that have
 * NO empress. So two empresses with room to grow could never fight AND could never absorb each other: they sat beside
 * one another forever, doing nothing. The contest is what makes that meeting mean something.
 * </p>
 * <p>
 * ⭐ THE ROLL WEIGHS NETWORK SIZE AND BANKED STRENGTH — [stated] he agreed it should, so "a big empire rarely swallows a
 * healthy rival but does mop up a dying one". Size alone would make the largest empire inevitable; strength alone would
 * ignore the empire she has actually built. Both together mean a small, well-fed empire can refuse a giant.
 * </p>
 */
public final class EmpressAbsorption {

    /**
     * Floor and ceiling on the defender's odds.
     * <p>
     * ⚠ NEITHER OUTCOME IS EVER CERTAIN. A guaranteed absorption makes meeting a bigger empire a cutscene; a guaranteed
     * refusal makes the whole contest decorative. Both ends stay possible so the player watching it cannot know which
     * way it went before it happens.
     * </p>
     */
    private static final double MIN_DEFEND_CHANCE = 0.10;

    private static final double MAX_DEFEND_CHANCE = 0.90;

    private EmpressAbsorption() {}

    /**
     * Attempts an absorption between two empire hives that have just met.
     *
     * @return true if the meeting is resolved (absorbed, or a proxy war opened) and no ordinary war should be declared
     */
    public static boolean tryResolveMeeting(long gameTime, HiveLocation aggressorSeat, HiveLocation defenderSeat) {
        var aggressorLineage = lineageOf(aggressorSeat);
        var defenderLineage = lineageOf(defenderSeat);
        if (aggressorLineage == null || defenderLineage == null) {
            return false;
        }

        var aggressorEmpress = aggressorLineage.empressId();
        var defenderEmpress = defenderLineage.empressId();
        if (aggressorEmpress == null || defenderEmpress == null || aggressorEmpress.equals(defenderEmpress)) {
            return false; // one crown or none - not a meeting of empires
        }

        // ⭐⭐ A BREAKAWAY IS UNTOUCHABLE WHILE HER GRACE HOLDS.
        //
        // ⚠⚠ WITHOUT THIS THE SCHISM IS POINTLESS. She founds ONE hive beside an empire of eight; the roll below
        // weighs network size and banked strength, so her defence floors at ten percent and she is swallowed within
        // minutes of leaving. The player who spawned her would watch her walk out and immediately rejoin.
        //
        // [stated] "even if its most likely the new comer will die due to size the grace period makes it more worth
        // it as a battle."
        if (defenderLineage.isInSchismGrace(gameTime)) {
            return false;
        }

        var random = aggressorSeat.centerPos() == null
            ? RandomSource.create()
            : RandomSource.create(aggressorSeat.centerPos().asLong());

        if (random.nextDouble() < defendChance(aggressorEmpress, defenderEmpress)) {
            // ⭐ SHE HELD. The empires do not merge; they go to war over the ground where they touched.
            Alien.LOGGER.info(
                "Empress absorption REFUSED - {} held against {}; proxy war opens at {} / {}",
                defenderEmpress,
                aggressorEmpress,
                defenderSeat.id(),
                aggressorSeat.id()
            );
            return false; // fall through to the ordinary war declaration, which the caller performs
        }

        absorb(aggressorEmpress, defenderLineage);
        Alien.LOGGER.info(
            "Empress absorption SUCCEEDED - {} swallowed the empire of {}",
            aggressorEmpress,
            defenderEmpress
        );
        return true;
    }

    /**
     * The defender's chance to refuse, from the two empires' relative size and banked strength.
     * <p>
     * ⚠ RELATIVE, NOT ABSOLUTE. What matters is how she compares to the empire in front of her — a three-hive empire is
     * formidable next to one hive and prey next to twelve. An absolute threshold would make the same empire always win
     * or always lose regardless of who it met.
     * </p>
     */
    private static double defendChance(UUID aggressor, UUID defender) {
        var aggressorScore = empireScore(aggressor);
        var defenderScore = empireScore(defender);
        var total = aggressorScore + defenderScore;
        if (total <= 0.0) {
            return 0.5; // two empty empires - a coin toss is as honest as anything
        }
        return Math.clamp(defenderScore / total, MIN_DEFEND_CHANCE, MAX_DEFEND_CHANCE);
    }

    /**
     * Size and strength of everything answering to one empress.
     * <p>
     * ⭐ HIVES COUNT FOR MORE THAN BIOMASS, deliberately. Territory is what an empire IS; biomass is what it happens to
     * be holding this minute, and a hive that just spent its stores on a raid is not suddenly a lesser empire. Biomass
     * is scaled down so it breaks ties between similarly-sized empires rather than deciding the contest.
     * </p>
     */
    private static double empireScore(UUID empressId) {
        var score = 0.0;
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (!location.isAlive()) {
                continue;
            }
            var lineage = lineageOf(location);
            if (lineage == null || !empressId.equals(lineage.empressId())) {
                continue;
            }
            score += 1.0;
            score += location.biomass() / 500.0;
        }
        return score;
    }

    /**
     * Hands the defender's lineage to the aggressor's crown.
     * <p>
     * ⭐⭐ VASSALAGE, NOT RE-PARENTING, AND THAT IS WHY IT IS SAFE. The absorbed queen keeps her hive and her lineage;
     * only the {@code empressId} changes. So one-queen-per-location is untouched, the 8-location cap is not crossed,
     * location numbers cannot collide, and the faction-membership eviction race is never entered because no member
     * changes faction. It is the same mechanism {@code EmpressNetworkSync} already uses to adopt empressless lineages.
     * </p>
     * <p>
     * ⚠ The pending seat is cleared too: a crowning that was queued under the old crown must not complete under the new
     * one.
     * </p>
     */
    private static void absorb(UUID aggressorEmpress, LineageFactionData defenderLineage) {
        defenderLineage.setEmpressId(aggressorEmpress);
        defenderLineage.setPendingEmpressSeatId(null);
        defenderLineage.markDirty();
    }

    private static @org.jetbrains.annotations.Nullable LineageFactionData lineageOf(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        return faction != null && faction.data() instanceof LineageFactionData lineage ? lineage : null;
    }
}
