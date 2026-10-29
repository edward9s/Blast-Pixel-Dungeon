package com.spd.blast;

/** Blast version, independent of the upstream SPD release. */
public final class BlastVersion {
    // The maintainer changes this manually before publishing a new Blast version.
    public static final String VERSION = "0.1.1";

    private BlastVersion() {}

    public static String display(String spdVersion) {
        return format(spdVersion, VERSION, BlastUpdates.latestVersion());
    }

    // Show Blast first, followed by the upstream SPD version, in both screens.
    static String format(String spdVersion, String installed, String latest) {
        return "BPD v" + installed + " | SPD v" + spdVersion
                + (latest == null ? "" : " | new " + latest);
    }
}
