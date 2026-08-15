package com.alien.common.gameplay.advancement;

import com.alien.Alien;
import com.alien.common.data.AlienAdvancements;
import com.alien.compatibility.avp_human.AVPHuman;
import com.blib.api.common.advancement.v1.BLibAdvancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

public final class AlienAdvancementEvents {

    private static final BLibAdvancement[] VARIANT_XENOCIDE_ADVANCEMENTS = {
        AlienAdvancements.KILL_ALL_NORMAL_ALIENS,
        AlienAdvancements.KILL_ALL_ABERRANT_ALIENS,
        AlienAdvancements.KILL_ALL_NETHER_ALIENS,
        AlienAdvancements.KILL_ALL_IRRADIATED_ALIENS
    };

    public static void initialize() {
        Alien.MOD.events().onPlayerAdvancementAward().register(AlienAdvancementEvents::grantXenocideIfComplete);
    }

    private static void grantXenocideIfComplete(
        ServerPlayer player,
        AdvancementHolder advancementHolder,
        String criterionKey
    ) {
        if (AlienAdvancements.KILL_ALL_ALIENS.isGranted(player)) {
            return;
        }

        for (var advancement : VARIANT_XENOCIDE_ADVANCEMENTS) {
            if (!isRequiredVariantXenocideAdvancement(advancement)) {
                continue;
            }

            if (!advancement.isGranted(player)) {
                return;
            }
        }

        AlienAdvancements.KILL_ALL_ALIENS.grant(player);
    }

    private static boolean isRequiredVariantXenocideAdvancement(BLibAdvancement advancement) {
        return !AlienAdvancements.KILL_ALL_IRRADIATED_ALIENS.equals(advancement) || AVPHuman.MOD.isLoaded();
    }

    private AlienAdvancementEvents() {}
}
