package com.alien.common.gameplay.entity.living.alien.xenomorph.ai;

import com.blib.mod.common.registry.init.BLibGameRules;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * A short-lived memo for the EXPENSIVE PART of a sensor - an entity query or a block scan - never for a whole sensor.
 * <p>
 * ⭐ WHY NOT JUST {@code Sensors.every} EVERYWHERE (Oct 1). {@code Sensors.every} holds a sensor's whole VALUE, and some
 * values must be read live: the host sensor's "already carrying one" check, for instance, is exactly what stops the
 * documented capture/deliver flicker, and holding the sensor would bring that flicker back for the length of the
 * window. Memoising only the scan keeps those guards live while still skipping the costly query on most ticks.
 * </p>
 * <p>
 * ⚠ PATHFINDING IS UNTOUCHED: nothing here feeds a navigator. Actions that move or capture keep resolving their own
 * target live (e.g. {@code CaptureHostAction} calls {@code HostSensors.findCaptureTarget} directly), so a memoised
 * value can only delay a DECISION by at most its window - never point a path at a stale spot.
 * </p>
 * <p>
 * ⚠ Follows BLib's {@code blibGoapSensingOptimizations} game rule, the same switch as {@code Sensors.every}: with it
 * off every call computes fresh, which is the pre-Oct-1 behaviour and the A/B baseline for Spark.
 * </p>
 * <p>
 * Keys are held weakly (entities or hive locations), so dead mobs and removed hives drop out on their own.
 * </p>
 */
public final class SensorMemo<K, V> {

    private record Entry<V>(
        long tick,
        V value
    ) {}

    private final Map<K, Entry<V>> entries = Collections.synchronizedMap(new WeakHashMap<>());

    private final int ticks;

    public SensorMemo(int ticks) {
        this.ticks = Math.max(1, ticks);
    }

    /** The value computed within the last {@code ticks} game ticks for {@code key}, or a fresh one. */
    public V get(K key, Level level, Supplier<V> compute) {
        if (!level.getGameRules().getBoolean(BLibGameRules.GOAP_SENSING_OPTIMIZATIONS)) {
            return compute.get();
        }
        var now = level.getGameTime();
        var entry = entries.get(key);
        if (entry != null && now - entry.tick() >= 0 && now - entry.tick() < ticks) {
            return entry.value();
        }
        var value = compute.get();
        entries.put(key, new Entry<>(now, value));
        return value;
    }
}
