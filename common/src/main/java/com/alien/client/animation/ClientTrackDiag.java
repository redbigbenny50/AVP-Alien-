package com.alien.client.animation;

import com.alien.Alien;
import com.blib.api.client.animation.v1.animator.AzEntityAnimator;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/**
 * Logs what the animation TRACK is actually playing, on the client, each tick.
 * <p>
 * ⭐⭐ THIS ANSWERS THE QUESTION THE SERVER DIAGNOSTIC CANNOT. {@code AnimationDiag} reports which branch the animator
 * CHOOSES, from synced flags - and on the stiff crusher it reported {@code chose=IDLE} steadily for ten seconds, so the
 * decision is right. What nobody could see is whether the chosen clip then actually RUNS: the track lives on the client
 * and never reaches the server log.
 * </p>
 * <p>
 * ⚠ THE THREE ANSWERS AND WHAT EACH MEANS:
 * </p>
 * <ul>
 * <li>{@code playing=idle state=PLAYING} while the model is frozen - the clip IS running, so the fault is in bone
 * application or rendering, and re-authoring the animation would change nothing;</li>
 * <li>{@code playing=null} or {@code state=STOPPED} - the dispatch is not taking, and the fault is in the command or
 * the track, not the art;</li>
 * <li>{@code playing=} something OTHER than idle - a different clip owns the track and is holding a pose.</li>
 * </ul>
 * <p>
 * ⚠ Client-side and inert unless a specific entity is being watched - one UUID comparison per animated entity per frame
 * when idle, which is nothing.
 * </p>
 */
public final class ClientTrackDiag {

    private static volatile UUID watched;

    private static volatile int framesLeft;

    private static String lastLine = "";

    private static int repeats;

    private ClientTrackDiag() {}

    public static void watch(UUID uuid, int frames) {
        watched = uuid;
        framesLeft = frames;
        lastLine = "";
        repeats = 0;
    }

    /**
     * Called from an animator after it has dispatched, with the track it dispatched onto.
     *
     * @param chose what the animator decided this frame, so the log lines up with the server diagnostic.
     */
    public static void record(Entity entity, AzEntityAnimator<?> animator, String trackName, String chose) {
        if (watched == null || framesLeft <= 0 || !watched.equals(entity.getUUID())) {
            return;
        }

        framesLeft--;

        var playing = "none";
        var state = "unknown";
        var behavior = "-";

        try {
            var track = animator.getAnimationTrackContainer().getOrNull(trackName);

            if (track == null) {
                state = "NO SUCH TRACK";
            } else {
                var current = track.currentAnimation();

                if (current != null) {
                    playing = current.animation().name();
                    behavior = String.valueOf(current.playBehavior());
                }

                var machine = track.stateMachine();
                state = machine.isPlaying()
                    ? "PLAYING"
                    : machine.isTransitioning()
                        ? "TRANSITIONING"
                        : machine.isPaused() ? "PAUSED" : machine.isStopped() ? "STOPPED" : "?";
            }
        } catch (Throwable throwable) {
            // ⚠ A DIAGNOSTIC MUST NEVER CRASH THE RENDER THREAD. If the API shifts under us, say so and carry on.
            state = "ERROR " + throwable.getClass().getSimpleName();
        }

        var line = String.format("chose=%-10s playing=%-24s state=%-14s behavior=%s", chose, playing, state, behavior);

        if (line.equals(lastLine)) {
            repeats++;
        } else {
            if (repeats > 0) {
                Alien.LOGGER.info("[trackdiag]   ... same for {} more frames", repeats);
                repeats = 0;
            }
            Alien.LOGGER.info("[trackdiag] {}", line);
            lastLine = line;
        }

        if (framesLeft <= 0) {
            if (repeats > 0) {
                Alien.LOGGER.info("[trackdiag]   ... same for {} more frames", repeats);
            }
            Alien.LOGGER.info("[trackdiag] === finished ===");
            watched = null;
        }
    }
}
