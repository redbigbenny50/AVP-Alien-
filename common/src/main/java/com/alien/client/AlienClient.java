package com.alien.client;

import com.alien.Alien;
import com.alien.AlienResources;
import com.alien.client.particle.AcidParticleProvider;
import com.alien.client.particle.BlueAcidParticleProvider;
import com.alien.client.particle.IrradiatedAcidParticleProvider;
import com.alien.client.render.armor.AberrantChitinArmorRenderer;
import com.alien.client.render.armor.ChitinArmorRenderer;
import com.alien.client.render.armor.IrradiatedChitinArmorRenderer;
import com.alien.client.render.armor.NetherChitinArmorRenderer;
import com.alien.client.render.armor.PlatedAberrantChitinArmorRenderer;
import com.alien.client.render.armor.PlatedChitinArmorRenderer;
import com.alien.client.render.armor.PlatedIrradiatedChitinArmorRenderer;
import com.alien.client.render.armor.PlatedNetherChitinArmorRenderer;
import com.alien.client.render.block.AnchorBlockEntityRenderer;
import com.alien.client.render.block.CrusherHeadBlockEntityRenderer;
import com.alien.client.render.block.QueenHeadBlockEntityRenderer;
import com.alien.client.render.block.XenomorphHeadBlockEntityRenderer;
import com.alien.client.render.dismemberment.PraetorianRenderedLimbPicker;
import com.alien.client.render.entity.AcidRenderer;
import com.alien.client.render.entity.AcidSpitRenderer;
import com.alien.client.render.entity.AdolescentRenderer;
import com.alien.client.render.entity.BoilerRenderer;
import com.alien.client.render.entity.BursterRenderer;
import com.alien.client.render.entity.CarrierRenderer;
import com.alien.client.render.entity.ChestbursterRenderer;
import com.alien.client.render.entity.ChrysalisRenderer;
import com.alien.client.render.entity.CrusherRenderer;
import com.alien.client.render.entity.DroneRenderer;
import com.alien.client.render.entity.EmpressRenderer;
import com.alien.client.render.entity.HarbingerRenderer;
import com.alien.client.render.entity.OvipositorRenderer;
import com.alien.client.render.entity.OvomorphRenderer;
import com.alien.client.render.entity.PraetorianRenderer;
import com.alien.client.render.entity.PredalienAdolescentRenderer;
import com.alien.client.render.entity.PredalienChestbursterRenderer;
import com.alien.client.render.entity.PredalienRenderer;
import com.alien.client.render.entity.ProwlerRenderer;
import com.alien.client.render.entity.QueenRenderer;
import com.alien.client.render.entity.RavagerRenderer;
import com.alien.client.render.entity.RazorClawRenderer;
import com.alien.client.render.entity.RoyalCocoonRenderer;
import com.alien.client.render.entity.RunnerRenderer;
import com.alien.client.render.entity.SpitterRenderer;
import com.alien.client.render.entity.WarriorRenderer;
import com.alien.client.render.entity.head.AlienEntityHeadData;
import com.alien.client.render.entity.head.EntityHeadDataCache;
import com.alien.client.render.entity.parasite.attachment.AlienParasiteHeadAttachmentOffsetData;
import com.alien.client.render.entity.parasite.attachment.ParasiteHeadAttachmentOffsetDataCache;
import com.alien.client.render.entity.parasite.facehugger.FacehuggerRenderer;
import com.alien.common.registry.init.AlienBlockEntityTypes;
import com.alien.common.registry.init.AlienEntityTypes;
import com.alien.common.registry.init.AlienParticleTypes;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.common.registry.init.item.AlienArmorItems;
import com.alien.common.registry.init.item.AlienItems;
import com.alien.common.registry.init.item.AlienXenomorphHeadItems;
import com.alien.compatibility.blib_engine.BLibEngine;
import com.blib.api.client.mod.v1.BLibClientMod;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitPredictionRegistry;
import com.blib.api.common.registry.v1.BLibHolder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.List;

public class AlienClient {

    private static final BLibClientMod MOD = BLibClientMod.createFor(Alien.MOD);

    public static void initialize() {
        MOD.initialize(AlienClient::runInitialization);
    }

