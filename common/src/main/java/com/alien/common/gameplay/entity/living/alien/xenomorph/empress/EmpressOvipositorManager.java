package com.alien.common.gameplay.entity.living.alien.xenomorph.empress;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.EntityUtil;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import com.blib.api.common.time.v1.Cooldown;
import com.just.core.functional.option.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

public class EmpressOvipositorManager implements NBTSerializable {

    private final Cooldown ovipositorCreationCooldown;

    private final Empress empress;

    private boolean hadOvipositorLastTick;

    public EmpressOvipositorManager(Empress empress) {
        this.ovipositorCreationCooldown = Cooldown.withCooldownTime("ovipositorCreationCooldownInTicks", Duration.ofMinutes(1));
        this.empress = empress;
    }

    public void tick() {
        if (empress.level().isClientSide) {
            return;
        }

        ovipositorCreationCooldown.tick();

        var hasOvipositor = hasOvipositor();

        if (!hasOvipositor && hadOvipositorLastTick) {
            ovipositorCreationCooldown.reset();
        }

        this.hadOvipositorLastTick = hasOvipositor;

        // Exile is applied to the LOCATION, which can happen while she is unloaded - so the entity-side flag is
        // latched here, the first time she ticks at a remnant. Once latched it is permanent and travels with her
        // even if she wanders off the ruin.
        if (!empress.isExiled()) {
            var location = currentLocation();
            if (location != null && location.isExiled()) {
                empress.exile();
                return;
            }
        } else if (hasOvipositor) {
            // Defensive: an exiled empress must never be wearing one.
            abandonOvipositor();
            return;
        }

        if (hasOvipositor) {
            getOvipositor().ifSome(ovipositor -> {
                ovipositor.setYRot(empress.getYRot());
                ovipositor.setXRot(empress.getXRot());
                // Body rotation.
                ovipositor.yBodyRot = empress.yBodyRot;
                // Head rotation.
                ovipositor.yHeadRot = empress.yHeadRot;
            });
            return;
        }

        if (!canCreateOvipositor()) {
            return;
        }

        if (!tryPayCreationCost()) {
            return;
        }

        createOvipositor();
        ovipositorCreationCooldown.reset();
    }

    public Vec3 getEggLayingPosition() {
        return EntityUtil.getRelativePosition(empress, 6, 0, 2.5);
    }

    public @Nullable Ovipositor getOvipositorOrNull() {
        return (Ovipositor) empress.getPassengers()
            .stream()
            .filter(passenger -> passenger.getType() == AlienEntityTypes.EMPRESS_OVIPOSITOR.get())
            .findFirst()
            .orElse(null);
    }

    public Option<Ovipositor> getOvipositor() {
        return Option.ofNullable(getOvipositorOrNull());
    }

    public boolean hasOvipositor() {
        return getOvipositorOrNull() != null;
    }

    public void abandonOvipositor() {
        getOvipositor().ifSome(ovipositor -> {
            ovipositor.stopRiding();
            ovipositor.discard();
        });
        for (var passenger : List.copyOf(empress.getPassengers())) {
            if (passenger.getType() == AlienEntityTypes.EMPRESS_OVIPOSITOR.get()) {
                passenger.stopRiding();
                passenger.discard();
            }
        }
        if (!empress.level().isClientSide) {
            var area = empress.getBoundingBox().inflate(8.0D);
            for (var ovipositor : empress.level().getEntitiesOfClass(Ovipositor.class, area)) {
                if (ovipositor.getVehicle() == empress || ovipositor.distanceToSqr(empress) <= 16.0D) {
                    ovipositor.stopRiding();
                    ovipositor.discard();
                }
            }
        }
    }

    private void createOvipositor() {
        var ovipositor = AlienEntityTypes.EMPRESS_OVIPOSITOR.get().create(empress.level());

        if (ovipositor != null) {
            ovipositor.moveTo(empress.position(), empress.getYRot(), empress.getXRot());
            ovipositor.startRiding(empress, true);

            // Body rotation.
            ovipositor.yBodyRot = empress.yBodyRot;
            // Head rotation.
            ovipositor.yHeadRot = empress.yHeadRot;

            empress.level().addFreshEntity(ovipositor);
        }
    }

