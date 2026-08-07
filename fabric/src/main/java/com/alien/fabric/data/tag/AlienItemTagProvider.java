package com.alien.fabric.data.tag;

import com.alien.Alien;
import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.init.item.block.IrradiatedAlienResinBlockItems;
import com.alien.common.registry.tag.AlienItemTags;
import com.alien.compatibility.avp_human.AVPHuman;
import com.blib.api.common.tag.v1.CommonItemTags;
import com.human.common.registry.tag.HumanItemTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;

import java.util.concurrent.CompletableFuture;

public class AlienItemTagProvider extends FabricTagProvider.ItemTagProvider {

    public AlienItemTagProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider wrapperLookup) {
        addArmors();
        addAutomatedTagItems();

        addCompatibilityTags();

        // Every strain's resin ball, so the field manual recipe accepts any variant.
        getOrCreateTagBuilder(AlienItemTags.RESIN_BALLS)
            .add(
                AlienItems.RESIN_BALL.get(),
                AlienItems.ABERRANT_RESIN_BALL.get(),
                AlienItems.IRRADIATED_RESIN_BALL.get(),
                AlienItems.NETHER_RESIN_BALL.get()
            );

        // Acid-resistant items
        getOrCreateTagBuilder(AlienItemTags.ACID_IMMUNE)
            .addTag(AlienItemTags.CHITIN_ARMORS)
            .addTag(AlienItemTags.PLATED_CHITIN_ARMORS)
            .add(
                AlienItems.CHITIN.get(),
                AlienItems.NETHER_CHITIN.get(),
                AlienItems.ABERRANT_CHITIN.get(),
                AlienItems.IRRADIATED_CHITIN.get(),
                AlienItems.PLATED_CHITIN.get(),
                AlienItems.PLATED_NETHER_CHITIN.get(),
                AlienItems.PLATED_ABERRANT_CHITIN.get(),
                AlienItems.PLATED_IRRADIATED_CHITIN.get()
            );

        // VANILLA tag, not BLib's deprecated DECORATIVE_POT_SHERDS. Nothing in BLib 419 reads that constant any
        // more, and `minecraft:decorated_pot_sherds` is the tag vanilla itself checks when crafting a decorated
        // pot - so this both clears the deprecation and lets our sherds actually be used in one.
        getOrCreateTagBuilder(ItemTags.DECORATED_POT_SHERDS)
            .add(
                AlienItems.OVOID_POTTERY_SHERD.get(),
                AlienItems.PARASITE_POTTERY_SHERD.get(),
                AlienItems.ROYALTY_POTTERY_SHERD.get(),
                AlienItems.VECTOR_POTTERY_SHERD.get()
            );

        if (AVPHuman.MOD.isLoaded()) {
            getOrCreateTagBuilder(HumanItemTags.RADIOACTIVE_ITEMS)
                .add(
                    AlienItems.IRRADIATED_CHITIN.get(),
                    AlienItems.PLATED_IRRADIATED_CHITIN.get(),
                    AlienItems.IRRADIATED_RESIN_BALL.get(),
                    IrradiatedAlienResinBlockItems.IRRADIATED_RESIN.get(),
                    IrradiatedAlienResinBlockItems.IRRADIATED_RESIN_NODE.get(),
                    IrradiatedAlienResinBlockItems.IRRADIATED_RESIN_VEIN.get(),
                    IrradiatedAlienResinBlockItems.IRRADIATED_RESIN_WEB.get()
                );
            getOrCreateTagBuilder(HumanItemTags.URANIUM_NUGGET_LIKE)
                .add(AlienItems.IRRADIATED_CHITIN.get());
        }
    }

    private void addAutomatedTagItems() {
        // Armor
        var headArmorTagProvider = getOrCreateTagBuilder(ItemTags.HEAD_ARMOR);
        var chestArmorTagProvider = getOrCreateTagBuilder(ItemTags.CHEST_ARMOR);
        var legArmorTagProvider = getOrCreateTagBuilder(ItemTags.LEG_ARMOR);
        var footArmorTagProvider = getOrCreateTagBuilder(ItemTags.FOOT_ARMOR);

        // Blocks
        var buttonTagProvider = getOrCreateTagBuilder(ItemTags.BUTTONS);
        var doorTagProvider = getOrCreateTagBuilder(ItemTags.DOORS);
        var fenceTagProvider = getOrCreateTagBuilder(ItemTags.FENCES);
        var slabTagProvider = getOrCreateTagBuilder(ItemTags.SLABS);
        var stairsTagProvider = getOrCreateTagBuilder(ItemTags.STAIRS);
        var trapdoorTagProvider = getOrCreateTagBuilder(ItemTags.TRAPDOORS);
        var wallTagBuilder = getOrCreateTagBuilder(ItemTags.WALLS);

        // Tools
        var axeTagProvider = getOrCreateTagBuilder(ItemTags.AXES);
        var hoeTagProvider = getOrCreateTagBuilder(ItemTags.HOES);
        var pickaxeTagProvider = getOrCreateTagBuilder(ItemTags.PICKAXES);
        var shovelTagProvider = getOrCreateTagBuilder(ItemTags.SHOVELS);

        // Weapons
        var swordTagProvider = getOrCreateTagBuilder(ItemTags.SWORDS);

        Alien.MOD.registries().getAllHolders(BuiltInRegistries.ITEM).forEach(deferredHolder -> {
            var item = deferredHolder.get();

            if (item instanceof ArmorItem armorItem) {
                switch (armorItem.getType()) {
                    case HELMET -> headArmorTagProvider.add(item);
                    case CHESTPLATE -> chestArmorTagProvider.add(item);
                    case LEGGINGS -> legArmorTagProvider.add(item);
                    case BOOTS -> footArmorTagProvider.add(item);
                    case BODY -> { /* NO-OP */ }
                }
            }

            if (item instanceof BlockItem blockItem) {
                var block = blockItem.getBlock();

                if (block instanceof ButtonBlock) {
                    buttonTagProvider.add(item);
                }

                if (block instanceof DoorBlock) {
                    doorTagProvider.add(item);
                }

                if (block instanceof FenceBlock) {
                    fenceTagProvider.add(item);
                }

                if (block instanceof SlabBlock) {
                    slabTagProvider.add(item);
                }

                if (block instanceof StairBlock) {
                    stairsTagProvider.add(item);
                }

                if (block instanceof TrapDoorBlock) {
                    trapdoorTagProvider.add(item);
                }

                if (block instanceof WallBlock) {
                    wallTagBuilder.add(item);
                }
            }

            if (item instanceof AxeItem) {
                axeTagProvider.add(item);
            }

            if (item instanceof HoeItem) {
                hoeTagProvider.add(item);
            }

            if (item instanceof PickaxeItem) {
                pickaxeTagProvider.add(item);
            }

            if (item instanceof ShovelItem) {
                shovelTagProvider.add(item);
            }

            if (item instanceof SwordItem) {
                swordTagProvider.add(item);
            }
        });
    }

    private void addArmors() {
        getOrCreateTagBuilder(AlienItemTags.NETHER_CHITIN_ARMOR)
            .add(
                AlienArmorItems.NETHER_CHITIN_BOOTS.get(),
                AlienArmorItems.NETHER_CHITIN_CHESTPLATE.get(),
                AlienArmorItems.NETHER_CHITIN_HELMET.get(),
                AlienArmorItems.NETHER_CHITIN_LEGGINGS.get()
            );

        getOrCreateTagBuilder(AlienItemTags.PLATED_NETHER_CHITIN_ARMOR)
            .add(
                AlienArmorItems.PLATED_NETHER_CHITIN_BOOTS.get(),
                AlienArmorItems.PLATED_NETHER_CHITIN_CHESTPLATE.get(),
                AlienArmorItems.PLATED_NETHER_CHITIN_HELMET.get(),
                AlienArmorItems.PLATED_NETHER_CHITIN_LEGGINGS.get()
            );

        getOrCreateTagBuilder(AlienItemTags.ABERRANT_CHITIN_ARMOR)
            .add(
                AlienArmorItems.ABERRANT_CHITIN_HELMET.get(),
                AlienArmorItems.ABERRANT_CHITIN_CHESTPLATE.get(),
                AlienArmorItems.ABERRANT_CHITIN_LEGGINGS.get(),
                AlienArmorItems.ABERRANT_CHITIN_BOOTS.get()
            );

        getOrCreateTagBuilder(AlienItemTags.NORMAL_CHITIN_ARMOR)
            .add(
                AlienArmorItems.CHITIN_HELMET.get(),
                AlienArmorItems.CHITIN_CHESTPLATE.get(),
                AlienArmorItems.CHITIN_LEGGINGS.get(),
                AlienArmorItems.CHITIN_BOOTS.get()
            );

        getOrCreateTagBuilder(AlienItemTags.IRRADIATED_CHITIN_ARMOR)
            .add(
                AlienArmorItems.IRRADIATED_CHITIN_HELMET.get(),
                AlienArmorItems.IRRADIATED_CHITIN_CHESTPLATE.get(),
                AlienArmorItems.IRRADIATED_CHITIN_LEGGINGS.get(),
                AlienArmorItems.IRRADIATED_CHITIN_BOOTS.get()
            );

        getOrCreateTagBuilder(AlienItemTags.PLATED_ABERRANT_CHITIN_ARMOR)
            .add(
                AlienArmorItems.PLATED_ABERRANT_CHITIN_HELMET.get(),
                AlienArmorItems.PLATED_ABERRANT_CHITIN_CHESTPLATE.get(),
                AlienArmorItems.PLATED_ABERRANT_CHITIN_LEGGINGS.get(),
                AlienArmorItems.PLATED_ABERRANT_CHITIN_BOOTS.get()
            );

        getOrCreateTagBuilder(AlienItemTags.PLATED_NORMAL_CHITIN_ARMOR)
            .add(
                AlienArmorItems.PLATED_CHITIN_HELMET.get(),
                AlienArmorItems.PLATED_CHITIN_CHESTPLATE.get(),
                AlienArmorItems.PLATED_CHITIN_LEGGINGS.get(),
                AlienArmorItems.PLATED_CHITIN_BOOTS.get()
            );

        getOrCreateTagBuilder(AlienItemTags.PLATED_IRRADIATED_CHITIN_ARMOR)
            .add(
                AlienArmorItems.PLATED_IRRADIATED_CHITIN_HELMET.get(),
                AlienArmorItems.PLATED_IRRADIATED_CHITIN_CHESTPLATE.get(),
                AlienArmorItems.PLATED_IRRADIATED_CHITIN_LEGGINGS.get(),
                AlienArmorItems.PLATED_IRRADIATED_CHITIN_BOOTS.get()
            );

        getOrCreateTagBuilder(AlienItemTags.PLATED_NETHER_CHITIN_ARMOR)
            .add(
                AlienArmorItems.PLATED_NETHER_CHITIN_HELMET.get(),
                AlienArmorItems.PLATED_NETHER_CHITIN_CHESTPLATE.get(),
                AlienArmorItems.PLATED_NETHER_CHITIN_LEGGINGS.get(),
                AlienArmorItems.PLATED_NETHER_CHITIN_BOOTS.get()
            );

        // Start composite tags

        getOrCreateTagBuilder(AlienItemTags.CHITIN_ARMORS)
            .addTag(AlienItemTags.ABERRANT_CHITIN_ARMOR)
            .addTag(AlienItemTags.IRRADIATED_CHITIN_ARMOR)
            .addTag(AlienItemTags.NETHER_CHITIN_ARMOR)
            .addTag(AlienItemTags.NORMAL_CHITIN_ARMOR);

        // BLibItemTags.FIRE_RESISTANT_ARMORS was deprecated for removal in BLib 0.3.0-alpha.419 and NOTHING in
        // BLib reads it any more, so populating it did nothing. Removed rather than suppressed.
        //
        // ⚠ IF nether chitin armour is meant to grant fire resistance to the wearer, that behaviour has to live
        // somewhere in avp_alien now - nothing in this repo implements it, and the tag it was relying on is
        // dead. The armour items themselves are still fireproof via their item properties; it is the WEARER
        // effect that has no owner.

        getOrCreateTagBuilder(AlienItemTags.PLATED_CHITIN_ARMORS)
            .addTag(AlienItemTags.PLATED_ABERRANT_CHITIN_ARMOR)
            .addTag(AlienItemTags.PLATED_IRRADIATED_CHITIN_ARMOR)
            .addTag(AlienItemTags.PLATED_NETHER_CHITIN_ARMOR)
            .addTag(AlienItemTags.PLATED_NORMAL_CHITIN_ARMOR);
    }

    private void addCompatibilityTags() {
        getOrCreateTagBuilder(CommonItemTags.MUSIC_DISCS)
            .add(AlienItems.ALIEN_MUSIC_DISC_1.get());
    }
}
