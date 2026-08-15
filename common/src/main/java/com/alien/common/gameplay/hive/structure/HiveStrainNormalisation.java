package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.tag.AlienBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Retints any NORMAL-strain resin inside a non-normal hive to that hive's own strain.
 * <p>
 * [stated] "standing over section at 39 57 200 and its a regular resin corner piece the rest of the hive is nether and
 * there are no regular strains here how could that happen?"
 * </p>
 * <h2>⚠⚠ THIS IS A SAFETY NET FOR A WHOLE CLASS OF BUG, NOT ONE BUG</h2> At least three separate routes put normal
 * resin into a strain hive, and each was found only after a player noticed:
 * <ul>
 * <li>{@code HivePieceCatalog.strainFolderPrefix} returns {@code ""} - the NORMAL folder - when the lineage variant is
 * momentarily null, so an ENTIRE piece stamps in normal resin. That is the corner he found.</li>
 * <li>Every non-normal piece NBT carries a normal {@code avp_alien:resin_vent} - 57 files across the three strains, an
 * authoring slip when they were derived from the normal set.</li>
 * <li>Builders using the alien's own variant instead of the hive's, fixed once already via {@code getForBuild}.</li>
 * </ul>
 * Rather than chase each one, this runs after every stamp and makes the footprint consistent by construction.
 * <h2>⚠ WRONG-STRAIN RESIN IS NEVER COSMETIC</h2> It is invisible to {@code isStandingOnVariantResin} and to the
 * per-strain resin tags, so a normal block inside a nether hive is a hole in every gate that asks "am I on my own
 * resin" - not just a colour mismatch.
 */
public final class HiveStrainNormalisation {

    /**
     * ⚠ DERIVED FROM THE ID, NOT A HAND-WRITTEN TABLE. Every strain block is its normal counterpart with the strain
     * inserted immediately before {@code resin}: {@code resin_slab} → {@code nether_resin_slab}, {@code ribbed_resin} →
     * {@code ribbed_nether_resin}. Verified against the real registry before relying on it. A table of sixty entries
     * would rot the first time a resin family was added; this picks new ones up for free.
     */
    private static final Map<String, Block> CACHE = new HashMap<>();

    private HiveStrainNormalisation() {}

    public static void normalise(ServerLevel level, HiveLocation location, BoundingBox box) {
        var variant = location.lineageVariantOrNull();

        if (variant == null || variant == AlienVariant.NORMAL) {
            return; // a normal hive wants normal resin, and a strainless one has no answer yet
        }

        var strain = variant.name().toLowerCase(java.util.Locale.ROOT);
        var pos = new BlockPos.MutableBlockPos();
        var converted = 0;

        for (var x = box.minX(); x <= box.maxX(); x++) {
            for (var y = box.minY(); y <= box.maxY(); y++) {
                for (var z = box.minZ(); z <= box.maxZ(); z++) {
                    pos.set(x, y, z);

                    var state = level.getBlockState(pos);

                    // ⚠ THE NORMAL_RESIN TAG IS THE GATE. It contains only normal-strain resin, so a block that is in
                    // it inside a nether hive is wrong by definition - and anything else is left alone, including the
                    // player's own blocks and the surrounding stone.
                    if (!state.is(AlienBlockTags.NORMAL_RESIN)) {
                        continue;
                    }

                    var replacement = strainCounterpart(state.getBlock(), strain);

                    if (replacement == null) {
                        continue;
                    }

                    // ⚠ copyPropertiesTo KEEPS THE STATE - slab halves, stair shapes, waterlogging, vent facing. A
                    // defaultBlockState() here would silently flatten every stair in the piece.
                    level.setBlock(pos, copyProperties(state, replacement.defaultBlockState()), 2);
                    converted++;
                }
            }
        }

        if (converted > 0) {
            com.alien.Alien.LOGGER.info(
                "Hive: retinted {} normal-strain block(s) to {} inside a stamped piece at {}",
                converted,
                strain,
                box.getCenter()
            );
        }
    }

    private static @org.jetbrains.annotations.Nullable Block strainCounterpart(Block normal, String strain) {
        var key = BuiltInRegistries.BLOCK.getKey(normal);
        var path = key.getPath();

        if (!path.contains("resin")) {
            return null;
        }

        var wanted = path.replaceFirst("resin", strain + "_resin");

        return CACHE.computeIfAbsent(wanted, id -> {
            var located = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), id);

            return BuiltInRegistries.BLOCK.containsKey(located) ? BuiltInRegistries.BLOCK.get(located) : null;
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static BlockState copyProperties(BlockState from, BlockState to) {
        var result = to;

        for (var property : from.getProperties()) {
            if (result.hasProperty(property)) {
                result = result.setValue(
                    (net.minecraft.world.level.block.state.properties.Property) property,
                    (Comparable) from.getValue(property)
                );
            }
        }

        return result;
    }
}
