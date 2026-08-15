package com.alien.common.gameplay.entity.living.alien.parasite;

import com.alien.common.gameplay.hive.party.HostStruggle;
import com.alien.common.registry.init.AlienSoundEvents;
import com.alien.common.util.AlienPredicates;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Player counterplay to a facehugger on your face.
 * <p>
 * The clock is not generous and it is not ours: {@link ParasiteAttachmentManager} implants at <b>400 ticks (20s)</b> of
 * attachment. Everything here is sized to fit inside that window - a fresh struggle is ~30 mashes (about four and a
 * half seconds of hammering), and even a fully escalated one is 60 mashes (about nine seconds), leaving room to fumble.
 * <p>
 * Mechanically it is the twin of {@link HostStruggle} - same SPACE / LEFT-CLICK mash, same
 * {@link HostStruggle#MASH_COOLDOWN_TICKS} rate limit so autoclickers gain nothing, same server-owned boss bar - with
 * three differences:
 * <ul>
 * <li>The host is <b>immobile</b> while hugged (see {@code ParasiteAttachmentManager} for the server-side lock and
 * {@code MixinKeyboardInput_HuggerLock} for the client-side one). You cannot run; you can only fight.</li>
 * <li>It gets <b>harder every time</b>: {@link #MASHES_PER_ESCAPE} more mashes per previous escape, up to
 * {@link #MAX_MASHES}. That count decays over time - see {@link HuggerImmunity}.</li>
 * <li>Winning <b>costs a quarter of your hunger bar</b> (and all your saturation - otherwise a well-fed player pays
 * nothing they can see). Tearing it off is not free.</li>
 * </ul>
 * Win and the hugger is flung off, still fertile, and cannot come back for {@link HuggerImmunity#IMMUNITY_TICKS}. Lose
 * and the existing implant timer does what it was always going to do.
 * <p>
 * [Flag for teammate review: parasite/lifecycle interaction.]
 */
public final class HuggerStruggle {

    private HuggerStruggle() {}

    /** A first, unescalated struggle: ~30 mashes, roughly four and a half seconds of hammering. */
    private static final int BASE_MASHES = 30;

    /** Every previous escape adds this many mashes to the next struggle. */
    private static final int MASHES_PER_ESCAPE = 10;

    /** Hard ceiling: ~9 seconds of mashing, comfortably inside the 20-second implant window. */
    private static final int MAX_MASHES = 60;

    /** Ticks of silence before the bar starts bleeding back down. */
    private static final int DECAY_GRACE_TICKS = 10;

    /** Once decaying, a tick costs a quarter of what one mash gains - so the feel holds as the bar gets longer. */
    private static final float DECAY_FRACTION_OF_A_MASH = 0.25F;

    /** A quarter of the twenty-point hunger bar. */
    private static final int HUNGER_COST = 5;

    private static final Map<ServerPlayer, State> STRUGGLES = new WeakHashMap<>();

    /** The fertile parasite currently riding this host's face, or null. */
    public static @Nullable Parasite attachedParasite(Player player) {
        for (var passenger : player.getPassengers()) {
            if (passenger instanceof Parasite parasite) {
                return parasite;
            }
        }
        return null;
    }

    /** True if a hugger is on this host's face right now. Safe on both sides - used by the client input lock too. */
    public static boolean isBeingHugged(Player player) {
        return attachedParasite(player) != null;
    }

    /**
     * Driven from the player's own tick (server side), alongside {@link HostStruggle#tickPlayer}. Starts, advances and
     * tears down the struggle depending on whether a hugger is currently attached.
     */
    public static void tickPlayer(Player player) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        var parasite = attachedParasite(player);

        // No hugger, or the fight is already lost (implanted): nothing left to struggle against.
        if (parasite == null || !parasite.isFertile.get() || AlienPredicates.hasEmbryo(player)) {
            end(serverPlayer);
            return;
        }

        var state = STRUGGLES.get(serverPlayer);
        if (state == null) {
            state = begin(serverPlayer, parasite);
        }
        state.parasite = parasite;

        var now = serverPlayer.level().getGameTime();
        if (now - state.lastInputTick > DECAY_GRACE_TICKS) {
            state.fill = Math.max(0.0F, state.fill - state.gain * DECAY_FRACTION_OF_A_MASH);
        }

        state.bar.setProgress(Math.clamp(state.fill, 0.0F, 1.0F));

        if (state.fill >= 1.0F) {
            win(serverPlayer, parasite);
        }
    }

    /** A mash arrived from the client. Rate-limited; ignored if the player is not actually being hugged. */
    public static void onMash(ServerPlayer player) {
        var state = STRUGGLES.get(player);
        if (state == null || !isBeingHugged(player)) {
            return;
        }

        var now = player.level().getGameTime();
        if (now - state.lastInputTick < HostStruggle.MASH_COOLDOWN_TICKS) {
            return; // too soon - an autoclicker earns nothing here
        }

        state.lastInputTick = now;
        state.fill = Math.min(1.0F, state.fill + state.gain);
    }

    private static State begin(ServerPlayer player, Parasite parasite) {
        var required = Math.min(
            MAX_MASHES,
            BASE_MASHES + MASHES_PER_ESCAPE * HuggerImmunity.escapeCount(player)
        );

        var bar = new ServerBossEvent(
            Component.literal("Get it off! [ SPACE / LEFT-CLICK ]").withStyle(ChatFormatting.GREEN),
            BossEvent.BossBarColor.GREEN,
            BossEvent.BossBarOverlay.PROGRESS
        );
        bar.setProgress(0.0F);
        bar.addPlayer(player);

        var state = new State(bar, parasite, 1.0F / required);
        state.lastInputTick = player.level().getGameTime();
        STRUGGLES.put(player, state);
        return state;
    }

    /**
     * The host filled the bar: the hugger is torn off and flung away, and the effort costs a quarter of the hunger bar.
     */
    private static void win(ServerPlayer player, Parasite parasite) {
        end(player);

        parasite.detach();

        // Fling it off the face rather than letting it land on the player's toes and immediately re-touch.
        var look = player.getLookAngle();
        parasite.setDeltaMovement(look.x * 0.6, 0.4, look.z * 0.6);
        parasite.hasImpulse = true;

        HuggerImmunity.onEscape(player);

        var food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - HUNGER_COST));
        food.setSaturation(0.0F);

        player.level()
            .playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                AlienSoundEvents.ENTITY_FACEHUGGER_ESCAPE.get(),
                SoundSource.HOSTILE,
                1.0F,
                1.0F
            );

        com.alien.Alien.LOGGER.info(
            "Player {} tore a facehugger off (escape #{}).",
            player.getName().getString(),
            HuggerImmunity.escapeCount(player)
        );
    }

    /** Tear down a struggle for any reason: won, implanted, hugger fell off or was killed, host died. */
    public static void end(ServerPlayer player) {
        var state = STRUGGLES.remove(player);
        if (state != null) {
            state.bar.removeAllPlayers();
        }
    }

    private static final class State {

        private final ServerBossEvent bar;

        /** Bar fill per accepted mash. Baked in at the start of the struggle from the host's escalation. */
        private final float gain;

        private Parasite parasite;

        private float fill;

        private long lastInputTick;

        private State(ServerBossEvent bar, Parasite parasite, float gain) {
            this.bar = bar;
            this.parasite = parasite;
            this.gain = gain;
        }
    }
}
