package com.alien.common.gameplay.hive.structure;

import com.alien.common.gameplay.hive.party.HostGrabImmunity;
import com.alien.common.model.alien.FreeMob;
import com.alien.common.model.alien.Host;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Plants a delivered host into a host-chamber web spot (see {@link HostChamberSlots}). The captive is placed feet-first
 * inside the authored wall webbing - which slows it, since {@code resin_web} is {@code noCollision} - turned to face
 * the open interior where its egg is later set, and kept from despawning.
 * <p>
 * Players stay mobile-but-slowed (the webbing alone) and can cut themselves free with a tool. Mobs are additionally
 * frozen: {@link Mob#setNoAi(boolean)} halts any brain- or goal-driven mob universally, and
 * {@link FreeMob#removeFreedom()} applies the hive's own incapacitation idiom on top.
 * <p>
 * <b>Cutting a captive loose.</b> Breaking the resin web a host is embedded in {@link #releaseAt releases} it: the AI
 * comes back on and it walks away. Nothing used to undo the freeze - {@code setNoAi(false)} was called nowhere in the
 * codebase - so a webbed mob was frozen forever and could not be rescued by anyone.
 */
public final class HostParking {

    private HostParking() {}

    /** How far from a broken web block a captive may be and still be cut loose by it. */
    private static final double RELEASE_RADIUS = 1.5;

    /**
     * True if this host is RIGHT NOW webbed into a host chamber.
     * <p>
     * Keyed off {@code isNoAi()} rather than the embed timestamp. The timestamp is written once by {@link #embed} and
     * persisted forever, so it says "was captured at some point", not "is captured" - a player who wriggles free would
     * be branded uncapturable for life. {@code noAi} is set only here and cleared only by {@link #release}, so it
     * tracks the actual state. The embed stamp is checked alongside it purely so an unrelated NoAI mob (a spawn-egg
     * statue, another mod's prop) is not mistaken for the hive's cargo.
     * <p>
     * Two things depend on this: the hive does not send a capture party after hosts it has already caught, and
     * xenomorphs do not KILL them. A marine webbed in a chamber is meat in storage, not an enemy on the field.
     */
    public static boolean isParked(LivingEntity host) {
        if (host instanceof Player player) {
            return isPlayerParked(player);
        }

        return host instanceof Mob mob
            && mob.isNoAi()
            && host instanceof Host hostState
            && hostState.getEmbedGameTime() != Long.MIN_VALUE;
    }

    /**
     * Oct 9 - A WEBBED PLAYER IS CARGO TOO, WHILE THEY STAY IN THE WEB.
     * <p>
     * [tester] "When abducted by drones and sent back to the hive they make no attempt to provide a facehugger ... the
     * moment you're teleported in and placed in resin another drone or any xeno simply kills you". Log: "host
     * LordOfRoaches delivered to a host chamber spot", then "LordOfRoaches was slain by Drone" a moment later.
     * {@link #isParked} required a MOB with its AI switched off - a player is neither, so a delivered player was never
     * counted as the hive's cargo: every xeno in the chamber saw an ordinary enemy and killed them before the egg could
     * arrive (the egg is only sent after the host has settled).
     * <p>
     * A player is never frozen - the webbing slows them, they can always crawl out. So a player counts as parked only
     * while they are still at the spot they were webbed into; once they leave it they are an escaping prisoner, the
     * stamp is cleared, and the hive treats them as it would anyone (the escape window from {@link #embed} still stops
     * an instant re-grab).
     */
    private static boolean isPlayerParked(Player player) {
        if (!(player instanceof Host hostState) || hostState.getEmbedGameTime() == Long.MIN_VALUE) {
            return false;
        }

        var spot = PLAYER_SPOTS.get(player);

        if (spot == null || player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return false;
        }

        var dx = player.getX() - (spot.getX() + 0.5);
        var dz = player.getZ() - (spot.getZ() + 0.5);
        var dy = player.getY() - spot.getY();

        if (dx * dx + dz * dz <= PLAYER_SPOT_RADIUS * PLAYER_SPOT_RADIUS && Math.abs(dy) <= 1.5) {
            return true;
        }

        // Crawled out: no longer cargo.
        if (!player.level().isClientSide) {
            hostState.setEmbedGameTime(Long.MIN_VALUE);
            PLAYER_SPOTS.remove(player);
        }

        return false;
    }

    /** Oct 9 - how far a webbed player may shift in the web and still count as held there, in blocks. */
    private static final double PLAYER_SPOT_RADIUS = 1.25;

    /** Oct 9 - where each webbed player was embedded (server side; entries die with the player object). */
    private static final java.util.Map<Player, BlockPos> PLAYER_SPOTS = new java.util.WeakHashMap<>();

    /** Break the webbing around {@code webPos} and any captive it was holding walks free. */
    public static void releaseAt(ServerLevel level, BlockPos webPos) {
        for (var candidate : captivesAt(level, webPos)) {
            release(candidate);
        }
    }

    /** True if this web is currently holding a captive. Asked BEFORE the block breaks, to know it was a rescue. */
    public static boolean holdsCaptive(ServerLevel level, BlockPos webPos) {
        return !captivesAt(level, webPos).isEmpty();
    }

    private static java.util.List<LivingEntity> captivesAt(ServerLevel level, BlockPos webPos) {
        var box = new AABB(webPos).inflate(RELEASE_RADIUS);
        return level.getEntitiesOfClass(LivingEntity.class, box, HostParking::isParked);
    }

    /** Undo an embed: AI back on, no longer counted as the hive's cargo, and briefly immune to being re-taken. */
    public static void release(LivingEntity host) {
        if (host instanceof Mob mob) {
            mob.setNoAi(false);
            if (mob instanceof FreeMob freeMob) {
                freeMob.restoreFreedom();
            }
        }

        if (host instanceof Host hostState) {
            hostState.setEmbedGameTime(Long.MIN_VALUE);
        }

        if (host instanceof Player player) {
            PLAYER_SPOTS.remove(player);
        }

        // Give the rescuer a chance to actually lead it out, rather than the nearest drone plucking it straight back.
        HostGrabImmunity.grantImmunity(host, HostGrabImmunity.EMBED_ESCAPE_DURATION_TICKS);
    }

    /** Embed {@code host} at {@code spot}, facing {@code facing}. Server-side. */
    public static void embed(ServerLevel level, LivingEntity host, BlockPos spot, Direction facing) {
        float yaw = facing.toYRot();
        host.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
        host.setYBodyRot(yaw);
        host.setYHeadRot(yaw);
        host.setDeltaMovement(Vec3.ZERO);
        host.hasImpulse = true;
        host.fallDistance = 0.0F;

        if (host instanceof Mob mob) {
            mob.setPersistenceRequired();
            mob.setNoAi(true);
            if (mob instanceof FreeMob freeMob) {
                freeMob.removeFreedom();
            }
        }

        if (host instanceof Player) {
            // Losing the struggle is not the end of the line: the webbing slows a player but never holds them, so give
            // them a window in which the hive will not simply pluck them off the wall again while they crawl out.
            HostGrabImmunity.grantImmunity(host, HostGrabImmunity.EMBED_ESCAPE_DURATION_TICKS);
            PLAYER_SPOTS.put((Player) host, spot.immutable());
        }

        if (host instanceof Host hostState) {
            hostState.setEmbedGameTime(level.getGameTime());
        }
    }
}
