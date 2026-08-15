package com.alien.compatibility.blib_engine;

import com.alien.compatibility.blib_engine.client.inspector.AlienInspectorSections;
import com.blib.api.BLibAPI;
import com.blib.api.common.mod.v1.BLibMod;

public class BLibEngine {

    public static final BLibMod MOD = BLibAPI.createMod("blib_engine");

    private BLibEngine() {}

    public static void registerInspectorSections() {
        if (MOD.isLoaded()) {
            AlienInspectorSections.register();
        }
    }
}
