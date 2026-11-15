package com.spd.blast;

/** Headless checks for the deterministic, shared-distance-map Regrowth escape. */
public final class BlastAutoBombAITest {
    private static void equals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        boolean[] safe = {true, true, true, true, true};
        int[] distances = {0, 1, 2, 3, 4};

        // Choose the closest reachable cell outside the explosion footprint.
        equals(3, BlastAutoBombAI.nearestEscape(
                safe, distances, new boolean[]{true, true, true, false, false}));

        // A blocked nearest exit must not be selected.
        equals(4, BlastAutoBombAI.nearestEscape(
                new boolean[]{true, true, true, false, true}, distances,
                new boolean[]{true, true, true, false, false}));

        // No escape destination means the bomb must not be placed.
        equals(-1, BlastAutoBombAI.nearestEscape(
                safe, distances, new boolean[]{true, true, true, true, true}));

        // Unreachable cells are never destinations.
        equals(-1, BlastAutoBombAI.nearestEscape(
                new boolean[]{true, true, true}, new int[]{0, 1, Integer.MAX_VALUE},
                new boolean[]{true, true, false}));

        try {
            BlastAutoBombAI.nearestEscape(new boolean[2], new int[3], new boolean[2]);
            throw new AssertionError("Invalid escape maps must fail");
        } catch (IllegalArgumentException expected) {
            // Verified.
        }
        System.out.println("BlastAutoBombAITest passed");
    }
}
