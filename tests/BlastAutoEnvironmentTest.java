package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Fire;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicalFireRoom;
import com.watabou.utils.Point;
import com.watabou.utils.Rect;

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

        check(true, BlastAutoEnvironment.gasOnlyAt(2, hazards),
                "gas alone is traversable during a gas escape");
        check(false, BlastAutoEnvironment.gasOnlyAt(1, hazards),
                "fire is never a gas escape corridor");
        check(false, BlastAutoEnvironment.gasOnlyAt(0, hazards),
                "clear tiles need no gas exception");
        fire.cur[2] = 1;
        check(false, BlastAutoEnvironment.gasOnlyAt(2, hazards),
                "overlapping fire cannot be crossed as gas");
        fire.cur[2] = 0;

        boolean[] clean = {true, false, false, true, true};
        int[] gasRoute = {0, 1, 2, 3, Integer.MAX_VALUE};
        equal(3, BlastAutoEnvironment.nearestSafeExit(0, clean, gasRoute),
                "escape through two surrounding gas tiles to reachable clean ground");
        gasRoute[3] = Integer.MAX_VALUE;
        equal(-1, BlastAutoEnvironment.nearestSafeExit(0, clean, gasRoute),
                "do not select clean ground with no escape route");
        gasRoute[3] = 3;
        gasRoute[4] = 2;
        equal(4, BlastAutoEnvironment.nearestSafeExit(0, clean, gasRoute),
                "choose nearest reachable clean exit");
        try {
            BlastAutoEnvironment.nearestSafeExit(0, clean, new int[1]);
            throw new AssertionError("Mismatched escape map lengths must fail");
        } catch (IllegalArgumentException expected) {
            // Invalid route data must not lead to movement.
        }

        check(true, BlastAutoEnvironment.HAZARD_TYPES.contains(
                MagicalFireRoom.EternalFire.class), "eternal fire registered as a hazard");
        MagicalFireRoom.EternalFire eternalFire = new MagicalFireRoom.EternalFire();
        eternalFire.cur = new int[]{0, 2, 0, 0};
        eternalFire.volume = 2;
        check(true, BlastAutoEnvironment.hazardousAt(1,
                new Blob[]{eternalFire, null}), "eternal fire blocks movement");

        int[] magicalFlames = new int[49];
        magicalFlames[3 + 3 * 7] = 1;
        boolean[] fireBarriers = new boolean[magicalFlames.length];
        BlastAutoEnvironment.markEternalFire(magicalFlames, 7, fireBarriers);
        check(true, fireBarriers[3 + 3 * 7], "fire wall blocked");
        check(true, fireBarriers[2 + 3 * 7], "fire wall west burn zone blocked");
        check(true, fireBarriers[4 + 3 * 7], "fire wall east burn zone blocked");
        check(true, fireBarriers[3 + 2 * 7], "fire wall north burn zone blocked");
        check(true, fireBarriers[3 + 4 * 7], "fire wall south burn zone blocked");
        check(false, fireBarriers[2 + 2 * 7], "fire wall diagonal stays safe");

        BlastAutoEnvironment.markFireRoomPerimeter(
                7, new Rect(1, 1, 5, 5), new Point(1, 3), fireBarriers);
        check(true, fireBarriers[1 + 2 * 7], "cannot phase through fire room wall");
        check(true, fireBarriers[5 + 3 * 7], "cannot bypass fire from behind");
        check(true, fireBarriers[3 + 1 * 7], "top fire room wall blocked");
        check(false, fireBarriers[1 + 3 * 7], "fire room entrance remains open");
        check(false, fireBarriers[2 + 2 * 7], "ordinary interior remains traversable");
        check(false, fireBarriers[0], "outside fire room unaffected");

        int[] terrain = new int[49];
        terrain[2 + 2 * 7] = Terrain.EMPTY_SP;
        terrain[3 + 3 * 7] = Terrain.EMPTY_SP;
        terrain[4 + 2 * 7] = Terrain.EMPTY;
        terrain[0] = Terrain.EMPTY_SP;
        terrain[1 + 3 * 7] = Terrain.EMPTY_SP;
        boolean[] sentryFloors = new boolean[terrain.length];
        BlastAutoEnvironment.markSentryFloors(
                terrain, 7, new Rect(1, 1, 5, 5), sentryFloors);
        check(true, sentryFloors[2 + 2 * 7], "sentry floor blocked");
        check(true, sentryFloors[3 + 3 * 7], "second sentry floor blocked");
        check(false, sentryFloors[4 + 2 * 7], "ordinary room floor is safe");
        check(false, sentryFloors[0], "special floor outside sentry room is safe");
        check(false, sentryFloors[1 + 3 * 7], "room perimeter stays safe");
        // Flooded sentry floors must return to normal auto-exploration.
        terrain[2 + 2 * 7] = Terrain.WATER;
        sentryFloors = new boolean[terrain.length];
        BlastAutoEnvironment.markSentryFloors(
                terrain, 7, new Rect(1, 1, 5, 5), sentryFloors);
        check(false, sentryFloors[2 + 2 * 7], "flooded floor is traversable");
        check(true, sentryFloors[3 + 3 * 7], "unflooded red floor stays blocked");

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
