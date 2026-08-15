package com.alien.common.gameplay.block.entity.xenomorph.head;

import com.alien.common.gameplay.block.xenomorph.head.XenomorphHeadBlock;
import com.alien.common.gameplay.block.xenomorph.head.XenomorphWallHeadBlock;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class XenomorphHeadBlockEntity extends BlockEntity {

    public XenomorphHeadBlockEntity(BlockPos pos, BlockState state) {
        super(AlienBlockEntityTypes.XENOMORPH_HEAD.get(), pos, state);
    }

    public @Nullable AlienXenomorphHeadItems.Entry entry() {
        var block = this.getBlockState().getBlock();

        if (block instanceof XenomorphHeadBlock floor) {
            return AlienXenomorphHeadItems.getOrNull(floor.itemPath());
        }

        if (block instanceof XenomorphWallHeadBlock wall) {
            return AlienXenomorphHeadItems.getOrNull(wall.itemPath());
        }

        return null;
    }
}
