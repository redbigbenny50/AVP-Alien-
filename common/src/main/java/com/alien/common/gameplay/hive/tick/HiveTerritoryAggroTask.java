package com.alien.common.gameplay.hive.tick;

import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Retargets loaded hive xenomorphs when a threat is present in the location's claimed chunks. Covers both trespassing
 * players and hated non-player enemies (marines, predators — anything tagged {@code HATED_BY_XENOMORPHS} or
 * {@code XENOMORPH_THREAT_3_HIGH_DANGER}), so a hive defends its territory against any high-priority intruder, not just
 * players.
 */
public final class HiveTerritoryAggroTask {

    private static final long INTERVAL_TICKS = 20L;

    /**
     * A player's dwell only accrues while they've damaged a member within this window — keeps "fighting" distinct from
     * "passing through".
     */
    private static final long HOSTILE_RECENCY_TICKS = 100L; // 5s

    private HiveTerritoryAggroTask() {}

    public static boolean shouldFire(long currentTick) {
        return currentTick % INTERVAL_TICKS == 0L;
    }

    public static void run(ServerLevel level, HiveLocation location) {
        var everyone = intrudersInTerritory(level, location);

        // The visit log and the dwell timer see EVERY intruder, vermin included - those drive the hive's memory
        // of who has been nosing around, which is a different question from who gets pulled off work.
        recordVisitors(level, location, everyone);
        trackIntrusionDwell(level, location, everyone);

        if (everyone.isEmpty()) {
            return;
        }

        // ⭐ TWO CLASSES, TWO RESPONSES. Threats call the whole hive; vermin are housekeeping and only reach
        // members with nothing else on. See aggroMembers.
        var threats = new ArrayList<LivingEntity>();
        var vermin = new ArrayList<LivingEntity>();

        for (var intruder : everyone) {
            if (isHatedNonPlayer(intruder) || !(intruder instanceof Monster) || intruder instanceof Player) {
                threats.add(intruder);
            } else if (isVerminInsideTheHive(intruder, location)) {
                vermin.add(intruder);
            } else {
                threats.add(intruder); // rival-strain xenomorphs and anything else the scan admitted
            }
        }

        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.XENOMORPHS)) {
                continue;
            }

            aggroMembers(level, entry.getValue(), threats, vermin);
        }
    }

    /**
     * Notes everyone who turns up, hostile or not.
     * <p>
     * Separate from dwell tracking below, which deliberately ignores a player who has not attacked anything - that map
     * answers "who has been fighting us". This one answers "who has been here", and it is the only record that catches
     * someone who walked in quietly, did something drastic and left without throwing a punch. Only the FIRST sighting
     * is kept, so the ledger reads as an order of arrival rather than a last-seen list.
     */
    private static void recordVisitors(ServerLevel level, HiveLocation location, List<LivingEntity> intruders) {
        var currentTick = level.getGameTime();

        for (var intruder : intruders) {
            if (intruder instanceof net.minecraft.server.level.ServerPlayer player) {
                location.recordTerritoryVisit(player.getUUID(), currentTick);
            }
        }
    }

    /**
     * Accrues per-player in-claim-while-recently-hostile dwell time and, once it crosses the intrusion threshold,
     * begins a two-wave retribution {@link com.alien.common.gameplay.hive.party.AttackCampaign}. A player must be both
     * physically inside a claimed chunk AND have damaged a member recently (within {@link #HOSTILE_RECENCY_TICKS}) for
     * dwell to accrue — merely passing through peacefully doesn't count. A player who has intruded, had their campaign
     * cleared, then re-enters and re-engages starts a fresh campaign (the prior cleared state is overwritten).
     */
    private static void trackIntrusionDwell(ServerLevel level, HiveLocation location, List<LivingEntity> intruders) {
        var currentTick = level.getGameTime();
        var config = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.config();
        var dwellThreshold = config.attackIntrusionDwellTicks();

        for (var intruder : intruders) {
            if (!(intruder instanceof net.minecraft.server.level.ServerPlayer player)) {
                continue;
            }
            var campaign = location.attackCampaigns().get(player.getUUID());
            if (campaign == null) {
                // No hostile hit recorded yet (recordAttackByPlayer creates the entry on first hit) — a player merely
                // standing in-claim without having attacked accrues nothing.
                continue;
            }

            // Only accrue while the player has been hostile recently; a cleared campaign accrues toward a fresh one.
            var recentlyHostile = currentTick - campaign.lastHostileTick() <= HOSTILE_RECENCY_TICKS;
            if (!recentlyHostile) {
                continue;
            }

            if (campaign.campaignActive()) {
                // Active campaign already running — no re-accrual until it clears.
                continue;
            }

            if (campaign.cleared()) {
                // Re-intrusion after a prior campaign cleared: this hit already reset lastHostileTick; start dwell
                // over.
                campaign.resetDwell();
                // Un-clear by beginning fresh dwell accrual (campaign starts once threshold re-crossed below).
                // We leave `cleared` true until the threshold is actually re-crossed, so a single stray hit post-clear
                // doesn't immediately re-arm; beginCampaign() resets the flag.
            }

            campaign.addDwellTicks(INTERVAL_TICKS);

            if (campaign.dwellTicks() >= dwellThreshold) {
                campaign.beginCampaign(currentTick);
                com.alien.Alien.LOGGER.info(
                    "Hive: player {} intrusion threshold crossed at location {} — retribution campaign armed",
                    player.getUUID(),
                    location.id()
                );
            }
        }
    }

    /** Public: the vent-defense dispatcher reuses this exact detection. */
    public static List<LivingEntity> intrudersInTerritory(ServerLevel level, HiveLocation location) {
        var intruders = new ArrayList<LivingEntity>();

        // Players first (cheap — scan the level player list directly). CREATIVE AND SPECTATOR PLAYERS ARE NOT
        // INTRUDERS — [stated] "people in creative and spectator modes are triggering this ... exclude people in
        // creative and spectator triggering its timer for intrusions." This is the single choke point every
        // intrusion consumer reads (the visit ledger, dwell/campaign accrual, and the vent-defense dispatcher via
        // the public reuse below), so filtering here silences all of them at once: a builder flying through in
        // creative, or an observer in spectator, accrues nothing and triggers no wave.
        for (var player : level.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            if (location.claimedChunks().contains(new ChunkPos(player.blockPosition()))) {
                intruders.add(player);
            }
        }

        // Hated non-player enemies (marines, predators, etc.) AND rival-strain xenomorphs — scan each claimed
        // chunk's column for tagged threats. Strains are always hostile to one another, so a rival strain's
        // xenomorph inside the claim is an invasion the whole location responds to, not something only the
        // members who happen to see it will fight.
        var locationVariant = location.lineageVariantOrNull();
        var claimed = location.claimedChunks();

        if (claimed.isEmpty()) {
            return intruders;
        }

        // ⭐⭐ ONE QUERY OVER THE WHOLE CLAIM, THEN FILTER - NOT ONE QUERY PER CHUNK.
        //
        // ⚠⚠ THIS WAS THE MOD'S HEAVIEST RECURRING COST. It ran a FULL-COLUMN getEntitiesOfClass for EVERY
        // claimed chunk, every 20 ticks, per hive. A 23x23 empress hive claims 529 chunks, so that was 529
        // separate world-height entity queries per second from ONE hive - roughly 12,700 entity-section visits,
        // and about 50,000 across four loaded hives. Each call also re-walked overlapping section lists and paid
        // its own setup.
        //
        // One AABB spanning the claim visits each entity section ONCE. The chunk-membership test then keeps the
        // result identical: a claim is not always rectangular (contested borders, expansion bands), so the box
        // can cover chunks the hive does not own - and those entities are dropped exactly as before.
        var minX = Integer.MAX_VALUE;
        var minZ = Integer.MAX_VALUE;
        var maxX = Integer.MIN_VALUE;
        var maxZ = Integer.MIN_VALUE;

        for (var chunk : claimed) {
            minX = Math.min(minX, chunk.getMinBlockX());
            minZ = Math.min(minZ, chunk.getMinBlockZ());
            maxX = Math.max(maxX, chunk.getMaxBlockX() + 1);
            maxZ = Math.max(maxZ, chunk.getMaxBlockZ() + 1);
        }

        var box = new AABB(minX, level.getMinBuildHeight(), minZ, maxX, level.getMaxBuildHeight(), maxZ);

        for (
            var entity : level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                entity -> isHatedNonPlayer(entity)
                    || isRivalStrainXenomorph(entity, locationVariant)
                    || isVerminInsideTheHive(entity, location)
            )
        ) {
            // ⚠ THE MEMBERSHIP TEST IS WHAT MAKES THE SINGLE BOX SAFE - without it a hive would answer threats
            // standing on ground it has not claimed.
            if (claimed.contains(new ChunkPos(entity.blockPosition()))) {
                intruders.add(entity);
            }
        }

        return intruders;
    }

    /** A living xenomorph of a DIFFERENT strain than this location's lineage. */
    private static boolean isRivalStrainXenomorph(
        LivingEntity entity,
        @Nullable com.alien.common.model.alien.variant.AlienVariant locationVariant
    ) {
        if (locationVariant == null || !entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        return entity.getType().is(AlienEntityTypeTags.XENOMORPHS)
            && entity instanceof com.alien.common.gameplay.entity.living.alien.Alien alien
            && !java.util.Objects.equals(alien.getVariant(), locationVariant);
    }

    /**
     * ⭐⭐ ORDINARY MONSTERS LOOSE INSIDE THE HIVE ARE INTRUDERS TOO.
     * <p>
     * [stated] "xenos docile toward zombies they store in harvest rooms." A harvest chamber is a captured VANILLA
     * spawner - it produces plain zombies, right inside the hive - and this scan only ever admitted HATED factions and
     * THREAT_3 high-danger mobs. A zombie is THREAT_2, so nothing in the hive was ever told about it and the xenomorphs
     * stood beside their own livestock doing nothing.
     * </p>
     * <p>
     * ⚠⚠ THE PREDICATE LAYER ALREADY ALLOWED THIS - the gap was that nobody ASKED. {@code AlienPredicates}'s vermin
     * rule makes any non-avp monster inside a hive slab a legal target regardless of threat tier or biomass level. But
     * a legal target is not an assigned one: THREAT_2 prey is only pursued when the hive is low on biomass or a hunting
     * party is out, and neither applies to a well-fed hive standing in its own harvest room. This task is what actually
     * hands members a target, so the rule had to be mirrored HERE to have any effect.
     * </p>
     * <p>
     * ⚠ SAME THREE CONDITIONS AS THE PREDICATE, deliberately, so the two can never disagree: a Monster, not one of
     * ours, and inside the location's vertical slab. The slab test is what keeps a zombie wandering the surface far
     * above a deep hive from dragging the whole colony up through the ceiling.
     * </p>
     */
    private static boolean isVerminInsideTheHive(LivingEntity entity, HiveLocation location) {
        if (!entity.isAlive() || entity.isRemoved() || !(entity instanceof Monster)) {
            return false;
        }

        // Ours are never vermin - rival strains are handled by isRivalStrainXenomorph, which knows about lineages.
        if (com.alien.Alien.MOD_ID.equals(EntityType.getKey(entity.getType()).getNamespace())) {
            return false;
        }

        return location.withinSlab(entity.blockPosition().getY());
    }

    private static boolean isHatedNonPlayer(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        return entity.getType().is(AlienEntityTypeTags.HATED_BY_XENOMORPHS)
            || entity.getType().is(AlienEntityTypeTags.XENOMORPH_THREAT_3_HIGH_DANGER);
    }

    /**
     * ⭐⭐ VERMIN DO NOT PULL WORKERS OFF THEIR JOBS. [stated] "anyone doing a task shouldnt try to intercept a vermin
     * until the task is done have the defense party deal with that."
     * <p>
     * ⚠ THIS SPLIT IS THE WHOLE POINT OF THE METHOD NOW. A marine raid or a rival strain is an EMERGENCY and still
     * calls every adult in the hive. A zombie loose in a harvest room is HOUSEKEEPING and only reaches members who are
     * not mid-task - the hive's standing defence, in other words, since a xenomorph with nothing assigned IS the
     * defence.
     * </p>
     * <p>
     * ⚠ Without the split, adding vermin to the intruder list (which is what made the harvest chamber respond at all)
     * would have had every egg hauler, host carrier and resin builder in the hive drop what it was holding every time a
     * spider wandered in. The fix for one bug would have created a worse one.
     * </p>
     */
    private static void aggroMembers(
        ServerLevel level,
        Set<UUID> memberIds,
        List<LivingEntity> intruders,
        List<LivingEntity> vermin
    ) {
        for (var memberId : memberIds) {
            var entity = level.getEntity(memberId);
            if (!(entity instanceof Xenomorph xenomorph) || !xenomorph.isAlive() || xenomorph.isRemoved()) {
                continue;
            }

            // ⭐ CHILDREN DO NOT ANSWER THE CALL. setHiveIntruderTarget writes its OWN field and drives its OWN
            // navigator, so Adolescent.setTarget refusing the target is not enough to stop it here - without this
            // they would path to armed intruders and stand there with nothing to fight with.
            if (AlienPredicates.isJuvenile(xenomorph)) {
                continue;
            }

            var target = nearestTarget(xenomorph, intruders);

            // Nothing worth dropping a job for - fall back to vermin, but only for members who have no job.
            if (target == null && !vermin.isEmpty() && !isOccupiedWithATask(xenomorph)) {
                target = nearestTarget(xenomorph, vermin);
            }

            if (target != null) {
                xenomorph.setHiveIntruderTarget(target);
            }
        }
    }

    /**
     * Is this xenomorph mid-job? Vermin are refused to anyone who is.
     * <p>
     * ⚠ CARGO FIRST, because it is the case that actually bit: a drone carrying a captured host or an egg hauler
     * mid-delivery is the worst possible thing to send after a zombie - it abandons the cargo in the open, which is the
     * same self-sabotage the captured-host target rule already exists to prevent.
     * </p>
     * <p>
     * ⚠ PARTY MEMBERSHIP COUNTS AS A JOB. A hunting or attack party is out on the hive's business with its own target
     * discipline; a zombie at home is not its problem.
     * </p>
     * <p>
     * ⚠ A xenomorph that already HAS a target is busy by definition - re-pointing it at nearer vermin mid-fight would
     * let a zombie peel a defender off the marine currently killing it.
     * </p>
     */
    private static boolean isOccupiedWithATask(Xenomorph xenomorph) {
        if (!xenomorph.getPassengers().isEmpty()) {
            return true; // hauling a host, an egg, or a rider of any kind
        }

        if (xenomorph.isPassenger()) {
            return true; // being hauled - it is cargo itself
        }

        if (xenomorph.partyMembership() != null) {
            return true;
        }

        return xenomorph.getTarget() != null;
    }

    private static @Nullable LivingEntity nearestTarget(Xenomorph xenomorph, List<LivingEntity> intruders) {
        LivingEntity nearest = null;
        var nearestDistanceSqr = Double.MAX_VALUE;

        for (var intruder : intruders) {
            if (!AlienPredicates.canAcquireTarget(xenomorph, intruder)) {
                continue;
            }

            var distanceSqr = xenomorph.distanceToSqr(intruder);
            if (distanceSqr < nearestDistanceSqr) {
                nearest = intruder;
                nearestDistanceSqr = distanceSqr;
            }
        }

        return nearest;
    }
}
