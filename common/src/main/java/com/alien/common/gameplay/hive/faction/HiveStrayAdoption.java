package com.alien.common.gameplay.hive.faction;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.faction.v1.FactionMember;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Takes in the hive's own strays, and kills the ones that belong to somebody else.
 * <p>
 * THE PROBLEM THIS SOLVES. Membership is only ever granted in {@code Alien.finalizeSpawn}, and only to an alien that
 * spawns inside a claimed chunk. Transitions do not go through finalizeSpawn - they carry membership across explicitly
 * via {@code FactionMembershipTransfer} - so a chestburster that erupts from a host OUT IN THE FIELD has no membership
 * to carry, and nothing it later matures into ever gets any. It walks home to the resin and lives in the hive forever
 * as a non-member.
 * </p>
 * <p>
 * That was invisible because the hive's systems do not agree on what "its xenomorphs" means. {@code EggHaulDispatch}
 * searches a WORLD BOX, so it finds strays and puts them to work hauling. {@code CarveWorkers} and
 * {@code HiveBreachRepair} iterate {@code loadedMembersByType()}, which is MEMBERSHIP, so to them the same xenomorphs
 * do not exist. A hive could therefore log "no free drones, nothing in reserve" while visibly dispatching haulers - and
 * leave a hole in its wall open indefinitely with a dozen idle bodies standing in it.
 * </p>
 * <p>
 * THE RULE, exactly as specified: a xenomorph standing in the claim with NO lineage at all is ADOPTED, free. One of a
 * DIFFERENT STRAIN is KILLED. One that belongs to ANOTHER LINEAGE OF THE SAME STRAIN is LEFT ALONE - a neighbour's
 * drone is the neighbour's, and overlapping claims are settled by the territory contest, not by quietly draining the
 * other hive's workforce.
 * </p>
 * <p>
 * Adoption costs nothing: host-born xenomorphs are meant to land in the hive's pool and be usable for whatever it
 * needs, so this only repairs the bookkeeping that already should have happened.
 * </p>
 */
public final class HiveStrayAdoption {

    /**
     * Coarse cadence - this is bookkeeping repair, not a behaviour. Runs on the same 200-tick beat as structure growth.
     */
    public static final long TICK_INTERVAL = 200L;

    /**
     * Vertical reach around the claim when sweeping for strays.
     * <p>
     * Claims are chunk columns, so the horizontal bounds come from the claimed chunks themselves; a hive is a vertical
     * thing and a stray may be several floors above or below the piece that owns the chunk.
     * </p>
     */
    private static final double VERTICAL_REACH = 48.0;

    private HiveStrayAdoption() {
        throw new UnsupportedOperationException();
    }

    public static void run(ServerLevel level, HiveLocation location) {
        if (!location.isAlive()) {
            return;
        }

        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return;
        }

        var factions = Alien.MOD.factions();
        var lineageFaction = factions.get(location.lineageFactionId());
        if (lineageFaction == null) {
            return;
        }

        var adopted = 0;
        var culled = 0;

        for (var candidate : sweep(level, location)) {
            // Already ours, or already somebody's: hasMember on OUR lineage answers the first, and a membership in any
            // other lineage is what makeup() looks for.
            if (lineageFaction.membership().hasMember(FactionMember.entity(candidate))) {
                continue;
            }

            if (belongsToAnotherLineage(candidate)) {
                // SAME STRAIN, ANOTHER HIVE -> leave it be. A rival STRAIN is culled below; a rival of our own strain
                // is a neighbour, and poaching its workers is the territory contest's job, not this pass's.
                if (FactionVariantPolicy.variantMatches(candidate, variant)) {
                    continue;
                }

                candidate.kill();
                culled++;
                continue;
            }

            if (!FactionVariantPolicy.variantMatches(candidate, variant)) {
                // Unaffiliated but the wrong strain: still a rival standing in the claim.
                candidate.kill();
                culled++;
                continue;
            }

            // join() is idempotent and re-checks the variant itself, so this cannot admit a stranger even if the
            // sweep above ever loosens.
            LocationMembership.join(location, candidate);
            adopted++;
        }

        if (adopted > 0 || culled > 0) {
            Alien.LOGGER.info(
                "Hive at {}: adopted {} unaffiliated xenomorph(s) and culled {} rival-strain intruder(s) from its claim.",
                location.centerPos(),
                adopted,
                culled
            );
        }
    }

    /**
     * Xenomorphs physically standing inside the claimed chunks.
     * <p>
     * ⚠ ONE query over the whole claim, then a per-entity chunk test - NOT one query per claimed chunk. A mature hive
     * can hold hundreds of claims (a live log showed 307 reconciled), and a query each would be tens of entity lookups
     * per second per hive for a pass that is only bookkeeping repair. The bounding box is wider than the claim, which
     * is exactly why each candidate is still checked against {@code claimedChunks} before anything happens to it.
     * </p>
     */
    private static List<Xenomorph> sweep(ServerLevel level, HiveLocation location) {
        var claimed = location.claimedChunks();

        if (claimed.isEmpty()) {
            return List.of();
        }

        var minChunkX = Integer.MAX_VALUE;
        var minChunkZ = Integer.MAX_VALUE;
        var maxChunkX = Integer.MIN_VALUE;
        var maxChunkZ = Integer.MIN_VALUE;

        for (var chunkPos : claimed) {
            minChunkX = Math.min(minChunkX, chunkPos.x);
            minChunkZ = Math.min(minChunkZ, chunkPos.z);
            maxChunkX = Math.max(maxChunkX, chunkPos.x);
            maxChunkZ = Math.max(maxChunkZ, chunkPos.z);
        }

        var box = new AABB(
            minChunkX * 16.0,
            location.hiveFloorY() - VERTICAL_REACH,
            minChunkZ * 16.0,
            maxChunkX * 16.0 + 16.0,
            location.hiveCeilingY() + VERTICAL_REACH,
            maxChunkZ * 16.0 + 16.0
        );

        var found = new ArrayList<Xenomorph>();

        for (var xenomorph : level.getEntitiesOfClass(Xenomorph.class, box, Entity::isAlive)) {
            if (claimed.contains(xenomorph.chunkPosition()) && xenomorph.getType().is(AlienEntityTypeTags.XENOMORPHS)) {
                found.add(xenomorph);
            }
        }

        return found;
    }

    /**
     * Whether this xenomorph is already a member of some OTHER lineage.
     * <p>
     * Asked of every lineage faction rather than read off the entity, because membership lives in BLib's faction tables
     * and an alien carries no back-reference to its hive.
     * </p>
     */
    private static boolean belongsToAnotherLineage(Xenomorph xenomorph) {
        var factions = Alien.MOD.factions();

        // Asked by UUID rather than by scanning every faction's membership - getFactionIds is the reverse index BLib
        // already keeps, so this is a lookup per stray instead of a walk over the whole faction table.
        for (var factionId : factions.getFactionIds(xenomorph.getUUID())) {
            var faction = factions.get(factionId);

            if (faction != null && faction.data() instanceof LineageFactionData) {
                return true;
            }
        }

        return false;
    }
}
