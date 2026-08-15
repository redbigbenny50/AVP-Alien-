package com.alien.common.gameplay.hive.economy;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.EggCarrier;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;

/**
 * Tells the hive about its own egg backlog.
 * <p>
 * THE BUG THIS EXISTS FOR. An ovomorph asks for a hauler by broadcasting a game event every 20 ticks, and
 * {@code EggPickupRequestListener.getListenerRadius()} is <b>16</b>. The listener lives on the CARRIER - a wandering
 * entity - so the request only ever travels 16 blocks from whichever drone happens to be nearest. In a hive bigger than
 * that bubble, eggs laid away from the workers are never heard about at all, and the pile grows forever.
 * <p>
 * Razorem's tester described the mechanism exactly without knowing it: "any drones that were in the hive were nowhere
 * near the queen room and only ended up there after i shot an egg which caused them to come from vents... afterwards
 * they started calling others to move the eggs." Shooting the egg fired the CRY FOR HELP, which is relayed by vents and
 * so does reach across the hive; the drones it summoned arrived inside the 16-block radius and only then began hearing
 * the pickup requests that had been going out all along.
 * <p>
 * Compare the two designs and the asymmetry is the whole bug: a cry is heard by VENTS, which are fixed and spread
 * through the hive, and each vent draws on the location's reserves. An egg is heard only by a mob that happens to be
 * standing close. This class supplies the missing relay - not by widening the bubble, but by letting the hive itself
 * notice a waiting egg and hand it to a worker.
 * <p>
 * TRAVEL IS ALREADY SOLVED, which is why this class is so small. Once a carrier has a target, {@code PickUpEggAction}
 * plans a duct leg through {@code HiveVents.ductTravel} and shortcuts across the hive by vent, and
 * {@code DropOffEggAction} does the same on the way to the nursery. Nothing here needs to move anybody; it only has to
 * make the introduction.
 */
public final class EggHaulDispatch {

    /** Assignments per run. A backlog drains over a few passes rather than every idle worker leaving at once. */
    private static final int MAX_ASSIGNMENTS_PER_RUN = 3;

    /** Vertical reach around the hive floor - enough for the chamber band without scanning the whole column. */
    private static final int VERTICAL_REACH = 24;

    /** Path probes per egg before giving up on it this pass. Bounds the only real cost in here. */
    private static final int MAX_PATH_PROBES_PER_EGG = 3;

    private EggHaulDispatch() {
        throw new UnsupportedOperationException();
    }

