package com.alien.fabric;

import com.alien.Alien;
import com.alien.common.gameplay.capture.CaptureChainInteraction;
import com.alien.common.gameplay.capture.CaptureHoldManager;
import com.alien.common.gameplay.capture.MobChainManager;
import com.alien.common.server.FieldManualGrant;
import com.alien.fabric.common.FlammableBlockRegistry;
import com.alien.fabric.data.loot.LootTableModifier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class AlienFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        Alien.initialize();

        // Functionality
        LootTableModifier.initialize();
        FlammableBlockRegistry.initialize();

        // Capture-chain hold tether (server-side reel-in for player-held mobs).
        // Every player is handed the field manual once, on first join. The grant is idempotent - it checks a
        // persistent player tag - so this fires on every join and acts on only the first.
        ServerPlayConnectionEvents.JOIN.register(
            (handler, sender, server) -> FieldManualGrant.grantIfNeeded(handler.getPlayer())
        );

        ServerTickEvents.END_SERVER_TICK.register(CaptureHoldManager::tick);
        ServerTickEvents.END_SERVER_TICK.register(MobChainManager::tick);

        // Capture chain grabs a mob before its own right-click (e.g. villager trade) can consume the interaction.
        UseEntityCallback.EVENT.register(
            (player, world, hand, entity, hitResult) -> CaptureChainInteraction.tryHold(player, entity, hand)
        );
    }
}
