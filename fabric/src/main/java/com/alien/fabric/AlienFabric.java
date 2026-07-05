package com.alien.fabric;

import com.alien.Alien;
import com.alien.common.gameplay.capture.CaptureChainInteraction;
import com.alien.common.gameplay.capture.CaptureHoldManager;
import com.alien.common.gameplay.capture.MobChainManager;
import com.alien.fabric.common.FlammableBlockRegistry;
import com.alien.fabric.data.loot.LootTableModifier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

public class AlienFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Alien.initialize();

        // Functionality
        LootTableModifier.initialize();
        FlammableBlockRegistry.initialize();

        // Capture-chain hold tether (server-side reel-in for player-held mobs).
        ServerTickEvents.END_SERVER_TICK.register(CaptureHoldManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(MobChainManager::tick);

        // Capture chain grabs a mob before its own right-click (e.g. villager trade) can consume the interaction.
        UseEntityCallback.EVENT.register(
            (player, world, hand, entity, hitResult) -> CaptureChainInteraction.tryHold(player, entity, hand)
        );
    }
}
