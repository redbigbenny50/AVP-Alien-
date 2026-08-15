package com.alien.common.gameplay.radiation;

import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.init.item.block.AlienBlockItems;
import com.alien.common.registry.tag.AlienBlockTags;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_human.RadiationCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;

import java.util.Map;

/**
 * The irradiated strain leaks. Its ground, its walls and its harvest all dose whoever is near them.
 * <h2>Three sources, all small and continuous</h2>
 * <ul>
 * <li><b>The slab.</b> Standing anywhere inside an irradiated hive's Y band doses you, crater and open air included -
 * the whole site is hot, not just the surfaces.</li>
 * <li><b>Contact.</b> Standing in or on irradiated resin or chitin doses you again, and stacks with the slab, so the
 * floor of a hive is worse than the air above it.</li>
 * <li><b>Carrying.</b> Irradiated material in your pack doses you by the ITEM, so a single chitin is an hour's problem
 * and a full stack is a minute's. [stated] the ratio is "uranium nugget to ingot": nine loose pieces equal one block,
 * and a jelly block simply counts as the nine sources it is.</li>
 * </ul>
 * <h2>What does NOT dose</h2> [stated] "we will be nice and the trophies and shields dont dose" - the heads and head
 * shields are inert. Neither does the ARMOUR, and that one matters: the irradiated chitin sets are the mod's radiation
 * PROTECTION and a full suit grants immunity. Raw material is dangerous, the finished suit is shielded.
 * <h2>Cost</h2> Throttled to once a second, and the slab test is a chunk-map lookup rather than a scan. Everything here
 * also short-circuits the moment {@code RadiationCompat} finds nothing to talk to.
 */
public final class IrradiatedExposureTask {

    /** Once a second. A dose is a slow pressure, not a per-tick drip. */
    private static final int INTERVAL_TICKS = 20;

    /**
     * Per second, standing inside the hive's slab. At {@code EXPOSURE_PER_SICKNESS_LEVEL} of 3600 this is six minutes
     * to the first rung on air alone - long enough to raid, short enough to want the suit.
     */
    private static final int SLAB_EXPOSURE_PER_SECOND = 10;

    /** Per second, standing in or on the strain's own resin or chitin. Stacks with the slab. */
    private static final int CONTACT_EXPOSURE_PER_SECOND = 10;

    /** Per second, per loose piece carried. A stack of 64 is about a minute to the first rung. */
    private static final int EXPOSURE_PER_CARRIED_PIECE = 1;

    /** Seconds of sickness per point of exposure, when we are keeping the books ourselves. */
    private static final int FALLBACK_TICKS_PER_EXPOSURE = 4;

    /** However long you linger, the fallback effect tops out here rather than stacking to an unsurvivable timer. */
    private static final int FALLBACK_MAX_DURATION_TICKS = 20 * 60 * 5;

    /** [stated] nugget-to-ingot: the jelly block is nine raw jellies pressed together, so it doses as nine. */
    private static final int PIECES_PER_JELLY_BLOCK = 9;

    /** [stated] "the resin and chitin blocks arent made from 9 items only 4" - so they weigh four, not nine. */
    private static final int PIECES_PER_BUILDING_BLOCK = 4;

    private static long tickCounter;

    private IrradiatedExposureTask() {
        throw new UnsupportedOperationException();
    }

    public static void tick(MinecraftServer server) {
        if (++tickCounter % INTERVAL_TICKS != 0) {
            return;
        }

        for (var level : server.getAllLevels()) {
            for (var player : level.players()) {
                doseIfExposed(level, player);
            }
        }
    }

    private static void doseIfExposed(ServerLevel level, ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        var exposure = slabExposure(level, player) + contactExposure(level, player) + carriedExposure(player);
        if (exposure <= 0) {
            return;
        }

        // THE FALLBACK MATTERS. RadiationCompat.addExposure is a reflective bridge into AVP: Human and silently does
        // NOTHING when that mod is absent - so without this branch the entire slab/contact/cargo system was dead on
        // any server running avp_alien alone, while the egg and hugger detonations correctly fell back. Same bridge,
        // same rule, both places.
        if (AVPHuman.MOD.isLoaded()) {
            RadiationCompat.addExposure(player, exposure);
            return;
        }

        applyFallbackSickness(player, exposure);
    }

