package com.alien.common.gameplay.block.entity.jelly;

import com.alien.common.gameplay.block.jelly.JellyType;
import com.alien.common.gameplay.block.jelly.JellyVatBlock;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A jelly vat: a floor block that visibly fills with royal or scourge jelly. The fill level (0..{@link #MAX_FILL})
 * lives here in the block entity because it changes often at runtime (the hive's bank trickles jelly in; the hive
 * drains it when needed); the jelly TYPE lives on the block state ({@link JellyVatBlock#JELLY_TYPE}) because it is
 * fixed for the vat's lifetime and must be readable from a structure piece's palette.
 * <p>
 * Raising the level plays a slime-block place sound; lowering it plays a slime-block break sound. The level is synced
 * to clients so the {@code AzBlockEntityRenderer} can show the matching fill-stage bone.
 */
public class JellyVatBlockEntity extends BlockEntity {

    public static final int MAX_FILL = 9;

    private static final String TAG_FILL_LEVEL = "FillLevel";

    private int fillLevel;

    public JellyVatBlockEntity(BlockPos pos, BlockState state) {
        super(AlienBlockEntityTypes.JELLY_VAT.get(), pos, state);
    }

    public int getFillLevel() {
        return fillLevel;
    }

    /** The jelly type this vat holds, read from its block state. */
    public JellyType getJellyType() {
        return getBlockState().getValue(JellyVatBlock.JELLY_TYPE);
    }

    /**
     * Sets the fill level (clamped to 0..{@link #MAX_FILL}). Plays a slime place/break sound when the level
     * rises/falls, syncs the change to clients, and marks the block entity dirty. No-op if the level is unchanged.
     */
    public void setFillLevel(int newLevel) {
        int clamped = Math.max(0, Math.min(MAX_FILL, newLevel));
        if (clamped == fillLevel) {
            return;
        }
        boolean rising = clamped > fillLevel;
        this.fillLevel = clamped;

        if (level != null && !level.isClientSide) {
            var sound = rising ? SoundEvents.SLIME_BLOCK_PLACE : SoundEvents.SLIME_BLOCK_BREAK;
            level.playSound(null, getBlockPos(), sound, SoundSource.BLOCKS, 0.8F, 1.0F);
            setChanged();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Convenience: raise the fill by one level (the bank-trickle step). */
    public void addOneLevel() {
        setFillLevel(fillLevel + 1);
    }

    /** Convenience: lower the fill by one level (the drain step). */
    public void removeOneLevel() {
        setFillLevel(fillLevel - 1);
    }

    public boolean isFull() {
        return fillLevel >= MAX_FILL;
    }

    public boolean isEmpty() {
        return fillLevel <= 0;
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.fillLevel = Math.max(0, Math.min(MAX_FILL, tag.getInt(TAG_FILL_LEVEL)));
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_FILL_LEVEL, fillLevel);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt(TAG_FILL_LEVEL, fillLevel);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
