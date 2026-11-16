package com.spd.blast;

import java.util.function.IntPredicate;

/** Harvest travel must not reverse when a crop disappears behind a wall. */
public final class BlastAutoHarvestTest {
    private static void equal(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    public static void main(String[] args) {
        BlastAutoHarvest harvest = new BlastAutoHarvest();
        boolean[] safe = {true, true, true, true, true};
        boolean[] visible = {true, true, true, false, false};
        boolean[] crops = {false, false, true, false, false};
        int[] distances = {0, 1, 2, 3, 4};
        IntPredicate isCrop = cell -> crops[cell];

        equal(2, harvest.chooseDestination(0, safe, distances, visible, false, isCrop),
                "choose visible harvest target");
        visible[2] = false;
        distances = new int[]{1, 0, 1, 2, 3};
        equal(2, harvest.chooseDestination(1, safe, distances, visible, false, isCrop),
                "retain target after losing sight around corner");
        visible[2] = true;
        distances = new int[]{2, 1, 0, 1, 2};
        equal(2, harvest.chooseDestination(2, safe, distances, visible, true, isCrop),
                "trample target after arrival");

        crops[2] = false;
        equal(-1, harvest.chooseDestination(2, safe, distances, visible, false, isCrop),
                "discard harvested target");

        harvest.reset();
        crops[2] = true;
        distances = new int[]{0, 1, 2, 3, 4};
        equal(2, harvest.chooseDestination(0, safe, distances, visible, false, isCrop),
                "reselect crop after reset");
        safe[2] = false;
        equal(-1, harvest.chooseDestination(0, safe, distances, visible, false, isCrop),
                "discard unsafe target");

        System.out.println("BlastAutoHarvestTest passed");
    }
}