    /**
     * Our own sickness effect, for servers without AVP: Human.
     * <p>
     * Their model is a continuous exposure counter; ours is a timed effect, so the two cannot be mapped one to one.
     * Accumulated exposure is converted into effect DURATION - keep standing in the hot zone and the timer keeps being
     * pushed out, which is the same pressure expressed the only way our effect can express it.
     */
    private static void applyFallbackSickness(ServerPlayer player, int exposure) {
        var existing = player.getEffect(AlienMobEffects.getRadiationSicknessHolder());
        var added = Math.max(1, exposure * FALLBACK_TICKS_PER_EXPOSURE);
        var duration = Math.min(
            FALLBACK_MAX_DURATION_TICKS,
            (existing == null ? 0 : existing.getDuration()) + added
        );

        player.addEffect(new MobEffectInstance(AlienMobEffects.getRadiationSicknessHolder(), duration, 0));
    }

    /** A chunk-map lookup, not a scan: the registry already indexes locations by chunk. */
    private static int slabExposure(ServerLevel level, ServerPlayer player) {
        var location = HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), new ChunkPos(player.blockPosition()));

        if (location == null || !location.isAlive() || location.lineageVariantOrNull() != AlienVariant.IRRADIATED) {
            return 0;
        }

        return location.withinSlab(player.blockPosition().getY()) ? SLAB_EXPOSURE_PER_SECOND : 0;
    }

    /** Feet block and the one below it, so both standing on resin and wading through it count. */
    private static int contactExposure(ServerLevel level, ServerPlayer player) {
        var feet = player.blockPosition();

        return isIrradiatedMaterial(level, feet) || isIrradiatedMaterial(level, feet.below())
            ? CONTACT_EXPOSURE_PER_SECOND
            : 0;
    }

    private static boolean isIrradiatedMaterial(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(AlienBlockTags.IRRADIATED_RESIN) || state.is(AlienBlockTags.IRRADIATED_CHITIN);
    }

    /** Counted by piece, so hoarding is what hurts rather than possession. */
    private static int carriedExposure(ServerPlayer player) {
        var pieces = 0;

        for (var stack : player.getInventory().items) {
            pieces += stack.getCount() * weightOf(stack.getItem());
        }

        return pieces * EXPOSURE_PER_CARRIED_PIECE;
    }

    /**
     * How many loose pieces an item is worth, or zero if it is inert.
     * <p>
     * The building blocks are resolved from the BLOCK TAGS rather than listed, so every irradiated resin and chitin
     * variant - slabs, stairs, the lot - is covered automatically and a new one added later needs no change here.
     */
    private static int weightOf(Item item) {
        var loose = CARRIED_WEIGHTS.get(item);
        if (loose != null) {
            return loose;
        }

        if (item instanceof net.minecraft.world.item.BlockItem blockItem) {
            var block = blockItem.getBlock().defaultBlockState();
            if (block.is(AlienBlockTags.IRRADIATED_RESIN) || block.is(AlienBlockTags.IRRADIATED_CHITIN)) {
                return PIECES_PER_BUILDING_BLOCK;
            }
        }

        return 0;
    }

    /**
     * The LOOSE material. Building blocks are handled by tag in {@link #weightOf} instead, so this stays short.
     * <p>
     * Trophies, shields and armour are absent DELIBERATELY - see the class note. Anything neither listed here nor
     * carrying an irradiated resin/chitin block is inert.
     */
    private static final Map<Item, Integer> CARRIED_WEIGHTS = Map.of(
        AlienItems.IRRADIATED_CHITIN.get(),
        1,
        AlienItems.PLATED_IRRADIATED_CHITIN.get(),
        1,
        AlienItems.IRRADIATED_RESIN_BALL.get(),
        1,
        AlienItems.RAW_IRRADIATED_JELLY.get(),
        1,
        AlienBlockItems.IRRADIATED_JELLY_BLOCK.get(),
        PIECES_PER_JELLY_BLOCK
    );
}
