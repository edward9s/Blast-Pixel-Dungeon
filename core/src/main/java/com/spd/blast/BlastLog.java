package com.spd.blast;

/** Lightweight diagnostic gate for future Blast mechanics. */
public final class BlastLog {
    private BlastLog() {}

    public static void debug(String message) {
        if (BlastSettings.debugLog()) {
            System.out.println("[Blast] " + message);
        }
    }
}
