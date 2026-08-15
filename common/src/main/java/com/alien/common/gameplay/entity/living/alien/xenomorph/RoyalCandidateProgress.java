package com.alien.common.gameplay.entity.living.alien.xenomorph;

import com.alien.common.data.AlienVariantTypes;
import com.alien.common.gameplay.hive.faction.HiveMemberLocationResolver;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.alien.common.registry.init.AlienMobEffects;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.blib.api.common.entity.v1.PlayerUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class RoyalCandidateProgress {

    private static final int REQUIRED_KILLS = 5;

    private static final int METAMORPHOSIS_DURATION_IN_TICKS = 20 * 60 * 5;

    private RoyalCandidateProgress() {}

    public static void carryRoyalLineCandidate(Xenomorph source, Xenomorph destination) {
        if (!source.getType().is(AlienEntityTypeTags.ROYAL_ALIENS) || !source.getType().is(AlienEntityTypeTags.ADOLESCENTS)) {
            return;
        }

        if (!isAdultCandidate(destination)) {
            return;
        }

        destination.getXenomorphData().setRoyalLineCandidate(true);
        destination.getXenomorphData().setRoyalCandidateKills(0);
    }

    public static void onKilledEntity(Xenomorph candidate, ServerLevel level, LivingEntity killedEntity) {
        if (!candidate.getXenomorphData().isRoyalLineCandidate()) {
            return;
        }

        if (!isAdultCandidate(candidate) || killedEntity.getType().is(AlienEntityTypeTags.ALIENS) || !isWild(candidate)) {
            return;
        }

        var kills = candidate.getXenomorphData().incrementRoyalCandidateKills();
        if (kills >= REQUIRED_KILLS) {
            candidate.getXenomorphData().setRoyalLineCandidate(false);
            candidate.addEffect(new MobEffectInstance(AlienMobEffects.getMetamorphosisHolder(), METAMORPHOSIS_DURATION_IN_TICKS));
            notifyTracking(candidate, "A royal metamorphosis has begun.");
        }
    }

    private static boolean isAdultCandidate(Xenomorph xenomorph) {
        return xenomorph.getType().is(AlienEntityTypeTags.CRUSHERS) || xenomorph.getType().is(AlienEntityTypeTags.PRAETORIANS);
    }

    private static boolean isWild(Xenomorph xenomorph) {
        var locationAtPosition = HiveLocationRegistry.INSTANCE.getByChunk(xenomorph.level().dimension(), xenomorph.chunkPosition());
        return (locationAtPosition == null || !locationAtPosition.isAlive()) && HiveMemberLocationResolver.reserveReturnLocation(
            xenomorph
        ) == null;
    }

    private static void notifyTracking(Xenomorph xenomorph, String message) {
        for (var player : PlayerUtil.getTrackingPlayers(xenomorph)) {
            player.sendSystemMessage(
                Component.literal(message)
                    .withStyle(AlienVariantTypes.getFor(xenomorph).chatColor(), ChatFormatting.ITALIC)
            );
        }
    }
}
