package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Sends a resolved party's members home: the ONE implementation behind the surface, biomass-hunting, host-hunt and
 * attack party resolutions.
 * <p>
 * Oct 5 audit - those four resolutions were the same loop copied four times, and the copies had drifted and shared one
 * bug between them:
 * </p>
 * <ul>
 * <li>🚨 <b>A member on egg or host duty was banked AND kept.</b> Each loop added the member to the reserve bank, then
 * skipped discarding it because it was carrying an egg or a host - so the hive gained a phantom member every time a
 * party ended mid-haul, while the real one walked on. Duty members now stay in the world as ordinary members and are
 * NOT banked.</li>
 * <li>⚠ The surface copy never cleared a kept member's party membership. Nothing persists that membership and its party
 * no longer exists, but CarveWorkers, HiveBreachRepair, VentDefenseTask, BroodBankTask and StrandedMemberRecovery all
 * skip any xenomorph that still has one - so that drone was quietly excluded from every hive job until it
 * unloaded.</li>
 * <li>The three copies that teleported members home teleported EVERY member and then discarded all but the duty ones -
 * the move only ever mattered for the members that stayed. Only duty members are moved now, and only when the caller
 * passes a vent (the surface resolution never did).</li>
 * </ul>
 */
public final class PartyReturn {

    private PartyReturn() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns every member and the unspawned remainder of {@code party} to {@code location}.
     *
     * @param dutyHomeVent where a member still carrying an egg or a host is moved to, or null to leave it where it is
     * @return the block position of the last live member seen, or null if none was loaded
     */
    public static @Nullable BlockPos returnHome(
        ServerLevel level,
        HiveLocation location,
        HiveParty party,
        @Nullable BlockPos dutyHomeVent
    ) {
        BlockPos lastKnownPos = null;

        for (var entry : new ArrayList<>(party.materializedMembers().entrySet())) {
            var entity = level.getEntity(entry.getKey());
            party.untrackMaterializedMember(entry.getKey());

            // ⚠ Not loaded. A member that unloads while its party is out is now virtualised by the unload handler AND
            // untracked from the party there (see HiveIdentityReserveUnloadHandler), so an id still listed here belongs
            // to a member that was never processed on the way out - a server stop, chiefly. Banked as before.
            if (entity == null) {
                location.localReserves().addReturningMember(entry.getValue(), 1);
                continue;
            }

            if (!entity.isAlive()) {
                continue;
            }

            lastKnownPos = entity.blockPosition();

            if (entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien) {
                alien.clearPartyMembership();
            }

            if (isOnDuty(entity)) {
                if (dutyHomeVent != null) {
                    entity.teleportTo(dutyHomeVent.getX() + 0.5, dutyHomeVent.getY(), dutyHomeVent.getZ() + 0.5);
                }

                continue;
            }

            location.localReserves().addReturningMember(entry.getValue(), 1);
            entity.discard();
        }

        for (var type : new ArrayList<>(party.composition().getAvailableEntityTypes())) {
            var count = party.composition().getCount(type);

            if (count <= 0) {
                continue;
            }

            location.localReserves().addReturningMember(type, count);
            party.composition().add(type, -count);
        }

        return lastKnownPos;
    }

    /**
     * Carrying an egg or a captured host. EggDutyGuard already counts any passenger in the egg or host tags; the
     * HostCaptureTask check is belt and braces for a captive the capture rules took in, since discarding its carrier
     * would leave it on the ground still stripped of its freedom.
     */
    private static boolean isOnDuty(net.minecraft.world.entity.Entity entity) {
        return EggDutyGuard.isOnEggDuty(entity)
            || entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien
                && HostCaptureTask.isCarryingHost(alien);
    }
}
