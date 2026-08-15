package com.alien.common.gameplay.hive.lifecycle;

import com.alien.Alien;
import com.alien.common.gameplay.hive.id.HiveLocationIds;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.HiveLoadedSpawner;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;

/**
 * END-STYLE royal succession - [stated] "when a queen dies a praetorian spawns out of the reserves. and persists... the
 * hive should persist as long as it has members they would keep coming out especially if under attack. once the reserve
 * is empty the hive dies."
 * <p>
 * On any QUEENS-tag death (queen or empress) belonging to an end-style hive, a REGENT PRAETORIAN rises at the death
 * spot: drawn identity-intact from the bank when one is stocked, MATERIALIZED FRESH when none is - the hive always
 * produces its regent, so a player who never banked a praetorian is not silently left regent-less. She persists
 * (persistence-required, like every End member), holds the fortress on its stored strength, and the player restores the
 * crown by force-evolving her (scourge potion is the harbinger route; the queen route is the royal jelly ladder) or by
 * summoning a new queen, who takes over. The hive itself dies only when the bank runs dry and the cull clock
 * (EndHiveTickTask) runs out.
 */
public final class EndRegent {

    private EndRegent() {}

    /** Called from the entity base's die() for QUEENS-tag deaths in end-style dimensions. */
    public static void onRoyalDied(ServerLevel level, com.alien.common.gameplay.entity.living.alien.Alien royal) {
        var location = locationOf(level, royal);
        if (location == null || !location.isEndStyleHive() || !location.isAlive()) {
            return;
        }

        var praetorianType = praetorianTypeFor(location);
        if (praetorianType == null) {
            return;
        }

        var pos = royal.blockPosition();

        // Bank first: the exact praetorian the player supplied walks out of the ducts to take the throne room.
        var regent = HiveLoadedSpawner.trySpawnIdentityReserve(level, location, praetorianType, pos);
        if (regent == null) {
            // None stocked - the hive PRODUCES its regent. MOB_SUMMONED runs finalizeSpawn, which auto-joins her to
            // the location the death happened in, exactly like every other arrival route.
            var fresh = praetorianType.create(level);
            if (fresh == null) {
                return;
            }
            fresh.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, royal.getYRot(), 0.0F);
            if (fresh instanceof net.minecraft.world.entity.Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
            }
            level.addFreshEntity(fresh);
            regent = fresh;
        } else {
            ReserveSpawnUtil.markSpawnedFromReserves(regent);
        }

        if (regent instanceof com.alien.common.gameplay.entity.living.alien.Alien alienRegent) {
            alienRegent.setPersistenceRequired();
        }

        Alien.LOGGER.info(
            "End hive at {}: the {} has fallen - a regent praetorian rises at {}. The fortress holds on its bank.",
            location.centerPos(),
            royal.getType().is(com.alien.common.registry.tag.AlienEntityTypeTags.EMPRESSES) ? "empress" : "queen",
            pos
        );
    }

    private static HiveLocation locationOf(ServerLevel level, com.alien.common.gameplay.entity.living.alien.Alien royal) {
        // Membership is authoritative; the chunk she died in is the fallback (a royal fighting at the territory edge
        // is still this hive's royal).
        for (var factionId : Alien.MOD.factions().getFactionIds(royal.getUUID())) {
            if (HiveLocationIds.isHiveLocationId(factionId)) {
                var location = HiveLocationRegistry.INSTANCE.get(
                    com.alien.common.gameplay.hive.id.HiveLocationId.of(factionId)
                );
                if (location != null) {
                    return location;
                }
            }
        }
        return HiveLocationRegistry.INSTANCE.getByChunk(level.dimension(), new ChunkPos(royal.blockPosition()));
    }

    private static EntityType<?> praetorianTypeFor(HiveLocation location) {
        var variant = location.lineageVariantOrNull();
        if (variant == null) {
            return null;
        }
        return switch (variant) {
            case NORMAL -> com.alien.common.registry.init.AlienEntityTypes.PRAETORIAN.get();
            case NETHER -> com.alien.common.registry.init.AlienEntityTypes.NETHER_PRAETORIAN.get();
            case ABERRANT -> com.alien.common.registry.init.AlienEntityTypes.ABERRANT_PRAETORIAN.get();
            case IRRADIATED -> com.alien.common.registry.init.AlienEntityTypes.IRRADIATED_PRAETORIAN.get();
        };
    }
}
