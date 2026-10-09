package com.alien.common.gameplay.hive.diag;

import com.alien.Alien;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Logs, tick by tick, exactly which animation branch a xenomorph's animator is taking - and why.
 * <p>
 * [stated] "is there a diag i can do that shows you what the animations are actually doing or a way i can make a video
 * you can actually see". Video cannot be read here; this can, and it is more precise anyway.
 * </p>
 * <p>
 * ⭐⭐ SERVER-SIDE, THOUGH THE ANIMATOR IS A CLIENT. That works because every animator branches on SYNCED values -
 * attackType, isLunging, isMovingHorizontally, isMovingQuickly, onGround, crawling. The client's choice is a pure
 * function of those, so logging them from the server reproduces the decision without touching client code or adding a
 * packet.
 * </p>
 * <p>
 * ⚠ WHAT A STIFF MOB LOOKS LIKE IN THIS OUTPUT:
 * </p>
 * <ul>
 * <li>{@code chose=IDLE} every line while the mob is visibly still - the branch is right, so the fault is in the clip
 * or the track, not the logic;</li>
 * <li>{@code chose=} FLIPPING between WALK and IDLE tick to tick - the alternation restarts both clips, which reads as
 * a frozen or stuttering mob;</li>
 * <li>{@code chose=ATTACK} or {@code AIRBORNE} while nothing is happening - a latched state is returning before the
 * gait is ever reached.</li>
 * </ul>
 * <p>
 * Those three look identical in game and have completely different causes, which is why guessing from a description has
 * not worked.
 * </p>
 */
public final class AnimationDiag {

    private static volatile UUID watched;

    private static volatile int ticksLeft;

    private static String lastLine = "";

    private static int repeats;

    private AnimationDiag() {}

    /**
     * Set by the client at startup so the same command also starts the CLIENT-side track watch.
     * <p>
     * WARNING: a hook rather than a direct call, because this class is common code and runs on dedicated servers too.
     * Touching a client class from here would be a NoClassDefFoundError the moment anyone ran the command headless.
     * Null on a dedicated server, which simply means no track log.
     * </p>
     */
    public static volatile java.util.function.BiConsumer<UUID, Integer> clientWatchHook;

    public static void watch(UUID uuid, int ticks) {
        watched = uuid;
        ticksLeft = ticks;
        lastLine = "";
        repeats = 0;

        var hook = clientWatchHook;

        if (hook != null) {
            hook.accept(uuid, ticks);
        }
    }

    public static boolean isWatching() {
        return watched != null && ticksLeft > 0;
    }

    /** Called from the xenomorph's server tick. Cheap and inert unless this exact entity is being watched. */
    public static void record(Xenomorph xenomorph) {
        if (watched == null || ticksLeft <= 0 || !watched.equals(xenomorph.getUUID())) {
            return;
        }

        if (!(xenomorph.level() instanceof ServerLevel)) {
            return;
        }

        ticksLeft--;

        var attackType = xenomorph.attackType.get();
        var crawling = xenomorph.getCrawlingManager().isCrawling();
        var moving = xenomorph.isMovingHorizontally.get();
        var onGround = xenomorph.onGround();
        var quick = xenomorph.isMovingQuickly.get();
        var lunging = xenomorph.isLunging.get();
        var verticalSpeed = Math.abs(xenomorph.getDeltaMovement().y);

        // The animators all share this decision shape, so naming the branch here names the clip they will pick.
        String chose;
        if (!attackType.isNone()) {
            chose = "ATTACK(" + attackType + " id=" + xenomorph.attackId.get() + ")";
        } else if (lunging) {
            chose = "LUNGE";
        } else if (!onGround && verticalSpeed > 0.01D) {
            chose = "AIRBORNE";
        } else if (moving && onGround) {
            chose = crawling ? "CRAWL" : (quick ? "RUN" : "WALK");
        } else {
            chose = crawling ? "CRAWL_IDLE" : "IDLE";
        }

        var line = String.format(
            "chose=%-22s moving=%-5s onGround=%-5s quick=%-5s crawl=%-5s lunge=%-5s dY=%.4f",
            chose,
            moving,
            onGround,
            quick,
            crawling,
            lunging,
            verticalSpeed
        );

        // ⚠ COLLAPSE REPEATS. A stable mob would otherwise bury the interesting transitions under hundreds of
        // identical lines - and it is the TRANSITIONS that show a flip-flop.
        if (line.equals(lastLine)) {
            repeats++;
        } else {
            if (repeats > 0) {
                Alien.LOGGER.info("[animdiag]   ... same for {} more ticks", repeats);
                repeats = 0;
            }
            Alien.LOGGER.info("[animdiag] {}", line);
            lastLine = line;
        }

        if (ticksLeft <= 0) {
            if (repeats > 0) {
                Alien.LOGGER.info("[animdiag]   ... same for {} more ticks", repeats);
            }
            Alien.LOGGER.info("[animdiag] === watch finished for {} ===", xenomorph.getUUID());
            watched = null;
        }
    }
}
