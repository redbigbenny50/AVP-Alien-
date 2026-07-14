package com.alien.common.gameplay.hive.party;

import com.alien.AlienResources;
import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.registry.init.AlienSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Player counterplay to being carried off: the struggle bar.
 * <p>
 * Mobs do not resist a capture - being grabbed immobilizes them. Players do. Once a drone has a player on its back
 * (only possible at or below {@link HostCaptureRules#PLAYER_GRAB_HEALTH_FRACTION} of max health), a boss bar appears
 * and the player mashes <b>SPACE</b> or <b>LEFT-CLICK</b> to fill it:
 * <ul>
 * <li>Each accepted input needs {@link #MASH_COOLDOWN_TICKS} of cooldown, so an autoclicker gains nothing over a human
 * hammering the key - the cooldown caps everyone at the same input rate.</li>
 * <li>The bar {@link #DECAY_PER_TICK decays} once the player stops mashing (after a short grace), so it has to be
 * filled in one sustained effort - roughly ten seconds of solid mashing.</li>
 * <li>As it fills, the carrier <b>slows down</b>: -10% at a quarter, -20% at a half, -40% at three quarters. A player
 * who is losing the race can still buy enough time for the drone to be killed off them.</li>
 * <li>Full bar = free. {@link HostGrabImmunity#breakCapture} drops the player, grants 60s grab-immunity (other
 * xenomorphs still attack to kill - the hive just stops trying to take them alive) and stuns the carrier for 60s.</li>
 * </ul>
 * The bar is a plain {@link ServerBossEvent} shown to the struggling player alone - the server owns the whole mechanic
 * and no client state has to be synced. The client only sends {@code C2SHostStruggleMashPayload}; it can tell it is
 * grabbed simply by observing that its vehicle is an {@link Alien}.
 * <p>
 * The tick is driven from {@code MixinPlayer_HostCapture} (the player's own tick), NOT from the carrier's, so the bar
 * is still torn down correctly when the carrier dies, unloads, or is otherwise never ticked again.
 * <p>
 * [Flag for teammate review: capture/lifecycle interaction.]
 */
public final class HostStruggle {

    private HostStruggle() {}

    /** Minimum ticks between two accepted mashes. Caps autoclickers at a human's rate. */
    public static final int MASH_COOLDOWN_TICKS = 3;

    /** Bar fill per accepted mash: ~60 mashes to fill, which is ~10 seconds of solid hammering. */
    private static final float MASH_GAIN = 1.0F / 60.0F;

    /** Ticks of silence before the bar starts bleeding back down. */
    private static final int DECAY_GRACE_TICKS = 10;

    /** Decay per tick once the grace has lapsed: a full bar empties in 20 seconds of not mashing. */
    private static final float DECAY_PER_TICK = 1.0F / 400.0F;

    private static final ResourceLocation STRUGGLE_SPEED_MODIFIER = AlienResources.location("host_struggle_speed");

    private static final Map<ServerPlayer, State> STRUGGLES = new WeakHashMap<>();

    /** True if this player is currently being carried off by a xenomorph. */
    public static boolean isBeingCarried(Player player) {
        return player.getVehicle() instanceof Alien alien && HostCaptureTask.carriedHost(alien) == player;
    }

    /**
     * Driven from the player's own tick (server side). Starts, advances and tears down the struggle depending on
     * whether the player is currently on a carrier's back.
     */
    public static void tickPlayer(Player player) {
        if (player.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!(player.getVehicle() instanceof Alien carrier) || HostCaptureTask.carriedHost(carrier) != player) {
            end(serverPlayer);
            return;
        }

        var state = STRUGGLES.get(serverPlayer);
        if (state == null) {
            state = begin(serverPlayer, carrier);
        }
        state.carrier = carrier;

        var now = serverPlayer.level().getGameTime();
        if (now - state.lastInputTick > DECAY_GRACE_TICKS) {
            state.fill = Math.max(0.0F, state.fill - DECAY_PER_TICK);
        }

        state.bar.setProgress(Math.clamp(state.fill, 0.0F, 1.0F));
        applySlowdown(carrier, state.fill);

        if (state.fill >= 1.0F) {
            win(serverPlayer, carrier);
        }
    }

    /** A mash arrived from the client. Rate-limited; ignored if the player is not actually struggling. */
    public static void onMash(ServerPlayer player) {
        var state = STRUGGLES.get(player);
        if (state == null) {
            return;
        }
        if (!isBeingCarried(player)) {
            return;
        }

        var now = player.level().getGameTime();
        if (now - state.lastInputTick < MASH_COOLDOWN_TICKS) {
            return; // too soon - an autoclicker earns nothing here
        }

        state.lastInputTick = now;
        state.fill = Math.min(1.0F, state.fill + MASH_GAIN);
    }

    private static State begin(ServerPlayer player, Alien carrier) {
        var bar = new ServerBossEvent(
                Component.literal("Struggle! [ SPACE / LEFT-CLICK ]").withStyle(ChatFormatting.RED),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bar.setProgress(0.0F);
        bar.addPlayer(player);

        var state = new State(bar, carrier);
        state.lastInputTick = player.level().getGameTime();
        STRUGGLES.put(player, state);

        // Logged so "no bar appeared" is answerable from the log: if this line is absent the mixin never ran, and
        // if it is present the bar was opened server-side and the problem is elsewhere.
        com.alien.Alien.LOGGER.info("Opened the struggle bar for {} - grabbed by a carrier.", player.getName().getString());

        return state;
    }

    /** The player filled the bar: they tear free, the drone is left stunned and helpless. */
    private static void win(ServerPlayer player, Alien carrier) {
        HostGrabImmunity.breakCapture(carrier, player);
        end(player);

        carrier.level()
                .playSound(
                        null,
                        carrier.getX(),
                        carrier.getY(),
                        carrier.getZ(),
                        AlienSoundEvents.ENTITY_XENOMORPH_ESCAPE_HOST.get(),
                        SoundSource.HOSTILE,
                        1.0F,
                        1.0F
                );

        com.alien.Alien.LOGGER.info(
                "Player {} broke free of a capture and stunned the carrier.",
                player.getName().getString()
        );
    }

    /** Tear down a struggle for any reason: won, rescued, killed, dismounted, logged out. */
    public static void end(ServerPlayer player) {
        var state = STRUGGLES.remove(player);
        if (state == null) {
            return;
        }
        state.bar.removeAllPlayers();
        clearSlowdown(state.carrier);
    }

    /** Carrier speed scales down as the bar fills: 90% at a quarter, 80% at a half, 60% at three quarters. */
    private static void applySlowdown(Alien carrier, float fill) {
        var attribute = carrier.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) {
            return;
        }

        attribute.removeModifier(STRUGGLE_SPEED_MODIFIER);

        double reduction;
        if (fill >= 0.75F) {
            reduction = 0.40;
        } else if (fill >= 0.50F) {
            reduction = 0.20;
        } else if (fill >= 0.25F) {
            reduction = 0.10;
        } else {
            return;
        }

        attribute.addTransientModifier(
                new AttributeModifier(
                        STRUGGLE_SPEED_MODIFIER,
                        -reduction,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                )
        );
    }

    private static void clearSlowdown(Alien carrier) {
        if (carrier == null) {
            return;
        }
        var attribute = carrier.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.removeModifier(STRUGGLE_SPEED_MODIFIER);
        }
    }

    private static final class State {

        private final ServerBossEvent bar;

        private Alien carrier;

        private float fill;

        private long lastInputTick;

        private State(ServerBossEvent bar, Alien carrier) {
            this.bar = bar;
            this.carrier = carrier;
        }
    }
}