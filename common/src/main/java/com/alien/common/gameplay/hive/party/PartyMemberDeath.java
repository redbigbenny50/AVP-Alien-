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
        untrack(alien);
    }

    /**
     * Removes {@code alien} from its party's member list, if it is in one. Oct 5 audit: also called when a party member
     * is virtualised on chunk unload, which banks it - left listed, the party's resolution banked it a second time.
     */
    public static void untrack(Alien alien) {
        var membership = alien.partyMembership();
        if (membership == null) {
            untrackUnmarked(alien);
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

    /**
     * ⚠ Oct 5 audit - TWO DISPATCHERS NEVER STAMP A MEMBERSHIP. The surface party and the internal host party track
     * their members by id but do not call setPartyMembership (the internal one on purpose: its drone works as an
     * interior sweeper, and a membership would switch it to open-field host hunting). With no membership the lookup
     * above had nothing to go on, so a member that DIED stayed listed - and when the party resolved, the missing entity
     * read as "unloaded" and was banked: dead party members came back. The parties are searched by id instead - its own
     * hive first, then every hive.
     */
    private static void untrackUnmarked(Alien alien) {
        var home = com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver.reserveReturnLocation(alien);

        if (home != null && untrackFrom(home, alien)) {
            return;
        }

        // Its home could not be resolved (its faction ties may already be gone by the time this runs) - every hive's
        // party list is short, and this only happens on a death or an unload.
        for (var location : HiveLocationRegistry.INSTANCE.all()) {
            if (location != home && untrackFrom(location, alien)) {
                return;
            }
        }
    }

    private static boolean untrackFrom(com.alien.common.gameplay.hive.location.HiveLocation location, Alien alien) {
        for (var party : location.parties()) {
            if (party.untrackMaterializedMember(alien.getUUID()) != null) {
                return true;
            }
        }

        return false;
    }
}
