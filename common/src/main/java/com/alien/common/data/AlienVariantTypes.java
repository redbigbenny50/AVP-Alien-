package com.alien.common.data;

import com.alien.common.gameplay.entity.acid.Acid;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.model.alien.variant.AlienVariantType;
import com.alien.common.registry.init.AlienGameEvents;
import com.alien.common.registry.init.AlienParticleTypes;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.tag.AlienBlockTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.just.core.functional.option.Option;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

public class AlienVariantTypes {

    public static final AlienVariantType ABERRANT = new AlienVariantType(
        AlienVariant.ABERRANT,

        AberrantAlienResinBlocks.ABERRANT_RESIN,
        AberrantAlienResinBlocks.ABERRANT_RESIN_NODE,
        AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN,
        AberrantAlienResinBlocks.ABERRANT_RESIN_VENT,
        AberrantAlienResinBlocks.ABERRANT_RESIN_WEB,

        AlienBlockTags.ABERRANT_RESIN,
        AlienBlockTags.ABERRANT_RESIN_REPLACEABLE,

        AlienItems.ABERRANT_CHITIN,
        AlienItems.PLATED_ABERRANT_CHITIN,
        AlienItems.ABERRANT_RESIN_BALL,

        AlienGameEvents.XENOMORPH_ABERRANT_CRY_FOR_HELP,
        AlienGameEvents.EGG_ABERRANT_PICKUP_REQUEST,
        AlienGameEvents.XENOMORPH_ABERRANT_RESIN_SPREAD,

        AlienParticleTypes.ACID,

        BossEvent.BossBarColor.YELLOW,
        ChatFormatting.YELLOW,
        true
    );

    public static final AlienVariantType IRRADIATED = new AlienVariantType(
        AlienVariant.IRRADIATED,

        IrradiatedAlienResinBlocks.IRRADIATED_RESIN,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT,
        IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB,

        AlienBlockTags.IRRADIATED_RESIN,
        AlienBlockTags.IRRADIATED_RESIN_REPLACEABLE,

        AlienItems.IRRADIATED_CHITIN,
        AlienItems.PLATED_IRRADIATED_CHITIN,
        AlienItems.IRRADIATED_RESIN_BALL,

        AlienGameEvents.XENOMORPH_IRRADIATED_CRY_FOR_HELP,
        null,
        AlienGameEvents.XENOMORPH_IRRADIATED_RESIN_SPREAD,

        AlienParticleTypes.IRRADIATED_ACID,

        BossEvent.BossBarColor.BLUE,
        ChatFormatting.BLUE,
        false
    );

    public static final AlienVariantType NETHER = new AlienVariantType(
        AlienVariant.NETHER,

        NetherAlienResinBlocks.NETHER_RESIN,
        NetherAlienResinBlocks.NETHER_RESIN_NODE,
        NetherAlienResinBlocks.NETHER_RESIN_VEIN,
        NetherAlienResinBlocks.NETHER_RESIN_VENT,
        NetherAlienResinBlocks.NETHER_RESIN_WEB,

        AlienBlockTags.NETHER_RESIN,
        AlienBlockTags.NETHER_RESIN_REPLACEABLE,

        AlienItems.NETHER_CHITIN,
        AlienItems.PLATED_NETHER_CHITIN,
        AlienItems.NETHER_RESIN_BALL,

        AlienGameEvents.XENOMORPH_NETHER_CRY_FOR_HELP,
        AlienGameEvents.EGG_NETHER_PICKUP_REQUEST,
        AlienGameEvents.XENOMORPH_NETHER_RESIN_SPREAD,

        AlienParticleTypes.BLUE_ACID,

        BossEvent.BossBarColor.RED,
        ChatFormatting.RED,
        true
    );

    public static final AlienVariantType NORMAL = new AlienVariantType(
        AlienVariant.NORMAL,

        AlienResinBlocks.RESIN,
        AlienResinBlocks.RESIN_NODE,
        AlienResinBlocks.RESIN_VEIN,
        AlienResinBlocks.RESIN_VENT,
        AlienResinBlocks.RESIN_WEB,

        AlienBlockTags.NORMAL_RESIN,
        AlienBlockTags.NORMAL_RESIN_REPLACEABLE,

        AlienItems.CHITIN,
        AlienItems.PLATED_CHITIN,
        AlienItems.RESIN_BALL,

        AlienGameEvents.XENOMORPH_CRY_FOR_HELP,
        AlienGameEvents.EGG_PICKUP_REQUEST,
        AlienGameEvents.XENOMORPH_RESIN_SPREAD,

        AlienParticleTypes.ACID,

        BossEvent.BossBarColor.GREEN,
        ChatFormatting.GREEN,
        true
    );

