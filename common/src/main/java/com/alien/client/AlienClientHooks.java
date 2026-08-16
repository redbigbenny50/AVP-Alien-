package com.alien.client;

/**
 * Common-side bridge to client-only behaviour.
 * <p>
 * Common code must not import a screen directly - loading that class on a dedicated server would drag in client
 * rendering. Each loader registers the real opener during client setup, and the server simply calls a no-op.
 * <p>
 * Mirrors the same bridge in the dropship_transport module, which is where the field manual came from.
 */
public final class AlienClientHooks {

    private static Runnable fieldManualScreenOpener = () -> {};

    private AlienClientHooks() {
        throw new UnsupportedOperationException();
    }

    public static void registerFieldManualScreenOpener(Runnable opener) {
        fieldManualScreenOpener = opener == null ? () -> {} : opener;
    }

    public static void openFieldManualScreen() {
        fieldManualScreenOpener.run();
    }
}
