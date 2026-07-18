package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.entity.living.alien.ovipositor.Ovipositor;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.AlienEntityTypes;
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

public class OvipositorManager implements NBTSerializable {

    private final Cooldown ovipositorCreationCooldown;

    private final Queen queen;

    private boolean hadOvipositorLastTick;

    public OvipositorManager(Queen queen) {
        this.ovipositorCreationCooldown = Cooldown.withCooldownTime("ovipositorCreationCooldownInTicks", Duration.ofMinutes(1));
        this.queen = queen;
    }

    public void tick() {
        if (queen.level().isClientSide) {
            return;
        }

        ovipositorCreationCooldown.tick();

        var hasOvipositor = hasOvipositor();

        if (!hasOvipositor && hadOvipositorLastTick) {
            ovipositorCreationCooldown.reset();
        }

        this.hadOvipositorLastTick = hasOvipositor;

        if (hasOvipositor) {
            // Released: a chained eggsack on a queen who is no longer inhibited (inhibitor pried off) is dropped —
            // she's
            // free again, not a captive breeder.
            if (!queen.isInhibited() && getOvipositor().isSomeAnd(Ovipositor::isChainedEggsack)) {
                getOvipositor().ifSome(ovipositor -> ovipositor.discard());
                return;
            }

            // Captured-queen eggsack handling. Reads the AUTHORITATIVE anchor count (isFullyBound), not the synced
            // isContained(): the bind-chain-count sync key isn't persisted, so just after a reload it reads 0 for one
            // tick (this manager ticks before the bind manager re-syncs it). The persisted anchors are correct
            // immediately, so this avoids tearing the eggsack down on every reload.
            if (queen.isInhibited()) {
                // Inhibited but not contained (chains stripped) — "inhibited but loose" produces nothing per design.
                if (!queen.getBindManager().isFullyBound()) {
                    getOvipositor().ifSome(ovipositor -> ovipositor.discard());
                    return;
                }

                // Inhibited AND contained: she belongs on the CHAINED eggsack. If she's still riding her founding
                // ovipositor (captured after she'd already founded), drop it so canCreateChainedEggsack below grows the
                // chained one in its place — otherwise she stays stuck on the founding eggsack forever.
                if (!getOvipositor().isSomeAnd(Ovipositor::isChainedEggsack)) {
                    getOvipositor().ifSome(ovipositor -> ovipositor.discard());
                    return;
                }
            }
            getOvipositor().ifSome(ovipositor -> {
                ovipositor.setYRot(queen.getYRot());
                ovipositor.setXRot(queen.getXRot());
                // Body rotation.
                ovipositor.yBodyRot = queen.yBodyRot;
                // Head rotation.
                ovipositor.yHeadRot = queen.yHeadRot;
            });
            return;
        }

        // Contained-captive path: an inhibited, contained queen (four capture chains now; a titanium enclosure
        // later) grows a chained eggsack instead of a founding ovipositor — no resin, no biomass, no founding chamber.
        // The existing egg-laying GOAP lays from it afterwards (her genome rides the eggs). Runs before the founding
        // prep + normal gate below, both of which are meaningless for a captive and are disabled for inhibited queens.
        if (canCreateChainedEggsack()) {
            createChainedEggsack();
            ovipositorCreationCooldown.reset();
            return;
        }

        // Founding preparation: a founding queen with a full biomass tank, standing near her center, prepares her
        // chamber BEFORE the creation gate - carve the room and stamp the resin floor. This must happen before
        // canCreateOvipositor() because that gate REQUIRES resin underfoot and clear space, which the prep provides.
        // Without this the queen deadlocks: she can't create (no resin/no room) but the resin/room only came from
        // create. Prep runs once; afterwards the gates pass and the normal flow below creates the ovipositor.
        prepareFoundingChamberIfNeeded();

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
     * If the queen is founding (location founded, not yet reproductive), has filled her founding biomass tank, and is
     * near her center, carve the founding chamber and stamp the resin floor - ONCE. This prepares the space the
     * ovipositor-creation gate requires (resin underfoot, clear room). Spends the resin-floor share of the biomass.
     * No-op if already prepared (resin already underfoot), not founding, not full, or not near center.
     */
    private void prepareFoundingChamberIfNeeded() {
        var location = currentLocation();
        if (
            location == null
                || location.founderId() == null
                || location.reproductiveEstablished()
                || !isNearHiveCenter(location)
        ) {
            return;
        }
        // Already prepared? (resin already under her - don't re-carve/re-stamp every tick)
        if (isStandingOnVariantResin()) {
            return;
        }
        // Tank must be full (the founding target) before committing the prep spend.
        var target = com.alien.common.gameplay.hive.growth.BiomassIncome.foundingBiomassTarget(
            HiveLocationRegistry.INSTANCE.config()
        );
        if (location.biomass() < target) {
            return;
        }

        carveFoundingChamber();
        stampFoundingResinFloor();

        // Place the queen at the chunk center on top of the fresh bone floor, so she's standing on resin at the middle
        // of the carved chamber. This makes the resin / fit gates pass deterministically regardless of where exactly
        // she
        // wandered within the center area. (A light position correction - full navigate-to-center is Option B.)
        var centerChunk = new ChunkPos(location.centerPos());
        queen.moveTo(
            centerChunk.getMiddleBlockX() + 0.5,
            location.hiveFloorY() + 1,
            centerChunk.getMiddleBlockZ() + 0.5,
            queen.getYRot(),
            queen.getXRot()
        );
    }

    public Vec3 getEggLayingPosition() {
        return EntityUtil.getRelativePosition(queen, 6, 0, 2.5);
    }

    public @Nullable Ovipositor getOvipositorOrNull() {
        return (Ovipositor) queen.getPassengers()
            .stream()
            .filter(passenger -> passenger.getType() == AlienEntityTypes.OVIPOSITOR.get())
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
        for (var passenger : List.copyOf(queen.getPassengers())) {
            if (passenger.getType() == AlienEntityTypes.OVIPOSITOR.get()) {
                passenger.stopRiding();
                passenger.discard();
            }
        }
        if (!queen.level().isClientSide) {
            var area = queen.getBoundingBox().inflate(8.0D);
            for (var ovipositor : queen.level().getEntitiesOfClass(Ovipositor.class, area)) {
                if (ovipositor.getVehicle() == queen || ovipositor.distanceToSqr(queen) <= 16.0D) {
                    ovipositor.stopRiding();
                    ovipositor.discard();
                }
            }
        }
    }

    /**
     * Debug-only: runs each ovipositor-creation gate independently and returns a human-readable pass/fail report. Used
     * by {@code /avp_alien debug hive inspect_ovipositor} to pinpoint exactly which condition is blocking egg-laying,
     * instead of the single opaque "unsatisfied" the GOAP inspector shows. Does not create anything or pay any cost.
     */
    public String debugReport() {
        if (hasOvipositor()) {
            return "Queen ALREADY HAS an ovipositor (should be laying).";
        }
        var sb = new StringBuilder("Ovipositor gates for nearest queen:\n");
        sb.append("  no target:          ").append(queen.getTarget() == null).append('\n');
        sb.append("  variant canReproduce: ").append(AlienVariantTypes.getFor(queen.getVariant()).canReproduce()).append('\n');
        sb.append("  cooldown ready:     ").append(!ovipositorCreationCooldown.isActive()).append('\n');
        sb.append("  on variant resin:   ").append(isStandingOnVariantResin()).append('\n');
        sb.append("  suitable location:  ").append(hasSuitableHiveLocation()).append('\n');
        sb.append("  ovipositor fits:    ").append(canOvipositorFit()).append('\n');
        sb.append("  => canCreate:       ").append(canCreateOvipositor()).append('\n');
        var loc = currentLocation();
        var cost = HiveLocationRegistry.INSTANCE.config().ovipositorCreationBiomassCost();
        var biomass = loc != null ? loc.biomass() : -1;
        sb.append("  biomass: ")
            .append(biomass)
            .append(" / cost ")
            .append(cost)
            .append(biomass >= cost ? "  (CAN PAY)" : "  (TOO POOR - this is the blocker)");
        return sb.toString();
    }

    private void createOvipositor() {
        // The founding chamber and resin floor were prepared before the gate (see prepareFoundingChamberIfNeeded), so
        // by here the space is carved and resin is underfoot. This just creates the ovipositor entity and finalizes.
        var ovipositor = AlienEntityTypes.OVIPOSITOR.get().create(queen.level());

        if (ovipositor != null) {
            ovipositor.moveTo(queen.position(), queen.getYRot(), queen.getXRot());
            ovipositor.startRiding(queen, true);

            // Body rotation.
            ovipositor.yBodyRot = queen.yBodyRot;
            // Head rotation.
            ovipositor.yHeadRot = queen.yHeadRot;

            queen.level().addFreshEntity(ovipositor);

            // The hive is now reproductive - founding mode ends. Cap reverts to the normal formula and the location may
            // resume expansion (claims), resin spread, and spawning. See founding-priority design.
            var loc = currentLocation();
            if (loc != null) {
                loc.setReproductiveEstablished(true);
            }
        }
    }

    /**
     * Carves the founding chamber: clears the center chunk's slab band ABOVE the floor to air (solids and fluids alike,
     * so water/lava drain), leaving a hollow room for the eggsack. The bottom floor layer is left for
     * {@link #stampFoundingResinFloor()} to surface with resin, so she doesn't fall through. Instant block-deletion for
     * now; animated excavation comes with the structure system (same carve primitive). See carve-contract design.
     */
    private void carveFoundingChamber() {
        var location = currentLocation();
        if (location == null) {
            return;
        }
        var level = queen.level();
        var center = location.centerPos();
        var centerChunk = new ChunkPos(center);
        var minX = centerChunk.getMinBlockX();
        var minZ = centerChunk.getMinBlockZ();
        var maxX = centerChunk.getMaxBlockX();
        var maxZ = centerChunk.getMaxBlockZ();

        // Floor is the bottom of the slab band; clear from floor+1 up to the ceiling, leaving the floor layer intact.
        var floorY = location.hiveFloorY();
        var ceilingY = location.hiveCeilingY();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        var pos = new BlockPos.MutableBlockPos();
        for (var x = minX; x <= maxX; x++) {
            for (var z = minZ; z <= maxZ; z++) {
                for (var y = floorY + 1; y < ceilingY; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        // setBlock with flag 3 (update + notify) so fluids drain and lighting updates correctly.
                        level.setBlock(pos, air, 3);
                    }
                }
            }
        }
    }

