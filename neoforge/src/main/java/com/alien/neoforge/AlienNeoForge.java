package com.alien.neoforge;

import com.alien.Alien;
import com.alien.common.gameplay.capture.CaptureChainInteraction;
import com.alien.common.gameplay.capture.CaptureHoldManager;
import com.alien.common.gameplay.capture.MobChainManager;
import com.alien.common.server.FieldManualGrant;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(Alien.MOD_ID)
public class AlienNeoForge {

    public AlienNeoForge() {
        Alien.initialize();
        // Capture-chain hold tether (server-side reel-in for player-held mobs).
        NeoForge.EVENT_BUS.addListener(
            (ServerTickEvent.Post event) -> CaptureHoldManager.tick(event.getServer())
        );
        NeoForge.EVENT_BUS.addListener(
            (ServerTickEvent.Post event) -> MobChainManager.tick(event.getServer())
        );

        // Capture chain grabs a mob before its own right-click (e.g. villager trade) can consume the interaction.
        // Field manual on first join - see the Fabric side; the grant itself is shared and idempotent.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                FieldManualGrant.grantIfNeeded(serverPlayer);
            }
        });

        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            var result = CaptureChainInteraction.tryHold(event.getEntity(), event.getTarget(), event.getHand());
            if (result.consumesAction()) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }
}
