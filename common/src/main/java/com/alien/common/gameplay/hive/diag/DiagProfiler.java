package com.alien.common.gameplay.hive.diag;

import com.alien.Alien;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⭐⭐⭐ A PROFILER FOR OUR OWN SYSTEMS, THAT A PLAYER CAN RUN WITHOUT KNOWING WHAT A PROFILER IS.
 * <p>
 * Spark is the right tool and nobody reporting a bug knows how to use it. This measures only avp_alien's tasks and
 * writes ONE plain-text file the reporter can attach, so the instruction is "run this, play until it lags, run that,
 * send me the file it names" rather than a tutorial.
 * </p>
 * <p>
 * ⚠⚠ OFF BY DEFAULT AND FREE WHEN OFF. Every measured call site is wrapped in {@code if (DiagProfiler.isRunning())},
 * which is a static boolean read - a profiler that costs something when disabled would be a performance bug shipped to
 * fix performance bugs.
 * </p>
 * <p>
 * ⭐⭐ THE WORST SINGLE CALL MATTERS MORE THAN THE AVERAGE, and the report leads with it. A task averaging 0.2ms that
 * spikes to 400ms once a minute drops the server visibly while looking healthy in a mean - that spike IS the report we
 * are chasing, and an average would hide it.
 * </p>
 * <p>
 * ⚠ Nanosecond timing only measures WALL TIME on the server thread. It cannot see a stall caused by another mod, and it
 * will attribute time spent inside BLib to whichever of our tasks called in - which is exactly what we want when the
 * suspicion is our pathing calls, and worth remembering when reading anything else.
 * </p>
 */
public final class DiagProfiler {

    /**
     * ⚠ Volatile, not synchronized. Read on every measured call from the server thread and written by a command; a
     * plain field could keep a stale value in a JIT-optimised loop and silently record nothing.
     */
    private static volatile boolean running;

    private static volatile long startedAtNanos;

    private static volatile long startedAtTick;

    /** ⚠ Concurrent because chunk and entity work can be driven off-thread under C2ME. */
    private static final Map<String, Sample> SAMPLES = new ConcurrentHashMap<>();

    private static final class Sample {

        private long totalNanos;

        private long calls;

        private long worstNanos;

        private synchronized void record(long nanos) {
            totalNanos += nanos;
            calls++;
            if (nanos > worstNanos) {
                worstNanos = nanos;
            }
        }
    }

    private DiagProfiler() {}

    public static boolean isRunning() {
        return running;
    }

    public static void start(MinecraftServer server) {
        SAMPLES.clear();
        SHOUTED.clear();
        OUTCOMES.clear();
        startedAtNanos = System.nanoTime();
        startedAtTick = server.overworld().getGameTime();
        running = true;
    }

    /**
     * Records one call of a named section. Call from a finally block so a throwing task still reports.
     */
    public static void record(String section, long startNanos) {
        if (!running) {
            return;
        }
        var elapsed = System.nanoTime() - startNanos;
        SAMPLES.computeIfAbsent(section, $ -> new Sample()).record(elapsed);

        // ⭐⭐⭐ A SINGLE CALL THIS SLOW IS THE WHOLE REPORT, AND THE PLAYER MAY NEVER GET TO STOP THE DIAGNOSTIC.
        //
        // ⚠⚠ THE CASE THAT PROMPTED THIS: "lag so bad it locked up the game and had to force close it." If the
        // session ends with a force-close, `stop` never runs and everything measured is lost - the worse the freeze,
        // the less likely we are to hear what caused it. Writing the offender to the log the moment it happens means
        // even a force-closed session leaves evidence in latest.log.
        //
        // ⚠ Logged at most once per section per session, so a task that is slow every tick cannot flood the log -
        // the first occurrence tells us what we need.
        if (elapsed > STALL_LOG_THRESHOLD_NANOS && SHOUTED.add(section)) {
            Alien.LOGGER.warn(
                "Hive diagnostic: {} took {} ms in a single call - this is the kind of stall we are looking for",
                section,
                elapsed / 1_000_000L
            );
        }
    }

    /** One second in a single call. Far past anything healthy, so it cannot fire on ordinary work. */
    private static final long STALL_LOG_THRESHOLD_NANOS = 1_000_000_000L;

    private static final java.util.Set<String> SHOUTED = ConcurrentHashMap.newKeySet();

