package com.spd.blast;

import com.badlogic.gdx.utils.JsonReader;

/** Run with the patched SPD core on the classpath; no network or graphics needed. */
public final class BlastUpdatesTest {
    private static void equals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        equals("BPD v" + BlastVersion.VERSION + " | SPD v4.0.2", BlastVersion.display("4.0.2"));
        equals("BPD v0.1.0 | SPD v4.0.2", BlastVersion.format("4.0.2", "0.1.0", null));
        equals("BPD v0.1.0 | SPD v4.0.2 | new 0.1.1",
                BlastVersion.format("4.0.2", "0.1.0", "0.1.1"));
        equals(1, BlastUpdates.compareVersions("0.1.10", "0.1.9"));
        equals(0, BlastUpdates.compareVersions("0.2", "0.2.0"));
        equals(-1, BlastUpdates.compareVersions("0.1.9", "0.1.10"));

        String releases = "["
                + "{\"tag_name\":\"Blast-v0.1.2-SPD-v4.0.2\",\"draft\":true,\"prerelease\":false},"
                + "{\"tag_name\":\"Blast-v0.1.8-SPD-v4.0.2\",\"draft\":false,\"prerelease\":true},"
                + "{\"tag_name\":\"build-test\",\"draft\":false,\"prerelease\":true},"
                + "{\"tag_name\":\"Blast-v0.1.9-SPD-v4.0.2\",\"draft\":false,\"prerelease\":false},"
                + "{\"tag_name\":\"Blast-v0.1.10-SPD-v4.0.1\",\"draft\":false,\"prerelease\":false},"
                + "{\"tag_name\":\"SPD-v4.0.2-m0.3.10\",\"draft\":false,\"prerelease\":false}"
                + "]";
        equals("0.1.10", BlastUpdates.newestReleaseVersion(new JsonReader().parse(releases)));

        String sameVersion = "["
                + "{\"tag_name\":\"Blast-v0.1.0-SPD-v4.0.3\",\"draft\":false,\"prerelease\":false}"
                + "]";
        equals("0.1.0", BlastUpdates.newestReleaseVersion(new JsonReader().parse(sameVersion)));

        try {
            BlastUpdates.newestReleaseVersion(new JsonReader().parse("{}"));
            throw new AssertionError("Non-array GitHub response must fail");
        } catch (IllegalArgumentException expected) {
            // Verified.
        }
        System.out.println("BlastUpdatesTest passed");
    }
}
