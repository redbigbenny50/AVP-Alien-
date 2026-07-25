package com.alien.common.gameplay.hive.dimension;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.model.alien.variant.AlienVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Per-dimension hive behavior profiles. Dimensions are FUNDAMENTALLY different structures - the overworld has a sky
 * surface and depth below sea level; the Nether has a bedrock ceiling, a floor at Y 0, stacked open caverns instead of
 * one surface, and lava everywhere - so every system that asks "where is the surface", "how deep is deep", or "is this
 * ground safe to dig" resolves its answer through the profile instead of assuming overworld shape.
 *
 * <p>Known dimensions get exact hand-tuned profiles (the OVERWORLD profile encodes today's behavior verbatim). Unknown
 * MODDED dimensions get a profile DERIVED from their own {@code DimensionType} flags - a ceiled dimension behaves
 * nether-like (open shelves as surface, capped at logical height), an ultrawarm one gets lava safety - so packs work
 * without configuration. The END is deliberately unprofiled for now (derived default applies) - parked by design.</p>
 *
 * <p>Core concepts:</p>
 * <ul>
 *   <li><b>Surface mode:</b> {@code SKY_SURFACE} = the heightmap/canSeeSky world everyone knows. {@code OPEN_SHELF} =
 *       ceiled dimensions where "surface" is a PROPERTY, not a place: any sturdy floor with at least
 *       {@link Profile#minShelfAirHeight()} blocks of open air above it counts, at ANY level - the Nether's stacked
 *       exposed caverns are all valid party ground.</li>
 *   <li><b>Depth band:</b> where queens dig to and wild queens take root, resolved against the dimension's REAL
 *       vertical range (the Nether's floor is Y 0, not -64).</li>
 *   <li><b>Lava safety:</b> in lava-rich dimensions, strains that are not fireproof refuse to found, vent, or surface
 *       adjacent to lava - nether morphs ignore it, everyone else digs AROUND it.</li>
 *   <li><b>Host-rich biomes:</b> an optional biome tag party site selection PREFERS (hosts are scarce in the Nether,
 *       so scouts favor piglin/hoglin country) - a preference with fallback, never a hard filter.</li>
 * </ul>
 */
public final class DimensionHiveProfiles {

    private DimensionHiveProfiles() {}

    /** Synthetic day length for fixed-time dimensions: vanilla's 24000-tick cycle, first half "day". */
    private static final long SYNTHETIC_CYCLE_TICKS = 24000L;

    private static final long SYNTHETIC_DAY_TICKS = 12000L;

    /**
     * Day/night for HIVE RHYTHMS - the drop-in replacement for {@code level.isDay()} in party logic. Dimensions with
     * a frozen clock (the Nether's fixed time sits permanently mid-night, so a real dawn never comes and nocturnal
     * parties would launch forever and resolve never) synthesize a cycle from GAME TIME instead: 10 minutes of
     * "day", 10 of "night", the same pacing the overworld gives - driven by ticks, because the dimension has no sun.
     */
    public static boolean isHiveDay(ServerLevel level) {
        if (!level.dimensionType().hasFixedTime()) {
            return level.isDay();
        }
        return level.getGameTime() % SYNTHETIC_CYCLE_TICKS < SYNTHETIC_DAY_TICKS;
    }

    /** Sentinel returned by {@link #surfaceY} when a column holds no valid surface at all. */
    public static final int NO_SURFACE = Integer.MIN_VALUE;

    /** Biomes the Nether's party site selection prefers - where the hosts actually live. Datapack-editable tag. */
    public static final TagKey<Biome> NETHER_HOST_RICH = TagKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath("avp_alien", "nether_host_rich")
    );

    /**
     * Structures that get a GUARANTEED surface vent when a scout party wraps up within reach (the drop roll is
     * skipped): host parties gain a permanent door into them. The Nether points this at bastion remnants - the
     * piglin larder. Datapack-editable, so packs can add their own structure targets.
     */
    public static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> PRIORITY_VENT_STRUCTURES =
        TagKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath("avp_alien", "priority_vent_structures")
        );

    public enum SurfaceMode {
        SKY_SURFACE,
        OPEN_SHELF
    }

    public record Profile(
        SurfaceMode surfaceMode,
        int minShelfAirHeight,
        boolean lavaSafetyForNonFireproof,
        int depthMinY,
        int depthMaxY,
        @Nullable TagKey<Biome> hostRichBiomes,
        @Nullable TagKey<net.minecraft.world.level.levelgen.structure.Structure> priorityVentStructures
    ) {}

    /** The source envelope the queen's hand-tuned overworld dig bands were authored against (see the phase manager). */
    private static final int OVERWORLD_BAND_MIN = -50;

    private static final int OVERWORLD_BAND_MAX = 45;

    public static Profile get(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            return new Profile(SurfaceMode.SKY_SURFACE, 8, false, OVERWORLD_BAND_MIN, OVERWORLD_BAND_MAX, null, null);
        }
        if (level.dimension() == Level.NETHER) {
            return new Profile(
                SurfaceMode.OPEN_SHELF,
                8,
                true,
                level.getMinBuildHeight() + 5,
                level.dimensionType().logicalHeight() - 8,
                NETHER_HOST_RICH,
                PRIORITY_VENT_STRUCTURES
            );
        }
        // Derived default for modded dimensions (and, for now, the End - parked by design): shape follows the
        // dimension's own flags so packs behave sensibly with zero configuration.
        var type = level.dimensionType();
        var ceiled = type.hasCeiling();
        var min = level.getMinBuildHeight();
        var top = ceiled ? min + type.logicalHeight() : level.getMaxBuildHeight();
        var range = top - min;
        return new Profile(
            ceiled ? SurfaceMode.OPEN_SHELF : SurfaceMode.SKY_SURFACE,
            8,
            type.ultraWarm(),
            ceiled ? min + 5 : min + Math.round(range * 0.04F),
            ceiled ? top - 8 : min + Math.round(range * 0.28F),
            null,
            null
        );
    }

    /**
     * The Y of the walkable surface at (x, z) for this dimension, or {@link #NO_SURFACE}. SKY mode is the classic
     * heightmap. SHELF mode searches outward from {@code nearY} (clamped into the depth-to-ceiling range) for the
     * NEAREST shelf floor - a sturdy block with the profile's worth of open air above - so parties operate on the
     * cavern level they are actually on, not the bedrock roof the heightmap reports in ceiled dimensions.
     */
    public static int surfaceY(ServerLevel level, Profile profile, int x, int z, int nearY) {
        if (profile.surfaceMode() == SurfaceMode.SKY_SURFACE) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }
        var minY = level.getMinBuildHeight() + 1;
        var maxY = Math.min(level.getMinBuildHeight() + level.dimensionType().logicalHeight(), level.getMaxBuildHeight())
            - profile.minShelfAirHeight();
        var start = Math.max(minY, Math.min(maxY, nearY));
        for (var d = 0; d <= maxY - minY; d++) {
            var below = start - d;
            if (below >= minY && isShelfFloor(level, profile, x, below, z)) {
                return below;
            }
            var above = start + d;
            if (d > 0 && above <= maxY && isShelfFloor(level, profile, x, above, z)) {
                return above;
            }
        }
        return NO_SURFACE;
    }

    private static boolean isShelfFloor(ServerLevel level, Profile profile, int x, int y, int z) {
        var ground = new BlockPos(x, y - 1, z);
        if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
            return false;
        }
        for (var i = 0; i < profile.minShelfAirHeight(); i++) {
            if (!level.getBlockState(new BlockPos(x, y + i, z)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code pos} is "open to the world" - the party-system replacement for {@code canSeeSky}. SKY mode:
     * literal sky. SHELF mode: standing room of the profile's shelf height above the position.
     */
    public static boolean isOpenToWorld(ServerLevel level, Profile profile, BlockPos pos) {
        if (profile.surfaceMode() == SurfaceMode.SKY_SURFACE) {
            return level.canSeeSky(pos);
        }
        for (var i = 0; i < profile.minShelfAirHeight(); i++) {
            if (!level.getBlockState(pos.above(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** True when this lineage variant must avoid lava in this dimension (lava-rich world, non-fireproof strain). */
    public static boolean needsLavaSafety(Profile profile, @Nullable AlienVariant variant) {
        if (!profile.lavaSafetyForNonFireproof()) {
            return false;
        }
        return AlienVariantTypes.getFor(variant) != AlienVariantTypes.NETHER;
    }

    /** True when no lava exists within a cubic {@code radius} of {@code pos} - "safe to stand, dig, or vent here". */
    public static boolean isLavaSafe(ServerLevel level, BlockPos pos, int radius) {
        for (var probe : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
            if (level.getFluidState(probe).is(FluidTags.LAVA)) {
                return false;
            }
        }
        return true;
    }

    /** Site-selection preference: true when the profile has no biome preference, or the biome here is host-rich. */
    public static boolean isHostRichPreferred(ServerLevel level, Profile profile, BlockPos pos) {
        var tag = profile.hostRichBiomes();
        return tag == null || level.getBiome(pos).is(tag);
    }

    /**
     * Remaps a Y rolled against the hand-tuned OVERWORLD dig band envelope ({@value #OVERWORLD_BAND_MIN}..
     * {@value #OVERWORLD_BAND_MAX}) into this profile's depth band, preserving the band's weighted shape. Identity in
     * the overworld, so tuned behavior there is untouched.
     */
    public static int remapFromOverworldBand(int y, Profile profile) {
        if (profile.depthMinY() == OVERWORLD_BAND_MIN && profile.depthMaxY() == OVERWORLD_BAND_MAX) {
            return y;
        }
        var srcSpan = (float) (OVERWORLD_BAND_MAX - OVERWORLD_BAND_MIN);
        var fraction = (y - OVERWORLD_BAND_MIN) / srcSpan;
        return profile.depthMinY() + Math.round(fraction * (profile.depthMaxY() - profile.depthMinY()));
    }
}
