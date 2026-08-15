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
                ovipositor.setYRot(
                    empress.getYRot()
                        + com.alien.common.gameplay.entity.living.alien.xenomorph.queen.OvipositorManager.OVIPOSITOR_YAW_OFFSET_DEGREES
                ); // keep the model offset every tick
                ovipositor.setXRot(empress.getXRot());
                // Body rotation.
                ovipositor.yBodyRot = empress.yBodyRot
                    + com.alien.common.gameplay.entity.living.alien.xenomorph.queen.OvipositorManager.OVIPOSITOR_YAW_OFFSET_DEGREES; // sack
                                                                                                                                     // model
                                                                                                                                     // offset
                                                                                                                                     // -
                                                                                                                                     // one
                                                                                                                                     // shared
                                                                                                                                     // dial
                // Head rotation.
                ovipositor.yHeadRot = empress.yHeadRot
                    + com.alien.common.gameplay.entity.living.alien.xenomorph.queen.OvipositorManager.OVIPOSITOR_YAW_OFFSET_DEGREES;
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

    /**
     * Where a laid egg appears, as (LATERAL, VERTICAL, FORWARD) from her body facing.
     * <p>
     * Was {@code (6, 0, 2.5)} - the queen's numbers, copied verbatim exactly like the ride offset in
     * {@code Empress.positionRider} was.
     * <p>
     * CORRECTED EMPIRICALLY, from markers he placed in-world: gold where the queen values put the egg, diamond where my
     * first correction put it. The diamond landed FURTHER from the sack's exit than the gold, so the offset had to move
     * the OPPOSITE way - these are the queen values shifted by the same magnitude in the other direction.
     * <p>
     * My reasoning for the first attempt was that the egg offset and the ride offset are both measured from the royal,
     * so shifting the egg by however much the ride shifted would keep the egg in the same spot ON the sack. The
     * in-world result says otherwise, which means the exit does NOT sit at the same place on her sack as it does on the
     * queen's - her sack is longer (tail tip 247.1u against the queen's 190.8u) and reshaped.
     * <p>
     * So this is a measured correction, not a derived one, and it is only as good as the two markers. Naming the exit
     * bone would settle it exactly, the way queenattachcube settled the ride offset in a single pass.
     */
    private static final double EGG_LAY_LATERAL = 8.9253;

    private static final double EGG_LAY_VERTICAL = 0.0;

    private static final double EGG_LAY_FORWARD = 3.026;

    public Vec3 getEggLayingPosition() {
        return EntityUtil.getRelativePosition(empress, EGG_LAY_LATERAL, EGG_LAY_VERTICAL, EGG_LAY_FORWARD);
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
            ovipositor.moveTo(
                empress.position(),
                empress.getYRot()
                    + com.alien.common.gameplay.entity.living.alien.xenomorph.queen.OvipositorManager.OVIPOSITOR_YAW_OFFSET_DEGREES,
                empress.getXRot()
            );
            ovipositor.startRiding(empress, true);

            // Body rotation.
            ovipositor.yBodyRot = empress.yBodyRot
                + com.alien.common.gameplay.entity.living.alien.xenomorph.queen.OvipositorManager.OVIPOSITOR_YAW_OFFSET_DEGREES; // sack
                                                                                                                                 // model
                                                                                                                                 // offset
                                                                                                                                 // -
                                                                                                                                 // one
                                                                                                                                 // shared
                                                                                                                                 // dial
            // Head rotation.
            ovipositor.yHeadRot = empress.yHeadRot;

            empress.level().addFreshEntity(ovipositor);

            // Fresh sitting, fresh bar - see Empress.resetDisturbance.
            empress.resetDisturbance();
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
        // ⭐ SAME SEAT RULE AS THE QUEEN. An empress who has not been seated as this location's founder has not
        // established here yet, and an unseated royal laying on someone else's (or nobody's) resin is the same
        // bug in a bigger body - she just has a higher bar to clear otherwise, which is why it never showed.
        var founderId = location.founderId();
        if (founderId == null || !founderId.equals(empress.getUUID())) {
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

    /**
     * Support probes MEASURED, no longer the queen's verbatim (they were 1.5/2.5, -2/2, 5.7/8.25, 0/7 - her exact
     * four). Correction applied: the SEAT-CUBE DELTA (+2.9253 lateral, +0.526 forward blocks), the same rule that fixed
     * the ride offset and the egg-laying position, and it is verified three independent ways: (1) his in-world
     * gold/diamond egg markers settled the sign and magnitude; (2) the ride seat (queenattachcube) moved by exactly
     * this in the geo; (3) measured differentially for THIS fix, the throne and outer support families' ground feet
     * moved by exactly (+2.92, +0.53) blocks between ovipositor.geo and empress_ovipositor.geo - the support structure
     * shifted RIGIDLY with the model re-centering, so the queen's probe pattern translated by the seat delta lands on
     * her actual supports.
     * <p>
     * The REAR support family alone reshaped beyond the rigid move (her sack is longer, 247.1u vs 190.8u tail tip).
     * Measured empress rear feet, model units /16, for a one-pass in-game fine-tune of the far/back probes if they ever
     * misbehave: left rear (3.92, 0.37), right rear (0.15, 4.74).
     */
    private boolean canOvipositorFit() {
        var leftBottomSupport = EntityUtil.getRelativePosition(empress, 4.4253, 0, 3.026);
        var rightBottomSupport = EntityUtil.getRelativePosition(empress, 0.9253, 0, 2.526);
        var farLeftBottomSupport = EntityUtil.getRelativePosition(empress, 8.6253, 0, 8.776);
        var backBottomSupport = EntityUtil.getRelativePosition(empress, 2.9253, 0, 7.526);

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
