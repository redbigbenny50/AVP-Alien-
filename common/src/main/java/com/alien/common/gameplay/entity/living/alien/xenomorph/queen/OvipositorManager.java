package com.alien.common.gameplay.entity.living.alien.xenomorph.queen;

import com.alien.Alien;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

public class OvipositorManager implements NBTSerializable {

    /**
     * Yaw applied to the eggsack on top of the royal's own facing. **ZERO ON PURPOSE.**
     * <p>
     * This was 25 degrees, added to answer "it needs to rotate more to the right". That was the wrong tool and it broke
     * the placement: the sack rides at a DERIVED offset - `positionRider` uses `EntityUtil.getRelativePosition(this, 3,
     * 0.01, 5.25)`, and those numbers come from the seat cube in the model (`gFullSack`'s first cube, centre x -2.969 z
     * -4.805 blocks) so that the seat lands under her. The seat sits 5.65 blocks from the sack's own origin, so yawing
     * the whole model about that origin swings the seat through an arc - 2.44 blocks sideways at 25 degrees. That is
     * why the sack drifted off centre.
     * <p>
     * [stated] "lets not rotate it and just leave it at the default positioning i can adjust rotations in the model
     * itself. the important part is it sits under her." So the dial stays at zero and orientation is an ART decision.
     * If it is ever set non-zero again the ride offsets in `Queen.positionRider` and the empress's
     * OVIPOSITOR_RIDE_LATERAL / _DISTANCE must be rotated to match, or the seat walks off her again.
     */
    public static final float OVIPOSITOR_YAW_OFFSET_DEGREES = 0.0F;

    /**
     * How long after losing an eggsack before she can grow another. Started the tick she stops carrying one, so it runs
     * alongside the abandoned sack's own decay rather than after it.
     */
    public static final Duration OVIPOSITOR_REGROWTH_COOLDOWN = Duration.ofSeconds(180);

    private final Cooldown ovipositorCreationCooldown;

    private final Queen queen;

    /** The founding-floor bone block matching a queen's strain. */
    private static net.minecraft.world.level.block.Block strainResinBone(Queen queen) {
        var type = com.alien.common.data.AlienVariantTypes.getFor(queen.getVariant());

        if (type == com.alien.common.data.AlienVariantTypes.ABERRANT) {
            return com.alien.common.registry.init.block.AberrantAlienResinBlocks.ABERRANT_RESIN_BONE.get();
        }
        if (type == com.alien.common.data.AlienVariantTypes.NETHER) {
            return com.alien.common.registry.init.block.NetherAlienResinBlocks.NETHER_RESIN_BONE.get();
        }
        if (type == com.alien.common.data.AlienVariantTypes.IRRADIATED) {
            return com.alien.common.registry.init.block.IrradiatedAlienResinBlocks.IRRADIATED_RESIN_BONE.get();
        }

        return com.alien.common.registry.init.block.AlienResinBlocks.RESIN_BONE.get();
    }

    private boolean hadOvipositorLastTick;

    public OvipositorManager(Queen queen) {
        this.ovipositorCreationCooldown = Cooldown.withCooldownTime(
            "ovipositorCreationCooldownInTicks",
            OVIPOSITOR_REGROWTH_COOLDOWN
        );
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
                logEggsackRemoval("chained eggsack on a no-longer-inhibited queen");
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
                    logEggsackRemoval("inhibited but not fully bound (chains stripped)");
                    getOvipositor().ifSome(ovipositor -> ovipositor.discard());
                    return;
                }