    private static void runInitialization() {
        registerArmorRenderers();
        registerBlockRenderLayers();
        registerEntityRenderers();
        registerItemRenderers();
        registerBlockEntityRenderers();
        registerParticleProviderFactories();
        LimbHitPredictionRegistry.registerClientProvider(new PraetorianRenderedLimbPicker());

        // Engine workspace: add hive-specific inspector sections under the generic faction inspector so picking an AVP
        // faction in the FactionBrowser reveals biomass/jelly/caste/territory/leadership/vigilance data.
        BLibEngine.registerInspectorSections();

        MOD.events().onClientSetup().register(() -> {
            registerEntityHeadData();
            registerParasiteHeadAttachmentOffsetData();
        });
    }

    private static void registerArmorRenderers() {
        MOD.registries()
            .registerArmorRenderer(
                AberrantChitinArmorRenderer::new,
                List.of(
                    AlienArmorItems.ABERRANT_CHITIN_HELMET,
                    AlienArmorItems.ABERRANT_CHITIN_CHESTPLATE,
                    AlienArmorItems.ABERRANT_CHITIN_LEGGINGS,
                    AlienArmorItems.ABERRANT_CHITIN_BOOTS
                )
            );
        MOD.registries()
            .registerArmorRenderer(
                ChitinArmorRenderer::new,
                List.of(
                    AlienArmorItems.CHITIN_HELMET,
                    AlienArmorItems.CHITIN_CHESTPLATE,
                    AlienArmorItems.CHITIN_LEGGINGS,
                    AlienArmorItems.CHITIN_BOOTS
                )
            );
        MOD.registries()
            .registerArmorRenderer(
                IrradiatedChitinArmorRenderer::new,
                List.of(
                    AlienArmorItems.IRRADIATED_CHITIN_HELMET,
                    AlienArmorItems.IRRADIATED_CHITIN_CHESTPLATE,
                    AlienArmorItems.IRRADIATED_CHITIN_LEGGINGS,
                    AlienArmorItems.IRRADIATED_CHITIN_BOOTS
                )
            );
        MOD.registries()
            .registerArmorRenderer(
                NetherChitinArmorRenderer::new,
                List.of(
                    AlienArmorItems.NETHER_CHITIN_HELMET,
                    AlienArmorItems.NETHER_CHITIN_CHESTPLATE,
                    AlienArmorItems.NETHER_CHITIN_LEGGINGS,
                    AlienArmorItems.NETHER_CHITIN_BOOTS
                )
            );
        MOD.registries()
            .registerArmorRenderer(
                PlatedAberrantChitinArmorRenderer::new,
                List.of(
                    AlienArmorItems.PLATED_ABERRANT_CHITIN_HELMET,
                    AlienArmorItems.PLATED_ABERRANT_CHITIN_CHESTPLATE,
                    AlienArmorItems.PLATED_ABERRANT_CHITIN_LEGGINGS,
                    AlienArmorItems.PLATED_ABERRANT_CHITIN_BOOTS
                )
            );
        MOD.registries()
            .registerArmorRenderer(
                PlatedChitinArmorRenderer::new,
                List.of(
                    AlienArmorItems.PLATED_CHITIN_HELMET,
                    AlienArmorItems.PLATED_CHITIN_CHESTPLATE,
                    AlienArmorItems.PLATED_CHITIN_LEGGINGS,
                    AlienArmorItems.PLATED_CHITIN_BOOTS
                )
            );
        MOD.registries()
            .registerArmorRenderer(
                PlatedIrradiatedChitinArmorRenderer::new,
                List.of(
                    AlienArmorItems.PLATED_IRRADIATED_CHITIN_HELMET,
                    AlienArmorItems.PLATED_IRRADIATED_CHITIN_CHESTPLATE,
                    AlienArmorItems.PLATED_IRRADIATED_CHITIN_LEGGINGS,
                    AlienArmorItems.PLATED_IRRADIATED_CHITIN_BOOTS
                )
            );
        MOD.registries()
            .registerArmorRenderer(
                PlatedNetherChitinArmorRenderer::new,
                List.of(
                    AlienArmorItems.PLATED_NETHER_CHITIN_HELMET,
                    AlienArmorItems.PLATED_NETHER_CHITIN_CHESTPLATE,
                    AlienArmorItems.PLATED_NETHER_CHITIN_LEGGINGS,
                    AlienArmorItems.PLATED_NETHER_CHITIN_BOOTS
                )
            );
    }

