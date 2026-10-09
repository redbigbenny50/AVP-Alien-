package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.config.BuildFreeMode;
import com.alien.common.gameplay.hive.faction.FactionVariantPolicy;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.war.AlienTerritoryWarSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * BUILD-FREE ONLY: a queen standing inside a hive of her own strain whose throne is EMPTY takes that throne.
 * <p>
 * ⚠⚠ THE BUG THIS ANSWERS. [stated, relayed tester report] "i keep getting 'this hive still lives... the brood returns
 * to its search.' every 5 seconds in my freebuild hive and the queen is only making black particles and no eggsack." In
 * build-free mode a queen founds WHERE SHE STANDS - there is no second candidate and no dig. When she is standing in a
 * claim that already exists and is not hers, SpreadZoneCheck Rule 1 refuses her, the halt that was meant to stop the
 * retry only silenced the LOG, and the settlement timer fired the refusal (and the founding dust, and the chat line)
 * every settlement cycle forever. She never became founder of anything, so the ovipositor gate
 * ({@code hasSuitableHiveLocation} requires the seat to be HERS) never let her grow a sack.
 * </p>
 * <p>
 * ⭐ IN THIS MODE THE ONLY COHERENT OUTCOME FOR A SAME-STRAIN QUEEN IN A QUEENLESS HIVE IS TO RULE IT. She cannot found
 * on top of it (Rule 1), cannot dig elsewhere (the mode does not dig), and cannot be a daughter (build-free forces
 * daughter slots to 0). Every other choice leaves her standing there forever.
 * </p>
 * <p>
 * ⚠⚠ NOT APPLIED OUTSIDE BUILD-FREE, DELIBERATELY. In ordinary play the OPPOSITE was ruled: a queen in a claim with a
 * vacant seat must NOT adopt it, she goes and founds a true hive ([stated] the metamorphosis-crusher report in
 * {@code OvipositorManager.hasSuitableHiveLocation}). Ordinary succession stays with {@link QueenSuccessionTask} and
 * {@link QueenlessMaturationTask}.
 * </p>
 * <p>
 * The seat counts as empty when {@code founderId} is null, OR when it names a royal that is neither loaded in this
 * level nor known as a member of ANY hive - a dangling pointer to a queen nothing remembers. A founder who is merely
 * UNLOADED is still a known member, so she is never usurped.
 * </p>
 * <p>
 * Same gates as {@link QueenSuccessionTask} where they apply: no succession while the hive is at war or has lost one,
 * and none while a rescue campaign is still out for its captured queen.
 * </p>
 */
public final class BuildFreeVacantThrone {

    private BuildFreeVacantThrone() {}

    /** True if she took the throne. False changes nothing, so the caller carries on with its own refusal. */
    public static boolean tryTake(Queen queen, @Nullable HiveLocation location) {
        if (!BuildFreeMode.isEnabled() || location == null) {
            return false;
        }
        if (!(queen.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!location.isAlive() || location.isInhibited() || location.isExiled()) {
            return false;
        }
        if (!location.dimension().equals(level.dimension())) {
            return false;
        }
        var variant = location.lineageVariantOrNull();
        if (variant == null || !FactionVariantPolicy.variantMatches(queen, variant)) {
            return false; // a rival strain's hive is never hers to rule
        }
        if (AlienTerritoryWarSystem.isExcludedFromQueenReplacement(location)) {
            return false;
        }
        if (location.rescueCampaign() != null) {
            return false; // the hive is still holding out for its captured queen
        }
        if (!isSeatVacant(level, location, queen.getUUID())) {
            return false;
        }

        var previousFounder = location.founderId();
        location.setFounderId(queen.getUUID());
        queen.getLifecyclePhaseManager().assumeVacantThrone(location);
        queen.getLifecyclePhaseManager().clearFoundingHalt();
        QueenSettlementDetector.forget(queen.getUUID());
        markOwningLineageDirty(location);

        Alien.LOGGER.info(
            "Hive (build-free): queen {} took the empty throne of location {} (previous founder {})",
            queen.getUUID(),
            location.id(),
            previousFounder == null ? "none" : previousFounder
        );

        var radiusSq = ANNOUNCE_RADIUS * ANNOUNCE_RADIUS;
        for (var player : level.getPlayers(p -> p.distanceToSqr(queen) <= radiusSq)) {
            player.displayClientMessage(
                Component.literal("The brood bows to a new queen").withStyle(ChatFormatting.DARK_PURPLE),
                true
            );
        }
        return true;
    }

    /**
     * Empty, or naming a royal nothing remembers. ⚠ "Not loaded" alone is NOT enough - an unloaded founder is still a
     * known member of her hive and must keep her seat.
     */
    private static boolean isSeatVacant(ServerLevel level, HiveLocation location, UUID claimant) {
        var founder = location.founderId();
        if (founder == null) {
            return true;
        }
        if (founder.equals(claimant)) {
            return false; // already hers; the caller never gets here in that case
        }
        if (level.getEntity(founder) != null) {
            return false;
        }
        for (var any : HiveLocationRegistry.INSTANCE.all()) {
            for (var members : any.knownMembersByType().values()) {
                if (members.contains(founder)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * ⚠ The settlement path runs off the QUEEN's tick, not the hive's loaded tick, so the seat change must mark its own
     * lineage dirty or BLib skips writing it and the next load hands the throne back to nobody.
     */
    private static void markOwningLineageDirty(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (faction != null && faction.data() instanceof LineageFactionData lineage) {
            lineage.markDirty();
        }
    }

    /** Same radius the refusal line uses: she has to have been visible for it to make sense. */
    private static final double ANNOUNCE_RADIUS = 48.0;
}
