package com.alien.compatibility.blib_engine.client.inspector;

import com.blib.engine.api.client.v1.inspector.InspectorSectionRegistry;

/**
 * Single registration entry point for AVP-Alien's contributions to the BLib engine workspace's Inspector panel. Called
 * from {@code AlienClient#runInitialization} during client init. Sections are routed by the public
 * {@link InspectorSectionRegistry}; each section's typeId filter ensures it only renders for its own AVP faction kind.
 */
public final class AlienInspectorSections {

    private AlienInspectorSections() {}

    public static void register() {
        InspectorSectionRegistry.register(new LocationFactionInspectorSection());
        InspectorSectionRegistry.register(new LineageFactionInspectorSection());
        InspectorSectionRegistry.register(new VariantFactionInspectorSection());
        InspectorSectionRegistry.register(new HiveConfigInspectorSection());
    }
}