    private static void registerBlockRenderLayers() {
        MOD.registries().registerBlockRenderLayer(AlienResinBlocks.RESIN_VEIN, RenderType.translucent());
        MOD.registries().registerBlockRenderLayer(AlienResinBlocks.RESIN_WEB, RenderType.translucent());

        MOD.registries().registerBlockRenderLayer(NetherAlienResinBlocks.NETHER_RESIN_VEIN, RenderType.translucent());
        MOD.registries().registerBlockRenderLayer(NetherAlienResinBlocks.NETHER_RESIN_WEB, RenderType.translucent());

        MOD.registries().registerBlockRenderLayer(AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN, RenderType.translucent());
        MOD.registries().registerBlockRenderLayer(AberrantAlienResinBlocks.ABERRANT_RESIN_WEB, RenderType.translucent());

        MOD.registries().registerBlockRenderLayer(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN, RenderType.translucent());
        MOD.registries().registerBlockRenderLayer(IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB, RenderType.translucent());

        MOD.registries().registerBlockRenderLayer(AlienBlocks.ROYAL_JELLY_BLOCK, RenderType.translucent());
        MOD.registries().registerBlockRenderLayer(AlienBlocks.SCOURGE_JELLY_BLOCK, RenderType.translucent());
    }

    private static void registerEntityHeadData() {
        EntityHeadDataCache.put(EntityType.CAMEL, AlienEntityHeadData.CAMEL);
        EntityHeadDataCache.put(EntityType.COW, AlienEntityHeadData.COW);
        EntityHeadDataCache.put(EntityType.DONKEY, AlienEntityHeadData.HORSE);
        EntityHeadDataCache.put(EntityType.DOLPHIN, AlienEntityHeadData.DOLPHIN);
        EntityHeadDataCache.put(EntityType.EVOKER, AlienEntityHeadData.VILLAGER);
        EntityHeadDataCache.put(EntityType.FOX, AlienEntityHeadData.FOX);
        EntityHeadDataCache.put(EntityType.GOAT, AlienEntityHeadData.GOAT);
        EntityHeadDataCache.put(EntityType.HOGLIN, AlienEntityHeadData.HOGLIN);
        EntityHeadDataCache.put(EntityType.HORSE, AlienEntityHeadData.HORSE);
        EntityHeadDataCache.put(EntityType.ILLUSIONER, AlienEntityHeadData.VILLAGER);
        EntityHeadDataCache.put(EntityType.LLAMA, AlienEntityHeadData.LLAMA);
        EntityHeadDataCache.put(EntityType.MOOSHROOM, AlienEntityHeadData.COW);
        EntityHeadDataCache.put(EntityType.MULE, AlienEntityHeadData.HORSE);
        EntityHeadDataCache.put(EntityType.PANDA, AlienEntityHeadData.PANDA);
        EntityHeadDataCache.put(EntityType.PIG, AlienEntityHeadData.PIG);
        EntityHeadDataCache.put(EntityType.PIGLIN, AlienEntityHeadData.PIGLIN);
        EntityHeadDataCache.put(EntityType.PIGLIN_BRUTE, AlienEntityHeadData.PIGLIN);
        EntityHeadDataCache.put(EntityType.PILLAGER, AlienEntityHeadData.VILLAGER);
        EntityHeadDataCache.put(EntityType.PLAYER, AlienEntityHeadData.PLAYER);
        EntityHeadDataCache.put(EntityType.POLAR_BEAR, AlienEntityHeadData.POLAR_BEAR);
        EntityHeadDataCache.put(EntityType.RAVAGER, AlienEntityHeadData.RAVAGER);
        EntityHeadDataCache.put(EntityType.SHEEP, AlienEntityHeadData.SHEEP);
        EntityHeadDataCache.put(EntityType.SNIFFER, AlienEntityHeadData.SNIFFER);
        EntityHeadDataCache.put(EntityType.TRADER_LLAMA, AlienEntityHeadData.LLAMA);
        EntityHeadDataCache.put(EntityType.VILLAGER, AlienEntityHeadData.VILLAGER);
        EntityHeadDataCache.put(EntityType.VINDICATOR, AlienEntityHeadData.VILLAGER);
        EntityHeadDataCache.put(EntityType.WITCH, AlienEntityHeadData.VILLAGER);
        EntityHeadDataCache.put(EntityType.WANDERING_TRADER, AlienEntityHeadData.VILLAGER);
        EntityHeadDataCache.put(EntityType.WOLF, AlienEntityHeadData.WOLF);
        EntityHeadDataCache.put(EntityType.ZOGLIN, AlienEntityHeadData.HOGLIN);
        EntityHeadDataCache.put(EntityType.ZOMBIE_VILLAGER, AlienEntityHeadData.VILLAGER);
    }