    private static final Map<AlienVariant, AlienVariantType> TYPE_BY_VARIANT = Util.make(() -> {
        var map = new EnumMap<AlienVariant, AlienVariantType>(AlienVariant.class);

        map.put(AlienVariant.ABERRANT, ABERRANT);
        map.put(AlienVariant.IRRADIATED, IRRADIATED);
        map.put(AlienVariant.NETHER, NETHER);
        map.put(AlienVariant.NORMAL, NORMAL);

        if (!Arrays.stream(AlienVariant.values()).allMatch(map::containsKey)) {
            throw new IllegalStateException("Missing variant types from variant map.");
        }

        return map;
    });

    private static final Map<Integer, AlienVariantType> TYPE_BY_ID = TYPE_BY_VARIANT.entrySet()
        .stream()
        .collect(Collectors.toMap(entry -> entry.getKey().getId(), Map.Entry::getValue));

    public static AlienVariantType getFor(AlienVariant alienVariant) {
        return TYPE_BY_VARIANT.get(alienVariant);
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static Option<AlienVariantType> getFor(Integer alienVariantId) {
        return Option.ofNullable(TYPE_BY_ID.get(alienVariantId));
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static Option<AlienVariantType> getFor(Block block) {
        return Option.ofNullable(getForOrNull(block));
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static @Nullable AlienVariantType getForOrNull(Block block) {
        return getForOrNull(block.defaultBlockState());
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static Option<AlienVariantType> getFor(BlockState blockState) {
        return Option.ofNullable(getForOrNull(blockState));
    }

    /**
     * Usage of this function is discouraged since it can return null. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static @Nullable AlienVariantType getForOrNull(BlockState blockState) {
        if (blockState.is(AlienBlockTags.ABERRANT_RESIN)) {
            return ABERRANT;
        } else if (blockState.is(AlienBlockTags.IRRADIATED_RESIN)) {
            return IRRADIATED;
        } else if (blockState.is(AlienBlockTags.NETHER_RESIN)) {
            return NETHER;
        } else if (blockState.is(AlienBlockTags.RESIN)) {
            return NORMAL;
        }

        return null;
    }

    public static AlienVariantType getFor(Alien alien) {
        return getFor(alien.getVariant());
    }

    /**
     * ⭐⭐ THE STRAIN A BUILDER SHOULD BUILD IN: the HIVE'S, not its own.
     * <p>
     * A hive's structure is the hive's, not a tally of who happened to lay each block. An off-strain member - a
     * spawned-in normal runner, a captured drone, a survivor of a lineage that has since been nuked and converted - was
     * stamping ITS OWN resin into someone else's hive, so an irradiated hive came out patched with black normal resin.
     * Worse, the mismatch is not cosmetic: {@code isStandingOnVariantResin} and the resin tags are strain-specific, so
     * the wrong block is invisible to the systems that look for the hive's own.
     * </p>
     * <p>
     * ⚠ FALLS BACK TO THE BUILDER'S OWN STRAIN when it is outside any hive, or the location has no lineage. A lone
     * alien webbing up a cave has no hive to speak for, and its own strain is the only right answer there.
     * </p>
     */
    public static AlienVariantType getForBuild(Alien alien, @Nullable HiveLocation location) {
        if (location != null) {
            var lineageVariant = location.lineageVariantOrNull();

            if (lineageVariant != null) {
                return getFor(lineageVariant);
            }
        }

        return getFor(alien);
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static Option<AlienVariantType> getFor(Entity entity) {
        return Option.ofNullable(getForOrNull(entity));
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static @Nullable AlienVariantType getForOrNull(Entity entity) {
        return getForOrNull(entity.getType());
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    @Deprecated(forRemoval = true)
    public static Option<AlienVariantType> getFor(Acid entity) {
        if (entity.isIrradiated()) {
            return Option.some(IRRADIATED);
        } else if (entity.isNetherAfflicted()) {
            return Option.some(NETHER);
        }

        return Option.some(NORMAL);
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static Option<AlienVariantType> getFor(EntityType<?> entityType) {
        return Option.ofNullable(getForOrNull(entityType));
    }

    /**
     * Usage of this function is discouraged since it can return Option#none. Consider using getFor(AlienVariant) where
     * possible.
     */
    public static @Nullable AlienVariantType getForOrNull(EntityType<?> entityType) {
        if (entityType.is(AlienEntityTypeTags.ABERRANT_ALIENS)) {
            return ABERRANT;
        } else if (entityType.is(AlienEntityTypeTags.IRRADIATED_ALIENS)) {
            return IRRADIATED;
        } else if (entityType.is(AlienEntityTypeTags.NETHER_ALIENS)) {
            return NETHER;
        } else if (entityType.is(AlienEntityTypeTags.NORMAL_ALIENS)) {
            return NORMAL;
        }

        return null;
    }
}
