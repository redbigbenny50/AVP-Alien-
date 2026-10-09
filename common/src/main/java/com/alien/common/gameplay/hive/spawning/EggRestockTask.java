package com.alien.common.gameplay.hive.spawning;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.hive.faction.LocationMembership;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.structure.HiveChamberSlots;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Stage 4 of the egg logistics: the RESERVE egg bank restocks the physical nurseries. When free chamber beds exist and
 * no loose egg is already awaiting a hauler, one banked egg materializes at the queen (or the hive core when she is
 * absent - a raided, queenless hive can still restock from its savings) and the normal pickup/haul pipeline carries it
 * to a bed, visibly. One egg per growth cycle, and the bank keeps a floor so purchases stay funded.
 */
public final class EggRestockTask {

    /** Banked eggs kept back for unit purchases - restocking never drains below this. */
    private static final int RESERVE_EGG_FLOOR = 10;

    /** How far around the anchor a loose (unrooted, unhauled) egg suppresses another materialization. */
    private static final double PENDING_EGG_RADIUS = 48.0;

    private EggRestockTask() {}

    /** Called on the growth cadence. Materializes at most one banked egg per call. */
    public static void run(ServerLevel level, HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return;
        }
        var eggType = Ovomorph.getType(variant, false);
        if (eggType == null || location.localReserves().getCount(eggType) <= RESERVE_EGG_FLOOR) {
            return;
        }
        if (!hasFreeChamberBed(level, location)) {
            return;
        }

        var anchor = anchorPos(level, location);
        if (anchor == null) {
            return;
        }

        // One in flight at a time: a loose egg near the anchor means a hauler is (or will be) on it already.
        // HOST-BOUND eggs do not count: an egg the ferry just released from the queen's clutch (stamped for a host
        // cell) is a delivery in progress, not a pending restock, and must not starve the nursery of new eggs.
        // ⭐⭐⭐ AN EGG BEING CARRIED IS STILL AN EGG THE HIVE ALREADY PAID FOR.
        //
        // [stated] "i guess what we need to do is make the eggs still count even when being carried so it doesnt
        // keep making more."
        //
        // ⚠⚠ `!e.isPassenger()` WAS THE WHOLE BUG. The moment a hauler picked the egg up it stopped counting as
        // pending, so the gate reopened and another materialised at the queen on the next cycle. The rate was never
        // "one egg per growth cycle" as the class doc claims - it was ONE PER CYCLE PER AVAILABLE HAULER, which in a
        // busy hive is a continuous stream at her feet. Reported as eggs appearing every 20 seconds without limit.
        //
        // ⚠ THE RADIUS COMPOUNDED IT: at 16 blocks a hauler walking the egg away stopped blocking too, before it had
        // reached a bed. Widened so an egg in transit anywhere around the core still counts.
        // 🚨🚨 THE ANCHOR MOVES, SO A BOX AROUND IT WAS NEVER A RELIABLE TEST. anchorPos returns the QUEEN'S CURRENT
        // POSITION - or the empress's - recomputed every cycle. The moment she walked further than PENDING_EGG_RADIUS,
        // the egg she had just been given fell outside the new box, stopped counting as pending, and another was
        // materialised. Reported as eggs appearing endlessly underneath the empress, one replacing each one killed,
        // steadily draining a bank of 284.
        //
        // ⭐ ASK THE HIVE, NOT THE WORLD. The location already tracks its own members, so a loose egg counts wherever
        // it is - at her feet, halfway to a bed, or on a hauler's back - and walking no longer resets the gate.
        //
        // ⚠ Also strictly cheaper: a map lookup instead of an AABB entity query every cycle.
        var loadedEggs = location.loadedMembersByType().get(eggType);
        var pending = false;

        if (loadedEggs != null) {
            for (var uuid : loadedEggs) {
                if (
                    level.getEntity(uuid) instanceof Ovomorph egg
                        && egg.isAlive()
                        && !egg.isRooted.get()
                        && egg.getHostDropTarget() == null
                ) {
                    pending = true;
                    break;
                }
            }
        }
        if (pending) {
            return;
        }