    /**
     * Stamps a small resin floor patch centered on the queen's feet, in her variant's resin, spending the resin-floor
     * share of the founding biomass. Gives her ovipositor support points a valid resin surface (fixes the chronic "on
     * variant resin: false" for a queen who founded on bare stone). Instant stamp - no wandering, no partial floors.
     */
    private void stampFoundingResinFloor() {
        var location = currentLocation();
        if (location == null) {
            return;
        }
        var level = queen.level();
        // resin_bone is in NORMAL_RESIN, so it satisfies the "on variant resin" / support-point gates and counts as
        // spawnable resin. Used for the founding floor as a distinct, bone-like pad.
        var floorState = com.alien.common.registry.init.block.AlienResinBlocks.RESIN_BONE.get().defaultBlockState();

        // Anchor the disc to the CENTER CHUNK's middle at the slab floor Y (the row the carve left as the base), NOT
        // under the queen - so the floor is deterministic and aligned with the carved chamber regardless of exactly
        // where she's standing. FILL every cell in the circle (including air/gaps from drained fluids or caves) so the
        // chamber has a complete, hole-free floor for the ovipositor support points.
        var centerChunk = new ChunkPos(location.centerPos());
        var centerX = centerChunk.getMiddleBlockX();
        var centerZ = centerChunk.getMiddleBlockZ();
        var floorY = location.hiveFloorY();

        // Circular pad, radius 6 (12-block diameter). Circle = dx^2 + dz^2 <= radius^2. Sized to cover the ovipositor
        // support-point reach (~8 back / 6 side) so the "ovipositor fits" gate passes.
        var radius = 6;
        var radiusSq = radius * radius;
        var resinTag = AlienVariantTypes.getFor(queen.getVariant()).resinBlockTag();
        var pos = new BlockPos.MutableBlockPos();
        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue; // outside the circle
                }
                pos.set(centerX + dx, floorY, centerZ + dz);
                // Fill any cell that isn't already this variant's resin - convert solid ground AND fill air gaps, so
                // the
                // floor is complete. (Don't overwrite existing variant resin so we don't churn already-valid floor.)
                if (!level.getBlockState(pos).is(resinTag)) {
                    level.setBlock(pos, floorState, 3);
                }
            }
        }

        // Spend the resin-floor share of the founding biomass (mirrors the ovipositor cost so total founding cost is
        // 2x).
        var floorCost = HiveLocationRegistry.INSTANCE.config().ovipositorCreationBiomassCost();
        location.setBiomass(Math.max(0, location.biomass() - floorCost));
    }

    /**
     * Contained-captive eggsack gate: an inhibited, contained queen with no combat target may grow a chained eggsack.
     * Deliberately omits the founding gates (resin underfoot, hive-center, biomass) — a captive isn't founding; the
     * eggsack hangs from her restraints. Egg-laying capacity/spacing are still enforced downstream by the egg-laying
     * GOAP, which lays from her personal inhibited single-chunk location.
     */
    private boolean canCreateChainedEggsack() {
        return queen.isInhibited()
            && queen.getBindManager().isFullyBound()
            && queen.getTarget() == null
            && AlienVariantTypes.getFor(queen.getVariant()).canReproduce()
            && !ovipositorCreationCooldown.isActive();
    }

    /**
     * Creates the chained eggsack: the same ovipositor entity riding the queen, but minted free — no biomass spend and
     * no {@code reproductiveEstablished} flip, since a captive breeder is not founding a hive. The chained presentation
     * (geo + iron restraints) is a render-time choice driven by her contained+inhibited state.
     */
    private void createChainedEggsack() {
        var ovipositor = AlienEntityTypes.OVIPOSITOR.get().create(queen.level());
        if (ovipositor != null) {
            // Settle her facing authoritatively BEFORE attaching the eggsack: unify yRot / yBodyRot / yHeadRot to
            // one value so the eggsack (which copies her rotation) and the client-side contained-rotation lock
            // agree exactly. Without this, yRot and yBodyRot can differ by a few degrees at the capture instant
            // and the eggsack ends up locked a hair off from her body.
            var settledYaw = queen.yBodyRot;
            queen.setYRot(settledYaw);
            queen.yBodyRot = settledYaw;
            queen.yHeadRot = settledYaw;

            ovipositor.moveTo(queen.position(), settledYaw, queen.getXRot());
            ovipositor.startRiding(queen, true);
            ovipositor.yBodyRot = settledYaw;
            ovipositor.yHeadRot = settledYaw;
            // A captive breeder's eggsack must not vanish to far-away despawn while she's contained; the teardown above
            // is the only thing that removes it.
            ovipositor.setPersistenceRequired();
            ovipositor.setChainedEggsack(true);
            queen.level().addFreshEntity(ovipositor);
        }
    }

    private boolean canCreateOvipositor() {
        return !queen.isInhibited()
            && queen.getTarget() == null
            && AlienVariantTypes.getFor(queen.getVariant()).canReproduce()
            && !ovipositorCreationCooldown.isActive()
            && isStandingOnVariantResin()
            && hasSuitableHiveLocation()
            && canOvipositorFit();
    }

    private boolean isStandingOnVariantResin() {
        return queen.level()
            .getBlockState(queen.blockPosition().below())
            .is(AlienVariantTypes.getFor(queen.getVariant()).resinBlockTag());
    }

    /**
     * Hive: the queen needs to be standing near the center of an alive, calm hive location.
     */
    private boolean hasSuitableHiveLocation() {
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
        return true;
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
        return HiveLocationRegistry.INSTANCE.getByChunk(queen.level().dimension(), new ChunkPos(queen.blockPosition()));
    }

    private boolean isNearHiveCenter(HiveLocation location) {
        var centerChunk = new ChunkPos(location.centerPos());
        var queenChunk = new ChunkPos(queen.blockPosition());
        var dx = Math.abs(centerChunk.x - queenChunk.x);
        var dz = Math.abs(centerChunk.z - queenChunk.z);
        return Math.max(dx, dz) <= 1;
    }

    private boolean canOvipositorFit() {
        var leftBottomSupport = EntityUtil.getRelativePosition(queen, 1.5, 0, 2.5);
        var rightBottomSupport = EntityUtil.getRelativePosition(queen, -2, 0, 2);
        var farLeftBottomSupport = EntityUtil.getRelativePosition(queen, 5.7, 0, 8.25);
        var backBottomSupport = EntityUtil.getRelativePosition(queen, 0, 0, 7);

        return canOvipositorSupportExistAt(leftBottomSupport)
            && canOvipositorSupportExistAt(rightBottomSupport)
            && canOvipositorSupportExistAt(farLeftBottomSupport)
            && canOvipositorSupportExistAt(backBottomSupport)
            && isEggLayingPositionValid();
    }

    private boolean isEggLayingPositionValid() {
        var eggLayingPosition = getEggLayingPosition();
        var blockState = queen.level().getBlockState(BlockPos.containing(eggLayingPosition));
        var isClearForEgg = blockState.isAir() || blockState.canBeReplaced();

        return isClearForEgg && EntityUtil.canMobSeeBlock(queen, eggLayingPosition);
    }

    private boolean canOvipositorSupportExistAt(Vec3 vec3) {
        var blockPos = BlockPos.containing(vec3);

        var isSupported = false;
        var stepsDown = 0;

        while (!isSupported && stepsDown < 4) {
            blockPos = blockPos.below();
            var blockState = queen.level().getBlockState(blockPos);

            var aboveBlockState = queen.level().getBlockState(blockPos.above());
            isSupported = (aboveBlockState.isAir() || aboveBlockState.canBeReplaced())
                && blockState.is(AlienVariantTypes.getFor(queen.getVariant()).resinBlockTag());

            stepsDown++;
        }

        return isSupported && EntityUtil.canMobSeeBlock(queen, vec3);
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
