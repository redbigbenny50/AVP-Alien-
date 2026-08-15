package com.alien.fabric.client;

import com.alien.client.AlienClient;
import com.alien.client.AlienClientHooks;
import com.alien.client.screen.FieldManualScreen;
import com.alien.common.registry.init.block.AberrantAlienResinBlocks;
import com.alien.common.registry.init.block.AlienResinBlocks;
import com.alien.common.registry.init.block.IrradiatedAlienResinBlocks;
import com.alien.common.registry.init.block.NetherAlienResinBlocks;
import com.alien.fabric.client.render.ResinAlphaModelWrapper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;

public class AlienFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AlienClient.initialize();
        ResinAlphaModelWrapper.register();
        registerResinRenderLayers();
        com.alien.fabric.client.render.HiveRenderHook.register();
        AlienClientHooks.registerFieldManualScreenOpener(
            () -> net.minecraft.client.Minecraft.getInstance().setScreen(new FieldManualScreen())
        );
    }

    private static void registerResinRenderLayers() {
        registerTranslucent(AlienResinBlocks.RESIN_VEIN.get(), AlienResinBlocks.RESIN_WEB.get());
        registerTranslucent(NetherAlienResinBlocks.NETHER_RESIN_VEIN.get(), NetherAlienResinBlocks.NETHER_RESIN_WEB.get());
        registerTranslucent(AberrantAlienResinBlocks.ABERRANT_RESIN_VEIN.get(), AberrantAlienResinBlocks.ABERRANT_RESIN_WEB.get());
        registerTranslucent(
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_VEIN.get(),
            IrradiatedAlienResinBlocks.IRRADIATED_RESIN_WEB.get()
        );
    }

    private static void registerTranslucent(Block... blocks) {
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), blocks);
    }
}