    public static void run(ServerLevel level, HiveLocation location) {
        var structureChunks = location.structurePieceByChunk().keySet();

        if (structureChunks.isEmpty()) {
            return;
        }

        var minChunkX = Integer.MAX_VALUE;
        var minChunkZ = Integer.MAX_VALUE;
        var maxChunkX = Integer.MIN_VALUE;
        var maxChunkZ = Integer.MIN_VALUE;

        for (var chunk : structureChunks) {
            minChunkX = Math.min(minChunkX, chunk.x);
            minChunkZ = Math.min(minChunkZ, chunk.z);
            maxChunkX = Math.max(maxChunkX, chunk.x);
            maxChunkZ = Math.max(maxChunkZ, chunk.z);
        }

        // Bounded by the STRUCTURE, not the claim. A claim can be hundreds of blocks across and is mostly open
        // terrain; the eggs and the workers are both inside the built hive, so that is all this needs to sweep.
        var floorY = location.hiveFloorY();
        var box = new AABB(
            minChunkX << 4,
            floorY - VERTICAL_REACH,
            minChunkZ << 4,
            (maxChunkX << 4) + 16,
            floorY + VERTICAL_REACH,
            (maxChunkZ << 4) + 16
        );

        // UNROOTED EGGS ONLY, per his call. A shelved egg roots itself deliberately - that is what stopped the
        // haul/shelve/haul loop that used to freeze runners - so it must stay invisible to this sweep.
        //
        // ⚠⚠ AND ONLY EGGS NOBODY HAS CLAIMED. pickupRequestAcknowledged is the anti-pile-up flag the game-event
        // path has always honoured (see EggPickupManager.acknowledgePickupRequest, whose comment is about this exact
        // tester report: haulers "always got stuck on the egg and nothing else on eachother"). This relay did not
        // check it, so every pass handed the SAME egg to the next free carrier: the log shows "dispatched 1 hauler(s)
        // to 1 waiting egg(s)" once a second in bursts of nine and ten, sixty seconds apart - one ovomorph, the
        // hive's entire carrier pool walking to it. The egg lapses its own claim after PICKUP_CLAIM_TIMEOUT_TICKS if
        // no pickup follows, so honouring the flag costs at most one timeout and never strands an egg.
        var waiting = level.getEntitiesOfClass(
            Ovomorph.class,
            box,
            ovomorph -> ovomorph.isAlive()
                && !ovomorph.isPassenger()
                && !ovomorph.isRooted.get()
                && !ovomorph.pickupRequestAcknowledged
                && ovomorph.canBePickedUp()
        );

        // ⚠ THE BOX IS A HULL, SO FILTER BACK DOWN TO THE HIVE. min/max over the structure chunks is a rectangle, and
        // on a hive that has grown in an L or a cross that rectangle covers a great deal of open terrain the hive
        // never built. Eggs dropped out there were being chased across a hundred blocks - the same session logged
        // carriers at x=-122 and x=104 and six ovomorphs recovered as stranded. One hash lookup per egg puts the
        // sweep back on the actual footprint.
        waiting.removeIf(ovomorph -> !structureChunks.contains(new ChunkPos(ovomorph.blockPosition())));

        if (waiting.isEmpty()) {
            return;
        }

        if (waiting.isEmpty()) {
            return;
        }

        // A LIVE worker, never a spawn from reserves. An unhauled egg is not the deadlock an unstaffed carve site is -
        // the hive keeps producing carriers on its own - so this waits for a body rather than minting one, and a
        // neglected hive cannot breed haulers every time nobody is looking.
        var free = new ArrayList<Xenomorph>();

        for (var candidate : level.getEntitiesOfClass(Xenomorph.class, box, Xenomorph::isAlive)) {
            if (!(candidate instanceof EggCarrier carrier)) {
                continue;
            }
            // Busy: already reserved an egg, already carrying something, or in a fight. Fighting matters because
            // EggPickupManager.tick drops any reservation the moment a worker acquires an attack target, so
            // assigning one here would be undone on the next tick anyway.
            if (
                carrier.getEggPickupManager().getTargetOvomorphOrNull() != null
                    || !candidate.getPassengers().isEmpty()
                    || candidate.getTarget() != null
            ) {
                continue;
            }
            free.add(candidate);
        }

        if (free.isEmpty()) {
            return;
        }

        var assigned = 0;

        for (var egg : waiting) {
            if (assigned >= MAX_ASSIGNMENTS_PER_RUN || free.isEmpty()) {
                break;
            }

            // Nearest free worker to THIS egg that can actually GET to it, tried in distance order.
            //
            // ⚠⚠ THE PATH TEST IS NOT OPTIONAL, and my note saying otherwise was reasoning about a different cost.
            // acknowledgePickupRequest has always refused an egg it cannot path to; this relay did not, so it could
            // hand an unreachable ovomorph to the whole hive. PickUpEggAction then returns ABORT on NO_PATH and
            // onFinish clears the claim, which frees every carrier at once for the relay to re-hand them the same
            // unreachable egg on the next pass. That churn is what kept the carriers permanently "busy".
            //
            // The cost objection was about testing every worker against every egg. This is at most
            // MAX_PATH_PROBES_PER_EGG paths for ONE egg, and at most MAX_ASSIGNMENTS_PER_RUN eggs per pass.
            Xenomorph best = null;
            var probes = 0;
            var candidates = new ArrayList<>(free);
            candidates.sort(java.util.Comparator.comparingDouble(worker -> worker.distanceToSqr(egg)));

            for (var worker : candidates) {
                if (probes >= MAX_PATH_PROBES_PER_EGG) {
                    break;
                }
                probes++;
                if (worker.getNavigation().createPath(egg, 0) != null) {
                    best = worker;
                    break;
                }
            }

            if (best == null) {
                continue; // nobody near can reach it - leave the egg for a worker that ends up closer
            }

            // setTargetOvomorph marks the egg acknowledged, so it stops broadcasting and no second worker claims it.
            ((EggCarrier) best).getEggPickupManager().setTargetOvomorph(egg);
            free.remove(best);
            assigned++;
        }

        if (assigned > 0) {
            Alien.LOGGER.info(
                "Hive at {}: dispatched {} hauler(s) to {} waiting egg(s) out of earshot.",
                location.centerPos(),
                assigned,
                waiting.size()
            );
        }
    }
}
