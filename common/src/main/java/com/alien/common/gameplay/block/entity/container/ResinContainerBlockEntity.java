package com.alien.common.gameplay.block.entity.container;

import com.alien.common.gameplay.block.ResinContainerBlock;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * The resin container's inventory: a double chest's worth of slots in a single block.
 * <h2>⚠⚠ IT REUSES VANILLA'S 9x6 MENU, AND THAT IS THE WHOLE POINT</h2> {@code ChestMenu.sixRows} gives the
 * double-chest screen for free - no new MenuType, no menu class, no screen class, and nothing to register on the
 * client. avp_alien had no container infrastructure at all before this, so writing one would have meant a menu
 * registry, a screen, and a client-side binding for a UI the player already knows.
 * <h2>⚠ RandomizableContainerBlockEntity, NOT a bare BlockEntity</h2> It brings the save/load of the item list, the
 * loot-table hook, and - critically - the component round-trip that lets a broken container carry its contents onto the
 * dropped item, which is the shulker behaviour he asked for.
 */
public class ResinContainerBlockEntity extends RandomizableContainerBlockEntity {

    /** [stated] "the inventory space of a double chest" - 9 wide, 6 tall. */
    public static final int CONTAINER_SIZE = 54;

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    public ResinContainerBlockEntity(BlockPos pos, BlockState state) {
        super(AlienBlockEntityTypes.RESIN_CONTAINER.get(), pos, state);
    }

    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("container.avp_alien.resin_container");
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory) {
        return ChestMenu.sixRows(containerId, inventory, this);
    }

    /**
     * ⭐ THE SALVAGE ENTRY POINT. The hive pushes destroyed ore blocks and looted chest contents in through here.
     * <p>
     * ⚠ RETURNS WHAT IT COULD NOT TAKE, rather than voiding it or throwing. The caller decides what happens to the
     * remainder - which is what lets the salvage system move on to the next of the chamber's eight containers instead
     * of losing a stack at the first full one.
     * </p>
     */
    public ItemStack offerSalvage(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        var remainder = stack.copy();

        // Merge into partial stacks first, so a container does not fill with half-empty slots of the same ore.
        for (var slot = 0; slot < CONTAINER_SIZE && !remainder.isEmpty(); slot++) {
            var existing = items.get(slot);

            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remainder)) {
                continue;
            }

            var room = Math.min(existing.getMaxStackSize(), getMaxStackSize()) - existing.getCount();

            if (room <= 0) {
                continue;
            }

            var moved = Math.min(room, remainder.getCount());
            existing.grow(moved);
            remainder.shrink(moved);
        }

        for (var slot = 0; slot < CONTAINER_SIZE && !remainder.isEmpty(); slot++) {
            if (!items.get(slot).isEmpty()) {
                continue;
            }

            items.set(slot, remainder.copy());
            remainder = ItemStack.EMPTY;
        }

        if (remainder.getCount() != stack.getCount()) {
            setChanged();
        }

        return remainder;
    }

    /** True when every slot is occupied and full - the signal to move to the next container in the chamber. */
    public boolean isFullFor(ItemStack stack) {
        return offerSalvageWouldRemain(stack);
    }

    private boolean offerSalvageWouldRemain(ItemStack stack) {
        for (var slot = 0; slot < CONTAINER_SIZE; slot++) {
            var existing = items.get(slot);

            if (existing.isEmpty()) {
                return false;
            }

            if (
                ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), getMaxStackSize())
            ) {
                return false;
            }
        }

        return true;
    }

    /**
     * ⭐⭐ NO CONTAINER INSIDE A CONTAINER. [stated] "since they can hold inventory they shouldnt be able to be stored
     * inside eachother like how shulkers cant."
     * <p>
     * ⚠ THIS ONE OVERRIDE COVERS BOTH ROUTES IN. {@code Slot.mayPlace} defers to it, so the GUI refuses the drag, AND
     * hoppers consult it through {@code canPlaceItemInContainer}, so they cannot sneak one in either. There is no third
     * path to guard.
     * </p>
     * <p>
     * ⚠⚠ THE SECOND CLAUSE CLOSES A HOLE VANILLA STILL HAS. Refusing only resin containers would leave the INDIRECT
     * nesting open: a vanilla shulker box refuses shulkers but happily accepts a resin container, so container →
     * shulker → container would nest anyway and the recursion is back. Refusing any stack that carries a NON-EMPTY
     * {@code CONTAINER} component blocks that, and any modded container with contents, without touching empty ones - an
     * empty shulker is just a block and there is no reason to reject it.
     * </p>
     * <p>
     * ⚠ RESIN CONTAINERS ARE REFUSED EVEN WHEN EMPTY, matching shulker semantics exactly ({@code Block.byItem(...)
     * instanceof ShulkerBoxBlock} in vanilla) - the point is that the SHAPE nests, not that this particular one is full
     * right now.
     * </p>
     */
    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        if (Block.byItem(stack.getItem()) instanceof ResinContainerBlock) {
            return false;
        }

        var contents = stack.get(DataComponents.CONTAINER);

        return contents == null || contents.nonEmptyStream().findAny().isEmpty();
    }

    // ─── OPEN STATE, FOR THE ANIMATION ───────────────────────────────────────────────────────────────────────────

    /** How many players have it open. Server-authoritative; the client learns of it through a block event. */
    private int openCount;

    /**
     * ⭐⭐ THE ANIMATION'S ONLY INPUT. Everything the renderer needs is "is anyone looking inside".
     * <p>
     * ⚠ COUNTED, NOT A BOOLEAN. Two players can have the same container open; a flag would slam it shut the moment the
     * first of them walked away, mid-animation, while the second was still browsing.
     * </p>
     */
    public boolean isOpen() {
        return openCount > 0;
    }

    @Override
    public void startOpen(@NotNull Player player) {
        if (player.isSpectator() || level == null) {
            return;
        }

        openCount = Math.max(0, openCount) + 1;
        broadcastOpenCount();
    }

    @Override
    public void stopOpen(@NotNull Player player) {
        if (player.isSpectator() || level == null) {
            return;
        }

        openCount = Math.max(0, openCount - 1);
        broadcastOpenCount();
    }

    /**
     * ⚠ A BLOCK EVENT, NOT A BLOCK ENTITY SYNC. This is the same mechanism vanilla chests use for their lids: it is a
     * two-byte message to everyone tracking the chunk, where a full block-entity update would resend all 54 slots every
     * time somebody opened or closed the lid.
     */
    private void broadcastOpenCount() {
        if (level != null) {
            level.blockEvent(getBlockPos(), getBlockState().getBlock(), OPEN_COUNT_EVENT, openCount);
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == OPEN_COUNT_EVENT) {
            openCount = param;
            return true;
        }

        return super.triggerEvent(id, param);
    }

    /** ⚠ Must be forwarded by the block's triggerEvent override, or the client never receives it. */
    public static final int OPEN_COUNT_EVENT = 1;

    public static Container asContainer(ResinContainerBlockEntity blockEntity) {
        return blockEntity;
    }
}
