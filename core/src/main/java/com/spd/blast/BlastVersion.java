package com.spd.blast;

/** Blast version, independent of the upstream SPD release. */
public final class BlastVersion {
    // The maintainer changes this manually before publishing a new Blast version.
    public static final String VERSION = "0.1.1";

    private BlastVersion() {}

    public static String display(String spdVersion) {
        return format(spdVersion, VERSION, BlastUpdates.latestVersion());
    }

    // Match CoHero's SPD-first version display in both screens.
    static String format(String spdVersion, String installed, String latest) {
        return "SPD v" + spdVersion + " | BPD v" + installed
                + (latest == null ? "" : " | new " + latest);
    }
}