        // ⭐⭐ AND NEVER EXCEED WHAT THE HIVE CAN ACTUALLY HOLD.
        //
        // [stated] "its only supposed to allow 10 eggs before it goes to the chambers but if the chambers arent
        // built yet or worse missing then they will put more then 10 infront of the queen."
        //
        // ⚠⚠ THE RESTOCK NEVER CHECKED A TOTAL - only whether a chamber bed was free. So when haulers could not
        // reach the chambers, or the chambers were missing, the eggs piled up around the queen with nothing counting
        // them. The LAYING path has always respected this cap; the restock path simply did not ask.
        // ⚠ Asked through the QUEEN when she is present, because the capacity formula is written around an EggLayer.
        // A queenless hive restocking from savings has no layer to ask and keeps the old behaviour - the free-bed
        // test above is its only limit, which is correct there: without a queen there is no ring to overfill.
        var layer = queenEggLayer(level, location);

        // 🚨🚨 THE CAP APPLIES WHETHER OR NOT A ROYAL IS SEATED. This guard used to read
        // `layer != null && !hasPhysicalOvomorphCapacity(...)`, so a hive with no seated royal skipped the physical
        // limit ENTIRELY and restocked without end - eggs piled onto the anchor block until the vanilla entity cram
        // limit killed them.
        //
        // ⚠⚠ [stated] "I already set up a command block that insta kills eggs bc they just kept spawning on the exact
        // same block until the entity cram limit killed em". The royal was only ever supplying a fallback variant to
        // the count, never the cap itself, so there was never a reason to skip it.
        var hasRoom = layer != null
            ? com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayingSensors
                .hasPhysicalOvomorphCapacity(layer, location)
            : com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayingSensors
                .hasPhysicalOvomorphCapacity(level, location);

        if (!hasRoom) {
            return;
        }

        if (!location.localReserves().trySpawn(eggType)) {
            return;
        }
        var egg = eggType.create(level);
        if (egg == null) {
            return; // reserve count already decremented; vanishingly rare, self-corrects via banking
        }

        // ⚠⚠ A MATERIALIZED EGG IS BORN ROOTED UNLESS TOLD OTHERWISE. OVOMORPH_IS_ROOTED defaults to TRUE; every laying
        // path clears it (LayEggAction does, explicitly) and this one never did. A rooted egg is one no hauler will
        // pick up, and the "loose egg still waiting" gate above only counts UNROOTED eggs — so each growth cycle
        // materialized another, at the anchor, forever. [reported] "the majority of her eggs ... spawn beneath her
        // arms and instantly become rooted ... they all stack and blend into each other ... untouched" — 59 in one
        // log. Loose, like a laid egg, so the pickup/haul pipeline this task was written to feed actually runs.
        egg.isRooted.set(false);
        egg.setPersistenceRequired();

        // 🚨🚨 SPAWN ON THE FLOOR BENEATH HER, NOT AT HER OWN HEIGHT. `anchor` is the ROYAL'S BLOCK POSITION, and a
        // seated royal is SITTING ON HER OVIPOSITOR - so her position is well above the ground and eggs materialised
        // in mid-air. Reported as rooted eggs floating with nothing under them.
        //
        // ⚠⚠ THIS ONLY BECAME REACHABLE WITH THE EMPRESS FIXES. She was not a location member before, so anchorPos
        // could not find her and fell back to hiveFloorY() + 1 - the floor - which hid this entirely. Making her
        // findable exposed a spawn position that was always wrong for a seated royal.
        var spawnAt = groundedSpawnPos(level, location, anchor);

