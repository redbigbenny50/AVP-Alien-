package com.alien.common.network.handler;

import com.alien.common.gameplay.entity.living.alien.parasite.HuggerStruggle;
import com.alien.common.gameplay.hive.party.HostStruggle;
import com.alien.common.network.payload.C2SHostStruggleMashPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * One mash packet serves both struggles - being carried off by a drone, and having a facehugger on your face. The two
 * are mutually exclusive (a drone will not grab a host that already wears a hugger), and each struggle ignores a mash
 * unless it is the one actually running, so the packet is simply offered to both.
 */
public final class HostStruggleMashHandler {

    private HostStruggleMashHandler() {}

    public static void handle(C2SHostStruggleMashPayload payload, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            HostStruggle.onMash(serverPlayer);
            HuggerStruggle.onMash(serverPlayer);
        }
    }
}
