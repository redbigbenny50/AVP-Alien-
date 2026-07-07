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

        // Anything else: cancel the spawn if this candidate position is inside a hive location's slab.
        var location = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), new ChunkPos(pos));
        if (location != null && location.withinSlab(pos.getY())) {
            cir.setReturnValue(false);
        }
    }
}
