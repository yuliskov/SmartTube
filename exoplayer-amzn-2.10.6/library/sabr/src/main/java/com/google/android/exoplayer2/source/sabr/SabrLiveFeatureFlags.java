package com.google.android.exoplayer2.source.sabr;

/** Explicit live-SABR gates. Production routing remains hard-disabled. */
public final class SabrLiveFeatureFlags {
    private static volatile boolean debugHarnessEnabled;

    private SabrLiveFeatureFlags() {}

    public static boolean enableSabrLiveHarness() {
        return debugHarnessEnabled;
    }

    public static boolean enableSabrLiveProduction() {
        return false;
    }

    /** Called only by the debug-source-set harness, which is absent from release APKs. */
    public static void setSabrLiveHarnessEnabledForDebug(boolean enabled) {
        debugHarnessEnabled = enabled;
    }
}
