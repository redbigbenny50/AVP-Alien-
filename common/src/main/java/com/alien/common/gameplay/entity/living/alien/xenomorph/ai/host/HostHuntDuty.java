package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.host;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.party.HiveParty;
import net.minecraft.server.level.ServerLevel;

/**
 * "Am I a drone that was sent out to fetch a host?"
 * <p>
 * Host hunting is strictly a party job. A xenomorph that is not a member of an active {@link HiveParty.HostHunt} never
 * targets a host for capture, so the hive's ordinary workers do not wander off dragging cows home.
 * <p>
 * A xenomorph that is ALREADY carrying a host always counts as on duty, even if its party has since resolved - it must
 * be allowed to finish the delivery rather than stand there holding a villager forever.
 */
public final class HostHuntDuty {

    private HostHuntDuty() {}

    public static boolean isOnHostHunt(Xenomorph xenomorph) {
        if (!(xenomorph.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        var membership = xenomorph.partyMembership();
        if (membership == null) {
            return false;
        }
        var location = HiveLocationRegistry.INSTANCE.get(membership.sourceLocationId());
        if (location == null) {
            return false;
        }
        for (var party : location.parties()) {
            if (party instanceof HiveParty.HostHunt hostHunt && hostHunt.id().equals(membership.partyId())) {
                return true;
            }
        }
        return false;
    }
}
