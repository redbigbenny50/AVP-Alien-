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

    /**
     * ⚠⚠ THE {@code ModContainer} PARAMETER IS NEW HERE. NeoForge injects it into a {@code @Mod} constructor, and it is
     * the only way to register an extension point - which is what makes the "Config" button in the mod list (and in
     * Configured, and in anything else that reads the same hook) do something.
     * </p>
     */
    public AlienNeoForgeClient(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        // ⭐⭐ THE MOD-LIST CONFIG BUTTON.
        //
        // ⚠ Configured and friends only read NeoForge's own ModConfigSpec, which this mod deliberately does not use -
        // ModConfigSpec is NeoForge-only and avp_alien is multiloader, so its config is one JSON file that behaves
        // identically on both loaders. That is why those mods show nothing for us.
        //
        // ⚠ Registering a screen factory is the supported way to say "I have a config, here is how to edit it"
        // WITHOUT pretending to be a ModConfigSpec. The screen it opens redirects into /avp hive config gui, so the
        // button and the command are the same path rather than two.
        container.registerExtensionPoint(
            net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
            (ignoredContainer, parent) -> new com.alien.client.screen.HiveConfigEntryScreen(parent)
        );

        modBus.addListener(ResinAlphaBakedModel::modifyBakingResult);
        AlienClient.initialize();
        AlienClientHooks.registerFieldManualScreenOpener(
            () -> net.minecraft.client.Minecraft.getInstance().setScreen(new FieldManualScreen())
        );
        NeoForge.EVENT_BUS.register(com.alien.neoforge.client.render.HiveRenderHook.class);
    }
}
