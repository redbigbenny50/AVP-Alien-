package com.alien.common.registry.init;

import com.alien.Alien;
import com.alien.common.gameplay.block.entity.capture.anchor.AnchorBlockEntity;
import com.alien.common.gameplay.block.entity.container.ResinContainerBlockEntity;
import com.alien.common.gameplay.block.entity.crusher.CrusherHeadBlockEntity;
import com.alien.common.gameplay.block.entity.jelly.JellyVatBlockEntity;
import com.alien.common.gameplay.block.entity.queen.QueenHeadBlockEntity;
import com.alien.common.gameplay.block.entity.resin.node.ResinNodeBlockEntity;
import com.alien.common.gameplay.block.entity.resin.vent.ResinVentBlockEntity;
import com.alien.common.gameplay.block.entity.xenomorph.head.XenomorphHeadBlockEntity;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;
import java.util.stream.Stream;

public class AlienBlockEntityTypes {

    private static final BLibRegistry<BlockEntityType<?>> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.BLOCK_ENTITY_TYPE);

    /** ⚠ ONE block entity type for all four strains - they differ only in art, never in behaviour. */
    public static final BLibHolder<BlockEntityType<ResinContainerBlockEntity>> RESIN_CONTAINER = create(
        "resin_container",
        () -> BlockEntityType.Builder.of(
            ResinContainerBlockEntity::new,
            AlienBlocks.RESIN_CONTAINER.get(),
            AlienBlocks.NETHER_RESIN_CONTAINER.get(),
            AlienBlocks.ABERRANT_RESIN_CONTAINER.get(),
            AlienBlocks.IRRADIATED_RESIN_CONTAINER.get()
        )
    );

    public static final BLibHolder<BlockEntityType<ResinNodeBlockEntity>> RESIN_NODE = create(
        "resin_node",
        () -> BlockEntityType.Builder.of(
            ResinNodeBlockEntity::new,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_NODE.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_NODE.get(),
            NetherAlienResinBlocks.NETHER_RESIN_NODE.get(),
            AlienResinBlocks.RESIN_NODE.get()
        )
    );

    public static final BLibHolder<BlockEntityType<ResinVentBlockEntity>> RESIN_VENT = create(
        "resin_vent",
        () -> BlockEntityType.Builder.of(
            ResinVentBlockEntity::new,
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VENT.get(),
            AberrantAlienResinBlocks.ABERRANT_RESIN_VENT.get(),
            NetherAlienResinBlocks.NETHER_RESIN_VENT.get(),
            AlienResinBlocks.RESIN_VENT.get()
        )
    );

    public static final BLibHolder<BlockEntityType<QueenHeadBlockEntity>> QUEEN_HEAD = create(
        "queen_head",
        () -> BlockEntityType.Builder.of(
            QueenHeadBlockEntity::new,
            AlienBlocks.QUEEN_HEAD.get(),
            AlienBlocks.QUEEN_WALL_HEAD.get(),
            AlienBlocks.ABERRANT_QUEEN_HEAD.get(),
            AlienBlocks.ABERRANT_QUEEN_WALL_HEAD.get(),
            AlienBlocks.IRRADIATED_QUEEN_HEAD.get(),
            AlienBlocks.IRRADIATED_QUEEN_WALL_HEAD.get(),
            AlienBlocks.NETHER_QUEEN_HEAD.get(),
            AlienBlocks.NETHER_QUEEN_WALL_HEAD.get()
        )
    );

    public static final BLibHolder<BlockEntityType<CrusherHeadBlockEntity>> CRUSHER_HEAD = create(
        "crusher_head",
        () -> BlockEntityType.Builder.of(
            CrusherHeadBlockEntity::new,
            AlienBlocks.CRUSHER_HEAD.get(),
            AlienBlocks.CRUSHER_WALL_HEAD.get(),
            AlienBlocks.ABERRANT_CRUSHER_HEAD.get(),
            AlienBlocks.ABERRANT_CRUSHER_WALL_HEAD.get(),
            AlienBlocks.IRRADIATED_CRUSHER_HEAD.get(),
            AlienBlocks.IRRADIATED_CRUSHER_WALL_HEAD.get(),
            AlienBlocks.NETHER_CRUSHER_HEAD.get(),
            AlienBlocks.NETHER_CRUSHER_WALL_HEAD.get()
        )
    );

    public static final BLibHolder<BlockEntityType<XenomorphHeadBlockEntity>> XENOMORPH_HEAD = create(
        "xenomorph_head",
        () -> BlockEntityType.Builder.of(XenomorphHeadBlockEntity::new, xenomorphHeadBlocks())
    );

    public static final BLibHolder<BlockEntityType<AnchorBlockEntity>> ANCHOR = create(
        "anchor",
        () -> BlockEntityType.Builder.of(AnchorBlockEntity::new, AlienBlocks.ANCHOR.get())
    );

    public static final BLibHolder<BlockEntityType<JellyVatBlockEntity>> JELLY_VAT = create(
        "jelly_vat",
        () -> BlockEntityType.Builder.of(JellyVatBlockEntity::new, AlienBlocks.JELLY_VAT.get())
    );

    private static Block[] xenomorphHeadBlocks() {
        return AlienXenomorphHeadItems.GENERIC_BLOCK_ENTRIES.stream()
            .flatMap(entry -> Stream.of(entry.standingBlock().get(), entry.wallBlock().get()))
            .toArray(Block[]::new);
    }

    private static <T extends BlockEntity> BLibHolder<BlockEntityType<T>> create(
        String path,
        Supplier<BlockEntityType.Builder<T>> builder
    ) {
        return REGISTRY.createHolder(path, () -> builder.get().build(null));
    }

    public static void initialize() {
        REGISTRY.registerAll();
    }
}
