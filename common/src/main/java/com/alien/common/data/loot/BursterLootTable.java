package com.alien.common.data.loot;

import com.alien.common.model.alien.variant.AlienVariantType;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * A burster drops what a ravager drops: chitin and plated chitin, and NO jelly. Both are raid castes, and the scourge
 * tier is spent rather than harvested - the jelly went INTO making them.
 * <p>
 * Bursters previously had NO loot table registered at all, in any strain. They detonate on death, so leaving nothing
 * behind was at least arguable; they now leave what is left of the shell.
 */
public class BursterLootTable {

    public static LootTable.Builder create(HolderLookup.Provider provider, AlienVariantType alienVariantType) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(
                        LootItem.lootTableItem(alienVariantType.chitin().get())
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))
                            .apply(EnchantedCountIncreaseFunction.lootingMultiplier(provider, UniformGenerator.between(0, 1)))
                    )
            )
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(
                        LootItem.lootTableItem(alienVariantType.platedChitin().get())
                            .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1)))
                            .apply(EnchantedCountIncreaseFunction.lootingMultiplier(provider, UniformGenerator.between(0, 1)))
                    )
            );
    }

    private BursterLootTable() {
        throw new UnsupportedOperationException();
    }
}
