package com.spd.blast;

import java.util.function.IntPredicate;

/** Retain a visible harvest target while navigating around vision-blocking corners. */
final class BlastAutoHarvest {
    private int destination = -1;

    void reset() {
        destination = -1;
    }

    int chooseDestination(int position, boolean[] safe, int[] distances, boolean[] visible,
                          boolean canTrampleHere, IntPredicate harvestable) {
        int length = safe.length;
        if (position < 0 || position >= length || distances.length != length
                || visible.length != length || harvestable == null) {
            throw new IllegalArgumentException("Invalid auto-harvest map");
        }

        // Keep the last observed crop when it goes behind a wall. Validate its
        // actual presence once it comes into view again.
        if (destination >= 0 && safe[destination]
                && distances[destination] != Integer.MAX_VALUE
                && (!visible[destination] || harvestable.test(destination))
                && (position != destination || canTrampleHere)) {
            return destination;
        }
        destination = -1;

        int nearest = Integer.MAX_VALUE;
        for (int cell = 0; cell < length; cell++) {
            if (!visible[cell] || !safe[cell]
                    || distances[cell] == Integer.MAX_VALUE
                    || (cell == position && !canTrampleHere)
                    || !harvestable.test(cell)) continue;
            if (distances[cell] < nearest) {
                nearest = distances[cell];
                destination = cell;
            }
        }
        return destination;
    }
}
