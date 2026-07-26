package com.alien.common.gameplay.hive.structure.carve;

import com.blib.api.common.data_sync.v1.DataAccessor;

/**
 * A caste that can be rostered onto hive construction: digging, placing, and the repair work that keeps a finished
 * piece intact.
 * <p>
 * Drones and runners both qualify. The roster used to be typed directly to {@code Drone}, which quietly made drones the
 * only caste that could build - runners were always intended for this work and simply had no dig animation yet, so
 * nothing could show them doing it. The interface exists so the crew system can talk about "a worker" without caring
 * which caste turned up.
 * <p>
 * The single thing a worker must expose is its synced gait mode: {@code 0} idle, {@code 1} digger, {@code 2} placer.
 * Animation dispatch is client-side only, so the crew sets this on the server and each caste's animator folds it into
 * its own locomotion selection.
 */
public interface CarveWorker {

    /** Synced crew gait: 0 = not on a crew, 1 = digging, 2 = placing. */
    DataAccessor<Integer> carveDigMode();
}