    /**
     * ⭐ Times a call that RETURNS something, for wrapping BLib entry points we cannot instrument from inside.
     * <p>
     * ⚠ The pathfinder is the reason this exists. Time spent inside {@code NeoMoveToPosAction} is time spent in BLib's
     * preload loop, and attributing it to the action that called in is what turns "the server is lagging" into "our
     * idle wander is spending 300ms a tick in pathfinding".
     * </p>
     */
    public static <T> T timed(String section, java.util.function.Supplier<T> call) {
        if (!running) {
            return call.get();
        }
        var start = System.nanoTime();
        T result = null;
        try {
            result = call.get();
            return result;
        } finally {
            record(section, start);
            // ⭐⭐⭐ COUNT THE OUTCOME, NOT JUST THE TIME.
            //
            // ⚠⚠ TIME ALONE CANNOT ANSWER THE QUESTION BEING ASKED. "path/IdleActions: 1,240 calls, worst 890ms"
            // says pathing is expensive; it does not say whether those calls SUCCEEDED. A log with a hundred
            // "recovered a stranded xenomorph - no progress toward home for 90s" lines is a pathing FAILURE report,
            // and the fix for constant failure is nothing like the fix for constant slowness.
            //
            // ⭐ Recording FINISHED / MOVING / NO_PATH per call site turns "pathing is slow" into "IdleActions
            // returned NO PATH 1,190 times out of 1,240" - which names the behaviour that is losing them.
            if (result != null) {
                OUTCOMES
                    .computeIfAbsent(section, $ -> new ConcurrentHashMap<>())
                    .computeIfAbsent(String.valueOf(result), $ -> new java.util.concurrent.atomic.AtomicLong())
                    .incrementAndGet();
            }
        }
    }

    /** Per call site, how many times each result was returned. */
    private static final Map<String, Map<String, java.util.concurrent.atomic.AtomicLong>> OUTCOMES =
        new ConcurrentHashMap<>();

    /**
     * The finished report: where it was written, and the text itself.
     * <p>
     * ⚠ The TEXT is carried alongside the path on purpose. Plenty of reporters have no idea where their game directory
     * is, so the command offers a click-to-copy of the whole report - and that needs the text in hand, not a filename.
     * If the write fails, {@code file} is null and the clipboard route still works.
     * </p>
     */
    public record Report(
        @org.jetbrains.annotations.Nullable Path file,
        String text
    ) {}

    /**
     * Stops profiling and builds the report.
     *
     * @return the report, or null if none was running
     */
    public static @org.jetbrains.annotations.Nullable Report stop(MinecraftServer server) {
        if (!running) {
            return null;
        }
        running = false;

        var elapsedNanos = Math.max(1L, System.nanoTime() - startedAtNanos);
        var elapsedTicks = Math.max(1L, server.overworld().getGameTime() - startedAtTick);

        var text = new StringBuilder();
        text.append("AVP: ALIEN - HIVE DIAGNOSTIC\n");
        text.append("============================\n\n");
        text.append("Recorded over ")
            .append(elapsedTicks)
            .append(" ticks (")
            .append(String.format("%.1f", elapsedNanos / 1_000_000_000.0))
            .append(" seconds).\n");
        text.append("A healthy server runs 20 ticks per second. This session averaged ")
            .append(String.format("%.1f", elapsedTicks / (elapsedNanos / 1_000_000_000.0)))
            .append(".\n\n");

        // ⚠ Sorted by WORST single call, not by total. The report exists to find a spike, and a task that stalls the
        // server once is a more useful lead than one that costs a steady trickle - even when the trickle sums higher.
        var rows = new java.util.ArrayList<>(SAMPLES.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue().worstNanos, a.getValue().worstNanos));

        text.append(String.format("%-46s %10s %8s %10s %10s%n", "SECTION", "WORST ms", "CALLS", "TOTAL ms", "AVG ms"));
        text.append("-".repeat(88)).append('\n');

        var measuredNanos = 0L;
        for (var row : rows) {
            var s = row.getValue();
            measuredNanos += s.totalNanos;
            text.append(
                String.format(
                    "%-46s %10.2f %8d %10.2f %10.3f%n",
                    row.getKey(),
                    s.worstNanos / 1_000_000.0,
                    s.calls,
                    s.totalNanos / 1_000_000.0,
                    s.calls == 0 ? 0.0 : (s.totalNanos / (double) s.calls) / 1_000_000.0
                )
            );
        }

