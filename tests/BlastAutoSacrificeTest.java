package com.spd.blast;

/** Regression checks for sacrificial fire lure and marked kill decisions. */
public final class BlastAutoSacrificeTest {

    private static void check(boolean expected, boolean actual, String name) {
        if (expected != actual) {
            throw new AssertionError("Sacrificial lure: " + name);
        }
    }

    public static void main(String[] args) {
        // The SacrificialFire blob marks a 3x3 area around each burning tile.
        int width = 5;
        int[] fire = new int[width * width];
        fire[2 + 2 * width] = 1;
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < width; x++) {
                boolean expected = x >= 1 && x <= 3 && y >= 1 && y <= 3;
                check(expected, BlastAutoSacrifice.nearFlame(fire, width, x + y * width),
                        "fire aura at " + x + "," + y);
            }
        }

        // A cell on the right map edge must not wrap to the next row's left edge.
        fire = new int[width * width];
        fire[4] = 2;
        check(false, BlastAutoSacrifice.nearFlame(fire, width, 5), "no row wrap");
        check(true, BlastAutoSacrifice.nearFlame(fire, width, 8), "valid diagonal");
        check(true, BlastAutoSacrifice.nearFlame(fire, width, 4), "on fire");

        check(true, BlastAutoSacrifice.readyToSacrifice(true, true), "marked in aura");
        check(false, BlastAutoSacrifice.readyToSacrifice(true, false),
                "wait for marking before attacking");
        check(false, BlastAutoSacrifice.readyToSacrifice(false, true),
                "marked outside aura cannot contribute");
        check(false, BlastAutoSacrifice.readyToSacrifice(false, false), "ordinary enemy");
        check(true, BlastAutoSacrifice.shouldLure(true, false), "active pursuer");
        check(true, BlastAutoSacrifice.shouldLure(false, true), "enemy already in aura");
        check(false, BlastAutoSacrifice.shouldLure(false, false),
                "do not lure inactive distant enemy");

        try {
            BlastAutoSacrifice.nearFlame(new int[10], width - 1, 2);
            throw new AssertionError("Invalid fire dimensions must fail");
        } catch (IllegalArgumentException expected) {
            // A malformed geometry is a programming error.
        }
        System.out.println("BlastAutoSacrificeTest passed");
    }
}