    private static void registerEntityRenderers() {
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_ADOLESCENT, AdolescentRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_BOILER, BoilerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_CHESTBURSTER, ChestbursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_CARRIER, CarrierRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_CHRYSALIS, ChrysalisRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_CRUSHER, CrusherRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_DRONE, DroneRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_FACEHUGGER, FacehuggerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_HARBINGER, HarbingerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_OVOMORPH, OvomorphRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_PRAETORIAN, PraetorianRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_PREDALIEN, PredalienRenderer::new);
        MOD.registries()
            .registerEntityRenderer(
                AlienEntityTypes.ABERRANT_PREDALIEN_ADOLESCENT,
                PredalienAdolescentRenderer::new
            );
        MOD.registries()
            .registerEntityRenderer(
                AlienEntityTypes.ABERRANT_PREDALIEN_CHESTBURSTER,
                PredalienChestbursterRenderer::new
            );
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_PROWLER, ProwlerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_RAZOR_CLAW, RazorClawRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_RAVAGER, RavagerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_QUEEN, QueenRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_EMPRESS, EmpressRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_BURSTER, BursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_RUNNER, RunnerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_SPITTER, SpitterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_WARRIOR, WarriorRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ACID, AcidRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ACID_SPIT, AcidSpitRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ADOLESCENT, AdolescentRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.BOILER, BoilerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.CHESTBURSTER, ChestbursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.CARRIER, CarrierRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.CHRYSALIS, ChrysalisRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.CRUSHER, CrusherRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.DRONE, DroneRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.EMPRESS, EmpressRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.HARBINGER, HarbingerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.FACEHUGGER, FacehuggerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_CARRIER, CarrierRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_CHRYSALIS, ChrysalisRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_CRUSHER, CrusherRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_DRONE, DroneRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_PRAETORIAN, PraetorianRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_PREDALIEN, PredalienRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_PROWLER, ProwlerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_HARBINGER, HarbingerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_RAZOR_CLAW, RazorClawRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_RAVAGER, RavagerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_QUEEN, QueenRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_EMPRESS, EmpressRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_BURSTER, BursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_RUNNER, RunnerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.IRRADIATED_WARRIOR, WarriorRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_ADOLESCENT, AdolescentRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_BOILER, BoilerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_CHESTBURSTER, ChestbursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_CARRIER, CarrierRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_CHRYSALIS, ChrysalisRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_CRUSHER, CrusherRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_DRONE, DroneRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_FACEHUGGER, FacehuggerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_HARBINGER, HarbingerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_OVOMORPH, OvomorphRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_PRAETORIAN, PraetorianRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_PREDALIEN, PredalienRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_PREDALIEN_ADOLESCENT, PredalienAdolescentRenderer::new);
        MOD.registries()
            .registerEntityRenderer(
                AlienEntityTypes.NETHER_PREDALIEN_CHESTBURSTER,
                PredalienChestbursterRenderer::new
            );
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_PROWLER, ProwlerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_RAZOR_CLAW, RazorClawRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_RAVAGER, RavagerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_QUEEN, QueenRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_EMPRESS, EmpressRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_BURSTER, BursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_RUNNER, RunnerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_SPITTER, SpitterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_WARRIOR, WarriorRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.OVIPOSITOR, OvipositorRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_COCOON, RoyalCocoonRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ABERRANT_ROYAL_COCOON, RoyalCocoonRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.NETHER_ROYAL_COCOON, RoyalCocoonRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.OVOMORPH, OvomorphRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.PRAETORIAN, PraetorianRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.PREDALIEN, PredalienRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.PREDALIEN_ADOLESCENT, PredalienAdolescentRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.PREDALIEN_CHESTBURSTER, PredalienChestbursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.PROWLER, ProwlerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.RAZOR_CLAW, RazorClawRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.RAVAGER, RavagerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.QUEEN, QueenRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_ABERRANT_ADOLESCENT, AdolescentRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_ABERRANT_CHESTBURSTER, ChestbursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_ABERRANT_FACEHUGGER, FacehuggerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_ABERRANT_OVOMORPH, OvomorphRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_ADOLESCENT, AdolescentRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_CHESTBURSTER, ChestbursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_FACEHUGGER, FacehuggerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_NETHER_ADOLESCENT, AdolescentRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_NETHER_CHESTBURSTER, ChestbursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_NETHER_FACEHUGGER, FacehuggerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_NETHER_OVOMORPH, OvomorphRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.ROYAL_OVOMORPH, OvomorphRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.BURSTER, BursterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.RUNNER, RunnerRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.SPITTER, SpitterRenderer::new);
        MOD.registries().registerEntityRenderer(AlienEntityTypes.WARRIOR, WarriorRenderer::new);
    }

    private static void registerParasiteHeadAttachmentOffsetData() {
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.CAMEL, AlienParasiteHeadAttachmentOffsetData.CAMEL);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.COW, AlienParasiteHeadAttachmentOffsetData.COW);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.DONKEY, AlienParasiteHeadAttachmentOffsetData.DONKEY);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.DOLPHIN, AlienParasiteHeadAttachmentOffsetData.DOLPHIN);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.EVOKER, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.FOX, AlienParasiteHeadAttachmentOffsetData.FOX);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.GOAT, AlienParasiteHeadAttachmentOffsetData.GOAT);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.HOGLIN, AlienParasiteHeadAttachmentOffsetData.HOGLIN);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.HORSE, AlienParasiteHeadAttachmentOffsetData.HORSE);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.ILLUSIONER, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.LLAMA, AlienParasiteHeadAttachmentOffsetData.LLAMA);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.MOOSHROOM, AlienParasiteHeadAttachmentOffsetData.COW);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.MULE, AlienParasiteHeadAttachmentOffsetData.MULE);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.PANDA, AlienParasiteHeadAttachmentOffsetData.PANDA);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.PIG, AlienParasiteHeadAttachmentOffsetData.PIG);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.PIGLIN, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.PIGLIN_BRUTE, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.PILLAGER, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.PLAYER, AlienParasiteHeadAttachmentOffsetData.PLAYER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.POLAR_BEAR, AlienParasiteHeadAttachmentOffsetData.POLAR_BEAR);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.RAVAGER, AlienParasiteHeadAttachmentOffsetData.RAVAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.SHEEP, AlienParasiteHeadAttachmentOffsetData.SHEEP);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.SNIFFER, AlienParasiteHeadAttachmentOffsetData.SNIFFER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.TRADER_LLAMA, AlienParasiteHeadAttachmentOffsetData.LLAMA);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.VILLAGER, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.VINDICATOR, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.WANDERING_TRADER, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.WITCH, AlienParasiteHeadAttachmentOffsetData.WITCH);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.WOLF, AlienParasiteHeadAttachmentOffsetData.WOLF);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.ZOGLIN, AlienParasiteHeadAttachmentOffsetData.HOGLIN);
        ParasiteHeadAttachmentOffsetDataCache.put(EntityType.ZOMBIE_VILLAGER, AlienParasiteHeadAttachmentOffsetData.VILLAGER);
    }

    private static void registerItemRenderers() {
        AlienXenomorphHeadItems.ALL.forEach(entry -> {
            registerAsset(entry.head(), entry.itemPath());
            registerAsset(entry.headShield(), entry.shieldItemPath());
        });
        registerAsset(AlienItems.ANCHOR, "anchor");
        registerAsset(AlienItems.INHIBITOR, "inhibitor");
        registerAsset(AlienItems.TRACKER, "tracker");
    }

    private static void registerAsset(BLibHolder<Item> holder, String configPath) {
        MOD.registries().registerGeoBoneItemRendererFromAsset(holder, AlienResources.location(configPath));
    }

    private static void registerBlockEntityRenderers() {
        MOD.registries()
            .registerBlockEntityRenderer(
                AlienBlockEntityTypes.QUEEN_HEAD,
                ctx -> new QueenHeadBlockEntityRenderer()
            );
        MOD.registries()
            .registerBlockEntityRenderer(
                AlienBlockEntityTypes.CRUSHER_HEAD,
                ctx -> new CrusherHeadBlockEntityRenderer()
            );
        MOD.registries()
            .registerBlockEntityRenderer(
                AlienBlockEntityTypes.XENOMORPH_HEAD,
                ctx -> new XenomorphHeadBlockEntityRenderer()
            );
        MOD.registries()
            .registerBlockEntityRenderer(
                AlienBlockEntityTypes.ANCHOR,
                ctx -> new AnchorBlockEntityRenderer()
            );
    }

    private static void registerParticleProviderFactories() {
        MOD.registries().registerParticleProviderFactory(AlienParticleTypes.ACID, AcidParticleProvider::new);
        MOD.registries().registerParticleProviderFactory(AlienParticleTypes.BLUE_ACID, BlueAcidParticleProvider::new);
        MOD.registries()
            .registerParticleProviderFactory(
                AlienParticleTypes.IRRADIATED_ACID,
                IrradiatedAcidParticleProvider::new
            );
    }
}
