package com.alien.compatibility.avp_predator;

import net.minecraft.world.entity.LivingEntity;

/**
 * SERVER-side view of avp_predator's net hold - the client-side {@link PredatorNetProxy} reads the synced overlay
 * state, which a server tick cannot use. Same shape as avp_human's {@code HumanPredatorNet}: every avp_predator type
 * lives in the nested class, which is never loaded unless the mod is present, so the outer class carries no reference
 * to it in bytecode.
 */
public final class PredatorNetServerProxy {

    public static boolean isNetted(LivingEntity entity) {
        return AVPPredator.MOD.isLoaded() && Bridge.isNetted(entity);
    }

    private static final class Bridge {

        private static boolean isNetted(LivingEntity entity) {
            return com.predator.common.gameplay.net.PredatorNet.isNetted(entity);
        }

        private Bridge() {
            throw new UnsupportedOperationException();
        }
    }

    private PredatorNetServerProxy() {
        throw new UnsupportedOperationException();
    }
}
