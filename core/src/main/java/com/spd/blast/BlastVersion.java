package com.spd.blast;

/** Blast version, independent of the upstream SPD release. */
public final class BlastVersion {
    // The maintainer changes this manually before publishing a new Blast version.
    public static final String VERSION = "0.1.1";

    private BlastVersion() {}

    public static String display() {
        return format(VERSION, BlastUpdates.latestVersion());
    }

    // Shared by the title screen and the in-game menu.
    static String format(String installed, String latest) {
        return "v" + installed + (latest == null ? "" : " | new " + latest);
    }
}
