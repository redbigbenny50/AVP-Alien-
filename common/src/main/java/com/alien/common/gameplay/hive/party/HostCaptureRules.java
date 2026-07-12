package com.alien.common.gameplay.hive.party;

import com.alien.common.gameplay.entity.living.alien.Alien;
import com.alien.common.registry.tag.AlienEntityTypeTags;
import com.alien.common.util.AlienPredicates;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Who a host-hunt party may capture, and in what order.
 * <p>
 * Capture is strictly limited to entities in the host lists - a host party ignores everything else. (It still
 * defends itself: a non-host that attacks a drone is fought and killed through the normal targeting rules, it is
 * simply never CAPTURED.)
 * <p>
 * Priority follows what the hive gets out of the host: humanoid hosts (which grow into drones - the default,
 * generic path) first, then spitter hosts, then runner hosts.
 * <p>
 * Players are a special case: they may only be grabbed once worn down to {@link #PLAYER_GRAB_HEALTH_FRACTION} of
 * their max health, and never while they still have grab-immunity from a previous escape.
 * <p>
 * [Flag for teammate review: targeting/lifecycle interaction.]
 */
public final class HostCaptureRules {

    private HostCaptureRules() {}

    /** A player must be worn down to this fraction of max health before a drone will try to carry them off. */
    public static final float PLAYER_GRAB_HEALTH_FRACTION = 0.3F;

    private static final ResourceLocation LLAMA = ResourceLocation.withDefaultNamespace("llama");

    private static final ResourceLocation TRADER_LLAMA = ResourceLocation.withDefaultNamespace("trader_llama");

    /** Capture priority: lower sorts first. Humanoid/drone hosts, then spitter hosts, then runner hosts. */
    public static int capturePriority(Entity entity) {
        var type = entity.getType();
        if (isSpitterHost(type)) {
            return 1;
        }
        if (type.is(AlienEntityTypeTags.RUNNER_HOSTS)) {
            return 2;
        }
        return 0; // generic host -> grows into a drone
    }

    /** True if this entity may be carried off to a host chamber right now. */
    public static boolean isCapturable(Alien captor, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (!living.getType().is(AlienEntityTypeTags.HOSTS)) {
            return false; // only host-list creatures are ever captured
        }
        if (living.isPassenger() || !living.getPassengers().isEmpty()) {
            return false; // already being carried, or already wearing a facehugger
        }
        if (AlienPredicates.hasEmbryo(living)) {
            return false; // already implanted - nothing to gain
        }
        if (living instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            if (HostGrabImmunity.isImmune(player)) {
                return false; // just escaped a grab
            }
            return player.getHealth() <= player.getMaxHealth() * PLAYER_GRAB_HEALTH_FRACTION;
        }
        return true;
    }

    /** The best capture target from {@code candidates}, or null. Priority first, then distance. */
    public static @Nullable LivingEntity pickTarget(Alien captor, List<? extends LivingEntity> candidates) {
        return candidates.stream()
                .filter(candidate -> isCapturable(captor, candidate))
                .min(
                        Comparator.<LivingEntity>comparingInt(HostCaptureRules::capturePriority)
                                .thenComparingDouble(captor::distanceToSqr)
                )
                .orElse(null);
    }

    private static boolean isSpitterHost(EntityType<?> type) {
        var id = EntityType.getKey(type);
        return id.equals(LLAMA) || id.equals(TRADER_LLAMA);
    }
}