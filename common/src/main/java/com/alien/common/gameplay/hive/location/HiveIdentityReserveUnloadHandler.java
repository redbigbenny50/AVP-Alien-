package com.alien.common.gameplay.hive.location;

import com.alien.Alien;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.id.LineageIds;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.faction.v1.FactionMember;

import java.util.ArrayList;

public final class HiveIdentityReserveUnloadHandler {

    private HiveIdentityReserveUnloadHandler() {}

    public static boolean returnUnloaded(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        // Queens and empresses (the QUEENS tag) are unique identity entities, not fungible reserve members: a founder
        // queen carries the hive's lineage, sits on a riding ovipositor (a non-Alien Mob that would be orphaned if she
        // were discarded), and must survive chunk unload/reload as a real entity. Virtualizing her into the reserve
        // pool here discards her before serialization, so she vanishes on relog and the orphaned location then decays.
        // Never virtualize them — let them serialize normally.
        if (
            !alien.getType().is(AlienEntityTypeTags.XENOMORPHS)
                || alien.getType().is(AlienEntityTypeTags.QUEENS)
                || alien.isRemoved()
                || alien.convoyMembership() != null
                || isSomeonesSpecimen(alien)
        ) {
            return false;
        }

        var returnLocation = HiveMemberLocationResolver.reserveReturnLocation(alien);
        if (returnLocation == null || !returnLocation.isAlive()) {
            removeHiveOwnership(alien);
            return false;
        }

        var returned = alien.isPersistenceRequired()
            ? returnLocation.localReserves().addReturningIdentityMember(alien)
            : returnLocation.localReserves().addReturningMember(alien.getType(), 1);
        if (!returned) {
            return false;
        }

        removeHiveOwnership(alien);
        return true;
    }

    /**
     * Whether this xenomorph belongs to a PLAYER rather than to a hive, and so must never be virtualized.
     * <p>
     * Folding an alien into the reserve pool discards the entity - which is correct for a fungible drone wandering
     * home, and catastrophic for a specimen someone captured. Losing its hive is fine and happens all the time, notably
     * when a nuke levels the location it belonged to; QUIETLY CEASING TO EXIST inside a player's lab is not.
     * <p>
     * A NAME TAG is the clearest statement of ownership a player can make, and vanilla already treats it as "do not
     * despawn this". Honouring it here means a named xeno keeps existing as a real entity through chunk unloads and
     * through the death of whatever hive it once answered to.
     * <p>
     * Two further tells are planned and not built: proximity to a lot of player-placed blocks, and having stayed in one
     * small area for a long time - between them they describe the enclosures and zoos players actually make. A frozen
     * cryotube state will eventually be the unambiguous version of the same idea. Both need state nothing currently
     * tracks, so the name tag is the honest first pass rather than a guess dressed as a heuristic.
     */
    private static boolean isSomeonesSpecimen(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        return alien.hasCustomName();
    }

    private static void removeHiveOwnership(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        var member = FactionMember.entity(alien);
        for (var factionId : new ArrayList<>(Alien.MOD.factions().getFactionIds(alien.getUUID()))) {
            if (!HiveLocationIds.isHiveLocationId(factionId) && !LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = Alien.MOD.factions().get(factionId);
            if (faction != null) {
                faction.membership().removeMember(member);
            }
        }
    }
}
