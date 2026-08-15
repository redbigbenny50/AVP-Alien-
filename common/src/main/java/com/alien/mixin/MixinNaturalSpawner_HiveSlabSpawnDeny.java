package com.alien.mixin;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.level.saveddata.HiveRuinsData;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps hive slabs populated by the hive alone: denies vanilla and modded natural mob spawning inside any hive
 * location's slab band - and inside the ruins of one - allowing only the mod's own ({@code avp_alien}-namespaced)
 * entities through.
 * <p>
 * This targets {@link NaturalSpawner}'s per-candidate spawn-validity check, so it only affects natural (mob-cap driven)
 * spawning. Spawner blocks, spawn eggs, breeding, {@code /summon}, and the hive's own summoned aliens all use different
 * code paths and are untouched. The allowlist is by namespace so any future alien added to a natural spawn table is
 * still permitted, while everything else - regardless of which mod added it - is cancelled before it spawns (no
 * spawn-and-remove cost).
 * <h2>Three nets, in order</h2>
 * <ol>
 * <li><b>Bookkeeping.</b> Ask the registry which LIVE location owns this chunk, then test the piece map, the role map
 * and the slab band. Free - one hash lookup - and it is the whole answer for a healthy hive.</li>
 * <li><b>Ruins.</b> Ask {@link HiveRuinsData} whether a hive once BUILT here and at what band. This is the net the old
 * check was missing entirely, and it is the one that covers a whole room rather than just its resin.</li>
 * <li><b>Ground truth.</b> Look at the blocks around the candidate and refuse if it is standing in, on, or beside hive
 * material. Depends on nothing but the world itself, so no amount of index drift can get past it - and it teaches net 2
 * about ruins that predate this index.</li>
 * </ol>
 * <h2>Why nets 2 and 3 exist</h2> {@code LocationRemovalHelper.remove} releases every claimed chunk and unregisters the
 * location, but it demolishes nothing: the resin, the chambers and the trophy walls stay standing. A hive that dies of
 * {@code natural_decay} - queen killed, population zero, no-contact timeout - or that migrates away leaves a fully
 * built, sealed, pitch-dark ruin whose chunks no longer resolve to any location. The old check bailed at its
 * {@code location == null} guard and handed the derelict hive back to the vanilla spawner, which is exactly what a dark
 * 12-block-tall room is for. Net 2 closes that; net 3 makes sure a hive can never be repopulated because a map
 * somewhere disagreed with the blocks.
 */
@Mixin(NaturalSpawner.class)
public abstract class MixinNaturalSpawner_HiveSlabSpawnDeny {

    /** Game tick of the last ground-truth diagnostic line, so a spawn-heavy tick cannot flood the log. */
    private static long avp_alien$lastGroundTruthLogTick = Long.MIN_VALUE;

    /** Five seconds between diagnostic lines - enough to see the pattern, not enough to bury the log. */
    private static final long avp_alien$GROUND_TRUTH_LOG_INTERVAL_TICKS = 100L;

    /**
     * How far up and down a bone block the column scan reaches, in blocks. Matches the slab height, so it covers a
     * chamber floor-to-ceiling from anywhere inside it.
     */
    private static final int avp_alien$BONE_COLUMN_SCAN = 16;

