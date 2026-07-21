package com.alien.common.gameplay.block.entity.capture.anchor;

import com.alien.common.gameplay.block.capture.anchor.AnchorBlock;
import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Block entity for the capture anchor. Renders the custom geo, exposes the chain bind point
 * ({@link #chainAnchorPoint()} — the {@code gHandle} pivot in world space), and owns one capture chain: it stores the
 * bound mob, holds it within the chain's length each tick, and renders the chain to it.
 * <p>
 * The link lives only on the anchor side (the bound mob's UUID), so no mob classes are touched. Queens drive their own
 * multi-anchor restriction elsewhere; this entity applies the generic single-chain clamp (Layer 1).
 */
public class AnchorBlockEntity extends BlockEntity {

    /** Height of the {@code gHandle} pivot ([0, 9.8, 0]) above the mount surface, in blocks (9.8 / 16). */
    private static final double HANDLE_OFFSET = 9.8 / 16.0;

    /** Generic tether length for non-queen mobs, in blocks — a hard stop, not an elastic leash. */
    public static final double DEFAULT_CHAIN_LENGTH = 10.0;

    private static final String TAG_BOUND_MOB = "ChainBoundMob";

    private static final String TAG_CLIENT_MOB_ID = "ChainMobNetId";

    private static final String TAG_CLIENT_SHACKLE_SLOT = "ChainShackleSlot";

    /** Persisted: which mob this chain holds (null = no chain). */
    @Nullable
    private UUID boundMobId;

    /** Server-side cache of the bound mob's network id, mirrored to clients for rendering (-1 = none). */
    private int boundMobNetId = -1;

    /**
     * Which queen shackle slot this anchor occupies, mirrored to clients for the shackle-bone chain render. Re-asserted
     * every queen tick from her bind order, so it tracks mid-list releases. {@code -1} = not a queen shackle (the chain
     * then attaches to the generic body point).
     */
    private int shackleSlot = -1;

    public AnchorBlockEntity(BlockPos pos, BlockState state) {
        super(AlienBlockEntityTypes.ANCHOR.get(), pos, state);
    }

    // ---- chain link ----------------------------------------------------------------------------------------------

    public boolean hasChain() {
        return boundMobId != null;
    }

    @Nullable
    public UUID getBoundMobId() {
        return boundMobId;
    }

    /** Network id of the bound mob for client rendering (-1 if none / not loaded). */
    public int getBoundMobNetId() {
        return boundMobNetId;
    }

    /** Queen shackle slot for the chain render (-1 = generic body attach). */
    public int getShackleSlot() {
        return shackleSlot;
    }

    /** Set by the queen each tick from her bind order; syncs to clients only when it actually changes. */
    public void setShackleSlot(int slot) {
        if (this.shackleSlot != slot) {
            this.shackleSlot = slot;
            setChanged();
            syncToClients();
        }
    }

    /** Attach this anchor's chain to the given mob. */
    public void bind(LivingEntity mob) {
        this.boundMobId = mob.getUUID();
        this.boundMobNetId = mob.getId();
        if (mob instanceof Queen queen) {
            queen.getBindManager().attach(getBlockPos());
        }
        // Clank of the capture chain locking on. Played at the anchor (server-side so it carries to nearby
        // clients); bind() is the single choke point every chain attach flows through, so this fires once per
        // chain regardless of whether it's a queen shackle or a generic body attach.
        if (level != null && !level.isClientSide) {
            var p = getBlockPos();
            level.playSound(null, p, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        setChanged();
        syncToClients();
    }

    /** Drop this anchor's chain (frees the mob; the mob keeps whatever AI/state it had). */
    public void release() {
        if (
            boundMobId != null
                && level instanceof ServerLevel server
                && server.getEntity(boundMobId) instanceof Queen queen
        ) {
            queen.getBindManager().detach(getBlockPos());
        }
        // Chain comes off the anchor. Guarded on boundMobId so a no-op release stays silent.
        if (boundMobId != null && level != null && !level.isClientSide) {
            level.playSound(null, getBlockPos(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        this.boundMobId = null;
        this.boundMobNetId = -1;
        setChanged();
        syncToClients();
    }

    // ---- tick ----------------------------------------------------------------------------------------------------

    /**
     * Whether this anchor has announced itself to {@link AnchorIndex} yet. Registration happens on the first server
     * tick rather than in a load hook: vanilla {@code BlockEntity} has no {@code onLoad} (that is a NeoForge addition,
     * unavailable in common multiloader code), and ticking is the first point at which the level is reliably present.
     */
    private boolean indexed = false;

    /** Covers unload and destruction alike - either way this anchor stops being a target for hive defenders. */
    @Override
    public void setRemoved() {
        if (level != null) {
            AnchorIndex.remove(level, getBlockPos());
        }
        indexed = false;
        super.setRemoved();
    }

    public void serverTick() {
        // Announce to the index once. Defenders search that index rather than scanning the world for anchor
        // blocks, which would be far too costly from a GOAP sensor.
        if (!indexed && level != null) {
            AnchorIndex.add(level, getBlockPos());
            indexed = true;
        }
        if (boundMobId == null || !(level instanceof ServerLevel server)) {
            return;
        }
        Entity entity = server.getEntity(boundMobId);
        if (!(entity instanceof LivingEntity mob) || !mob.isAlive() || mob.isRemoved()) {
            release();
            return;
        }
        if (boundMobNetId != mob.getId()) {
            boundMobNetId = mob.getId();
            syncToClients();
        }
        // The queen runs her own multi-anchor restriction (Layer 2); the generic per-anchor clamp is for other mobs.
        if (!(mob instanceof Queen)) {
            enforceTether(mob);
        }
    }

    /**
     * Hard stop: if the mob passes the chain length it is pulled back to the boundary and its outward motion cancelled.
     */
    private void enforceTether(LivingEntity mob) {
        Vec3 anchor = chainAnchorPoint();
        Vec3 pos = mob.position();
        double dist = pos.distanceTo(anchor);
        if (dist <= DEFAULT_CHAIN_LENGTH || dist == 0.0) {
            return;
        }
        Vec3 dir = pos.subtract(anchor).scale(1.0 / dist);
        Vec3 clamped = anchor.add(dir.scale(DEFAULT_CHAIN_LENGTH));
        mob.setPos(clamped.x, clamped.y, clamped.z);
        Vec3 v = mob.getDeltaMovement();
        double outward = v.dot(dir);
        if (outward > 0.0) {
            mob.setDeltaMovement(v.subtract(dir.scale(outward)));
        }
        mob.hurtMarked = true;
    }

    // ---- bind point ----------------------------------------------------------------------------------------------

    /**
     * World position of the {@code gHandle} pivot — where a capture chain binds. The handle sits {@link #HANDLE_OFFSET}
     * blocks out from the mount surface along its normal, centred on the face.
     */
    public Vec3 chainAnchorPoint() {
        var pos = getBlockPos();
        var state = getBlockState();
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;

        if (!(state.getBlock() instanceof AnchorBlock)) {
            return new Vec3(cx, pos.getY() + 0.5, cz);
        }

        return switch (state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE)) {
            case FLOOR -> new Vec3(cx, pos.getY() + HANDLE_OFFSET, cz);
            case CEILING -> new Vec3(cx, pos.getY() + 1.0 - HANDLE_OFFSET, cz);
            case WALL -> {
                var normal = state.getValue(HorizontalDirectionalBlock.FACING).getNormal();
                yield new Vec3(
                    cx + normal.getX() * (HANDLE_OFFSET - 0.5),
                    pos.getY() + 0.5,
                    cz + normal.getZ() * (HANDLE_OFFSET - 0.5)
                );
            }
        };
    }

    // ---- persistence + sync --------------------------------------------------------------------------------------

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundMobId != null) {
            tag.putUUID(TAG_BOUND_MOB, boundMobId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundMobId = tag.hasUUID(TAG_BOUND_MOB) ? tag.getUUID(TAG_BOUND_MOB) : null;
        if (tag.contains(TAG_CLIENT_MOB_ID)) {
            boundMobNetId = tag.getInt(TAG_CLIENT_MOB_ID);
        }
        if (tag.contains(TAG_CLIENT_SHACKLE_SLOT)) {
            shackleSlot = tag.getInt(TAG_CLIENT_SHACKLE_SLOT);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt(TAG_CLIENT_MOB_ID, boundMobNetId);
        tag.putInt(TAG_CLIENT_SHACKLE_SLOT, shackleSlot);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