    private boolean canCreateOvipositor() {
        return !empress.isExiled()
            && empress.getTarget() == null
            && AlienVariantTypes.getFor(empress.getVariant()).canReproduce()
            && !empress.isPoisoned()
            && !ovipositorCreationCooldown.isActive()
            && isStandingOnVariantResin()
            && hasEnoughLocalSupport()
            && canOvipositorFit();
    }

    private boolean isStandingOnVariantResin() {
        return empress.level()
            .getBlockState(empress.blockPosition().below())
            .is(AlienVariantTypes.getFor(empress.getVariant()).resinBlockTag());
    }

    private boolean hasEnoughLocalSupport() {
        var location = currentLocation();
        if (location == null || !location.isAlive()) {
            return false;
        }
        if (!isNearHiveCenter(location)) {
            return false;
        }
        var bossBar = location.bossBar();
        if (bossBar != null && bossBar.isAngry()) {
            return false;
        }
        var loadedXenoCount = location.loadedMembersByType()
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().is(AlienEntityTypeTags.XENOMORPHS))
            .mapToInt(entry -> entry.getValue().size())
            .sum();
        return loadedXenoCount > 2;
    }

    private boolean tryPayCreationCost() {
        var location = currentLocation();
        if (location == null || !location.isAlive()) {
            return false;
        }

        var cost = HiveLocationRegistry.INSTANCE.config().ovipositorCreationBiomassCost();
        if (cost <= 0) {
            return true;
        }
        if (location.biomass() < cost) {
            return false;
        }

        location.setBiomass(location.biomass() - cost);
        return true;
    }

    private @Nullable HiveLocation currentLocation() {
        return HiveLocationRegistry.INSTANCE.getByChunk(empress.level().dimension(), new ChunkPos(empress.blockPosition()));
    }

    private boolean isNearHiveCenter(HiveLocation location) {
        var centerChunk = new ChunkPos(location.centerPos());
        var empressChunk = new ChunkPos(empress.blockPosition());
        var dx = Math.abs(centerChunk.x - empressChunk.x);
        var dz = Math.abs(centerChunk.z - empressChunk.z);
        return Math.max(dx, dz) <= 1;
    }

    private boolean canOvipositorFit() {
        var leftBottomSupport = EntityUtil.getRelativePosition(empress, 1.5, 0, 2.5);
        var rightBottomSupport = EntityUtil.getRelativePosition(empress, -2, 0, 2);
        var farLeftBottomSupport = EntityUtil.getRelativePosition(empress, 5.7, 0, 8.25);
        var backBottomSupport = EntityUtil.getRelativePosition(empress, 0, 0, 7);

        return canOvipositorSupportExistAt(leftBottomSupport)
            && canOvipositorSupportExistAt(rightBottomSupport)
            && canOvipositorSupportExistAt(farLeftBottomSupport)
            && canOvipositorSupportExistAt(backBottomSupport)
            && isEggLayingPositionValid();
    }

    private boolean isEggLayingPositionValid() {
        var eggLayingPosition = getEggLayingPosition();
        var blockState = empress.level().getBlockState(BlockPos.containing(eggLayingPosition));
        var isClearForEgg = blockState.isAir() || blockState.canBeReplaced();

        return isClearForEgg && EntityUtil.canMobSeeBlock(empress, eggLayingPosition);
    }

    private boolean canOvipositorSupportExistAt(Vec3 vec3) {
        var blockPos = BlockPos.containing(vec3);

        var isSupported = false;
        var stepsDown = 0;

        while (!isSupported && stepsDown < 4) {
            blockPos = blockPos.below();
            var blockState = empress.level().getBlockState(blockPos);

            var aboveBlockState = empress.level().getBlockState(blockPos.above());
            isSupported = (aboveBlockState.isAir() || aboveBlockState.canBeReplaced())
                && blockState.is(AlienVariantTypes.getFor(empress.getVariant()).resinBlockTag());

            stepsDown++;
        }

        return isSupported && EntityUtil.canMobSeeBlock(empress, vec3);
    }

    @Override
    public void load(CompoundTag compoundTag) {
        ovipositorCreationCooldown.load(compoundTag);
    }

    @Override
    public void save(CompoundTag compoundTag) {
        ovipositorCreationCooldown.save(compoundTag);
    }
}
