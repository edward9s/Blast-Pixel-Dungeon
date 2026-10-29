package com.spd.blast;

/** Blast version, independent of the upstream SPD release. */
public final class BlastVersion {
    // The maintainer changes this manually before publishing a new Blast version.
    public static final String VERSION = "0.1.1";

    private BlastVersion() {}

    public static String display() {
        String latest = BlastUpdates.latestVersion();
        return "BLAST v" + VERSION + (latest == null ? "" : " (new " + latest + ")");
    }
}
