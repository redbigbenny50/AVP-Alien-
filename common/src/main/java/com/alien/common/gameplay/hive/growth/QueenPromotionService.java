package com.alien.common.gameplay.hive.growth;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.crusher.Crusher;
import com.alien.common.gameplay.entity.living.alien.xenomorph.praetorian.Praetorian;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.config.HiveConfig;
import com.alien.common.gameplay.hive.economy.CastePopulation;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.model.lifecycle.growth.CocooningConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * Raises one of the hive's heavies into a founding queen, in the world, where you can watch it happen and interrupt it.
 * <p>
 * A hive that has outgrown itself does not find a queen lying in its stores - it MAKES one. It picks a praetorian (a
 * crusher only if it has no praetorian to spare), pays royal jelly, and puts it into a royal cocoon. Thirty seconds
 * later a queen steps out and goes looking for somewhere to dig. Twice per hive, ever.
 * <p>
 * Almost none of this is new machinery. {@link com.alien.common.gameplay.entity.living.alien.xenomorph.CocoonManager}
 * already runs caste molts end to end - it spawns the matching ROYAL COCOON when the target is a queen, plays both molt
 * animations, performs the transition, and carries hive faction membership across the new UUID. Growth stages have been
 * driving it this whole time. All this adds is a second reason to start one.
 * <p>
 * IRRADIATED has no royal cocoon (that strain's queens do not molt), so the visible promotion does not apply to it and
 * an irradiated lineage falls through to the abstract spread instead.
 * <p>
 * This is the LOADED half of hive spread. When nobody is watching, {@link AbstractSpreadAttempt} does the same
 * accounting without the theatre. Both charge the same jelly, both burn the same lifetime allowance, and this one
 * stamps the shared spread cooldown so the two can never both fire for one daughter.
 */
public final class QueenPromotionService {

    private QueenPromotionService() {}

    /** Returns true if a promotion was started this tick. */
    public static boolean tryPromote(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        HiveConfig config,
        long currentTick
    ) {
        if (!location.isAlive() || location.isExiled()) {
            return false;
        }
        if (location.daughterHivesFounded() >= config.maxDaughterHivesPerLocation()) {
            return false;
        }
        if (lineage.activeLocationCount() >= config.maxLocationsPerLineage()) {
            return false;
        }
        if (lineage.empressId() != null && lineage.activeLocationCount() >= config.maxLocationsUnderEmpress()) {
            return false;
        }
        if (location.royalJelly() < config.queenPromotionJellyCost()) {
            return false;
        }
        if (CastePopulation.totalTrackedPopulation(location) < config.minimumPopulationForHiveSpread()) {
            return false;
        }
        // Shares AbstractSpreadAttempt's cooldown deliberately - one hive, one spread clock, whichever half runs it.
        var lastSpread = location.lastAbstractSpreadTick();
        if (lastSpread > 0L && currentTick < lastSpread + config.lineageSpreadCooldownTicks()) {
            return false;
        }

        var queenType = Queen.getType(lineage.variant());
        if (queenType == null) {
            return false;
        }

        var candidate = pickPromotable(level, location, lineage);
        if (candidate == null) {
            return false;
        }

        var moltTicks = (int) Math.max(1L, config.queenPromotionMoltTicks());
        candidate.getCocoonManager().prepare(queenType, null, new CocooningConfig(moltTicks, moltTicks));

        location.setRoyalJelly(location.royalJelly() - config.queenPromotionJellyCost());
        location.setDaughterHivesFounded(location.daughterHivesFounded() + 1);
        location.setLastAbstractSpreadTick(currentTick);

        Alien.LOGGER.info(
            "Hive: {} is raising a {} into a founding queen at {} - daughter {}/{}",
            location.id(),
            candidate.getType().getDescriptionId(),
            candidate.blockPosition(),
            location.daughterHivesFounded(),
            config.maxDaughterHivesPerLocation()
        );

        return true;
    }

    /**
     * A loaded, idle heavy belonging to this hive. PRAETORIAN FIRST - a crusher is only taken when there is no
     * praetorian to give, since the praetorian is the more replaceable of the two at the caste caps.
     * <p>
     * Anything already mid-molt is skipped: {@code prepare} would no-op on it anyway, and we would have charged the
     * jelly for a promotion that never started.
     */
    private static @Nullable Xenomorph pickPromotable(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage
    ) {
        var praetorian = firstIdleOfType(level, location, Praetorian.getType(lineage.variant()));
        return praetorian != null ? praetorian : firstIdleOfType(level, location, Crusher.getType(lineage.variant()));
    }

    private static @Nullable Xenomorph firstIdleOfType(
        ServerLevel level,
        HiveLocation location,
        @Nullable EntityType<?> type
    ) {
        if (type == null) {
            return null;
        }

        var members = location.loadedMembersByType().get(type);
        if (members == null) {
            return null;
        }

        for (var memberId : members) {
            if (!(level.getEntity(memberId) instanceof Xenomorph xenomorph) || !xenomorph.isAlive()) {
                continue;
            }
            if (xenomorph.getCocoonManager().shouldRunCocoonAction()) {
                continue;
            }
            return xenomorph;
        }

        return null;
    }
}
