package com.alien.mixin;

import com.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps hive slabs populated by the hive alone: denies vanilla and modded natural mob spawning inside any hive
 * location's slab band, allowing only the mod's own ({@code avp_alien}-namespaced) entities through.
 * <p>
 * This targets {@link NaturalSpawner}'s per-candidate spawn-validity check, so it only affects natural (mob-cap driven)
 * spawning. Spawner blocks, spawn eggs, breeding, {@code /summon}, and the hive's own summoned aliens all use different
 * code paths and are untouched. The allowlist is by namespace so any future alien added to a natural spawn table is
 * still permitted, while everything else - regardless of which mod added it - is cancelled before it spawns (no
 * spawn-and-remove cost).
 */
@Mixin(NaturalSpawner.class)
public abstract class MixinNaturalSpawner_HiveSlabSpawnDeny {

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

        // Anything else: cancel the spawn if this candidate position belongs to the hive.
        var chunk = new ChunkPos(pos);
        var location = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), chunk);
        if (location == null) {
            return;
        }

        // A chunk holding a BUILT piece is hive interior, full stop - the vanilla equivalent of a structure
        // declaring empty spawn_overrides the way the ancient city does. Real spawn_overrides cannot be used here:
        // vanilla resolves them from a StructureStart, and hive pieces are stamped from templates at runtime, so
        // the StructureManager has never heard of them. This is the same statement made where it can be seen.
        //
        // Checked BEFORE the slab test on purpose. The slab band is derived from the location's centre, so a piece
        // built off that band - or a claim whose bookkeeping has drifted - fell straight through the old test and
        // spawned freely on any vanilla block inside it. Being inside a piece the hive actually built is the more
        // direct question, and the one that matches what a player sees.
        if (location.structurePieceByChunk().containsKey(chunk)) {
            cir.setReturnValue(false);
            return;
        }

        if (location.withinSlab(pos.getY())) {
            cir.setReturnValue(false);
        }
    }
}