        text.append('\n');
        text.append("avp_alien accounted for ")
            .append(String.format("%.2f", measuredNanos / 1_000_000.0))
            .append(" ms of ")
            .append(String.format("%.2f", elapsedNanos / 1_000_000.0))
            .append(" ms elapsed (")
            .append(String.format("%.2f", 100.0 * measuredNanos / elapsedNanos))
            .append("%).\n");
        // ⚠ SAY WHAT A LOW NUMBER MEANS. Without this line a reporter sees "3%" and assumes the tool failed, when it
        // is in fact the answer: the lag is not ours, and that is worth knowing immediately.
        text.append("If that percentage is low and the server is still lagging, the cause is outside avp_alien.\n");

        // ⭐⭐ THE PATHING SECTION - the one that says WHICH BEHAVIOUR IS LOSING THEM.
        if (!OUTCOMES.isEmpty()) {
            text.append('\n');
            text.append("PATHING OUTCOMES\n");
            text.append("----------------\n");
            text.append("A high NO_PATH share means that behaviour is asking for journeys it cannot make.\n");
            text.append("MOVING is normal - it means the actor is still on its way.\n\n");

            var pathRows = new java.util.ArrayList<>(OUTCOMES.entrySet());
            pathRows.sort((a, b) -> Double.compare(failureRate(b.getValue()), failureRate(a.getValue())));

            for (var row : pathRows) {
                var counts = row.getValue();
                var total = counts.values().stream().mapToLong(java.util.concurrent.atomic.AtomicLong::get).sum();
                text.append(String.format("%-46s %8d calls%n", row.getKey(), total));
                var kinds = new java.util.ArrayList<>(counts.entrySet());
                kinds.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
                for (var kind : kinds) {
                    var n = kind.getValue().get();
                    text.append(
                        String.format(
                            "    %-20s %8d  (%.1f%%)%n",
                            kind.getKey(),
                            n,
                            total == 0 ? 0.0 : 100.0 * n / total
                        )
                    );
                }
                var failed = failureRate(counts);
                if (failed >= FAILURE_CONCERN_RATE) {
                    // ⚠ Say it outright rather than leaving it to be spotted in a table. This line is the entire
                    // reason the section exists.
                    text.append(
                        String.format(
                            "    >> %.0f%% of these journeys FAILED. This behaviour is losing its xenomorphs.%n",
                            failed * 100.0
                        )
                    );
                }
                text.append('\n');
            }
        }

        text.append("\nBLib: ").append(blibVersion()).append('\n');
        text.append("Hives loaded: ")
            .append(com.alien.common.gameplay.hive.location.HiveLocationRegistry.INSTANCE.all().size())
            .append('\n');

        try {
            var directory = server.getServerDirectory().resolve("config");
            Files.createDirectories(directory);
            var file = directory.resolve(
                "avp_alien-diag-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt"
            );
            Files.writeString(file, text.toString());
            return new Report(file, text.toString());
        } catch (Exception e) {
            // ⚠ A failed write is not a failed diagnostic. The reporter can still copy the text, which is the route
            // most of them will use anyway - losing the whole session because a directory was read-only would be
            // the worst possible outcome for something that only runs when something is already going wrong.
            Alien.LOGGER.warn("Hive diagnostic: could not write the report file", e);
            return new Report(null, text.toString());
        }
    }

    /** Share of calls that ended with no usable path. */
    private static double failureRate(Map<String, java.util.concurrent.atomic.AtomicLong> counts) {
        var total = counts.values().stream().mapToLong(java.util.concurrent.atomic.AtomicLong::get).sum();
        if (total == 0) {
            return 0.0;
        }
        var failed = counts.getOrDefault("NO_PATH", new java.util.concurrent.atomic.AtomicLong()).get();
        return failed / (double) total;
    }

    /** Above this share of NO_PATH, the report says so in words rather than leaving it in a table. */
    private static final double FAILURE_CONCERN_RATE = 0.25;

    /** ⚠ The BLib version is on the report because a missing pathfinder clamp there looks exactly like our bug. */
    private static String blibVersion() {
        try {
            var pkg = Class.forName("com.blib.api.common.pathfinding.v1.search.PathfindingTuning").getPackage();
            var version = pkg == null ? null : pkg.getImplementationVersion();
            return version == null ? "present (version unknown)" : version;
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
