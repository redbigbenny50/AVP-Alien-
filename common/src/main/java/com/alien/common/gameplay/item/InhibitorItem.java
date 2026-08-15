package com.alien.common.gameplay.item;

import com.alien.common.gameplay.entity.living.alien.xenomorph.queen.Queen;
import com.alien.common.gameplay.hive.lifecycle.QueenInhibitionService;
import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The inhibitor device - right-click a queen to clamp it to her crest. An inhibited queen becomes a contained breeder:
 * her hive autonomy is suppressed and her claim is capped at one chunk, though she can still fight and defend. Chaining
 * an inhibited queen later gives her the ridable chained eggsack to lay from.
 * <p>
 * Application is gated to a queen who cannot resist - see {@link Queen#canBeInhibited()}. A refusal is NOT silent: she
 * hisses and the player gets an action-bar line naming the reason, because a quiet no-op is indistinguishable from a
 * broken item ([stated] tester confusion: "its not applying to her and im not getting a any kind of feedback").
 */
public class InhibitorItem extends Item {

    private static final String NOT_SUBDUED_KEY = "message.avp_alien.inhibitor.not_subdued";

    private static final String ALREADY_ATTACHED_KEY = "message.avp_alien.inhibitor.already_attached";

    public InhibitorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(
        @NotNull ItemStack stack,
        @NotNull Player player,
        @NotNull LivingEntity target,
        @NotNull InteractionHand hand
    ) {
        if (!(target instanceof Queen queen)) {
            return super.interactLivingEntity(stack, player, target, hand);
        }

        // EVERY decision below is server-only, deliberately. The client cannot answer canBeInhibited() honestly - the
        // bind anchors and the lifecycle phase live server-side only, and the player-placed flag is not synced - so a
        // client-side gate would disagree with the server and swallow legitimate attaches. The client just reports
        // SUCCESS so the arm swings, and the server rules on it.
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.sidedSuccess(true);
        }

        if (queen.isInhibited()) {
            player.displayClientMessage(Component.translatable(ALREADY_ATTACHED_KEY), true);
            return InteractionResult.sidedSuccess(false);
        }

        if (!queen.canBeInhibited()) {
            player.displayClientMessage(Component.translatable(NOT_SUBDUED_KEY), true);
            serverLevel.playSound(
                null,
                queen.getX(),
                queen.getY(),
                queen.getZ(),
                AlienSoundEvents.ENTITY_XENOMORPH_HISS.get(),
                SoundSource.HOSTILE,
                1.2F,
                0.9F
            );
            return InteractionResult.sidedSuccess(false);
        }

        queen.setInhibited(true);
        QueenInhibitionService.onInhibited(serverLevel, queen);

        // The clamp locking on. Deliberately NOT the chain sound the bind anchors use - a player should be able to
        // tell an inhibitor attach from a shackle by ear.
        serverLevel.playSound(
            null,
            queen.getX(),
            queen.getY(),
            queen.getZ(),
            SoundEvents.IRON_DOOR_CLOSE,
            SoundSource.HOSTILE,
            1.0F,
            0.8F
        );

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.sidedSuccess(false);
    }
}
