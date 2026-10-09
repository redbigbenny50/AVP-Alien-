package com.alien.common.network.handler;

import com.alien.common.gameplay.hive.config.HiveConfigSchema;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.network.payload.C2SUpdateHiveConfigPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class HiveConfigUpdateHandler {

    private HiveConfigUpdateHandler() {}

    public static void handle(C2SUpdateHiveConfigPayload payload, Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (!sp.hasPermissions(2)) {
            return;
        }

        try {
            var current = HiveLocationRegistry.INSTANCE.config();
            var updated = HiveConfigSchema.withParsedValue(current, payload.fieldName(), payload.value());
            HiveLocationRegistry.INSTANCE.setConfig(updated);

            // \u2b50\u2b50 PERSIST IT. This handler was the ONLY writer of the live config in the entire mod, and
            // nothing wrote a file \u2014 so every edit made through the in-game inspector was lost on restart. That is
            // the whole bug the config file exists to fix; the GUI was never the problem, the missing save was.
            var server = player.getServer();
            if (server != null) {
                com.alien.common.gameplay.hive.config.HiveConfigFile.save(server);
            }
        } catch (IllegalArgumentException exception) {
            sp.sendSystemMessage(Component.literal(exception.getMessage()));
        }
    }
}
