package com.alien.common.registry.init.creative_mode_tab.initializer;

import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import com.alien.compatibility.avp_human.AVPHuman;
import com.alien.compatibility.avp_predator.AVPPredator;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class XenomorphHeadCreativeModeTabEntries {

    private static final List<String> BASE_HEAD_ITEMS_BEFORE_PREDALIEN = List.of(
        "drone_head",
        "warrior_head",
        "praetorian_head",
        "queen_head",
        "empress_head",
        "runner_head",
        "burster_head",
        "prowler_head",
        "crusher_head"
    );

    private static final List<String> BASE_HEAD_ITEMS_AFTER_PREDALIEN = List.of(
        "boiler_head",
        "spitter_head",
        "chrysalis_head",
        "razor_claw_head",
        "carrier_head",
        "ravager_head",
        "harbinger_head"
    );

    private static final List<String> NETHER_HEAD_ITEMS_BEFORE_PREDALIEN = List.of(
        "nether_drone_head",
        "nether_warrior_head",
        "nether_praetorian_head",
        "nether_queen_head",
        "nether_empress_head",
        "nether_runner_head",
        "nether_burster_head",
        "nether_prowler_head",
        "nether_crusher_head"
    );

    private static final List<String> NETHER_HEAD_ITEMS_AFTER_PREDALIEN = List.of(
        "nether_boiler_head",
        "nether_spitter_head",
        "nether_chrysalis_head",
        "nether_razor_claw_head",
        "nether_carrier_head",
        "nether_ravager_head",
        "nether_harbinger_head"
    );

    private static final List<String> ABERRANT_HEAD_ITEMS_BEFORE_PREDALIEN = List.of(
        "aberrant_drone_head",
        "aberrant_warrior_head",
        "aberrant_praetorian_head",
        "aberrant_queen_head",
        "aberrant_empress_head",
        "aberrant_runner_head",
        "aberrant_burster_head",
        "aberrant_prowler_head",
        "aberrant_crusher_head"
    );

    private static final List<String> ABERRANT_HEAD_ITEMS_AFTER_PREDALIEN = List.of(
        "aberrant_boiler_head",
        "aberrant_spitter_head",
        "aberrant_chrysalis_head",
        "aberrant_razor_claw_head",
        "aberrant_carrier_head",
        "aberrant_ravager_head",
        "aberrant_harbinger_head"
    );

    private static final List<String> IRRADIATED_HEAD_ITEMS_BEFORE_PREDALIEN = List.of(
        "irradiated_drone_head",
        "irradiated_warrior_head",
        "irradiated_praetorian_head",
        "irradiated_queen_head",
        "irradiated_empress_head",
        "irradiated_runner_head",
        "irradiated_burster_head",
        "irradiated_prowler_head",
        "irradiated_crusher_head"
    );

    private static final List<String> IRRADIATED_HEAD_ITEMS_AFTER_PREDALIEN = List.of(
        "irradiated_chrysalis_head",
        "irradiated_razor_claw_head",
        "irradiated_carrier_head",
        "irradiated_ravager_head",
        "irradiated_harbinger_head"
    );

    private static final Map<String, AlienXenomorphHeadItems.Entry> HEAD_ITEMS_BY_PATH = AlienXenomorphHeadItems.ALL.stream()
        .collect(Collectors.toUnmodifiableMap(AlienXenomorphHeadItems.Entry::itemPath, Function.identity()));

    private XenomorphHeadCreativeModeTabEntries() {}

    static void addHeads(CreativeModeTab.Output output) {
        addInSpawnEggOrder(output, AlienXenomorphHeadItems.Entry::head);
    }

    static void addHeadShields(CreativeModeTab.Output output) {
        addInSpawnEggOrder(output, AlienXenomorphHeadItems.Entry::headShield);
    }

    private static void addInSpawnEggOrder(
        CreativeModeTab.Output output,
        Function<AlienXenomorphHeadItems.Entry, Supplier<? extends ItemLike>> itemSelector
    ) {
        addBaseXenomorphHeadItems(output, itemSelector);
        addNetherXenomorphHeadItems(output, itemSelector);
        addAberrantXenomorphHeadItems(output, itemSelector);

        if (AVPHuman.MOD.isLoaded()) {
            addIrradiatedXenomorphHeadItems(output, itemSelector);
        }
    }

    private static void addBaseXenomorphHeadItems(
        CreativeModeTab.Output output,
        Function<AlienXenomorphHeadItems.Entry, Supplier<? extends ItemLike>> itemSelector
    ) {
        acceptAll(output, BASE_HEAD_ITEMS_BEFORE_PREDALIEN, itemSelector);

        if (AVPPredator.MOD.isLoaded()) {
            accept(output, "predalien_head", itemSelector);
        }

        acceptAll(output, BASE_HEAD_ITEMS_AFTER_PREDALIEN, itemSelector);
    }

    private static void addNetherXenomorphHeadItems(
        CreativeModeTab.Output output,
        Function<AlienXenomorphHeadItems.Entry, Supplier<? extends ItemLike>> itemSelector
    ) {
        acceptAll(output, NETHER_HEAD_ITEMS_BEFORE_PREDALIEN, itemSelector);

        if (AVPPredator.MOD.isLoaded()) {
            accept(output, "nether_predalien_head", itemSelector);
        }

        acceptAll(output, NETHER_HEAD_ITEMS_AFTER_PREDALIEN, itemSelector);
    }

    private static void addAberrantXenomorphHeadItems(
        CreativeModeTab.Output output,
        Function<AlienXenomorphHeadItems.Entry, Supplier<? extends ItemLike>> itemSelector
    ) {
        acceptAll(output, ABERRANT_HEAD_ITEMS_BEFORE_PREDALIEN, itemSelector);

        if (AVPPredator.MOD.isLoaded()) {
            accept(output, "aberrant_predalien_head", itemSelector);
        }

        acceptAll(output, ABERRANT_HEAD_ITEMS_AFTER_PREDALIEN, itemSelector);
    }

    private static void addIrradiatedXenomorphHeadItems(
        CreativeModeTab.Output output,
        Function<AlienXenomorphHeadItems.Entry, Supplier<? extends ItemLike>> itemSelector
    ) {
        acceptAll(output, IRRADIATED_HEAD_ITEMS_BEFORE_PREDALIEN, itemSelector);

        if (AVPPredator.MOD.isLoaded()) {
            accept(output, "irradiated_predalien_head", itemSelector);
        }

        acceptAll(output, IRRADIATED_HEAD_ITEMS_AFTER_PREDALIEN, itemSelector);
    }

    private static void acceptAll(
        CreativeModeTab.Output output,
        List<String> itemPaths,
        Function<AlienXenomorphHeadItems.Entry, Supplier<? extends ItemLike>> itemSelector
    ) {
        itemPaths.forEach(itemPath -> accept(output, itemPath, itemSelector));
    }

    private static void accept(
        CreativeModeTab.Output output,
        String itemPath,
        Function<AlienXenomorphHeadItems.Entry, Supplier<? extends ItemLike>> itemSelector
    ) {
        var entry = HEAD_ITEMS_BY_PATH.get(itemPath);

        if (entry == null) {
            throw new IllegalStateException("Unknown xenomorph head item: " + itemPath);
        }

        CreativeModeTabUtil.accept(output, itemSelector.apply(entry));
    }
}