        egg.moveTo(
            spawnAt.getX() + 0.5,
            spawnAt.getY(),
            spawnAt.getZ() + 0.5,
            level.random.nextFloat() * 360.0F,
            0.0F
        );
        // 🚨 A RESTOCKED EGG INHERITS THE QUEEN'S GENES, EXACTLY AS A LAID ONE DOES. The bank stores a member as a
        // TYPE AND A COUNT - genes cannot survive that - so an egg materialised from reserve arrived with none, and a
        // hive restocking from a large bank slowly replaced its genetic line with blanks. Reported as "these eggs keep
        // appearing underneath the empress that have no gene data".
        //
        // ⚠ Same transfer LayEggAction performs, from the same royal, so a restocked egg and a freshly laid one are
        // indistinguishable from here on.
        //
        // 🚨🚨 layer IS NULLABLE AND THIS LINE DID NOT CHECK. queenEggLayer returns null whenever the location has no
        // LIVE royal, which the capacity test above handles deliberately ("a queenless hive restocking from savings
        // has no layer to ask") — and then this switch dereferenced it anyway. Kill the hive's only royal and the
        // very next restock tick threw NullPointerException out of the SERVER TICK LOOP, taking the world down.
        // [reported] "Game crashed when marines killed a iradiated empress"; crash-2026-09-16, EggRestockTask:157.
        //
        // ⚠ A queenless restock simply ships an egg with no inherited genes, which is the correct outcome: the genes
        // come FROM the royal, and there is no royal to take them from.
        if (layer != null) {
            switch (layer.getGeneManager()) {
                case com.alien.compatibility.avp_human.GeneManagerProxy.EMPTY ignored -> { /* NO-OP */ }
                case com.alien.compatibility.avp_human.GeneManagerProxy.Wrapper wrapper ->
                    wrapper.transfer(egg.getGeneManager(), false);
            }
        }

