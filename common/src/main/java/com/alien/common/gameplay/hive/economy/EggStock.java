package com.alien.common.gameplay.hive.economy;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.ovomorph.Ovomorph;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * The stored eggs in the hive's egg chambers back the ovomorph RESERVE the same way the jelly vats back the jelly
 * bank: when a unit purchase needs an ovomorph input and the reserve has none, a rooted egg is consumed from a
 * nursery chamber to cover it. Eggs around the queen are her clutch and are never taken - only chamber stock is
 * spent, which is exactly what makes raiding a hive's nurseries starve its growth.
 */
public final class EggStock {

    private EggStock() {}

    /**
     * Covers a purchase-input shortfall of {@code type} (an ovomorph variant) by consuming rooted eggs from the
     * hive's egg chambers into the reserve. No-op for non-egg inputs, absent shortfalls, or unloaded chambers.
     */
    public static void coverInputShortfall(MinecraftServer server, HiveLocation location, EntityType<?> type, int needed) {
        if (!type.is(AlienEntityTypeTags.OVOMORPHS)) {
            return;
        }
        int missing = needed - location.localReserves().getCount(type);
        if (missing <= 0) {
            return;
        }
        var level = server.getLevel(location.dimension());
        if (level == null) {
            return;
        }
        int floorY = location.hiveFloorY();
        int covered = 0;
        for (var entry : location.structurePieceByChunk().entrySet()) {
            if (covered >= missing || !entry.getValue().contains("chamber_egg")) {
                continue;
            }
            ChunkPos chamber = entry.getKey();
            if (!level.isLoaded(chamber.getWorldPosition())) {
                continue;
            }
            var box = new AABB(
                    chamber.getMinBlockX(), floorY - 1, chamber.getMinBlockZ(),
                    chamber.getMaxBlockX() + 1, floorY + 6, chamber.getMaxBlockZ() + 1
            );
            for (var egg : level.getEntitiesOfClass(Ovomorph.class, box,
                    e -> e.getType() == type && e.isRooted.get() && e.isAlive())) {
                if (covered >= missing) {
                    break;
                }
                if (location.localReserves().addReturningMember(type, 1)) {
                    egg.discard();
                    covered++;
                }
            }
        }
        if (covered > 0) {
            Alien.LOGGER.info("Hive at {}: consumed {} stored egg(s) from the nurseries to fund a purchase.",
                    location.centerPos(), covered);
        }
    }
}