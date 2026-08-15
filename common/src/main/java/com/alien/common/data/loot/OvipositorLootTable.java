package com.alien.common.data.loot;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The ovipositor drops NOTHING.
 * <p>
 * It used to hand over 2-4 raw royal jelly, which made it a renewable jelly source rather than a piece of the queen:
 * her eggsack can be cut away and she grows another, so the table paid out again every time. Jelly is meant to come off
 * royalty that had to be killed for it.
 * <p>
 * The table is still registered, empty, rather than removed - {@code getDefaultLootTable()} would otherwise point at a
 * table that does not exist. The {@code provider} parameter is kept so this matches every sibling loot table's shape.
 */
public class OvipositorLootTable {

    public static LootTable.Builder create(HolderLookup.Provider provider) {
        return LootTable.lootTable();
    }

    private OvipositorLootTable() {
        throw new UnsupportedOperationException();
    }
}
