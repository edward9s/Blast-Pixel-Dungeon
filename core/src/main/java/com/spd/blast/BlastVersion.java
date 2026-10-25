package com.spd.blast;

/** Blast version, independent of the upstream SPD release. */
public final class BlastVersion {
    public static final String VERSION = "0.1.0";

    private BlastVersion() {}

    public static String display() {
        return "BLAST v" + VERSION;
    }
}