    @Inject(method = "isValidSpawnPostitionForType", at = @At("HEAD"), cancellable = true)
    private static void avp_alien$denyNonAlienSpawnsInHiveSlab(
        ServerLevel level,
        MobCategory category,
        StructureManager structureManager,
        ChunkGenerator generator,
        MobSpawnSettings.SpawnerData data,
        BlockPos.MutableBlockPos pos,
        double distance,
        CallbackInfoReturnable<Boolean> cir
    ) {
        // The hive's own entities are always allowed to spawn.
        if (Alien.MOD_ID.equals(EntityType.getKey(data.type).getNamespace())) {
            return;
        }

        var chunk = new ChunkPos(pos);
        var location = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), chunk);

        // NET 1 - BOOKKEEPING (live hive). A chunk holding a BUILT piece is hive interior, full stop: the vanilla
        // equivalent of a structure declaring empty spawn_overrides the way the ancient city does. Real
        // spawn_overrides cannot be used here - vanilla resolves them from a StructureStart, and hive pieces are
        // stamped from templates at runtime, so the StructureManager has never heard of them.
        //
        // Both structure maps are consulted, not just one. The role map is a superset of the piece map (part chunks of
        // a multi-chunk piece may carry a role with no piece id), so testing it can only ever deny more. The slab test
        // is last because it is the loosest: it is derived from the location's centre, so a piece built off that band
        // falls through it.
        if (
            location != null
                && (location.structurePieceByChunk().containsKey(chunk)
                    || location.structureRoleByChunk().containsKey(chunk)
                    || location.withinSlab(pos.getY()))
        ) {
            cir.setReturnValue(false);
            return;
        }

        // NET 2 - RUINS (dead hive). Chunk-and-band granularity, so it covers every block in the room and not merely
        // the resin ones: the bare stone the stamp never wrote, the bone-block trophy plinths, all of it.
        var ruins = HiveRuinsData.getOrCreate(level);
        if (ruins.isRuinedSlab(chunk, pos.getY())) {
            cir.setReturnValue(false);
            return;
        }

        // NET 3 - GROUND TRUTH. Deliberately reached even when net 1 found a location and cleared it: nothing here
        // depends on claims, indexes or a live owner, so it is the one test a mis-indexed hive cannot slip past. Cost
        // is one block read on a candidate vanilla is about to read three times anyway. This is also the net that
        // catches the raid chamber's bone-block trophy plinths - the only vanilla solids in the whole hive set.
        var floorY = avp_alien$hiveInteriorFloorY(level, pos);
        if (floorY == null) {
            return;
        }

        // Backfill: worlds that already contain derelict hives have no ruins entry, because those hives died before
        // the index existed. One denial on hive material teaches the index this chunk and its band, and from the next
        // candidate onward net 2 covers the whole room - no migration step, no world reset.
        if (!ruins.hasRuin(chunk) && location == null) {
            ruins.recordObservedBand(chunk, floorY);
        }

        avp_alien$logGroundTruthDeny(level, data, pos, chunk, location);
        cir.setReturnValue(false);
    }

    /**
     * The Y this candidate is standing at if it is inside the hive, else null.
     * <p>
     * Checks the block the mob's feet occupy (webs, veins and vents sit there), the block it would stand on (the
     * chamber floor), and - for bone only - the floor block's horizontal neighbours.
     * <p>
     * <b>Why bone gets the extra look.</b> Every resin and chitin block already carries
     * {@code isValidSpawn = entityType.is(ALIENS)} from {@code AlienBlockProperties}, so vanilla itself refuses to nest
     * anything on hive material and this net can never be the thing that saves a resin floor. The raid chamber is the
     * ONE piece in the whole hive set built partly from a foreign block - 61 {@code minecraft:bone_block} per copy, 244
     * across all four strains, and not one vanilla solid anywhere else in 80 pieces. Bone is therefore literally the
     * only surface inside a hive a vanilla mob can stand on, which is exactly why this bug only ever showed up in raid
     * chambers. A bone block with hive material beside it - or anywhere in its column - is hive floor. Both scans are
     * scoped to bone so ordinary terrain never pays for them, and bone is close to nonexistent outside raid chambers
     * (desert and nether fossils, which have no resin near them and simply fall through).
     */
    private static @Nullable Integer avp_alien$hiveInteriorFloorY(
        ServerLevel level,
        BlockPos.MutableBlockPos pos
    ) {
        if (avp_alien$isHiveMaterial(level, pos)) {
            return pos.getY();
        }

        // MutableBlockPos.below() returns an immutable copy, so vanilla's cursor is never disturbed.
        var below = pos.below();
        if (avp_alien$isHiveMaterial(level, below)) {
            return below.getY() + 1;
        }

        if (!avp_alien$isLoadedInWorld(level, below) || !level.getBlockState(below).is(Blocks.BONE_BLOCK)) {
            return null;
        }

        for (var direction : Direction.Plane.HORIZONTAL) {
            if (avp_alien$isHiveMaterial(level, below.relative(direction))) {
                return below.getY() + 1;
            }
        }

        // A trophy plinth stood off the floor - bone hanging in open air with nothing beside it - has no hive block
        // touching it at all, so walk its column instead. Down first (the chamber floor), then up (a plinth slung
        // under the ceiling). The Y reported is the hive block's own, not the bone's, so the band this backfills is
        // anchored on the real floor and therefore covers the bone above it.
        for (var offset = 1; offset <= avp_alien$BONE_COLUMN_SCAN; offset++) {
            var down = below.below(offset);
            if (avp_alien$isHiveMaterial(level, down)) {
                return down.getY() + 1;
            }
        }

        for (var offset = 1; offset <= avp_alien$BONE_COLUMN_SCAN; offset++) {
            var up = below.above(offset);
            if (avp_alien$isHiveMaterial(level, up)) {
                return up.getY() - avp_alien$BONE_COLUMN_SCAN;
            }
        }

        return null;
    }

    /**
     * Never force a chunk load from inside the spawner: the candidate cursor wanders up to 24 blocks from the chunk it
     * was seeded in, and the bone neighbour scan reaches one further still, so either can land outside the loaded
     * region.
     */
    private static boolean avp_alien$isLoadedInWorld(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos)
            && level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static boolean avp_alien$isHiveMaterial(ServerLevel level, BlockPos pos) {
        if (!avp_alien$isLoadedInWorld(level, pos)) {
            return false;
        }

        var state = level.getBlockState(pos);
        return state.is(AlienBlockTags.RESIN) || state.is(AlienBlockTags.CHITIN);
    }

    /**
     * Diagnostic for the ground-truth net. Every line it prints is a spawn nets 1 and 2 should have caught and did not,
     * and it names which failure mode happened: no owning location and no ruin record (a hive that died before this
     * index existed - now backfilled), or an owning location whose structure maps and slab band all disagreed with the
     * blocks actually in the world.
     */
    private static void avp_alien$logGroundTruthDeny(
        ServerLevel level,
        MobSpawnSettings.SpawnerData data,
        BlockPos.MutableBlockPos pos,
        ChunkPos chunk,
        @Nullable HiveLocation location
    ) {
        var now = level.getGameTime();
        if (now - avp_alien$lastGroundTruthLogTick < avp_alien$GROUND_TRUTH_LOG_INTERVAL_TICKS) {
            return;
        }
        avp_alien$lastGroundTruthLogTick = now;

        var owner = location == null
            ? "NO live location and no ruin record (pre-index derelict hive; chunk backfilled)"
            : "live location "
                + location.id().value()
                + " role="
                + location.structureRole(chunk)
                + " piece="
                + location.structurePieceByChunk().get(chunk)
                + " floorY="
                + location.hiveFloorY();

        Alien.LOGGER.info(
            "[hive-spawn-deny] ground-truth net caught {} at {} (chunk {}, dim {}); bookkeeping said: {}; floor block = {}",
            EntityType.getKey(data.type),
            pos.immutable(),
            chunk,
            level.dimension().location(),
            owner,
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).getBlock())
        );
    }
}
