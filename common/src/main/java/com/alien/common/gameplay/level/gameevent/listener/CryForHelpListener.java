package com.alien.common.gameplay.level.gameevent.listener;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.block.entity.resin.vent.ResinVentBlockEntity;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.spawning.ReserveSpawnUtil;
import com.alien.common.registry.tag.AlienBlockTags;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.spatial.v1.block.BlockPosUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Reacts to a xenomorph's "cry for help" game event by summoning a defender at a vent. In hive the responding hive is
 * the {@link HiveLocation} bound to the vent's chunk; defender supply comes from the location's local reserves.
 */
public class CryForHelpListener implements GameEventListener {

    private static final int MAXIMUM_SUMMONED_XENOMORPHS_PER_LOCATION = 60;

    private final PositionSource positionSource;

    public CryForHelpListener(PositionSource positionSource) {
        this.positionSource = positionSource;
    }

    @Override
    public @NotNull PositionSource getListenerSource() {
        return positionSource;
    }

    @Override
    public @NotNull GameEventListener.DeliveryMode getDeliveryMode() {
        return DeliveryMode.BY_DISTANCE;
    }

    @Override
    public int getListenerRadius() {
        return 16;
    }

    @Override
    public boolean handleGameEvent(
        @NotNull ServerLevel serverLevel,
        @NotNull Holder<GameEvent> holder,
        @NotNull GameEvent.Context context,
        @NotNull Vec3 vec3
    ) {
        var sourceEntity = context.sourceEntity();

        if (
            sourceEntity == null
                // If there is no alien variant type for given source entity OR if there is a cry-for-help event
                // type mismatch...
                || AlienVariantTypes.getFor(sourceEntity)
                    .isNoneOr(alienVariantType -> !holder.is(alienVariantType.cryForHelpEvent()))
        ) {
            return false;
        }

        var blockPos = positionSource.getPosition(serverLevel)
            .map(BlockPos::containing)
            .orElse(null);

        if (blockPos == null) {
            return false;
        }

        var blockEntity = serverLevel.getBlockEntity(blockPos);

        if (
            !(blockEntity instanceof ResinVentBlockEntity vent)
                || vent.getAlienSpawnCooldown().isActive()
        ) {
            return false;
        }

        var location = vent.getBoundLocation();
        if (location == null || !location.isAlive()) {
            return false;
        }
        if (location.isInCombatRespite()) {
            return false;
        }

        // Strain gate: a vent only answers cries from ITS OWN strain. Without this, an alien crying for help next
        // to a RIVAL strain's hive would summon that hive's defenders to its rescue - strains are always hostile to
        // one another and never come to each other's aid.
        var crierVariantType = AlienVariantTypes.getForOrNull(sourceEntity);
        if (crierVariantType == null || !java.util.Objects.equals(location.lineageVariantOrNull(), crierVariantType.variant())) {
            return false;
        }

        // Cap concurrent helpers per location.
        var loadedXenomorphCount = location.loadedMembersByType()
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().is(AlienEntityTypeTags.XENOMORPHS))
            .mapToInt(entry -> entry.getValue().size())
            .sum();
        if (loadedXenomorphCount >= MAXIMUM_SUMMONED_XENOMORPHS_PER_LOCATION) {
            return false;
        }

        var basePos = vent.getBlockPos();
        var freeSpaces = BlockPosUtil.getNeighborsMatching(serverLevel, basePos, blockState -> blockState.is(AlienBlockTags.RESIN_WEBS));

        var spawnPos = freeSpaces.isEmpty()
            ? null
            : freeSpaces.get(sourceEntity.getRandom().nextInt(freeSpaces.size()));

        if (spawnPos == null) {
            return false;
        }

        // Pick a defender type from the location's local reserves.
        var localReserveTypes = location.localReserves()
            .getAvailableEntityTypes()
            .stream()
            .filter(type -> type.is(AlienEntityTypeTags.ANSWERS_XENOMORPH_CRIES_FOR_HELP))
            .toList();

        if (localReserveTypes.isEmpty()) {
            return false;
        }

        return trySpawnFromReserves(serverLevel, location, sourceEntity, spawnPos, localReserveTypes, vent);
    }

    private static boolean trySpawnFromReserves(
        ServerLevel level,
        HiveLocation location,
        Entity sourceEntity,
        BlockPos spawnPos,
        List<EntityType<?>> reserveTypes,
        ResinVentBlockEntity vent
    ) {
        var randomType = reserveTypes.get(sourceEntity.getRandom().nextInt(reserveTypes.size()));
        if (!location.localReserves().canSpawn(randomType)) {
            return false;
        }

        var summoned = randomType.spawn(level, spawnPos, MobSpawnType.MOB_SUMMONED);
        if (summoned == null) {
            return false;
        }

        ReserveSpawnUtil.markSpawnedFromReserves(summoned);
        location.localReserves().trySpawn(randomType);
        vent.getAlienSpawnCooldown().reset();
        retargetIfPossible(sourceEntity, summoned);
        return true;
    }

    private static void retargetIfPossible(Entity sourceEntity, Entity summoned) {
        if (sourceEntity instanceof Mob sourceMob && summoned instanceof Mob summonedMob) {
            summonedMob.setTarget(sourceMob.getTarget());
        }
    }
}
