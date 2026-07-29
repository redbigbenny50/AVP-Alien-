package com.alien.common.registry.init.item;

import com.alien.common.model.alien.variant.AlienVariant;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.block.AlienBlocks;
import com.blib.api.common.registry.v1.BLibHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AlienXenomorphHeadItems {

    public static final List<Entry> ALL = List.of(
        existing(
            "queen_head",
            "Queen",
            "queen",
            AlienEntityTypes.QUEEN,
            AlienBlocks.QUEEN_HEAD,
            AlienBlocks.QUEEN_WALL_HEAD,
            AlienItems.QUEEN_HEAD,
            AlienItems.QUEEN_HEAD_SHIELD
        ),
        existing(
            "aberrant_queen_head",
            "Aberrant Queen",
            "queen",
            AlienEntityTypes.ABERRANT_QUEEN,
            AlienBlocks.ABERRANT_QUEEN_HEAD,
            AlienBlocks.ABERRANT_QUEEN_WALL_HEAD,
            AlienItems.ABERRANT_QUEEN_HEAD,
            AlienItems.ABERRANT_QUEEN_HEAD_SHIELD
        ),
        existing(
            "irradiated_queen_head",
            "Irradiated Queen",
            "queen",
            AlienEntityTypes.IRRADIATED_QUEEN,
            AlienBlocks.IRRADIATED_QUEEN_HEAD,
            AlienBlocks.IRRADIATED_QUEEN_WALL_HEAD,
            AlienItems.IRRADIATED_QUEEN_HEAD,
            AlienItems.IRRADIATED_QUEEN_HEAD_SHIELD
        ),
        existing(
            "nether_queen_head",
            "Nether Queen",
            "queen",
            AlienEntityTypes.NETHER_QUEEN,
            AlienBlocks.NETHER_QUEEN_HEAD,
            AlienBlocks.NETHER_QUEEN_WALL_HEAD,
            AlienItems.NETHER_QUEEN_HEAD,
            AlienItems.NETHER_QUEEN_HEAD_SHIELD
        ),
        existing(
            "crusher_head",
            "Crusher",
            "crusher",
            AlienEntityTypes.CRUSHER,
            AlienBlocks.CRUSHER_HEAD,
            AlienBlocks.CRUSHER_WALL_HEAD,
            AlienItems.CRUSHER_HEAD,
            AlienItems.CRUSHER_HEAD_SHIELD
        ),
        existing(
            "aberrant_crusher_head",
            "Aberrant Crusher",
            "crusher",
            AlienEntityTypes.ABERRANT_CRUSHER,
            AlienBlocks.ABERRANT_CRUSHER_HEAD,
            AlienBlocks.ABERRANT_CRUSHER_WALL_HEAD,
            AlienItems.ABERRANT_CRUSHER_HEAD,
            AlienItems.ABERRANT_CRUSHER_HEAD_SHIELD
        ),
        existing(
            "irradiated_crusher_head",
            "Irradiated Crusher",
            "crusher",
            AlienEntityTypes.IRRADIATED_CRUSHER,
            AlienBlocks.IRRADIATED_CRUSHER_HEAD,
            AlienBlocks.IRRADIATED_CRUSHER_WALL_HEAD,
            AlienItems.IRRADIATED_CRUSHER_HEAD,
            AlienItems.IRRADIATED_CRUSHER_HEAD_SHIELD
        ),
        existing(
            "nether_crusher_head",
            "Nether Crusher",
            "crusher",
            AlienEntityTypes.NETHER_CRUSHER,
            AlienBlocks.NETHER_CRUSHER_HEAD,
            AlienBlocks.NETHER_CRUSHER_WALL_HEAD,
            AlienItems.NETHER_CRUSHER_HEAD,
            AlienItems.NETHER_CRUSHER_HEAD_SHIELD
        ),
        item("drone_head", "Drone", "drone", AlienEntityTypes.DRONE, false),
        item("aberrant_drone_head", "Aberrant Drone", "drone", AlienEntityTypes.ABERRANT_DRONE, false),
        item("irradiated_drone_head", "Irradiated Drone", "drone", AlienEntityTypes.IRRADIATED_DRONE, false),
        item("nether_drone_head", "Nether Drone", "drone", AlienEntityTypes.NETHER_DRONE, true),
        item("warrior_head", "Warrior", "warrior", AlienEntityTypes.WARRIOR, false),
        item("aberrant_warrior_head", "Aberrant Warrior", "warrior", AlienEntityTypes.ABERRANT_WARRIOR, false),
        item("irradiated_warrior_head", "Irradiated Warrior", "warrior", AlienEntityTypes.IRRADIATED_WARRIOR, false),
        item("nether_warrior_head", "Nether Warrior", "warrior", AlienEntityTypes.NETHER_WARRIOR, true),
        agileItem("runner_head", "Runner", "runner", AlienEntityTypes.RUNNER, false),
        agileItem("aberrant_runner_head", "Aberrant Runner", "runner", AlienEntityTypes.ABERRANT_RUNNER, false),
        agileItem("irradiated_runner_head", "Irradiated Runner", "runner", AlienEntityTypes.IRRADIATED_RUNNER, false),
        agileItem("nether_runner_head", "Nether Runner", "runner", AlienEntityTypes.NETHER_RUNNER, true),
        spitterItem("spitter_head", "Spitter", AlienVariant.NORMAL, AlienEntityTypes.SPITTER, false),
        spitterItem("aberrant_spitter_head", "Aberrant Spitter", AlienVariant.ABERRANT, AlienEntityTypes.ABERRANT_SPITTER, false),
        spitterItem("nether_spitter_head", "Nether Spitter", AlienVariant.NETHER, AlienEntityTypes.NETHER_SPITTER, true),
        spitterItem("irradiated_spitter_head", "Irradiated Spitter", AlienVariant.IRRADIATED, AlienEntityTypes.IRRADIATED_SPITTER, true),
        item("praetorian_head", "Praetorian", "praetorian", AlienEntityTypes.PRAETORIAN, false),
        item("aberrant_praetorian_head", "Aberrant Praetorian", "praetorian", AlienEntityTypes.ABERRANT_PRAETORIAN, false),
        item("irradiated_praetorian_head", "Irradiated Praetorian", "praetorian", AlienEntityTypes.IRRADIATED_PRAETORIAN, false),
        item("nether_praetorian_head", "Nether Praetorian", "praetorian", AlienEntityTypes.NETHER_PRAETORIAN, true),
        item("boiler_head", "Boiler", "boiler", AlienEntityTypes.BOILER, false),
        item("aberrant_boiler_head", "Aberrant Boiler", "boiler", AlienEntityTypes.ABERRANT_BOILER, false),
        item("nether_boiler_head", "Nether Boiler", "boiler", AlienEntityTypes.NETHER_BOILER, true),
        item("razor_claw_head", "Razor Claw", "razor_claw", AlienEntityTypes.RAZOR_CLAW, false),
        item("aberrant_razor_claw_head", "Aberrant Razor Claw", "razor_claw", AlienEntityTypes.ABERRANT_RAZOR_CLAW, false),
        item("irradiated_razor_claw_head", "Irradiated Razor Claw", "razor_claw", AlienEntityTypes.IRRADIATED_RAZOR_CLAW, false),
        item("nether_razor_claw_head", "Nether Razor Claw", "razor_claw", AlienEntityTypes.NETHER_RAZOR_CLAW, true),
        item("ravager_head", "Ravager", "ravager", AlienEntityTypes.RAVAGER, false),
        item("aberrant_ravager_head", "Aberrant Ravager", "ravager", AlienEntityTypes.ABERRANT_RAVAGER, false),
        item("irradiated_ravager_head", "Irradiated Ravager", "ravager", AlienEntityTypes.IRRADIATED_RAVAGER, false),
        item("nether_ravager_head", "Nether Ravager", "ravager", AlienEntityTypes.NETHER_RAVAGER, true),
        agileItem("prowler_head", "Prowler", "prowler", AlienEntityTypes.PROWLER, false),
        agileItem("aberrant_prowler_head", "Aberrant Prowler", "prowler", AlienEntityTypes.ABERRANT_PROWLER, false),
        agileItem("irradiated_prowler_head", "Irradiated Prowler", "prowler", AlienEntityTypes.IRRADIATED_PROWLER, false),
        agileItem("nether_prowler_head", "Nether Prowler", "prowler", AlienEntityTypes.NETHER_PROWLER, true),
        item("carrier_head", "Carrier", "carrier", AlienEntityTypes.CARRIER, false),
        item("aberrant_carrier_head", "Aberrant Carrier", "carrier", AlienEntityTypes.ABERRANT_CARRIER, false),
        item("irradiated_carrier_head", "Irradiated Carrier", "carrier", AlienEntityTypes.IRRADIATED_CARRIER, false),
        item("nether_carrier_head", "Nether Carrier", "carrier", AlienEntityTypes.NETHER_CARRIER, true),
        item("chrysalis_head", "Chrysalis", "chrysalis", AlienEntityTypes.CHRYSALIS, false),
        item("aberrant_chrysalis_head", "Aberrant Chrysalis", "chrysalis", AlienEntityTypes.ABERRANT_CHRYSALIS, false),
        item("irradiated_chrysalis_head", "Irradiated Chrysalis", "chrysalis", AlienEntityTypes.IRRADIATED_CHRYSALIS, false),
        item("nether_chrysalis_head", "Nether Chrysalis", "chrysalis", AlienEntityTypes.NETHER_CHRYSALIS, true),
        item("predalien_head", "Predalien", "predalien", AlienEntityTypes.PREDALIEN, false),
        item("aberrant_predalien_head", "Aberrant Predalien", "predalien", AlienEntityTypes.ABERRANT_PREDALIEN, false),
        item("irradiated_predalien_head", "Irradiated Predalien", "predalien", AlienEntityTypes.IRRADIATED_PREDALIEN, false),
        item("nether_predalien_head", "Nether Predalien", "predalien", AlienEntityTypes.NETHER_PREDALIEN, true),
        item("burster_head", "Burster", "burster", AlienEntityTypes.BURSTER, false),
        item("aberrant_burster_head", "Aberrant Burster", "burster", AlienEntityTypes.ABERRANT_BURSTER, false),
        item("irradiated_burster_head", "Irradiated Burster", "burster", AlienEntityTypes.IRRADIATED_BURSTER, false),
        item("nether_burster_head", "Nether Burster", "burster", AlienEntityTypes.NETHER_BURSTER, true),
        item("empress_head", "Empress", "empress", AlienEntityTypes.EMPRESS, false),
        item("aberrant_empress_head", "Aberrant Empress", "empress", AlienEntityTypes.ABERRANT_EMPRESS, false),
        item("irradiated_empress_head", "Irradiated Empress", "empress", AlienEntityTypes.IRRADIATED_EMPRESS, false),
        item("nether_empress_head", "Nether Empress", "empress", AlienEntityTypes.NETHER_EMPRESS, true),
        item("harbinger_head", "Harbinger", "harbinger", AlienEntityTypes.HARBINGER, false),
        item("aberrant_harbinger_head", "Aberrant Harbinger", "harbinger", AlienEntityTypes.ABERRANT_HARBINGER, false),
        item("irradiated_harbinger_head", "Irradiated Harbinger", "harbinger", AlienEntityTypes.IRRADIATED_HARBINGER, false),
        item("nether_harbinger_head", "Nether Harbinger", "harbinger", AlienEntityTypes.NETHER_HARBINGER, true)
    );

    public static final List<Entry> GENERIC_BLOCK_ENTRIES = ALL.stream()
        .filter(Entry::usesGenericBlocks)
        .toList();

    private static final Map<String, Entry> BY_ITEM_PATH = ALL.stream()
        .collect(Collectors.toUnmodifiableMap(Entry::itemPath, Function.identity()));

    private AlienXenomorphHeadItems() {}

    public static void initialize() {
        // Static initialization creates block and item holders before either registry is registered.
    }

    public static Entry getOrNull(String itemPath) {
        return BY_ITEM_PATH.get(itemPath);
    }

    private static Entry item(
        String itemPath,
        String displayName,
        String modelPath,
        BLibHolder<? extends EntityType<?>> entityType,
        boolean fireResistant
    ) {
        var standingBlock = AlienBlocks.createXenomorphHeadBlock(itemPath);
        var wallBlock = AlienBlocks.createXenomorphWallHeadBlock(wallBlockPath(itemPath), itemPath);

        return new Entry(
            itemPath,
            displayName,
            modelPath,
            texturePath(itemPath),
            entityType,
            standingBlock,
            wallBlock,
            AlienItems.createXenomorphHead(itemPath, standingBlock, wallBlock, fireResistant),
            AlienItems.createXenomorphHeadShield(itemPath + "_shield", fireResistant),
            true
        );
    }

    private static Entry agileItem(
        String itemPath,
        String displayName,
        String modelPath,
        BLibHolder<? extends EntityType<?>> entityType,
        boolean fireResistant
    ) {
        var standingBlock = AlienBlocks.createXenomorphHeadBlock(itemPath);
        var wallBlock = AlienBlocks.createXenomorphWallHeadBlock(wallBlockPath(itemPath), itemPath);

        return new Entry(
            itemPath,
            displayName,
            modelPath,
            texturePath(itemPath),
            entityType,
            standingBlock,
            wallBlock,
            AlienItems.createXenomorphHead(itemPath, standingBlock, wallBlock, fireResistant),
            AlienItems.createAgileXenomorphHeadShield(itemPath + "_shield", fireResistant),
            true
        );
    }

    private static Entry spitterItem(
        String itemPath,
        String displayName,
        AlienVariant variant,
        BLibHolder<? extends EntityType<?>> entityType,
        boolean fireResistant
    ) {
        var standingBlock = AlienBlocks.createXenomorphHeadBlock(itemPath);
        var wallBlock = AlienBlocks.createXenomorphWallHeadBlock(wallBlockPath(itemPath), itemPath);

        return new Entry(
            itemPath,
            displayName,
            "spitter",
            texturePath(itemPath),
            entityType,
            standingBlock,
            wallBlock,
            AlienItems.createXenomorphHead(itemPath, standingBlock, wallBlock, fireResistant),
            AlienItems.createSpitterHeadShield(itemPath + "_shield", variant, fireResistant),
            true
        );
    }

    private static Entry existing(
        String itemPath,
        String displayName,
        String modelPath,
        BLibHolder<? extends EntityType<?>> entityType,
        BLibHolder<? extends Block> standingBlock,
        BLibHolder<? extends Block> wallBlock,
        BLibHolder<Item> head,
        BLibHolder<Item> headShield
    ) {
        return new Entry(
            itemPath,
            displayName,
            modelPath,
            texturePath(itemPath),
            entityType,
            standingBlock,
            wallBlock,
            head,
            headShield,
            false
        );
    }

    private static String texturePath(String itemPath) {
        return itemPath.substring(0, itemPath.length() - "_head".length());
    }

    private static String wallBlockPath(String itemPath) {
        return itemPath.substring(0, itemPath.length() - "_head".length()) + "_wall_head";
    }

    public record Entry(
        String itemPath,
        String displayName,
        String modelPath,
        String texturePath,
        BLibHolder<? extends EntityType<?>> entityType,
        BLibHolder<? extends Block> standingBlock,
        BLibHolder<? extends Block> wallBlock,
        BLibHolder<Item> head,
        BLibHolder<Item> headShield,
        boolean usesGenericBlocks
    ) {

        public String shieldItemPath() {
            return itemPath + "_shield";
        }
    }
}
