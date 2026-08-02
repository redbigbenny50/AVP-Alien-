package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.faction.VariantFactionData;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The empress refilling a dying hive's firewall fund from a healthy sibling's.
 * <p>
 * This is the one thing that breaks the closed-loop extermination condition. Normally a hive is severable: crowning a
 * successor spends the firewall fund, refilling it takes 7 MC days of stability, and sustained pressure pauses that
 * accrual - so kill the queen, kill her successor, keep the pressure on, and the hive can never crown again and withers
 * on its own.
 * <p>
 * <b>It is REALLOCATION, not immunity.</b> The donor's fund is spent doing it and the donor must now accrue its own
 * seven days again. Every rescue disarms another hive, so a player who notices which hive went quiet knows exactly
 * where her network just went thin. That is the heart of the design and the reason a transfer is worth more to the
 * player than a simple denial would be.
 * <p>
 * Bounded twice over: {@code empressRescuesPerHive} (2) means any one hive gets two extra lives and then is written
 * off, and {@code empressRescueBudget} is a NETWORK-WIDE cap keyed on her empressId - without it, besieging one hive to
 * its third death would be the only viable tactic, because she could rescue everywhere forever.
 * <p>
 * THE SECOND RESCUE INTO A HIVE REVEALS HER. Over-extending to save a lost cause is what surfaces her position - the
 * mechanic that saves her is the mechanic that exposes her.
 */
public final class EmpressRescueService {

    private EmpressRescueService() {}

    /**
     * Try to refill {@code location}'s spent firewall fund from a sibling in her network.
     *
     * @return true if a transfer happened, in which case the caller should let the crowning proceed.
     */
    public static boolean tryRescue(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        HiveConfig config
    ) {
        if (com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.forbidsApexEconomy(level)) {
            return false; // END-STYLE: no rescue transfers - End banks are the player's hand-fed property
        }
        var empressId = lineage.empressId();
        if (empressId == null || location.isExiled()) {
            return false;
        }
        if (location.empressRescuesReceived() >= config.empressRescuesPerHive()) {
            // Third death. She writes it off; it dies like any other hive.
            return false;
        }

        var variantData = variantDataFor(lineage);
        if (
            variantData == null
                || variantData.empressRescuesSpent(empressId) >= config.empressRescueBudget()
        ) {
            return false;
        }

        var donor = findDonor(empressId, location);
        if (donor == null) {
            return false;
        }

        // The transfer itself: the donor surrenders its refilled fund and starts its own seven days over.
        donor.setFirewallFundAvailable(false);
        donor.setFirewallStableAccruedTicks(0L);
        location.setFirewallFundAvailable(true);
        location.setFirewallStableAccruedTicks(0L);

        location.setEmpressRescuesReceived(location.empressRescuesReceived() + 1);
        variantData.recordEmpressRescue(empressId);
        lineage.markDirty();

        Alien.LOGGER.info(
            "Hive: empress {} rescued {} from {} - rescue {}/{} for that hive, {}/{} network-wide",
            empressId,
            location.id(),
            donor.id(),
            location.empressRescuesReceived(),
            config.empressRescuesPerHive(),
            variantData.empressRescuesSpent(empressId),
            config.empressRescueBudget()
        );

        if (location.empressRescuesReceived() >= 2) {
            reveal(level, location, lineage, empressId);
        }

        return true;
    }

    /**
     * A sibling hive in her network with a refilled fund to give.
     * <p>
     * Never the hive being rescued, never a remnant, and never her own seat - stripping the seat's fund to save an
     * outlying hive would be the empress disarming herself, which is not a trade she would make.
     */
    private static @Nullable HiveLocation findDonor(UUID empressId, HiveLocation receiver) {
        HiveLocation best = null;

        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            if (!empressId.equals(lineage.empressId())) {
                continue;
            }

            for (var candidate : lineage.locationsById().values()) {
                if (
                    candidate == receiver
                        || !candidate.isAlive()
                        || candidate.isExiled()
                        || !candidate.firewallFundAvailable()
                        || empressId.equals(candidate.founderId())
                ) {
                    continue;
                }
                // Prefer the healthiest donor, so the network sheds its safety net where it hurts least.
                if (best == null || candidate.biomass() > best.biomass()) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    /**
     * The self-reveal. Every player the hive holds a grudge against is told where the help is coming from.
     * <p>
     * She does not relocate afterwards - [stated] "the player earned finding her now she has to boost her defense and
     * try to weather the storm." The flag is what her dig-in response reads.
     */
    private static void reveal(ServerLevel level, HiveLocation location, LineageFactionData lineage, UUID empressId) {
        if (lineage.empressRevealed()) {
            return;
        }
        lineage.setEmpressRevealed(true);

        var seat = seatOf(empressId);
        if (seat == null) {
            return;
        }
        var pos = seat.centerPos();

        var message = Component.literal(
            "You notice scouts feeding the hive from " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
        );

        for (var player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }

        Alien.LOGGER.info("Hive: empress {} REVEALED at {} by a second rescue of {}", empressId, pos, location.id());

        // She does not run - she calls everything in. The levy is the same trade as the rescue above: she cannot
        // create strength, only move it, so the network is left thinner for having defended her.
        EmpressLastStand.fortify(level, seat, empressId);
    }

    private static @Nullable HiveLocation seatOf(UUID empressId) {
        for (var factionId : Alien.MOD.factions().getAllIds()) {
            if (!LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction == null || !(faction.data() instanceof LineageFactionData lineage)) {
                continue;
            }
            for (var candidate : lineage.locationsById().values()) {
                if (empressId.equals(candidate.founderId())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static @Nullable VariantFactionData variantDataFor(LineageFactionData lineage) {
        for (var factionId : Alien.MOD.factions().getAllIds()) {
            var faction = Alien.MOD.factions().get(factionId);
            if (
                faction != null
                    && faction.data() instanceof VariantFactionData variantData
                    && variantData.variant() == lineage.variant()
            ) {
                return variantData;
            }
        }
        return null;
    }
}
