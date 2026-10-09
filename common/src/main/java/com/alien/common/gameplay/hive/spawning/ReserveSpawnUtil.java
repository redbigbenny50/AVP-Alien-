package com.alien.common.gameplay.hive.spawning;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.carrier.Carrier;
import net.minecraft.world.entity.Entity;

public final class ReserveSpawnUtil {

    private ReserveSpawnUtil() {}

    public static void markSpawnedFromReserves(Entity entity) {
        if (entity instanceof Alien alien) {
            alien.getMoltingManager().skipToFullMaturity();
        }
        if (entity instanceof Carrier carrier) {
            carrier.queueReserveFacehuggerPayload();
        }
    }

    /**
     * ⭐⭐⭐ FINALIZE A MEMBER THE HIVE HAS *ALREADY PAID FOR*. USE THIS INSTEAD OF CALLING {@code mob.finalizeSpawn(...)}
     * DIRECTLY ANYWHERE A COMPOSITION WAS DRAINED FIRST.
     * <p>
     * ⚠⚠⚠ EVERY PARTY IN THE MOD WAS COSTING THE HIVE TWICE WHAT IT FIELDED. The dispatchers drain the bank up front
     * (`drainComposition` → `reserves.trySpawn(type)`) and then spawn each member AT A VENT — which is inside the
     * location's own claimed chunk. {@code Alien.finalizeSpawn} debits the bank for ANY alien spawning in a claimed
     * chunk that still holds its type, so it charged a SECOND unit for a member already bought. Attack, surface,
     * host-hunt, biomass-hunt and internal-host parties, carve crews and raid convoys were all doing it. That is the
     * drain behind "no free drones, nothing in reserve" — the hive was paying double for every party it ever sent and
     * then could not staff its own construction.
     * </p>
     * <p>
     * ⚠⚠ AND IT DOUBLE-MARKED. {@code finalizeSpawn} calls {@link #markSpawnedFromReserves} when it debits, and every
     * one of those call sites called it again by hand — so each party CARRIER queued
     * {@code queueReserveFacehuggerPayload()} twice, TWELVE facehuggers instead of six.
     * </p>
     * <p>
     * ⭐ THE DEBIT IS MEASURED, NOT PREDICTED — same idiom as {@code HiveLoadedSpawner.trySpawnFromReserves}, and for
     * the same reasons: the chunk may belong to a DIFFERENT location than the one that paid, and a brood wildcard draw
     * does not match the per-type test finalizeSpawn uses. Read the bank either side, refund exactly what it took, and
     * mark exactly once whichever way it went.
     * </p>
     */
    public static void finalizePrepaidSpawn(
        net.minecraft.server.level.ServerLevel level,
        net.minecraft.world.entity.Mob mob,
        net.minecraft.core.BlockPos spawnPos
    ) {
        var locationAtPos = com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.getByChunk(
            level.dimension(),
            new net.minecraft.world.level.ChunkPos(spawnPos)
        );
        var bankBefore = locationAtPos == null ? -1 : locationAtPos.localReserves().getCount();

        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null);

        if (locationAtPos != null && locationAtPos.localReserves().getCount() < bankBefore) {
            // It charged us again for a member the composition already withdrew. Put it back — and do NOT mark,
            // because finalizeSpawn already did when it debited.
            locationAtPos.localReserves().addReturningMember(mob.getType(), 1);
            return;
        }

        markSpawnedFromReserves(mob);
    }
}
