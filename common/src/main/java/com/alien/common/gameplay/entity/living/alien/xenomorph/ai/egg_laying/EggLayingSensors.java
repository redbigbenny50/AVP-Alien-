package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.egg_laying;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.HiveLocationSpawnGate;
import com.alien.common.gameplay.hive.structure.HiveChamberSlots;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class EggLayingSensors {

    public static final StateKey.Sensed<Boolean> CAN_LAY_EGG = StateKey.sensed("can_lay_egg");

    private static final double MIN_HORIZONTAL_OVOMORPH_SPACING_BLOCKS = 2.0;

    private static final double VERTICAL_OVOMORPH_CHECK_BLOCKS = 4.0;

    /**
     * \u2b50 Oct 3 - WHO LAYS BY THE CAPTIVE RULE (one slot in front of her, anywhere, never into a hive's bank). The
     * inhibited breeder always did; since capture now SEVERS a queen from her hive, so does any queen on a chain or
     * held on one. Without this a chained, un-inhibited queen still riding her founding sack would either keep banking
     * eggs into the hive she was just cut off from, or - carried out of it - lay nothing at all.
     */
    public static boolean isCaptiveBreeder(EggLayer eggLayer) {
        if (eggLayer.isInhibited()) {
            return true;
        }
        return eggLayer.asEntity() instanceof com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen queen
            && com.alien.common.gameplay.hive.lifecycle.QueenCaptivity.isCaptive(queen);
    }

    public static <T extends EggLayer> Sensor.Mono<T, Boolean> canLayEgg() {
        return Sensors.map(
            CAN_LAY_EGG,
            eggLayer -> {
                if (!eggLayer.isEggLayCooldownReady()) {
                    return false;
                }

                if (
                    !eggLayer.asEntity().isAlive()
                        || !eggLayer.hasOvipositor()
                        || !AlienVariantTypes.getFor(eggLayer.getVariant()).canReproduce()
                ) {
                    return false;
                }

                // ⭐⭐⭐ A CAPTIVE BREEDER LAYS WHEREVER SHE IS CHAINED. NO HIVE, NO CLAIM, NO RESIN REQUIRED.
                //
                // [stated] "for chained inhibited queens there should be no resin requirement when they are like this
                // as its a captive thing."
                //
                // ⚠⚠ THIS HAD TO MOVE ABOVE THE LOCATION CHECK, AND THAT WAS THE ACTUAL BUG. Every lay required a
                // hive location, and a queen chained in a player's containment cell is not standing in one. She only
                // ever worked because the inhibitor MINTS her a personal claim - and that claim was not being
                // persisted, so after a reload she had a sack, a chain, and nowhere the game would let her lay. She
                // looked completely correct and simply never produced an egg.
                //
                // ⚠ Her cap is the single slot in front of her plus a small reserve, so removing the location
                // requirement grants her nothing beyond being able to work at all.
                var location = HiveLocationSpawnGate.locationContaining(eggLayer.asEntity().level(), eggLayer.asEntity().blockPosition());

                if (isCaptiveBreeder(eggLayer)) {
                    // \u2b50 Oct 3 - A CAPTIVE IGNORES WHATEVER HIVE SHE IS STANDING IN. She holds no claim, so she has
                    // no bank of her own; and a cell that happens to sit in some hive's claim column must not let her
                    // pay eggs into THAT hive's reserves. She lays into her one slot or she waits.
                    return layZoneClearForCaptive(eggLayer);
                }

                if (location == null) {
                    return false;
                }
                // She can lay a PHYSICAL egg only if there is bed/ring room AND a clear spot to place it; she can
                // ALWAYS lay into the RESERVE while the bank has room (a reserve lay spawns no entity, so a clear spot
                // is irrelevant). This is why a queen ring choked by un-hauled eggs must NOT stop production - it banks
                // into the reserve instead of shutting the whole lay goal (and its banking) off. [flag for review]
                // A pacified captive breeder is not running a hive economy: her lay spot is a single slot. She may
                // lay a physical egg only when that spot is clear of ANY egg (rooted or not - the founding-only
                // noEggsNearby ignores un-rooted eggs, which is why she was stacking); otherwise she banks up to a
                // small cap and then simply stops until the egg is hauled away.
                boolean physicalRoom = hasPhysicalOvomorphCapacity(eggLayer, location);
                boolean reserveRoom = hasReserveOvomorphCapacity(eggLayer, location);
                return (physicalRoom && noEggsNearby(eggLayer)) || reserveRoom;
            }
        );
    }

    /** Eggs the queen keeps producing INTO THE RESERVE once the hive is physically saturated. */
    public static final int RESERVE_EGG_CAP = 100;

    /**
     * A pacified CAPTIVE breeder has no economy - she banks only a tiny reserve while her single lay spot is blocked.
     */
    public static final int CAPTIVE_RESERVE_EGG_CAP = 5;

    /** Physical eggs allowed around the queen herself, on top of the nursery beds. */
    public static final int QUEEN_RING_EGG_CAP = 10;

    /**
     * Whether another PHYSICAL egg fits. The capacity is dynamic - what the hive actually built: the queen's own ring
     * plus a bedful per existing egg chamber (a full 5-chamber hive holds 10 + 5x6 = 40; a 3-chamber hive 28). Used by
     * the lay action to decide between spawning an egg and banking a reserve one.
     */
    /**
     * Physical egg capacity for a hive with NO royal to ask.
     * <p>
     * 🚨🚨 THE RESTOCK PATH USED TO SKIP THE CAP ENTIRELY WHEN THERE WAS NO SEATED ROYAL - its guard read
     * {@code layer != null && !hasPhysicalOvomorphCapacity(...)}, so a null layer meant no limit at all and eggs piled
     * onto one block until the ENTITY CRAM LIMIT killed them. Reported by a tester who had to set up a command block to
     * destroy eggs on sight.
     * </p>
     * <p>
     * ⚠ The count already falls back to the location's own lineage variant, so no layer is needed to answer this - the
     * royal was only ever supplying a fallback variant, never the cap itself.
     * </p>
     */
    public static boolean hasPhysicalOvomorphCapacity(Level level, HiveLocation location) {
        var variant = location.lineageVariantOrNull();

        if (variant == null) {
            return false; // No lineage variant to count against - refuse rather than lay blindly.
        }

        return hasPhysicalOvomorphCapacity(level, variant, location);
    }

    public static boolean hasPhysicalOvomorphCapacity(EggLayer eggLayer, HiveLocation location) {
        return hasPhysicalOvomorphCapacity(eggLayer.asEntity().level(), eggLayer.getVariant(), location);
    }

    private static boolean hasPhysicalOvomorphCapacity(Level level, AlienVariant fallbackVariant, HiveLocation location) {
        int chambers = 0;
        for (var pieceId : location.structurePieceByChunk().values()) {
            if (pieceId.contains("chamber_egg")) {
                chambers++;
            }
        }
        // \u2b50\u2b50 BUILD-FREE: THE CAP IS CONFIGURED, NOT DERIVED FROM CHAMBERS THAT CANNOT EXIST.
        //
        // The ordinary formula is "her ring plus a bedful per egg chamber the hive BUILT". With no chambers that
        // collapses to the bare ring of 10 forever, so a build-free hive would cap at ten physical eggs however large
        // it grew - and the reserve bank would swallow everything else, leaving the territory visually empty.
        //
        // [stated] "queen cluster size - default 10 max 30" is the ring around HER; the outlying clusters
        // (buildFreeEggClusterAmount x buildFreeEggClusterSize) are the rest, and they only count when he has them
        // switched on. Same shape as the End branch below, which already solved "no chambers" with a flat number.
        if (com.alien.common.gameplay.hive.config.BuildFreeMode.isEnabled()) {
            var buildFreeConfig = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.config();
            var buildFreeCap = buildFreeConfig.buildFreeQueenEggClusterSize()
                + (buildFreeConfig.buildFreeAdditionalEggClusters()
                    ? buildFreeConfig.buildFreeEggClusterAmount() * buildFreeConfig.buildFreeEggClusterSize()
                    : 0);
            return countSameVariantOvomorphs(level, location, fallbackVariant) < buildFreeCap;
        }

        int cap = location.isEndStyleHive()
            // END-STYLE: a flat physical limit - [stated] "the aliens though would place the eggs around her we
            // should set the physical limit to 20." No egg chambers exist to extend it.
            ? com.alien.common.gameplay.hive.dimension.EndStyleHiveRules.STANDING_EGG_CAP
            : QUEEN_RING_EGG_CAP + HiveChamberSlots.EGG_BEDS_PER_CHAMBER * chambers;
        return countSameVariantOvomorphs(level, location, fallbackVariant) < cap;
    }

    /** Whether the reserve egg bank is below {@link #RESERVE_EGG_CAP}. */
    public static boolean hasReserveOvomorphCapacity(EggLayer eggLayer, HiveLocation location) {
        if (location.isEndStyleHive()) {
            return false; // END-STYLE: no eggs in the reserve - [stated] "no eggs only adults in the end."
        }
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            variant = eggLayer.getVariant();
        }
        var type = Ovomorph.getType(variant, false);
        return type != null && location.localReserves().getCount(type) < RESERVE_EGG_CAP;
    }

    /**
     * ⭐ Oct 1 - memoised per hive for one second. In build-free this is an entity query over the WHOLE territory (up to
     * 19x19 chunks), run by the queen's lay sensor every tick her cooldown is ready but she has no room - i.e.
     * continuously while she waits. A count up to a second old can only let one egg past the physical cap, and an egg
     * over the cap banks into the reserve rather than being lost. Nothing here is used for movement.
     */
    private static int countSameVariantOvomorphs(Level level, HiveLocation location, AlienVariant fallbackVariant) {
        return EGG_COUNT.get(location, level, () -> scanSameVariantOvomorphs(level, location, fallbackVariant));
    }

    private static final com.alien.common.gameplay.entity.living.alien.xenomorph.ai.SensorMemo<HiveLocation, Integer> EGG_COUNT =
        new com.alien.common.gameplay.entity.living.alien.xenomorph.ai.SensorMemo<>(20);

    private static int scanSameVariantOvomorphs(Level level, HiveLocation location, AlienVariant fallbackVariant) {
        if (location.claimedChunks().isEmpty()) {
            return 0;
        }

        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            variant = fallbackVariant;
        }

        var normalType = Ovomorph.getType(variant, false);
        var royalType = Ovomorph.getType(variant, true);
        if (normalType == null && royalType == null) {
            return 0;
        }

        var bounds = claimedChunkBounds(level, location);
        return level.getEntitiesOfClass(
            Ovomorph.class,
            bounds,
            entity -> isSameVariantOvomorph(entity.getType(), normalType, royalType)
                && location.claimedChunks().contains(new ChunkPos(entity.blockPosition()))
        ).size();
    }

    private static AABB claimedChunkBounds(Level level, HiveLocation location) {
        var minX = Integer.MAX_VALUE;
        var minZ = Integer.MAX_VALUE;
        var maxX = Integer.MIN_VALUE;
        var maxZ = Integer.MIN_VALUE;

        for (var chunk : location.claimedChunks()) {
            var chunkMinX = chunk.x * 16;
            var chunkMinZ = chunk.z * 16;
            minX = Math.min(minX, chunkMinX);
            minZ = Math.min(minZ, chunkMinZ);
            maxX = Math.max(maxX, chunkMinX + 15);
            maxZ = Math.max(maxZ, chunkMinZ + 15);
        }

        return new AABB(minX, level.getMinBuildHeight(), minZ, maxX + 1, level.getMaxBuildHeight(), maxZ + 1);
    }

    private static boolean isSameVariantOvomorph(
        EntityType<?> type,
        EntityType<? extends Ovomorph> normalType,
        EntityType<? extends Ovomorph> royalType
    ) {
        return type == normalType || type == royalType;
    }

    public static boolean noEggsNearby(EggLayer eggLayer) {
        var eggPos = eggLayer.getEggLayingPosition();
        var halfSize = MIN_HORIZONTAL_OVOMORPH_SPACING_BLOCKS;

        var searchBox = new AABB(
            eggPos.x - halfSize,
            eggPos.y - VERTICAL_OVOMORPH_CHECK_BLOCKS,
            eggPos.z - halfSize,
            eggPos.x + halfSize,
            eggPos.y + 5,
            eggPos.z + halfSize
        );

        return eggLayer.asEntity()
            .level()
            .getEntitiesOfClass(
                Ovomorph.class,
                searchBox,
                entity -> entity.getType().is(AlienEntityTypeTags.OVOMORPHS) && entity.isRooted.get()
            )
            .isEmpty();
    }

    /**
     * Captive-breeder lay-zone check: like {@link #noEggsNearby} but counts EVERY ovomorph in the spot, rooted or not.
     * A captive queen's freshly-laid eggs are un-rooted, so the founding rooted-only check never saw them and she
     * stacked endlessly. She holds until the egg is moved out of the zone.
     */
    /**
     * Whether the captive's single lay slot is free.
     * <p>
     * 🚨🚨 ONE BLOCK, NOT A ZONE. This used to test a box 2 blocks wide and NINE TALL (4 below, 5 above) around the lay
     * point - a hive-sized spacing rule applied to a queen chained in a cell. One egg anywhere in that column stopped
     * her permanently, and an egg on another floor of the same build stopped her too.
     * </p>
     * <p>
     * ⚠⚠ [stated] "she should only lay at the one spot ... if the egg just sits there it stops until removed ... its
     * not a hive so it should only matter if that area has an egg in it already." That is exactly one block: the
     * gEggHole. Spacing between eggs is a HIVE concern - a captive has no hive and no room to spread.
     * </p>
     */
    public static boolean layZoneClearForCaptive(EggLayer eggLayer) {
        var eggPos = eggLayer.getEggLayingPosition();
        var searchBox = new AABB(net.minecraft.core.BlockPos.containing(eggPos));
        return eggLayer.asEntity()
            .level()
            .getEntitiesOfClass(
                Ovomorph.class,
                searchBox,
                entity -> entity.getType().is(AlienEntityTypeTags.OVOMORPHS)
            )
            .isEmpty();
    }

    /**
     * Whether a captive breeder's tiny reserve bank ({@link #CAPTIVE_RESERVE_EGG_CAP}) still has room.
     * <p>
     * \u26a0 Unused since Oct 3: that bank lived in the inhibitor's personal claim, and captives no longer hold one.
     * Kept because it is public and harmless.
     * </p>
     */
    public static boolean hasCaptiveReserveCapacity(EggLayer eggLayer, HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            variant = eggLayer.getVariant();
        }
        var type = Ovomorph.getType(variant, false);
        return type != null && location.localReserves().getCount(type) < CAPTIVE_RESERVE_EGG_CAP;
    }

    private EggLayingSensors() {
        throw new UnsupportedOperationException();
    }
}
