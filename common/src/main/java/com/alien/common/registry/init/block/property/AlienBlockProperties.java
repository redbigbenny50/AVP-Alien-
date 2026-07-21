package com.alien.common.registry.init.block.property;

import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.block.v1.BlockPropertyBuilder;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Supplier;

public class AlienBlockProperties {

    public static final BlockPropertyBuilder JELLY = BlockPropertyBuilder.of()
        .mapColor(MapColor.GRASS)
        .sound(SoundType.SLIME_BLOCK)
        .noOcclusion()
        .friction(0.8F);

    private static final Supplier<BlockPropertyBuilder> CHITIN_PROPERTIES_SUPPLIER = () -> BlockPropertyBuilder.of()
        .mapColor(MapColor.COLOR_BLACK)
        .requiresCorrectToolForDrops()
        .strength(5, 6)
        // Hive material, so only the hive nests on it - same rule the resin blocks carry. Without this, chitin was
        // an ordinary spawn surface and vanilla mobs bred happily on any chitin floor inside a hive.
        .isValidSpawn(($1, $2, $3, entityType) -> entityType.is(AlienEntityTypeTags.ALIENS));

    private static final Supplier<BlockPropertyBuilder> RESIN_PROPERTIES_SUPPLIER = () -> BlockPropertyBuilder.of()
        .mapColor(MapColor.COLOR_BLACK)
        .requiresCorrectToolForDrops()
        .strength(4F)
        // TODO: Make this something other than the honey block sound.
        .sound(SoundType.HONEY_BLOCK);

    private static final Supplier<BlockPropertyBuilder> ABERRANT_RESIN_BLOCK_PROPERTIES_SUPPLIER = () -> RESIN_PROPERTIES_SUPPLIER.get()
        .isValidSpawn(($1, $2, $3, entityType) -> entityType.is(AlienEntityTypeTags.ABERRANT_ALIENS));

    private static final Supplier<BlockPropertyBuilder> IRRADIATED_RESIN_BLOCK_PROPERTIES_SUPPLIER = () -> RESIN_PROPERTIES_SUPPLIER.get()
        .isValidSpawn(($1, $2, $3, entityType) -> entityType.is(AlienEntityTypeTags.IRRADIATED_ALIENS));

    private static final Supplier<BlockPropertyBuilder> NETHER_RESIN_BLOCK_PROPERTIES_SUPPLIER = () -> RESIN_PROPERTIES_SUPPLIER.get()
        .isValidSpawn(($1, $2, $3, entityType) -> entityType.is(AlienEntityTypeTags.NETHER_ALIENS));

    private static final Supplier<BlockPropertyBuilder> RESIN_BLOCK_PROPERTIES_SUPPLIER = () -> RESIN_PROPERTIES_SUPPLIER.get()
        .isValidSpawn(($1, $2, $3, entityType) -> entityType.is(AlienEntityTypeTags.NORMAL_ALIENS));

    public static final BlockPropertyBuilder ABERRANT_CHITIN = CHITIN_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.TERRACOTTA_GREEN);

    public static final BlockPropertyBuilder ABERRANT_RESIN = ABERRANT_RESIN_BLOCK_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.TERRACOTTA_GREEN);

    public static final BlockPropertyBuilder ABERRANT_RESIN_VEIN = RESIN_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.TERRACOTTA_GREEN)
        .noCollision()
        .noOcclusion()
        .pushReaction(PushReaction.DESTROY)
        .replaceable();

    public static final BlockPropertyBuilder ABERRANT_RESIN_WEB = ABERRANT_RESIN_BLOCK_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.TERRACOTTA_GREEN)
        .noCollision()
        .noOcclusion()
        .pushReaction(PushReaction.DESTROY);

    public static final BlockPropertyBuilder IRRADIATED_CHITIN = CHITIN_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_BLACK)
        .lightLevel(state -> 6);

    public static final BlockPropertyBuilder IRRADIATED_RESIN = IRRADIATED_RESIN_BLOCK_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_BLACK)
        .lightLevel(state -> 6);

    public static final BlockPropertyBuilder IRRADIATED_RESIN_VEIN = RESIN_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_BLACK)
        .noCollision()
        .noOcclusion()
        .pushReaction(PushReaction.DESTROY)
        .replaceable()
        .lightLevel(state -> 6);

    public static final BlockPropertyBuilder IRRADIATED_RESIN_WEB = IRRADIATED_RESIN_BLOCK_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_BLACK)
        .noCollision()
        .noOcclusion()
        .pushReaction(PushReaction.DESTROY)
        .lightLevel(state -> 6);

    public static final BlockPropertyBuilder NETHER_CHITIN = CHITIN_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_RED);

    public static final BlockPropertyBuilder NETHER_RESIN = NETHER_RESIN_BLOCK_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_RED);

    public static final BlockPropertyBuilder NETHER_RESIN_VEIN = RESIN_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_RED)
        .noCollision()
        .noOcclusion()
        .pushReaction(PushReaction.DESTROY)
        .replaceable();

    public static final BlockPropertyBuilder NETHER_RESIN_WEB = NETHER_RESIN_BLOCK_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_RED)
        .noCollision()
        .noOcclusion()
        .pushReaction(PushReaction.DESTROY);

    public static final BlockPropertyBuilder CHITIN = CHITIN_PROPERTIES_SUPPLIER.get();

    public static final BlockPropertyBuilder RESIN = RESIN_BLOCK_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_BLACK);

    public static final BlockPropertyBuilder RESIN_VEIN = RESIN_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_BLACK)
        .noCollision()
        .noOcclusion()
        .pushReaction(PushReaction.DESTROY)
        .replaceable();

    public static final BlockPropertyBuilder RESIN_WEB = RESIN_BLOCK_PROPERTIES_SUPPLIER.get()
        .mapColor(MapColor.COLOR_BLACK)
        .noCollision()
        .noOcclusion()
        .pushReaction(PushReaction.DESTROY);
}
