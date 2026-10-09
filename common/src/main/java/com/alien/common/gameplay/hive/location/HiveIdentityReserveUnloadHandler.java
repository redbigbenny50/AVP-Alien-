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
        // \u2b50\u2b50\u2b50 A QUEEN WALKING TO FOUND HANDS OVER TO THE ABSTRACT SPREAD SYSTEM WHEN NOBODY IS WATCHING.
        //
        // [stated] "most players will spawn her wait for like 5 mins then leave", and [stated] "it should spread even
        // when those chunks cant load its the whole point of that system. growth and movement without player
        // oversight needed."
        //
        // \u26a0\u26a0 SHE WAS A GAP IN THAT SYSTEM. The queen exclusion below is right for a SEATED queen - she
        // carries
        // the lineage and rides an ovipositor that would be orphaned - but a queen in the LOCATION phase is neither
        // seated nor carrying anything: she is walking to ground she has not dug yet. Serializing her froze her in a
        // field the moment the player left, and AbstractSpreadAttempt never knew she existed, so nothing progressed.
        //
        // \u26a0 Handing her to her mother hive's spread attempt is the honest equivalent - the hive founds
        // abstractly, and the player returns to a new hive, which is what they would have got by standing there.
        if (tryHandOffTravellingQueen(alien)) {
            return true;
        }

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

        // \u2b50\u2b50 LAST GATE BEFORE WE TAKE IT: is this somebody's caged specimen?
        //
        // \u26a0 Placed here, after every cheap check has already passed, because it is the most expensive test in
        // the chain - a chunk-store lookup per surrounding chunk - and the rarest to matter.
        if (
            alien.level() instanceof net.minecraft.server.level.ServerLevel containmentLevel
                && com.alien.common.gameplay.hive.containment.ContainmentDetector.isContained(containmentLevel, alien)
        ) {
            return false;
        }

        var returnLocation = HiveMemberLocationResolver.reserveReturnLocation(alien);
        if (returnLocation == null || !returnLocation.isAlive()) {
            removeHiveOwnership(alien);
            return false;
        }

        // 🚨🚨 A HOST-BORN XENOMORPH CARRIES GENES NOTHING ELSE CAN REPRODUCE, so it must bank as an IDENTITY entry.
        //
        // ⚠⚠ [stated] "Hosted xenos that get banked have the queens genes from the egg and the hosts genes added to
        // them. So they would keep a unique gene set." An abstract entry is a TYPE AND A COUNT - there is nowhere for
        // genes to live - so banking one as fungible stock silently destroyed that set, and it can never be recovered
        // because the host is long gone.
        //
        // ⭐ EVERYTHING ELSE STAYS FUNGIBLE ON PURPOSE. A reserve-created xenomorph takes the seated royal's genes when
        // it materialises, exactly as an egg does, so it has nothing unique to lose and does not deserve the storage
        // cost of a full NBT entry. isHostBorn survives molts - see BroodBankTask - so a host-born praetorian promoted
        // later still counts.
        var carriesUniqueGenes = alien.isPersistenceRequired() || alien.isHostBorn();

        var returned = carriesUniqueGenes
            ? returnLocation.localReserves().addReturningIdentityMember(alien)
            : returnLocation.localReserves().addReturningMember(alien.getType(), 1);
        // A MARKED returner (the crawl-retreat rule, or a disbanded carve worker) refused by a full reserve joins
        // the brood bank instead - [stated] "if the reserves are full it will join the host born bank as a bonus."
        // The brood bank is uncapped, so a retreating cripple that made it out is never turned away at the door.
        if (!returned && alien.isMarkedForReserveReturn()) {
            returned = returnLocation.localReserves().addBrood(alien.getType(), 1);
        }
        if (!returned) {
            return false;
        }

        // \u2b50\u2b50\u2b50 MARK THE OWNING LINEAGE DIRTY. THIS IS THE LINE WHOSE ABSENCE DESTROYED WORKERS.
        //
        // \u26a0\u26a0 BLib ONLY WRITES FACTION DATA THAT IS MARKED DIRTY (BLibFactionManager.save: "if
        // (internalData.isDirty()) shardManager.markDirty(factionId)"), and a hive location - reserves included -
        // lives inside LineageFactionData. HiveLocationLoadedTickTask marks the lineage dirty once per LOADED tick
        // and its comment explains why per-mutation marking was abandoned... but that safety net only runs while the
        // location is TICKING. This path runs at CHUNK UNLOAD, after ticking has stopped.
        //
        // So the sequence was: bank the worker into the in-memory reserves, DISCARD THE ENTITY (see the mixin - it
        // discards only when we return true), and then BLib writes nothing because the faction looks clean. Entity
        // destroyed, deposit never persisted, "banked=0" on reload. Every worker loaded at quit time was lost, which
        // is the reported "reloging IS killing or deleting workers" and the 9-to-1 population collapse.
        //
        // \u2b50 THE CONVOY SIBLING IN THE SAME MIXIN ALREADY DOES THIS - ConvoyMemberTracker.returnToReserves ends
        // with markDirty(membership). Only the hive path was missing it. That asymmetry is what made this look like
        // an entity bug rather than a persistence one.
        markOwningLineageDirty(returnLocation);

        removeHiveOwnership(alien);

        // ⚠⚠ Oct 5 audit - A PARTY MEMBER WAS BANKED TWICE. Party members are not convoy members, so they come through
        // here when their chunk unloads and are banked above - but their party still listed them, and when the party
        // resolved it found no loaded entity and banked each one AGAIN. Surface and hunting parties roam away from the
        // player, so this fired constantly and inflated every hive's reserve. Taking it off the party's list here
        // makes the bank above the only one.
        com.alien.common.gameplay.hive.party.PartyMemberDeath.untrack(alien);
        alien.clearPartyMembership();
        return true;
    }

    /**
     * Flags the lineage that owns this location so BLib actually serializes the deposit we just made.
     * <p>
     * Mirrors {@code ConvoyMemberTracker.markDirty} exactly - resolve the faction, check it really is a
     * {@code LineageFactionData}, mark it. A location always knows its lineage faction id, so no lookup can fail
     * silently in a way that loses data without also being visibly broken elsewhere.
     * </p>
     */
    private static void markOwningLineageDirty(HiveLocation location) {
        var faction = Alien.MOD.factions().get(location.lineageFactionId());
        if (
            faction != null
                && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData lineage
        ) {
            lineage.markDirty();
        }
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
    /**
     * Converts an unloading, still-travelling queen into an abstract spread attempt by her mother hive.
     *
     * @return true if she was handed over and should be discarded
     */
    private static boolean tryHandOffTravellingQueen(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        if (!(alien instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen queen)) {
            return false;
        }
        if (
            queen.getLifecyclePhaseManager()
                .getPhase() != com.alien.common.gameplay.entity.living.alien.xenomorph.queen.QueenLifecyclePhase.LOCATION
        ) {
            return false; // seated, developing or hibernating - none of those is a journey
        }
        // \u26a0 BELT AND BRACES ON THE ORPHANED-OVIPOSITOR WARNING BELOW. A LOCATION-phase queen has not been given a
        // sack yet, so this should never fire - but discarding a queen who somehow has one would strand a non-Alien
        // Mob riding her, and that is a bug nobody would trace back to here.
        if (queen.hasOvipositor()) {
            return false;
        }
        if (!(queen.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return false;
        }

        // ⚠ Her lineage is found the same way every other abstract path finds it - by walking her faction ids and
        // picking the one that IS a lineage. There is no direct accessor, and inventing one here would be a second
        // answer to a question the resolver already answers.
        com.alien.common.gameplay.hive.faction.LineageFactionData lineage = null;
        net.minecraft.resources.ResourceLocation lineageId = null;
        for (var factionId : com.alien.Alien.MOD.factions().getFactionIds(queen.getUUID())) {
            if (!com.alien.common.gameplay.hive.id.LineageIds.isLineageId(factionId)) {
                continue;
            }
            var faction = com.alien.Alien.MOD.factions().get(factionId);
            if (
                faction != null
                    && faction.data() instanceof com.alien.common.gameplay.hive.faction.LineageFactionData found
            ) {
                lineage = found;
                lineageId = factionId;
                break;
            }
        }
        if (lineage == null) {
            return false;
        }

        // Her mother hive is whichever live location of the lineage is nearest - she set out from one of them.
        com.alien.common.gameplay.hive.location.HiveLocation source = null;
        var bestDistance = Double.MAX_VALUE;
        for (var candidate : lineage.locationsById().values()) {
            if (!candidate.isAlive()) {
                continue;
            }
            var distance = candidate.centerPos().distSqr(queen.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                source = candidate;
            }
        }
        if (source == null) {
            return false; // no hive to spread FROM - let her serialize as before
        }

        com.alien.common.gameplay.hive.growth.AbstractSpreadAttempt.tryRun(
            level.getServer(),
            lineageId,
            lineage,
            source,
            level.getGameTime()
        );
        lineage.markDirty();

        com.alien.Alien.LOGGER.info(
            "Hive: travelling queen {} unloaded - handed to abstract spread from {}",
            queen.getUUID(),
            source.id()
        );
        return true;
    }

    private static boolean isSomeonesSpecimen(com.alien.common.gameplay.entity.living.alien.Alien alien) {
        // \u2b50\u2b50\u2b50 GROWTH SUPPRESSION IS A CLAIM OF OWNERSHIP, EXACTLY LIKE A NAME TAG.
        //
        // \u26a0\u26a0 [stated] "he made a zoo and growth supressed them. this is why i was so pushy about detecting
        // if a xeno is in containment before pulling back to reserves." A suppressed xeno is one a PLAYER
        // deliberately froze and put somewhere on purpose - a specimen in a containment cell, not a hive member out
        // on business. Absorbing it deletes the entity and hands its type to a hive as a number, and the player has
        // no way to know where their exhibit went.
        //
        // \u26a0 THE STRAY-ADOPTION FALLBACK MADE THIS SHARPER, AND I ADDED IT: a lineage-less xeno unloading within
        // 256 blocks of a same-strain hive is now taken into that hive's reserves. Before, a zoo xeno with no hive
        // simply serialized with its chunk and came back. A zoo built near a hive would have quietly drained.
        //
        // \u2b50 isPoisoned is the right marker because NOTHING ELSE SETS IT. It is permanent until cured with
        // metamorphosis, it can only be applied deliberately, and every other system already reads it as "this one
        // does not change" - GrowthManager.canNeverGrow and the isolated-royal ascension both gate on it.
        return alien.hasCustomName() || alien.isPoisoned();
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
