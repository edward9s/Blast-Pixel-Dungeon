package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Fire;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;

/** Environment avoidance and bounded safe-water navigation. */
public final class BlastAutoEnvironmentTest {
    private static void equal(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
        }
    }

    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label);
        }
    }

    public static void main(String[] args) {
        Fire fire = new Fire();
        fire.cur = new int[]{0, 3, 0, 0};
        fire.volume = 3;
        ToxicGas poison = new ToxicGas();
        poison.cur = new int[]{0, 0, 6, 0};
        poison.volume = 6;

        Blob[] hazards = new Blob[]{fire, poison, null};
        check(false, BlastAutoEnvironment.hazardousAt(0, hazards), "clear tile");
        check(true, BlastAutoEnvironment.hazardousAt(1, hazards), "burning tile");
        check(true, BlastAutoEnvironment.hazardousAt(2, hazards), "poison cloud");
        check(false, BlastAutoEnvironment.hazardousAt(3, hazards), "empty tile");
        check(false, BlastAutoEnvironment.hazardousAt(1, new Blob[2]), "no active blobs");

        boolean[] safe = {true, true, true, true, true, true, true, true};
        boolean[] water = {false, false, false, false, true, true, false, false};
        int[] distance = {0, 1, 2, 3, 4, 5, 6, Integer.MAX_VALUE};
        equal(4, BlastAutoEnvironment.nearbyWater(0, water, safe, distance),
                "water at four safe steps");
        safe[4] = false;
        equal(-1, BlastAutoEnvironment.nearbyWater(0, water, safe, distance),
                "dangerous water rejected, five-step water too far");
        safe[4] = true;
        distance[4] = Integer.MAX_VALUE;
        equal(-1, BlastAutoEnvironment.nearbyWater(0, water, safe, distance),
                "unreachable water rejected");
        distance[4] = 4;
        water[0] = true;
        equal(-1, BlastAutoEnvironment.nearbyWater(0, water, safe, distance),
                "already standing in water");
        water[0] = false;

        water[2] = true;
        equal(2, BlastAutoEnvironment.nearbyWater(0, water, safe, distance),
                "nearest safe water wins");
        safe[2] = false;
        equal(4, BlastAutoEnvironment.nearbyWater(0, water, safe, distance),
                "avoid hazardous nearer water");

        try {
            BlastAutoEnvironment.nearbyWater(0, new boolean[1], safe, distance);
            throw new AssertionError("Invalid navigation array lengths must fail");
        } catch (IllegalArgumentException expected) {
            // Correct: never silently accept inconsistent path data.
        }
        System.out.println("BlastAutoEnvironmentTest passed");
    }
}
