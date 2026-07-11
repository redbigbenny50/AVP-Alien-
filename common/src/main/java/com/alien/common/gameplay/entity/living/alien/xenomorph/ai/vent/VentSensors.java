package com.alien.common.gameplay.entity.living.alien.xenomorph.ai.vent;

import com.alien.common.gameplay.entity.living.alien.xenomorph.VentBuilder;
import com.alien.common.gameplay.entity.living.alien.xenomorph.Xenomorph;
import com.alien.common.gameplay.entity.living.alien.xenomorph.ai.vent.action.CreateVentAction;
import com.alien.common.gameplay.hive.location.HiveLocationRegistry;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import net.minecraft.world.level.ChunkPos;

public class VentSensors {

    private static final int VENT_COOLDOWN_IN_TICKS = 15 * 20;

    private static final int VENT_TARGET_SEARCH_RETRY_COOLDOWN_IN_TICKS = 10 * 20;

    // Vent-network cap. Vents are the hive's duct shortcuts + defense/dispatch points, but creation was UNCAPPED: every
    // vent-builder bored a fresh vent each cooldown forever (a tester's hive reached 707), so builders never fell
    // through to hauling and eggs piled up on the queen. Cap the count to the claimed footprint. Tunable.
    private static final int VENT_CLAIMED_CHUNKS_PER_VENT = 3;

    private static final int MIN_VENTS_PER_LOCATION = 4;

    public static final Sensor.Mono<Xenomorph, Boolean> CAN_CREATE_VENT = Sensors.map(
        StateKey.sensed("can_create_vent"),
        xenomorph -> {
            if (!(xenomorph instanceof VentBuilder ventBuilder)) {
                return false;
            }

            if (xenomorph.getTarget() != null) {
                return false;
            }

            if (xenomorph.isInWater() || xenomorph.isUnderWater()) {
                return false;
            }

            var ticksSinceLastVent = xenomorph.tickCount - ventBuilder.getVentData().getLastVentCreationTick();

            if (ticksSinceLastVent < VENT_COOLDOWN_IN_TICKS) {
                return false;
            }

            // Hive: only build vents while standing inside some location's territory and that location isn't angry.
            var owningLocation = HiveLocationRegistry.INSTANCE.getByChunk(
                xenomorph.level().dimension(),
                new ChunkPos(xenomorph.blockPosition())
            );
            if (owningLocation == null || !owningLocation.isAlive()) {
                return false;
            }

            // Enough vents already for this footprint? Stop drilling so builders go do other work (hauling).
            int ventCap = Math.max(
                MIN_VENTS_PER_LOCATION,
                owningLocation.claimedChunks().size() / VENT_CLAIMED_CHUNKS_PER_VENT
            );
            if (owningLocation.ventManager().ventCount() >= ventCap) {
                return false;
            }
            var bossBar = owningLocation.bossBar();
            return bossBar == null || !bossBar.isAngry();
        }
    );

    public static final Sensor.Mono<Xenomorph, Boolean> HAS_VENT_TARGET = Sensors.map(
        StateKey.sensed("has_vent_target"),
        xenomorph -> {
            if (!(xenomorph instanceof VentBuilder ventBuilder)) {
                return false;
            }

            var ventData = ventBuilder.getVentData();

            if (!ventData.canRetryVentTargetSearch(xenomorph.tickCount, VENT_TARGET_SEARCH_RETRY_COOLDOWN_IN_TICKS)) {
                return false;
            }

            var hasVentTarget = CreateVentAction.hasVentTarget(xenomorph);

            if (!hasVentTarget) {
                ventData.recordVentTargetSearchFailure(xenomorph.tickCount);
            }

            return hasVentTarget;
        }
    );

    private VentSensors() {
        throw new UnsupportedOperationException();
    }
}
