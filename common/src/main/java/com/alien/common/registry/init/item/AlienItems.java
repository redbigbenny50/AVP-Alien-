package com.alien.common.registry.init.item;

import com.alien.Alien;
import com.alien.common.gameplay.item.AgileXenomorphHeadShieldItem;
import com.alien.common.gameplay.item.CaptureChainItem;
import com.alien.common.gameplay.item.CrusherHeadItem;
import com.alien.common.gameplay.item.CrusherHeadShieldItem;
import com.alien.common.gameplay.item.InhibitorItem;
import com.alien.common.gameplay.item.PoisonJellyItem;
import com.alien.common.gameplay.item.QueenHeadItem;
import com.alien.common.gameplay.item.QueenHeadShieldItem;
import com.alien.common.gameplay.item.RawIrradiatedJellyItem;
import com.alien.common.gameplay.item.RawRoyalJellyItem;
import com.alien.common.gameplay.item.RawScourgeJellyItem;
import com.alien.common.gameplay.item.SpitterHeadShieldItem;
import com.alien.common.gameplay.item.TrackerItem;
import com.alien.common.gameplay.item.TrackingPdaItem;
import com.alien.common.gameplay.item.XenomorphHeadItem;
import com.alien.common.gameplay.item.XenomorphHeadShieldItem;
import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.key.AlienJukeboxSongKeys;
import com.blib.api.common.registry.v1.BLibHolder;
import com.blib.api.common.registry.v1.BLibRegistry;
import com.blib.api.common.registry.v1.impl.BLibDecoratedPotPatternRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiscFragmentItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class AlienItems {

    public static final BLibRegistry<Item> REGISTRY = Alien.MOD.registries().create(BuiltInRegistries.ITEM);

    private static final BLibDecoratedPotPatternRegistry DECORATED_POT_PATTERN_REGISTRY = Alien.MOD.registries()
        .createDecoratedPotPatternRegistry();

    public static final BLibHolder<Item> ABERRANT_CHITIN = create(
        "aberrant_chitin",
        new Item.Properties()
    );

    public static final BLibHolder<Item> ABERRANT_RESIN_BALL = create(
        "aberrant_resin_ball",
        new Item.Properties()
    );

    public static final BLibHolder<Item> ALIEN_MUSIC_DISC_1 = create(
        "alien_music_disc_1",
        new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(AlienJukeboxSongKeys.ALIEN_MUSIC_1)
    );

    public static final BLibHolder<Item> ALIEN_MUSIC_DISC_1_FRAGMENT = create(
        "alien_music_disc_1_fragment",
        () -> new DiscFragmentItem(new Item.Properties())
    );

    public static final BLibHolder<Item> CHITIN = create("chitin");

    public static final BLibHolder<Item> IRRADIATED_CHITIN = create("irradiated_chitin");

    public static final BLibHolder<Item> IRRADIATED_RESIN_BALL = create("irradiated_resin_ball");

    public static final BLibHolder<Item> NETHER_CHITIN = create("nether_chitin", new Item.Properties().fireResistant());

    public static final BLibHolder<Item> NETHER_RESIN_BALL = create(
        "nether_resin_ball",
        new Item.Properties().fireResistant()
    );

    public static final BLibHolder<Item> OVOID_POTTERY_SHERD = create("ovoid_pottery_sherd");

    public static final BLibHolder<Item> PARASITE_POTTERY_SHERD = create("parasite_pottery_sherd");

    public static final BLibHolder<Item> PLATED_CHITIN = create("plated_chitin");

    public static final BLibHolder<Item> PLATED_ABERRANT_CHITIN = create(
        "plated_aberrant_chitin",
        new Item.Properties()
    );

    public static final BLibHolder<Item> PLATED_IRRADIATED_CHITIN = create("plated_irradiated_chitin");

    public static final BLibHolder<Item> PLATED_NETHER_CHITIN = create(
        "plated_nether_chitin",
        new Item.Properties().fireResistant()
    );

    public static final BLibHolder<Item> POISON_JELLY = create("poison_jelly", PoisonJellyItem::new);

    public static final BLibHolder<Item> ANCHOR = create(
        "anchor",
        () -> new BlockItem(AlienBlocks.ANCHOR.get(), new Item.Properties())
    );

    public static final BLibHolder<Item> JELLY_VAT = create(
        "jelly_vat",
        () -> new BlockItem(AlienBlocks.JELLY_VAT.get(), new Item.Properties())
    );

    public static final BLibHolder<Item> CAPTURE_CHAIN = create(
        "capture_chain",
        () -> new CaptureChainItem(new Item.Properties())
    );

    public static final BLibHolder<Item> INHIBITOR = create(
        "inhibitor",
        () -> new InhibitorItem(new Item.Properties().stacksTo(16))
    );

    public static final BLibHolder<Item> TRACKER = create(
        "tracker",
        () -> new TrackerItem(new Item.Properties().stacksTo(16))
    );

    public static final BLibHolder<Item> TRACKING_PDA = create(
        "tracking_pda",
        () -> new TrackingPdaItem(new Item.Properties().stacksTo(1))
    );

    public static final BLibHolder<Item> QUEEN_HEAD = create(
        "queen_head",
        () -> new QueenHeadItem(
            AlienBlocks.QUEEN_HEAD.get(),
            AlienBlocks.QUEEN_WALL_HEAD.get(),
            new Item.Properties().stacksTo(1)
        )
    );

    public static final BLibHolder<Item> ABERRANT_QUEEN_HEAD = create(
        "aberrant_queen_head",
        () -> new QueenHeadItem(
            AlienBlocks.ABERRANT_QUEEN_HEAD.get(),
            AlienBlocks.ABERRANT_QUEEN_WALL_HEAD.get(),
            new Item.Properties().stacksTo(1)
        )
    );

    public static final BLibHolder<Item> IRRADIATED_QUEEN_HEAD = create(
        "irradiated_queen_head",
        () -> new QueenHeadItem(
            AlienBlocks.IRRADIATED_QUEEN_HEAD.get(),
            AlienBlocks.IRRADIATED_QUEEN_WALL_HEAD.get(),
            new Item.Properties().stacksTo(1)
        )
    );

    public static final BLibHolder<Item> NETHER_QUEEN_HEAD = create(
        "nether_queen_head",
        () -> new QueenHeadItem(
            AlienBlocks.NETHER_QUEEN_HEAD.get(),
            AlienBlocks.NETHER_QUEEN_WALL_HEAD.get(),
            new Item.Properties().stacksTo(1).fireResistant()
        )
    );

    public static final BLibHolder<Item> QUEEN_HEAD_SHIELD = create(
        "queen_head_shield",
        () -> new QueenHeadShieldItem(new Item.Properties().stacksTo(1).durability(512))
    );

    public static final BLibHolder<Item> ABERRANT_QUEEN_HEAD_SHIELD = create(
        "aberrant_queen_head_shield",
        () -> new QueenHeadShieldItem(new Item.Properties().stacksTo(1).durability(512))
    );

    public static final BLibHolder<Item> IRRADIATED_QUEEN_HEAD_SHIELD = create(
        "irradiated_queen_head_shield",
        () -> new QueenHeadShieldItem(new Item.Properties().stacksTo(1).durability(512))
    );

    public static final BLibHolder<Item> NETHER_QUEEN_HEAD_SHIELD = create(
        "nether_queen_head_shield",
        () -> new QueenHeadShieldItem(new Item.Properties().stacksTo(1).durability(512).fireResistant())
    );

    public static final BLibHolder<Item> CRUSHER_HEAD = create(
        "crusher_head",
        () -> new CrusherHeadItem(
            AlienBlocks.CRUSHER_HEAD.get(),
            AlienBlocks.CRUSHER_WALL_HEAD.get(),
            new Item.Properties().stacksTo(1)
        )
    );

    public static final BLibHolder<Item> ABERRANT_CRUSHER_HEAD = create(
        "aberrant_crusher_head",
        () -> new CrusherHeadItem(
            AlienBlocks.ABERRANT_CRUSHER_HEAD.get(),
            AlienBlocks.ABERRANT_CRUSHER_WALL_HEAD.get(),
            new Item.Properties().stacksTo(1)
        )
    );

    public static final BLibHolder<Item> IRRADIATED_CRUSHER_HEAD = create(
        "irradiated_crusher_head",
        () -> new CrusherHeadItem(
            AlienBlocks.IRRADIATED_CRUSHER_HEAD.get(),
            AlienBlocks.IRRADIATED_CRUSHER_WALL_HEAD.get(),
            new Item.Properties().stacksTo(1)
        )
    );

    public static final BLibHolder<Item> NETHER_CRUSHER_HEAD = create(
        "nether_crusher_head",
        () -> new CrusherHeadItem(
            AlienBlocks.NETHER_CRUSHER_HEAD.get(),
            AlienBlocks.NETHER_CRUSHER_WALL_HEAD.get(),
            new Item.Properties().stacksTo(1).fireResistant()
        )
    );

    public static final BLibHolder<Item> CRUSHER_HEAD_SHIELD = create(
        "crusher_head_shield",
        () -> new CrusherHeadShieldItem(new Item.Properties().stacksTo(1).durability(512))
    );

    public static final BLibHolder<Item> ABERRANT_CRUSHER_HEAD_SHIELD = create(
        "aberrant_crusher_head_shield",
        () -> new CrusherHeadShieldItem(new Item.Properties().stacksTo(1).durability(512))
    );

    public static final BLibHolder<Item> IRRADIATED_CRUSHER_HEAD_SHIELD = create(
        "irradiated_crusher_head_shield",
        () -> new CrusherHeadShieldItem(new Item.Properties().stacksTo(1).durability(512))
    );

    public static final BLibHolder<Item> NETHER_CRUSHER_HEAD_SHIELD = create(
        "nether_crusher_head_shield",
        () -> new CrusherHeadShieldItem(new Item.Properties().stacksTo(1).durability(512).fireResistant())
    );

    public static final BLibHolder<Item> RAW_ROYAL_JELLY = create("raw_royal_jelly", RawRoyalJellyItem::new);

    public static final BLibHolder<Item> RAW_SCOURGE_JELLY = create("raw_scourge_jelly", RawScourgeJellyItem::new);

    public static final BLibHolder<Item> RAW_IRRADIATED_JELLY =
        create("raw_irradiated_jelly", RawIrradiatedJellyItem::new);

    public static final BLibHolder<Item> RESIN_BALL = create("resin_ball");

    public static final BLibHolder<Item> ROYALTY_POTTERY_SHERD = create("royalty_pottery_sherd");

    public static final BLibHolder<Item> VECTOR_POTTERY_SHERD = create("vector_pottery_sherd");

    public static BLibHolder<Item> create(String name) {
        return create(name, new Item.Properties());
    }

    static BLibHolder<Item> createXenomorphHead(
        String name,
        Supplier<? extends Block> standingBlock,
        Supplier<? extends Block> wallBlock,
        boolean fireResistant
    ) {
        return create(
            name,
            () -> new XenomorphHeadItem(
                standingBlock.get(),
                wallBlock.get(),
                xenomorphHeadProperties(fireResistant)
            )
        );
    }

    static BLibHolder<Item> createXenomorphHeadShield(String name, boolean fireResistant) {
        return create(name, () -> new XenomorphHeadShieldItem(xenomorphHeadShieldProperties(fireResistant)));
    }

    static BLibHolder<Item> createAgileXenomorphHeadShield(String name, boolean fireResistant) {
        return create(name, () -> new AgileXenomorphHeadShieldItem(xenomorphHeadShieldProperties(fireResistant)));
    }

    static BLibHolder<Item> createSpitterHeadShield(String name, AlienVariant variant, boolean fireResistant) {
        return create(name, () -> new SpitterHeadShieldItem(variant, xenomorphHeadShieldProperties(fireResistant)));
    }

    private static Item.Properties xenomorphHeadProperties(boolean fireResistant) {
        var properties = new Item.Properties().stacksTo(1);
        return fireResistant ? properties.fireResistant() : properties;
    }

    private static Item.Properties xenomorphHeadShieldProperties(boolean fireResistant) {
        var properties = new Item.Properties().stacksTo(1).durability(512);
        return fireResistant ? properties.fireResistant() : properties;
    }

    private static BLibHolder<Item> create(String name, Item.Properties properties) {
        return create(name, () -> new Item(properties));
    }

    private static <T extends Item> BLibHolder<T> create(String name, Supplier<T> itemSupplier) {
        return REGISTRY.createHolder(name, itemSupplier);
    }

    public static void initialize() {
        AlienXenomorphHeadItems.initialize();
        REGISTRY.registerAll();
        DECORATED_POT_PATTERN_REGISTRY.register("ovoid_pottery_pattern", OVOID_POTTERY_SHERD);
        DECORATED_POT_PATTERN_REGISTRY.register("parasite_pottery_pattern", PARASITE_POTTERY_SHERD);
        DECORATED_POT_PATTERN_REGISTRY.register("royalty_pottery_pattern", ROYALTY_POTTERY_SHERD);
        DECORATED_POT_PATTERN_REGISTRY.register("vector_pottery_pattern", VECTOR_POTTERY_SHERD);
    }
}
