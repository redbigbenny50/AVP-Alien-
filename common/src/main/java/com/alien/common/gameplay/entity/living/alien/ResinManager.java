package com.alien.common.gameplay.entity.living.alien;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.block.entity.resin.node.ChargeCursor;
import com.alien.common.gameplay.block.entity.resin.node.ResinNodeBlockEntity;
import com.alien.common.gameplay.hive.location.HiveLocation;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.gameplay.hive.spawning.HiveLocationSpawnGate;
import com.alien.common.gameplay.level.gameevent.listener.ResinSpreadListener;
import com.alien.common.model.alien.variant.AlienVariantType;
import com.alien.common.model.resin.ResinData;
import com.blib.api.common.nbt.v1.model.NBTSerializable;
import com.just.core.functional.option.Option;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class ResinManager implements GameEventListener.Provider<ResinSpreadListener>, NBTSerializable {

    private static final String NBT_LAST_SPREAD_TICK = "lastSpreadTick";

    private static final int SPREAD_COOLDOWN_IN_TICKS = 15 * 20;

    private static final int SPREAD_CHARGE = 16;

    private static final int SPREAD_OPPORTUNITY_RECHECK_TICKS = 20;

    private static final int RESIN_NODE_SEARCH_RADIUS = 8;

    private static final int RESIN_SPREAD_TARGET_SEARCH_RADIUS = 6;

    /**
     * ⭐ HOW FAR A XENO REACHES TO RE-LAY FOREIGN RESIN, and how much of it per spread.
     * <p>
     * [stated] "the irradiated xenos are supposed to replace the old resin with their own like how it gets filled in
     * the first place. so they walk the hive filling the previous resin with their own." Deliberately a SMALL bite on
     * the ordinary spread cadence rather than a sweep - a converted hive should visibly turn over as its people move
     * through it, which is also what keeps a big fortress off the server tick.
     * </p>
     */
    private static final int FOREIGN_RESIN_SEARCH_RADIUS = 4;

    private static final int FOREIGN_RESIN_CONVERSIONS_PER_SPREAD = 6;

    private static final int RESIN_SPREAD_TARGET_VERTICAL_SEARCH_RANGE = 3;

    private final Alien alien;

    private final DynamicGameEventListener<ResinSpreadListener> dynamicResinSpreadListener;

    private final ResinSpreadListener resinSpreadListener;

    private final ResinData resinData;

    private long lastSpreadTick;

    private int ticksSinceAttemptedNodePlacement = 0;

    private int ticksUntilSpreadOpportunityCheck = 0;

    public ResinManager(Alien alien) {
        this.alien = alien;
        this.resinData = new ResinData(0, SPREAD_CHARGE, 0, 0);
        var positionSource = new EntityPositionSource(alien, 0F);
        var spreadType = new ResinSpreadListener.SpreaderType.Entity(alien);
        this.resinSpreadListener = new ResinSpreadListener(positionSource, spreadType);
        this.dynamicResinSpreadListener = new DynamicGameEventListener<>(resinSpreadListener);
    }

    @Override
    public @NotNull ResinSpreadListener getListener() {
        return resinSpreadListener;
    }

    public void tick() {
        if (alien.level().isClientSide) {
            return;
        }

        ticksSinceAttemptedNodePlacement = Math.max(0, ticksSinceAttemptedNodePlacement - 1);
        ticksUntilSpreadOpportunityCheck = Math.max(0, ticksUntilSpreadOpportunityCheck - 1);
    }

    public boolean canSpreadResin() {
        // Founding lockout: members of a queen-founded hive that has not yet established its egg sack do NOT spread
        // resin. During founding the queen fills her biomass tank and then stamps the resin floor + ovipositor all at
        // once at COMMIT - no piecemeal resin beforehand. Queenless hives (no founder) are unaffected.
        var foundingLocation = currentLocation();
        if (
            foundingLocation != null
                && foundingLocation.founderId() != null
                && !foundingLocation.reproductiveEstablished()
        ) {
            return false;
        }

        if (
            alien.tickCount - lastSpreadTick < SPREAD_COOLDOWN_IN_TICKS
                || isNodePlacementOnCooldown()
                || ticksUntilSpreadOpportunityCheck > 0
                || !canPaySpreadCost()
        ) {
            return false;
        }

        // Build in the HIVE'S strain where there is one - see AlienVariantTypes.getForBuild.
        var alienVariantType = AlienVariantTypes.getForBuild(alien, currentLocation());

        if (!canAttemptResinSpreadAtAlienPosition() || !hasResinSpreadOpportunity(alienVariantType)) {
            ticksUntilSpreadOpportunityCheck = SPREAD_OPPORTUNITY_RECHECK_TICKS;
            return false;
        }

        return true;
    }

    public void spreadResin() {
        var location = currentLocation();
        var alienVariantType = AlienVariantTypes.getForBuild(alien, location);

        if (
            !canPaySpreadCost(location)
                || !canAttemptResinSpreadAtAlienPosition()
                || !hasResinSpreadOpportunity(alienVariantType)
        ) {
            ticksUntilSpreadOpportunityCheck = SPREAD_OPPORTUNITY_RECHECK_TICKS;
            return;
        }

        // ⭐ RE-LAY ANY FOREIGN RESIN UNDERFOOT FIRST. This is what turns a converted hive over: its people walk it
        // and fill the old strain in with their own, a few blocks at a time, on the ordinary spread cadence.
        convertForeignResin(alien.level(), alienVariantType);

        // Set the charge so the nearest resin node listener can consume it.
        resinData.setResin(SPREAD_CHARGE);

        // Signal to the nearest resin node that we want to spread resin.
        alien.gameEvent(alienVariantType.resinSpreadEvent());

        lastSpreadTick = alien.tickCount;

        if (resinData.resin() <= 0) {
            return;
        }

        // If the alien still has resin even after signalling a resin spread event, that means there was no resin node
        // to intercept the event. So we try to place a resin node down here.
        if (resinData.resin() > 0) {
            var level = alien.level();
            // Try and find a suitable resin node block location.
            var suitableResinNodeBlockPosOption = findSuitableResinNodeBlockPos(level, alienVariantType);

            if (suitableResinNodeBlockPosOption.isNone()) {
                // Could not find a suitable resin node block position, so reset the node place cooldown and return.
                ticksSinceAttemptedNodePlacement = 20 * 10;
                resinData.setResin(0);
                return;
            }

            // If the resin holder still has more resin, then we place a resin node manually.
            var resinNodeBlockState = alienVariantType.resinNode().get().defaultBlockState();
            // Place the resin node block at the suitable position.
            var resinNodePos = suitableResinNodeBlockPosOption.unwrap();
            level.setBlockAndUpdate(resinNodePos, resinNodeBlockState);
            seedPlacedResinNode(level, resinNodePos, resinData.resin());
            resinData.setResin(0);
            paySpreadCost(location);
        }
    }

    public ResinData resinData() {
        return resinData;
    }

    public @Nullable ChargeCursor.SpreadCost spreadCost() {
        var location = currentLocation();
        if (location == null || !location.isAlive()) {
            return null;
        }

        var cost = HiveLocationRegistry.INSTANCE.config().resinSpreadBiomassCost();
        if (cost <= 0) {
            return null;
        }

        return new ChargeCursor.SpreadCost(location.id().value(), cost);
    }

    private boolean isNodePlacementOnCooldown() {
        return ticksSinceAttemptedNodePlacement > 0;
    }

    private boolean canAttemptResinSpreadAtAlienPosition() {
        // Alien must not have an attack target...
        if (alien.getTarget() != null) {
            return false;
        }
        if (alien.isInWater() || alien.isUnderWater()) {
            return false;
        }
        // AND alien must have not been hurt for more than 10 seconds...
        if (alien.tickCount <= alien.getLastHurtTimeInTicks() + (10 * 20)) {
            return false;
        }
        return isInsideHiveForResinSpread();
    }

    private boolean hasResinSpreadOpportunity(AlienVariantType alienVariantType) {
        var level = alien.level();

        // ⚠ FOREIGN RESIN IS AN OPPORTUNITY IN ITS OWN RIGHT. A converted hive is already fully built, so there is
        // no air left to spread into and neither clause below would ever fire - the re-laying would never start.
        return !findForeignResin(level, alienVariantType).isEmpty()
            || findSuitableResinNodeBlockPos(level, alienVariantType).isSome()
            || (hasNearbyResinNode(level, alienVariantType) && hasNearbyResinSpreadTarget(level, alienVariantType));
    }

    private boolean hasNearbyResinNode(Level level, AlienVariantType alienVariantType) {
        var origin = alien.blockPosition();
        var resinNode = alienVariantType.resinNode().get();
        var mutablePos = new BlockPos.MutableBlockPos();

        for (var dx = -RESIN_NODE_SEARCH_RADIUS; dx <= RESIN_NODE_SEARCH_RADIUS; dx++) {
            for (var dy = -RESIN_NODE_SEARCH_RADIUS; dy <= RESIN_NODE_SEARCH_RADIUS; dy++) {
                for (var dz = -RESIN_NODE_SEARCH_RADIUS; dz <= RESIN_NODE_SEARCH_RADIUS; dz++) {
                    mutablePos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

                    if (level.getBlockState(mutablePos).is(resinNode)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasNearbyResinSpreadTarget(Level level, AlienVariantType alienVariantType) {
        var origin = alien.blockPosition();
        var mutablePos = new BlockPos.MutableBlockPos();

        for (var dx = -RESIN_SPREAD_TARGET_SEARCH_RADIUS; dx <= RESIN_SPREAD_TARGET_SEARCH_RADIUS; dx++) {
            for (var dy = -RESIN_SPREAD_TARGET_VERTICAL_SEARCH_RANGE; dy <= RESIN_SPREAD_TARGET_VERTICAL_SEARCH_RANGE; dy++) {
                for (var dz = -RESIN_SPREAD_TARGET_SEARCH_RADIUS; dz <= RESIN_SPREAD_TARGET_SEARCH_RADIUS; dz++) {
                    mutablePos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

                    if (level.getBlockState(mutablePos).is(alienVariantType.resinReplaceableTag())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * "Is in a hive that's calm enough to spread resin." Under hive, "in a hive" means inside a claimed chunk of any
     * location; the angry check is the location's boss-bar angry state.
     */
    private boolean isInsideHiveForResinSpread() {
        var location = currentLocation();
        if (location == null) {
            return false;
        }
        var bossBar = location.bossBar();
        return bossBar == null || !bossBar.isAngry();
    }

    private boolean canPaySpreadCost() {
        return canPaySpreadCost(currentLocation());
    }

    private boolean canPaySpreadCost(HiveLocation location) {
        if (location == null || !location.isAlive()) {
            return false;
        }

        var cost = HiveLocationRegistry.INSTANCE.config().resinSpreadBiomassCost();
        return cost <= 0 || location.biomass() >= cost;
    }

    private void paySpreadCost(HiveLocation location) {
        var cost = HiveLocationRegistry.INSTANCE.config().resinSpreadBiomassCost();
        if (cost > 0) {
            location.setBiomass(location.biomass() - cost);
        }
    }

    private HiveLocation currentLocation() {
        return HiveLocationSpawnGate.locationContaining(alien.level(), alien.blockPosition());
    }

    /**
     * Every nearby resin block belonging to a DIFFERENT strain.
     * <p>
     * ⚠ Identified by asking {@code AlienVariantTypes} what strain the BLOCK belongs to, not by testing against a list
     * - so a strain added later is picked up with no change here.
     * </p>
     */
    private java.util.List<BlockPos> findForeignResin(Level level, AlienVariantType alienVariantType) {
        var found = new java.util.ArrayList<BlockPos>();
        var origin = alien.blockPosition();
        var mutablePos = new BlockPos.MutableBlockPos();

        for (var dx = -FOREIGN_RESIN_SEARCH_RADIUS; dx <= FOREIGN_RESIN_SEARCH_RADIUS; dx++) {
            for (var dy = -FOREIGN_RESIN_SEARCH_RADIUS; dy <= FOREIGN_RESIN_SEARCH_RADIUS; dy++) {
                for (var dz = -FOREIGN_RESIN_SEARCH_RADIUS; dz <= FOREIGN_RESIN_SEARCH_RADIUS; dz++) {
                    mutablePos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);

                    var state = level.getBlockState(mutablePos);
                    var owner = AlienVariantTypes.getForOrNull(state);

                    if (owner == null || owner.variant() == alienVariantType.variant()) {
                        continue;
                    }

                    found.add(mutablePos.immutable());

                    if (found.size() >= FOREIGN_RESIN_CONVERSIONS_PER_SPREAD) {
                        return found;
                    }
                }
            }
        }

        return found;
    }

    /** Swaps foreign resin for this strain's equivalent, preserving the blockstate and a node's stored charge. */
    private void convertForeignResin(Level level, AlienVariantType alienVariantType) {
        for (var pos : findForeignResin(level, alienVariantType)) {
            var oldState = level.getBlockState(pos);
            var owner = AlienVariantTypes.getForOrNull(oldState);

            if (owner == null) {
                continue; // changed under us between the scan and here
            }

            var replacement = equivalentBlock(oldState, owner, alienVariantType);

            if (replacement == null) {
                continue;
            }

            // ⚠ withPropertiesOf CARRIES THE SHARED BLOCKSTATE OVER - a vein's faces, a vent's facing, waterlogging.
            // Placing a default state instead would snap every vein flat and re-aim every vent.
            var newState = replacement.withPropertiesOf(oldState);

            // ⚠ A NODE KEEPS ITS SPREAD CURSORS IN A BLOCK ENTITY, and replacing the block destroys them. Both
            // strains use the same ResinNodeBlockEntity, so its saved tag carries across verbatim - without this
            // step, converting a hive would silently wipe every spreader in it and the infestation would stall.
            var savedNode = savedNodeData(level, pos);

            level.setBlockAndUpdate(pos, newState);

            if (savedNode != null && level.getBlockEntity(pos) instanceof ResinNodeBlockEntity converted) {
                converted.loadWithComponents(savedNode, level.registryAccess());
                converted.setChanged();
            }
        }
    }

    /** The same KIND of block in this strain: resin for resin, vein for vein, vent for vent, and so on. */
    private static @Nullable Block equivalentBlock(
        BlockState oldState,
        AlienVariantType owner,
        AlienVariantType target
    ) {
        if (oldState.is(owner.resinNode().get())) {
            return target.resinNode().get();
        }
        if (oldState.is(owner.resinVein().get())) {
            return target.resinVein().get();
        }
        if (oldState.is(owner.resinVent().get())) {
            return target.resinVent().get();
        }
        if (oldState.is(owner.resinWeb().get())) {
            return target.resinWeb().get();
        }
        if (oldState.is(owner.resin().get())) {
            return target.resin().get();
        }

        return null; // some other block of theirs we have no counterpart for - leave it be
    }

    /** A node's saved tag, so its cursors survive the block swap. Null for anything that is not a node. */
    private static @Nullable net.minecraft.nbt.CompoundTag savedNodeData(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ResinNodeBlockEntity node)) {
            return null;
        }

        return node.saveWithoutMetadata(level.registryAccess());
    }

    private void seedPlacedResinNode(Level level, BlockPos resinNodePos, int totalCharge) {
        if (totalCharge <= 0 || !(level.getBlockEntity(resinNodePos) instanceof ResinNodeBlockEntity resinNode)) {
            return;
        }

        var spreader = resinNode.getListener().getResinSpreader();
        spreader.addCursors(BlockPos.containing(alien.position().relative(Direction.UP, 0.5)), totalCharge);
        spreader.updateCursors(level, resinNodePos, level.getRandom());
    }

    private Option<BlockPos> findSuitableResinNodeBlockPos(Level level, AlienVariantType alienVariantType) {
        var origin = alien.blockPosition();
        var below = origin.below();
        var belowState = level.getBlockState(below);

        if (belowState.is(alienVariantType.resinReplaceableTag()) && isResinNodePlacementSpaceClear(level, below)) {
            return Option.some(below);
        }

        // Use mutable block pos for memory efficiency.
        var targetMutablePos = new BlockPos.MutableBlockPos();
        var belowTargetMutablePos = new BlockPos.MutableBlockPos();

        var radius = 2;

        for (var r = 0; r <= radius; r++) {
            for (var dx = -r; dx <= r; dx++) {
                var dz = r - Math.abs(dx);

                for (var sign : new int[] { 1, -1 }) {
                    var actualDz = dz * sign;

                    for (var dy = -1; dy <= 1; dy++) {
                        targetMutablePos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + actualDz);
                        belowTargetMutablePos.set(targetMutablePos.getX(), targetMutablePos.getY() - 1, targetMutablePos.getZ());

                        var targetStateToReplace = level.getBlockState(targetMutablePos);
                        var supportState = level.getBlockState(belowTargetMutablePos);

                        if (
                            // If the target state is air OR can be replaced...
                            (targetStateToReplace.isAir()
                                || targetStateToReplace.canBeReplaced())
                                // AND if the supporting state beneath the target state is a solid render...
                                && supportState.isSolidRender(level, belowTargetMutablePos)
                                && !supportState.is(alienVariantType.resinBlockTag())
                                && isResinNodePlacementSpaceClear(level, targetMutablePos)
                        ) {
                            // Then return the target state pos.
                            return Option.some(targetMutablePos.immutable());
                        }
                    }
                }
            }
        }

        return Option.none();
    }

    private boolean isResinNodePlacementSpaceClear(Level level, BlockPos blockPos) {
        return level.getEntities(null, new AABB(blockPos)).isEmpty();
    }

    public void updateDynamicGameEventListener(@NotNull BiConsumer<DynamicGameEventListener<?>, ServerLevel> biConsumer) {
        if (alien.level() instanceof ServerLevel serverLevel) {
            biConsumer.accept(dynamicResinSpreadListener, serverLevel);
        }
    }

    @Override
    public void load(CompoundTag compoundTag) {
        if (compoundTag.contains(NBT_LAST_SPREAD_TICK)) {
            this.lastSpreadTick = compoundTag.getLong(NBT_LAST_SPREAD_TICK);
        }
    }

    @Override
    public void save(CompoundTag compoundTag) {
        compoundTag.putLong(NBT_LAST_SPREAD_TICK, lastSpreadTick);
    }
}
