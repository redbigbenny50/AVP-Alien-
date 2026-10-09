package com.alien.compatibility.avp_predator;

import net.minecraft.world.entity.Entity;

/**
 * Client-side "is this entity in a capture net?" for the cocoon animation tracker.
 * <p>
 * ⚠⚠ THE avp_predator CLASS IS TOUCHED ONLY INSIDE {@link Bridge}, a nested class that is never loaded unless
 * {@link AVPPredator#MOD} reports the mod present. A direct reference here would resolve lazily on most JVMs and then
 * fail with NoClassDefFoundError the first time a xenomorph rendered on a client without avp_predator — the same shape
 * as the datagen trap documented in AlienEntityTypeTagProvider.
 */
public final class PredatorNetProxy {

    private PredatorNetProxy() {
        throw new UnsupportedOperationException();
    }

    public static boolean isNetted(Entity entity) {
        return AVPPredator.MOD.isLoaded() && Bridge.isNetted(entity);
    }

    private static final class Bridge {

        private static boolean isNetted(Entity entity) {
            return com.predator.client.net.ClientNetState.isNetted(entity.getId());
        }
    }
}
