package com.alien.neoforge.client;

import com.alien.Alien;
import com.alien.client.AlienClient;
import com.alien.client.AlienClientHooks;
import com.alien.client.screen.FieldManualScreen;
import com.alien.neoforge.client.render.ResinAlphaBakedModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Alien.MOD_ID, dist = Dist.CLIENT)
public class AlienNeoForgeClient {

    public AlienNeoForgeClient(IEventBus modBus) {
        modBus.addListener(ResinAlphaBakedModel::modifyBakingResult);
        AlienClient.initialize();
        AlienClientHooks.registerFieldManualScreenOpener(
            () -> net.minecraft.client.Minecraft.getInstance().setScreen(new FieldManualScreen())
        );
        NeoForge.EVENT_BUS.register(com.alien.neoforge.client.render.HiveRenderHook.class);
    }
}
