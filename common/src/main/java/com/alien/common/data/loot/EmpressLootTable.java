package com.alien.common.data.loot;

import com.alien.common.gameplay.entity.living.alien.xenomorph.empress.Empress;
import com.alien.common.model.alien.variant.AlienVariantType;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

public class EmpressLootTable {

    public static LootTable.Builder createLootTableBuilder(HolderLookup.Provider provider, AlienVariantType alienVariantType) {
        return LootTable.lootTable()
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(
                        LootItem.lootTableItem(JellyLoot.royalJelly(alienVariantType))
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 3)))
                            .apply(EnchantedCountIncreaseFunction.lootingMultiplier(provider, UniformGenerator.between(0, 1)))
                    )
            )
            .withPool(
                LootPool.lootPool()
                    .setRolls(ConstantValue.exactly(1))
                    .add(
                        LootItem.lootTableItem(alienVariantType.chitin().get())
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 3)))
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
            )
            .withPool(headTrophyPool(alienVariantType));
    }

    /**
     * Her trophy head, ALWAYS, exactly one, no looting scaling.
     * <p>
     * Every other trophy head in the mod comes from DISMEMBERMENT - a HEAD limb entity has to drop and then be
     * right-clicked (see {@code AlienLimbDrops}), so whether you get one is a matter of how she happened to come apart.
     * An empress is the endgame kill and should never leave you empty-handed, so hers is guaranteed on the loot table
     * instead. Looting is deliberately not applied: a second head is not a better trophy.
     */
    private static LootPool.Builder headTrophyPool(AlienVariantType alienVariantType) {
        var head = empressHead(alienVariantType);
        var pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1));

        return head == null
            ? pool
            : pool.add(
                LootItem.lootTableItem(head)
                    .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1)))
            );
    }

    /** Resolved off her ENTITY TYPE rather than by building an item path, so a rename cannot silently break it. */
    private static Item empressHead(AlienVariantType alienVariantType) {
        var empressType = Empress.getType(alienVariantType.variant());

        for (var entry : AlienXenomorphHeadItems.ALL) {
            if (entry.entityType().get() == empressType) {
                return entry.head().get();
            }
        }

        return null;
    }

    private EmpressLootTable() {
        throw new UnsupportedOperationException();
    }
}