        if (!level.addFreshEntity(egg)) {
            location.localReserves().tryAdd(eggType, 1); // put it back
            return;
        }
        LocationMembership.join(location, egg);
        level.playSound(null, egg, AlienSoundEvents.ENTITY_OVOMORPH_LAID.get(), SoundSource.HOSTILE, 1.0F, 1.1F);
        Alien.LOGGER.info(
            "Hive at {}: materialized a banked egg for nursery restock ({} left in reserve).",
            location.centerPos(),
            location.localReserves().getCount(eggType)
        );
    }

    /** Whether any loaded egg chamber has a bed without a rooted ovomorph on it. */
    /**
     * Every chunk that can hold egg beds - the built egg chambers, or the cluster chunks in build-free mode.
     * <p>
     * ⚠ Mirrors {@code DropOffEggAction.eggDestinationChunks} deliberately: the task that MAKES eggs and the task that
     * PLACES them must agree about where beds are, or one produces work the other cannot finish.
     * </p>
     */
    private static java.util.List<net.minecraft.world.level.ChunkPos> eggBedChunks(HiveLocation location) {
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            return com.alien.common.gameplay.hive.config.BuildFreeClusters
                .eggClusterChunks(location, new net.minecraft.world.level.ChunkPos(location.centerPos()));
        }
        var chambers = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (entry.getValue().contains("chamber_egg")) {
                chambers.add(entry.getKey());
            }
        }
        return chambers;
    }

    private static boolean hasFreeChamberBed(ServerLevel level, HiveLocation location) {
        // ⭐⭐⭐ BUILD-FREE HAS EGG BEDS TOO, AND THIS NEVER SAW THEM.
        //
        // [stated] "keep in mind this is the standard hive format not buildless however buildless also generates egg
        // beds so the same should apply in that case too."
        //
        // ⚠⚠ structurePieceByChunk IS EMPTY IN BUILD-FREE MODE - the whole point of that mode is that it never
        // stamps structure. So this loop found nothing, hasFreeChamberBed always returned false, and a build-free
        // hive could NEVER restock its nurseries from the egg bank. A silent no-op rather than a visible fault,
        // which is why it went unreported while the standard hive's runaway version got noticed immediately.
        //
        // ⚠ The cluster chunks are the SAME source DropOffEggAction hauls to. Two independent answers to "where do
        // eggs go" is what produced the earlier livelock of free beds and a full failed list; there must be one.
        for (var chamber : eggBedChunks(location)) {
            if (!level.isLoaded(chamber.getWorldPosition())) {
                continue;
            }
            for (var bed : HiveChamberSlots.eggBedSlots(level, location, chamber)) {
                var box = new AABB(
                    bed.getCenter().x - 1.0,
                    bed.getY() - 1.0,
                    bed.getCenter().z - 1.0,
                    bed.getCenter().x + 1.0,
                    bed.getY() + 2.0,
                    bed.getCenter().z + 1.0
                );
                boolean occupied = !level.getEntitiesOfClass(
                    Ovomorph.class,
                    box,
                    e -> e.isRooted.get() && e.isAlive()
                ).isEmpty();
                if (!occupied) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The hive's living queen as an EggLayer, or null when it has none loaded. */
    @Nullable
    private static com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer queenEggLayer(
        ServerLevel level,
        HiveLocation location
    ) {
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.QUEENS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                if (
                    level.getEntity(uuid) instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer carrier
                        && ((net.minecraft.world.entity.Entity) carrier).isAlive()
                ) {
                    return carrier;
                }
            }
        }
        return null;
    }

    /** The queen's position when one is loaded (eggs come from her), else the hive core. */
    @Nullable
    /**
     * Where a restocked egg appears.
     * <p>
     * 🚨🚨 IT MUST BE A ROYAL WHO IS ACTUALLY LAYING. This used to take the first living QUEENS-tagged member it found,
     * SEATED OR NOT - so a royal with no ovipositor, who lays nothing and wanders freely, was used as the lay anchor
     * and eggs appeared under her wherever she walked. That is the "why is the empress walking if she is laying eggs"
     * question: she was not laying at all.
     * </p>
     * <p>
     * ⭐ A royal WITH an ovipositor is seated on it and cannot walk, so the anchor stays put and eggs appear where the
     * nursery expects them. With no seated royal anywhere, the hive centre is used - a fixed point, which is the right
     * fallback for a hive whose queen is away or dead.
     * </p>
     */
    /**
     * Drops an anchor position down to the first solid floor beneath it.
     * <p>
     * ⚠ An egg belongs on the ground. A royal seated on her ovipositor is metres above it, and the drone that will come
     * to collect the egg cannot reach one hanging in the air either.
     * </p>
     * <p>
     * ⚠ Gives up after GROUND_SEARCH_DEPTH and returns the original position rather than dropping an egg into a void or
     * a deep shaft - falling from the anchor is still better than teleporting it somewhere unrelated.
     * </p>
     */
    private static BlockPos groundedSpawnPos(ServerLevel level, HiveLocation location, BlockPos anchor) {
        // ⚠⚠ THE HIVE FLOOR IS THE REFERENCE, NOT THE ROYAL'S FEET. [stated] "they shouldnt base themselves on the
        // queens height because what if shes not on the hives floor." A royal on a ledge, on a slope, or seated high
        // on her ovipositor would otherwise seed her eggs at that height - and dropping from HER position just lands
        // them on whatever ledge she happens to be over.
        //
        // ⭐ So start the search at the hive's own floor, directly under her X/Z, and only walk down from there if that
        // level is not itself solid ground - which handles a hive whose floor varies without ever using her elevation.
        // 🚨 Oct 1: build-free starts the drop from the anchor itself. hiveFloorY() there is the bottom of a 48-block
        // band,
        // 24 blocks under the queen, so every restocked egg was placed inside rock. A normal hive still starts at its
        // carved floor exactly as before.
        var startY = com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled() && !location.isEndStyleHive()
            ? anchor.getY()
            : location.hiveFloorY() + 1;
        var cursor = new BlockPos(anchor.getX(), startY, anchor.getZ());

        for (var drop = 0; drop < GROUND_SEARCH_DEPTH; drop++) {
            var below = cursor.below();

            // 🚨🚨 NEVER PASS null TO entityCanStandOn. It builds an EntityCollisionContext from the entity and
            // dereferences it immediately, so a null argument is an instant NPE - it crashed the server tick every
            // time a hive restocked an egg.
            //
            // ⚠ There is no entity to pass here: the egg being placed does not exist yet. The collision-shape test
            // answers the same question - is there something solid to stand on - and needs no entity at all, so the
            // entityCanStandOn call was redundant as well as unsafe.
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return cursor;
            }

            cursor = below;
        }

        return anchor;
    }

    /** How far below a royal to look for the floor her eggs should rest on. */
    private static final int GROUND_SEARCH_DEPTH = 8;

    private static BlockPos anchorPos(ServerLevel level, HiveLocation location) {
        for (var entry : location.loadedMembersByType().entrySet()) {
            if (!entry.getKey().is(AlienEntityTypeTags.QUEENS)) {
                continue;
            }
            for (var uuid : entry.getValue()) {
                if (
                    level.getEntity(uuid) instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying.EggLayer royal
                        && royal.asEntity().isAlive()
                        && royal.hasOvipositor()
                ) {
                    return royal.asEntity().blockPosition();
                }
            }
        }
        var center = location.centerPos();
        return level.isLoaded(center) ? new BlockPos(center.getX(), location.throneFloorY(level) + 1, center.getZ()) : null;
    }
}
