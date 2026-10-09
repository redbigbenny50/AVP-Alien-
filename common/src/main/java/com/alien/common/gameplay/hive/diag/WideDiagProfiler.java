package com.alien.common.gameplay.hive.diag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Times EVERY entity and block-entity tick on the server, from every mod, grouped by namespace.
 * <p>
 * [stated] "i have all the avp modules installed. so i want to know what else could be causing the issue ... i want to
 * see what else might be interacting with our mods and causing any slowdown".
 * </p>
 * <p>
 * !!! WHY THIS EXISTS SEPARATELY FROM DiagProfiler. That one times avp_alien's OWN sections, so every report it
 * produces ends the same way: "avp_alien accounted for 1.73% - the cause is outside avp_alien". True, and useless,
 * because it cannot say what the other 98% was. This measures the two paths that every ticking thing in the game passes
 * through, so the answer comes back as a league table of MODS.
 * </p>
 * <p>
 * ⚠⚠ THIS IS A HOT PATH AND THE COST IS REAL. Two nanoTime calls per entity per tick while a capture is running.
 * {@link #isCapturing()} is a single volatile read and returns false the rest of the time, so an idle server pays
 * essentially nothing - but a RUNNING capture measurably costs TPS itself. The numbers are for finding a 40% offender,
 * not for fine tuning; that is still spark's job.
 * </p>
 * <p>
 * ⚠ Timings are per TICK CALL, not wall-clock ownership. A mod whose work happens on a scheduled task, a network
 * handler or a chunk-load callback will under-report here. Absence of evidence is not evidence of innocence.
 * </p>
 */
public final class WideDiagProfiler {

    /** ⚠ Volatile: read from the server thread on every tick, written by a command. */
    private static volatile boolean capturing;

    private static volatile long startedAtNanos;

    /** ⚠ Concurrent - entity work can be driven off-thread under C2ME and friends. */
    private static final Map<String, Bucket> ENTITIES = new ConcurrentHashMap<>();

    private static final Map<String, Bucket> BLOCK_ENTITIES = new ConcurrentHashMap<>();

    /**
     * One measured group - a namespace, or a single type within one.
     * <p>
     * Atomics rather than a synchronized block: contention here would be self-defeating on the very path being
     * measured.
     * </p>
     */
    private static final class Bucket {

        private final AtomicLong totalNanos = new AtomicLong();

        private final AtomicLong calls = new AtomicLong();

        private final AtomicLong worstNanos = new AtomicLong();

        private void record(long nanos) {
            totalNanos.addAndGet(nanos);
            calls.incrementAndGet();
            worstNanos.accumulateAndGet(nanos, Math::max);
        }
    }

    private WideDiagProfiler() {}

    /**
     * Where a tick's start time is parked between the HEAD and TAIL handlers.
     * <p>
     * ⚠ THREAD-LOCAL, NOT A FIELD ON THE MIXIN. Entity ticking can be driven off the main thread by chunk-system mods
     * such as C2ME, and a shared field would then have two threads writing one slot and both reporting nonsense. It
     * also makes nesting harmless: an inner tick overwrites the slot, so the outer one records a short reading rather
     * than a corrupt one.
     * </p>
     */
    private static final ThreadLocal<long[]> TICK_START = ThreadLocal.withInitial(() -> new long[1]);

    public static boolean isCapturing() {
        return capturing;
    }

    /** Marks the start of a measured tick on this thread. */
    public static void markStart() {
        if (!capturing) {
            return;
        }

        TICK_START.get()[0] = System.nanoTime();
    }

    private static long elapsedSinceStart() {
        var start = TICK_START.get()[0];

        return start == 0L ? 0L : System.nanoTime() - start;
    }

    public static void start() {
        ENTITIES.clear();
        BLOCK_ENTITIES.clear();
        startedAtNanos = System.nanoTime();
        capturing = true;
    }

    /** Records one entity tick. Called from the mixin on the vanilla guard; a no-op unless capturing. */
    public static void recordEntity(Entity entity) {
        if (!capturing) {
            return;
        }

        record(ENTITIES, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), elapsedSinceStart());
    }

    /** Records one block-entity tick. */
    public static void recordBlockEntity(BlockEntity blockEntity) {
        if (!capturing) {
            return;
        }

        record(BLOCK_ENTITIES, BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()), elapsedSinceStart());
    }

    private static void record(Map<String, Bucket> into, ResourceLocation id, long nanos) {
        if (id == null) {
            return;
        }

        // Two keys per sample: the namespace (the headline) and the full id (the detail under it). Cheap, and it
        // means the report can answer both "which MOD" and "which THING" without a second capture.
        into.computeIfAbsent(id.getNamespace(), key -> new Bucket()).record(nanos);
        into.computeIfAbsent(id.getNamespace() + ":" + id.getPath(), key -> new Bucket()).record(nanos);
    }

    /**
     * Stops the capture and renders the report.
     *
     * @return the report text, or null if no capture was running.
     */
    public static String stop(MinecraftServer server) {
        if (!capturing) {
            return null;
        }

        capturing = false;

        var elapsedNanos = System.nanoTime() - startedAtNanos;
        var elapsedMs = elapsedNanos / 1_000_000.0D;

        var out = new StringBuilder();
        out.append("AVP: SERVER-WIDE DIAGNOSTIC\n");
        out.append("===========================\n");
        out.append(String.format("Recorded over %.1f seconds.%n", elapsedMs / 1000.0D));
        out.append("Every ticking entity and block entity on the server, grouped by the mod that owns it.\n");
        out.append("\n");

        appendSection(out, "ENTITY TICKS BY MOD", ENTITIES, elapsedMs, true);
        appendSection(out, "ENTITY TICKS - WORST INDIVIDUAL TYPES", ENTITIES, elapsedMs, false);
        appendSection(out, "BLOCK ENTITY TICKS BY MOD", BLOCK_ENTITIES, elapsedMs, true);
        appendSection(out, "BLOCK ENTITY TICKS - WORST INDIVIDUAL TYPES", BLOCK_ENTITIES, elapsedMs, false);

        appendCounts(out, server);

        out.append("\n");
        out.append("A mod high in these tables is where the tick is going. Note this measures TICK time only -\n");
        out.append("work done on scheduled tasks, packets or chunk loads will not appear here.\n");

        return out.toString();
    }

    /**
     * @param namespacesOnly true for the by-mod table (keys without a colon), false for the per-type table.
     */
    private static void appendSection(
        StringBuilder out,
        String title,
        Map<String, Bucket> source,
        double elapsedMs,
        boolean namespacesOnly
    ) {
        var rows = new ArrayList<Map.Entry<String, Bucket>>();
        for (var entry : source.entrySet()) {
            if (entry.getKey().contains(":") == namespacesOnly) {
                continue;
            }
            rows.add(entry);
        }

        if (rows.isEmpty()) {
            return;
        }

        rows.sort(
            Comparator.comparingLong((Map.Entry<String, Bucket> entry) -> entry.getValue().totalNanos.get())
                .reversed()
        );

        out.append(title).append('\n');
        out.append("-".repeat(title.length())).append('\n');
        out.append(String.format("%-44s %10s %10s %10s %8s%n", "NAME", "TOTAL ms", "CALLS", "AVG ms", "SHARE"));

        var limit = namespacesOnly ? rows.size() : Math.min(rows.size(), 15);
        for (var i = 0; i < limit; i++) {
            var row = rows.get(i);
            var totalMs = row.getValue().totalNanos.get() / 1_000_000.0D;
            var calls = row.getValue().calls.get();
            out.append(
                String.format(
                    "%-44s %10.2f %10d %10.3f %7.2f%%%n",
                    row.getKey(),
                    totalMs,
                    calls,
                    calls == 0 ? 0.0D : totalMs / calls,
                    elapsedMs == 0.0D ? 0.0D : (totalMs / elapsedMs) * 100.0D
                )
            );
        }

        out.append('\n');
    }

    /**
     * Raw counts, because sheer quantity is as common a cause as expensive logic.
     * <p>
     * A thousand dropped items ticking cheaply outweighs a handful of expensive mobs, and the timing tables alone make
     * that look like "minecraft is slow" rather than "something is spilling items".
     * </p>
     */
    private static void appendCounts(StringBuilder out, MinecraftServer server) {
        var counts = new java.util.HashMap<String, Integer>();
        var loadedChunks = 0;
        var totalEntities = 0;

        for (var level : server.getAllLevels()) {
            loadedChunks += level.getChunkSource().getLoadedChunksCount();
            for (var entity : level.getAllEntities()) {
                totalEntities++;
                var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                counts.merge(id == null ? "unknown" : id.toString(), 1, Integer::sum);
            }
        }

        out.append("ENTITY COUNTS\n");
        out.append("-------------\n");
        out.append(String.format("%d entities across %d loaded chunks.%n", totalEntities, loadedChunks));

        var rows = new ArrayList<>(counts.entrySet());
        rows.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        for (var i = 0; i < Math.min(rows.size(), 15); i++) {
            out.append(String.format("  %-44s %6d%n", rows.get(i).getKey(), rows.get(i).getValue()));
        }
    }

    /** The report split into chat-sized chunks, so it can be shown in game as well as written to the log. */
    public static List<String> chatLines(String report) {
        return List.of(report.split("\n"));
    }
}
