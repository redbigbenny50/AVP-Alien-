package com.alien.common.gameplay.block.entity.jelly;

import com.alien.common.gameplay.block.jelly.JellyType;
import com.alien.common.gameplay.block.jelly.JellyVatBlock;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.model.alien.variant.AlienVariant;
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
import net.minecraft.world.level.ChunkPos;
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

    private static final String TAG_COMMITTED = "TypeCommitted";

    private static final String TAG_HIVE_VISIBLE = "HiveVisible";

    private static final String TAG_STRAIN = "Strain";

    private int fillLevel;

    /**
     * Whether this vat's jelly type is locked in. A freshly player-placed vat starts uncommitted and accepts either
     * royal or scourge on its first fill; that first fill commits the type. Emptying the vat (player withdrawal) clears
     * the commitment so it can be re-typed. Structure-placed vats are committed the moment they hold jelly.
     */
    private boolean typeCommitted;

    /**
     * Whether the hive may see this vat as jelly storage. Set for player-placed vats; consumed by the (future) economy.
     */
    private boolean hiveVisible;

    /**
     * Which strain of hive this vat belongs to. Purely cosmetic - it selects the vat's shell texture, since every
     * strain grows its own - and it is SYNCED, unlike the two flags above, because only the client needs it.
     * <p>
     * Adopted from the owning hive the moment the vat first holds jelly, which is also when a structure-placed vat
     * commits its type. A vat outside any hive (a player's, sitting in a basement) stays NORMAL.
     */
    private AlienVariant strain = AlienVariant.NORMAL;

    public JellyVatBlockEntity(BlockPos pos, BlockState state) {
        super(AlienBlockEntityTypes.JELLY_VAT.get(), pos, state);
    }

    public int getFillLevel() {
        return fillLevel;
    }

    public boolean isTypeCommitted() {
        return typeCommitted;
    }

    public boolean isHiveVisible() {
        return hiveVisible;
    }

    /** Marks this vat as hive-visible storage (groundwork; the jelly economy will consume this later). */
    public void setHiveVisible(boolean hiveVisible) {
        this.hiveVisible = hiveVisible;
        setChanged();
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

    /**
     * Commits this vat to a jelly type (updates the block-state property and locks it). Called on the first fill of an
     * uncommitted vat. No-op if already committed to that type; ignored if the requested type differs from a committed
     * one (callers should check {@link #canAccept(JellyType)} first).
     */
    public void commitType(JellyType type) {
        if (typeCommitted && getJellyType() == type) {
            return;
        }
        this.typeCommitted = true;
        adoptHiveStrain();
        if (level != null && getJellyType() != type) {
            level.setBlock(getBlockPos(), getBlockState().setValue(JellyVatBlock.JELLY_TYPE, type), Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    public AlienVariant getStrain() {
        return strain;
    }

    /**
     * THE WIRING THAT WAS NEVER CONNECTED: the strain field, the sync, and the renderer's four shells all existed,
     * but nothing ever CALLED {@link #adoptHiveStrain()}, so every vat stayed NORMAL and every hive's vats rendered
     * the normal shell until its first FILL committed a jelly type (commitType is the one caller) - so freshly
     * grown, still-empty vats sat in nether/aberrant/irradiated rooms wearing the normal skin ([stated] "nether
     * hive vats dont use the nether texture when spawning in the rooms"). Adopting when the block entity joins a
     * level covers every path at once: freshly grown vats, player-placed vats inside a hive, AND every
     * already-placed vat in existing worlds - they self-heal the next time their chunk loads. Outside any hive the
     * lookup resolves NORMAL, the correct fallback; adoptHiveStrain early-outs when nothing changed; and a hive
     * CONVERTED to another strain (nuke) re-adopts on its next chunk load the same way.
     * <p>
     * setLevel rather than onLoad: onLoad is a NeoForge patch and does not exist in the multiloader common module.
     * setLevel is vanilla, runs on the main thread as the block entity joins the level (fresh placement and chunk
     * load alike), and the position is already set - everything adoptHiveStrain needs. The client-side call falls
     * out of adoptHiveStrain's own ServerLevel guard.
     */
    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        adoptHiveStrain();
    }

    /**
     * Takes the strain of whichever hive owns this chunk, if any. Server side only; pushes a block update so the client
     * renderer picks up the new shell.
     */
    public void adoptHiveStrain() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        var location = HiveLocationRegistry.INSTANCE.getByChunk(serverLevel.dimension(), new ChunkPos(getBlockPos()));
        var variant = location == null ? AlienVariant.NORMAL : location.lineageVariantOrNull();
        var resolved = variant == null ? AlienVariant.NORMAL : variant;
        if (resolved == strain) {
            return;
        }

        this.strain = resolved;
        setChanged();
        serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** True if this vat can accept the given jelly type: either uncommitted, or committed to that same type. */
    public boolean canAccept(JellyType type) {
        return !typeCommitted || getJellyType() == type;
    }

    /** Empties the vat and clears its type commitment so it can be re-typed. Plays the drain sound if it held jelly. */
    public void emptyAndUncommit() {
        setFillLevel(0);
        this.typeCommitted = false;
        setChanged();
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.fillLevel = Math.max(0, Math.min(MAX_FILL, tag.getInt(TAG_FILL_LEVEL)));
        this.typeCommitted = tag.getBoolean(TAG_COMMITTED);
        this.hiveVisible = tag.getBoolean(TAG_HIVE_VISIBLE);
        this.strain = AlienVariant.getById(tag.getInt(TAG_STRAIN)).unwrapOr(AlienVariant.NORMAL);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_FILL_LEVEL, fillLevel);
        tag.putBoolean(TAG_COMMITTED, typeCommitted);
        tag.putBoolean(TAG_HIVE_VISIBLE, hiveVisible);
        tag.putInt(TAG_STRAIN, strain.getId());
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(@NotNull HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt(TAG_FILL_LEVEL, fillLevel);
        // The shell texture is chosen client side, so the strain has to ride along with the fill level.
        tag.putInt(TAG_STRAIN, strain.getId());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
