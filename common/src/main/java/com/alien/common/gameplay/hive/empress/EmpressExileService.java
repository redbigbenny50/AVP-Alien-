package com.alien.common.gameplay.hive.empress;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.gameplay.hive.faction.LineageFactionData;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.tag.AlienEntityTypeTags;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

/**
 * Sends a fallen empress into exile instead of letting her hive take her with it.
 * <p>
 * When her seat fails, the lineage does not rescue her - it writes her off. Everything fungible evacuates or is lost,
 * and she is left holding a stripped ruin with whatever heavies refused the order to leave. She surrenders the crown on
 * the spot, so the surviving hives can elect a successor once the cooldown expires, and she surrenders her ovipositor
 * permanently, so the remnant can never rebuild. What is left is a last stand: no economy, no reinforcements, no way
 * back, and no reason for anyone to visit except to kill her.
 * <p>
 * The remnant location stays ALIVE - there has to be something to fight at - but {@link HiveLocation#isExiled()} takes
 * it out of every count the lineage makes, so it cannot be elected as the next seat, cannot satisfy the emergence
 * threshold, cannot be a convoy endpoint and cannot re-trigger the migration that created it.
 * <p>
 * Deliberately NOT wired to nuke destruction: a nuke kills every spawned hive member, and she dies with them. Exile is
 * for the hive that was lost slowly - evacuated after being ground down, or decayed because she failed to hold it.
 */
public final class EmpressExileService {

    private EmpressExileService() {}

    /** The castes that stay. Everything else leaves, is lost, or was already drained into the evacuation convoy. */
    public static boolean isRoyalGuard(EntityType<?> type) {
        return type.is(AlienEntityTypeTags.PRAETORIANS)
            || type.is(AlienEntityTypeTags.CRUSHERS)
            || type.is(AlienEntityTypeTags.PREDALIENS);
    }

    /** Whether this location is the seat of a living empress - i.e. whether losing it should exile her. */
    public static boolean isEmpressSeat(HiveLocation location, LineageFactionData lineage) {
        var empressId = lineage.empressId();
        return empressId != null && empressId.equals(location.founderId());
    }

    /**
     * Turn {@code location} into an exiled remnant. Returns false if this was not her seat, in which case the caller
     * should proceed with whatever it was going to do (normally: delete the location).
     *
     * @param keptGuardReserves whether the caller already left the guard castes in local reserves. Migration drains
     *                          reserves into the convoy and skips the guard itself; decay has drained nothing, so the
     *                          non-guard remainder is stripped here.
     */
    public static boolean exile(
        ServerLevel level,
        HiveLocation location,
        LineageFactionData lineage,
        boolean keptGuardReserves
    ) {
        if (location.isExiled() || !isEmpressSeat(location, lineage)) {
            return false;
        }

        var empressId = lineage.empressId();

        if (!keptGuardReserves) {
            stripToGuard(location);
        }
        location.setBiomass(0);
        location.setExiled(true);

        // The crown is surrendered immediately, not when she dies. The empire has already decided she is finished;
        // the cooldown is the only thing standing between it and her replacement.
        lineage.setEmpressId(null);
        lineage.setPendingEmpressSeatId(null);

        var server = level.getServer();
        if (server != null) {
            var cooldown = HiveLocationRegistry.INSTANCE.config().empressCrowningCooldownTicks();
            lineage.setEmpressCooldownUntilTick(server.overworld().getGameTime() + cooldown);
        }
        lineage.markDirty();

        // If she is loaded, take the ovipositor now. If not, EmpressOvipositorManager latches the exile off the
        // location flag the first time she ticks here, so the outcome is the same either way.
        if (empressId != null && level.getEntity(empressId) instanceof Empress empress) {
            empress.exile();
        }

        Alien.LOGGER.info(
            "Hive: empress EXILED at {} (lineage {}) - crown surrendered, remnant left with its royal guard",
            location.id(),
            lineage.factionId()
        );

        return true;
    }

    /** Drop every reserve that is not royal guard. The remnant keeps its heavies and nothing else. */
    private static void stripToGuard(HiveLocation location) {
        var reserves = location.localReserves();
        for (var type : java.util.List.copyOf(reserves.underlying().getAvailableEntityTypes())) {
            if (isRoyalGuard(type)) {
                continue;
            }
            var count = reserves.getCount(type);
            if (count > 0) {
                reserves.underlying().add(type, -count);
            }
        }
    }
}