                // Inhibited AND contained: she belongs on the CHAINED eggsack. If she's still riding her founding
                // ovipositor (captured after she'd already founded), drop it so canCreateChainedEggsack below grows the
                // chained one in its place — otherwise she stays stuck on the founding eggsack forever.
                if (!getOvipositor().isSomeAnd(Ovipositor::isChainedEggsack)) {
                    logEggsackRemoval("captured queen still on her founding eggsack");
                    getOvipositor().ifSome(ovipositor -> ovipositor.discard());
                    return;
                }
            }
            getOvipositor().ifSome(ovipositor -> {
                // Per-tick rotation glue - MUST carry the same model offset as the attach sites, or this line
                // overwrites the creation-time offset one tick after attach and the sack snaps back off-center.
                ovipositor.setYRot(queen.getYRot() + OVIPOSITOR_YAW_OFFSET_DEGREES);
                ovipositor.setXRot(queen.getXRot());
                // Body rotation.
                ovipositor.yBodyRot = queen.yBodyRot + OVIPOSITOR_YAW_OFFSET_DEGREES;
                // Head rotation.
                ovipositor.yHeadRot = queen.yHeadRot + OVIPOSITOR_YAW_OFFSET_DEGREES;
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

        // Belt-and-braces on the same §7b rule: even if every creation gate passes (resin underfoot, room clear),
        // the eggsack does not form while her founding core is still being excavated.
        if (foundingCoreStillExcavating()) {
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
                // Hers, not merely someone's - see hasSuitableHiveLocation.
                || !location.founderId().equals(queen.getUUID())
                || location.reproductiveEstablished()
                || !isNearHiveCenter(location)
        ) {
            return;
        }
        // Construction economy step 6 (design §7b): "the eggsack cannot form until the core is carved." This gate
        // sits ABOVE the standing-on-resin check on purpose: any resin that ends up under her mid-dig (spread creep,
        // pre-existing hive ground) must not read as "already prepared" while she is still excavating.
        if (foundingCoreStillExcavating()) {
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
        // END-STYLE: the biomass tank is WAIVED - biomass generation is off in end-style dimensions (anything that
        // accumulates unused is off), so the tank would never fill and she would never grow her eggsack, which the
        // design explicitly keeps ("her making an eggsack and eggs should still work"). The player brought her;
        // the fortress does not farm for the privilege.
        if (!location.isEndStyleHive() && location.biomass() < target) {
            return;
        }

        // NO HOLLOWING HERE. This used to carve the whole slab of her centre chunk out to air before stamping the
        // floor - a leftover from when the queen cleared her own chunk to make room for the eggsack. The carve
        // system now builds the core properly, so by the time she is ready to grow the sack the chamber and its
        // dome already exist; re-hollowing simply deleted a chunk-sized hole through the roof she had just built.
        // Only the floor pad is stamped now.
        //
        // The floor stamp still REPLACES the floor layer, so spawners are harvested first - the founding core does
        // not go through HiveRouter.place(), which is where every routed piece does this.
        if (queen.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.alien.common.gameplay.hive.structure.HarvestSpawnerCapture.captureBeforeStamp(
                serverLevel,
                location,
                java.util.List.of(new ChunkPos(location.centerPos()))
            );
        }
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

    /**
     * She stands up and leaves the eggsack behind.
     * <p>
     * Dismount only — no discard. The sack rots on its own timer once it has no royal (see
     * {@link Ovipositor#ABANDONED_LINGER_TICKS}), so what a player sees is the thing she was tending still lying where
     * she left it rather than blinking out of existence the moment she rises. It yields nothing when it goes.
     * <p>
     * The three discards in {@link #tick} are deliberately NOT routed through here: those are state corrections for a
     * captured queen (chains stripped, inhibitor pried off, wrong sack type), where the sack is being replaced rather
     * than abandoned and a lingering husk would just be in the way.
     */
    /**
     * Diagnostic for "the queen got off her eggsack".
     * <p>
     * Every server-side path that ends a ride now names itself. Three rounds were lost to theories because NOTHING
     * logged: if none of these lines appears and she still appears to stand, the sack never actually left her and the
     * problem is client-side presentation, not the eggsack logic. Rare events only - these fire once per removal.
     * </p>
     */
    private void logEggsackRemoval(String reason) {
        Alien.LOGGER.info(
            "Queen eggsack removed at {}: {}. (inhibited={}, fullyBound={}, health={}/{})",
            queen.blockPosition(),
            reason,
            queen.isInhibited(),
            queen.getBindManager().isFullyBound(),
            String.format("%.1f", queen.getHealth()),
            String.format("%.1f", queen.getMaxHealth())
        );
    }

    public void abandonOvipositor() {
        logEggsackRemoval("abandonOvipositor - she was roused or lost her ability to reproduce");
        getOvipositor().ifSome(Entity::stopRiding);

        for (var passenger : List.copyOf(queen.getPassengers())) {
            if (passenger.getType() == AlienEntityTypes.OVIPOSITOR.get()) {
                passenger.stopRiding();
            }
        }

        if (!queen.level().isClientSide) {
            var area = queen.getBoundingBox().inflate(8.0D);
            for (var ovipositor : queen.level().getEntitiesOfClass(Ovipositor.class, area)) {
                if (ovipositor.getVehicle() == queen || ovipositor.distanceToSqr(queen) <= 16.0D) {
                    ovipositor.stopRiding();
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
            ovipositor.moveTo(
                queen.position(),
                queen.getYRot() + OVIPOSITOR_YAW_OFFSET_DEGREES,
                queen.getXRot()
            );
            ovipositor.startRiding(queen, true);

            // Body rotation - offset so the sack model sits centered on her (see OVIPOSITOR_YAW_OFFSET_DEGREES).
            ovipositor.yBodyRot = queen.yBodyRot + OVIPOSITOR_YAW_OFFSET_DEGREES;
            // Head rotation.
            ovipositor.yHeadRot = queen.yHeadRot;

            queen.level().addFreshEntity(ovipositor);

            // Fresh sitting, fresh bar. The disturbance threshold is "health lost since she settled" - without this it
            // carried her whole life's damage, and a queen who had already been fought once stood up to the very next
            // scratch. See QueenLifecyclePhaseManager.resetDisturbance for why regeneration cannot be relied on to
            // clear it while a player is stood next to her.
            queen.getLifecyclePhaseManager().resetDisturbance();

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
    /**
     * The §7b founding order gate: TRUE while this queen's founding-core carve site is still excavating (or is a
     * loaded-but-unhydrated site whose state is unknown for another tick). Everything eggsack-shaped waits on this.
     */
    private boolean foundingCoreStillExcavating() {
        var location = currentLocation();
        if (location == null || location.founderId() == null || location.reproductiveEstablished()) {
            return false;
        }
        var carveSite = location.activeCarveSite();
        if (carveSite != null) {
            return carveSite.isFoundingCore() && !carveSite.isFullyExcavated();
        }
        return location.hasActiveCarveSite(); // loaded from save, not yet hydrated - wait for it to resolve
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
        // Resin bone is a distinct, bone-like pad for the founding floor - and it MUST be her own strain's.
        // This was hardcoded to the NORMAL block, whose only tag is NORMAL_RESIN, so a nether/aberrant/irradiated
        // queen laid a floor that failed her own "on variant resin" and support-point gates: she would carpet her
        // chamber and then be unable to use it.
        var floorState = strainResinBone(queen).defaultBlockState();

        // Anchor the disc to the CENTER CHUNK's middle at the slab floor Y, NOT under the queen - so the floor is
        // deterministic and aligned with the built chamber regardless of exactly where she's standing. FILL every
        // cell in the circle (including air/gaps from caves) so the chamber has a complete, hole-free floor for the
        // ovipositor support points.
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
        // FINISH HER GROWTH FIRST ([stated] "when she forms the chained eggsack have her fully grown"). Nothing in
        // the ovipositor code scales with the queen - the ride offsets and support probes are fixed block figures
        // - so an eggsack seated on an 0.85 queen would sit wrong and stay wrong. Now that a still-growing queen
        // is inhibitable, that pairing is reachable in normal play, not a corner case. Maturing her here rather
        // than at inhibit time keeps his rule intact: the growth only ends when she actually becomes a breeder.
        queen.getMoltingManager().matureImmediately();

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

            ovipositor.moveTo(
                queen.position(),
                settledYaw + OVIPOSITOR_YAW_OFFSET_DEGREES,
                queen.getXRot()
            );
            ovipositor.startRiding(queen, true);
            ovipositor.yBodyRot = settledYaw + OVIPOSITOR_YAW_OFFSET_DEGREES;
            ovipositor.yHeadRot = settledYaw;
            // A captive breeder's eggsack must not vanish to far-away despawn while she's contained; the teardown above
            // is the only thing that removes it.
            ovipositor.setPersistenceRequired();
            ovipositor.setChainedEggsack(true);
            queen.level().addFreshEntity(ovipositor);

            // Fresh sitting, fresh bar. The disturbance threshold is "health lost since she settled" - without this it
            // carried her whole life's damage, and a queen who had already been fought once stood up to the very next
            // scratch. See QueenLifecyclePhaseManager.resetDisturbance for why regeneration cannot be relied on to
            // clear it while a player is stood next to her.
            queen.getLifecyclePhaseManager().resetDisturbance();
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
        // ⭐⭐ SHE MUST BE THE SEATED FOUNDER OF THIS LOCATION. NOT "not someone else's" - HERS.
        //
        // ⚠⚠ THE NULL CASE USED TO PASS, AND THAT WAS THE HOLE. A queen standing in a claim whose founder seat is
        // VACANT adopted it on the spot and grew a sack where she stood. [stated] a tester drank a metamorphosis
        // potion onto a crusher: it matured straight into a queen inside an existing claim, skipped the whole
        // found-a-new-hive arc, and laid there instead. "once the tester killed the eggsack the queen dug like
        // normal to found a true hive" - the founding path was never broken, this gate simply got to her first.
        //
        // ⚠ REQUIRING THE SEAT IS SAFE because every legitimate laying queen is explicitly seated by one of:
        // HiveLocationFoundingService (settlement and inhibition's personal claim), ConvoyArrival (a founder
        // queen reaching a daughter claim), HiveLoadedSpawner, or LegacyHiveRecovery. A queen who owns no seat
        // has not founded yet - and founding is precisely what she should be doing instead of laying.
        var founderId = location.founderId();
        if (founderId == null || !founderId.equals(queen.getUUID())) {
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
