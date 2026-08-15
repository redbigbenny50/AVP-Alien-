package com.alien.common.gameplay.hive.bootstrap;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.gameplay.level.saveddata.RoyalBootstrapLeakData;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import net.minecraft.server.level.ServerLevel;

public final class RoyalBootstrapLeakRecorder {

    private RoyalBootstrapLeakRecorder() {}

    public static void recordIfEligible(Alien alien) {
        if (!(alien.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!alien.getType().is(AlienEntityTypeTags.ROYAL_ALIENS)) {
            return;
        }

        if (!alien.getType().is(AlienEntityTypeTags.CHESTBURSTERS) && !alien.getType().is(AlienEntityTypeTags.ADOLESCENTS)) {
            return;
        }

        if (com.alien.Alien.MOD.territory().isPlayerClaimed(serverLevel, alien.chunkPosition())) {
            return;
        }

        RoyalBootstrapLeakData.getOrCreate(serverLevel)
            .ifSome(data -> data.add(alien.getVariant()));
    }
}
