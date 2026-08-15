package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;

/**
 * Records the death of a party member, so that party resolution can tell "dead" from "simply not loaded".
 * <p>
 * Resolution refunds each surviving member back into the hive's reserves by looking the member up by UUID. But
 * {@code ServerLevel.getEntity(uuid)} returns null for an entity in an UNLOADED CHUNK just as it does for a dead one -
 * and the resolver treated both as losses. Parties roam (a host hunt walks well beyond the claim), so any member that
 * happened to be outside loaded chunks when its party timed out was silently written off and never refunded. The hive
 * bled members on every single dispatch.
 * <p>
 * Fix: a member that actually dies is untracked HERE, at the moment of death. Anything still tracked at resolution is
 * therefore alive - loaded or not - and is refunded.
 */
public final class PartyMemberDeath {

    private PartyMemberDeath() {}

    /** Called when an alien dies: if it was out with a party, remove it from that party's roster. */
    public static void onDeath(Alien alien) {
        var membership = alien.partyMembership();
        if (membership == null) {
            return;
        }
        var location = HiveLocationRegistry.INSTANCE.get(membership.sourceLocationId());
        if (location == null) {
            return;
        }
        for (var party : location.parties()) {
            if (!party.id().equals(membership.partyId())) {
                continue;
            }
            party.untrackMaterializedMember(alien.getUUID());
            return;
        }
    }
}
