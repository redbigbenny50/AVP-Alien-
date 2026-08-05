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
 * A crusher drops what a praetorian drops. Both are royal-jelly promotions off the same tier - a crusher comes from a
 * prowler, a praetorian from a warrior - so it is royalty by the same measure, and it goes through {@link JellyLoot}
 * like the rest of them: an irradiated crusher hands over irradiated jelly with no extra work.
 * <p>
 * Crushers previously had NO loot table registered at all, in any strain, so they dropped nothing whatsoever - not even
 * the chitin every other adult caste gives up.
 */
public class CrusherLootTable {

    public static LootTable.Builder create(HolderLookup.Provider provider, AlienVariantType alienVariantType) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(
                        LootItem.lootTableItem(JellyLoot.royalJelly(alienVariantType))
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(0, 1)))
                            .apply(EnchantedCountIncreaseFunction.lootingMultiplier(provider, UniformGenerator.between(0, 1)))
                    )
            )
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

    private CrusherLootTable() {
        throw new UnsupportedOperationException();
    }
}
