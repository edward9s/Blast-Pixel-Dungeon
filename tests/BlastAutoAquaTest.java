package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;

/** Exercises Aqua Brew footprint scoring using the native SPD distance algorithm. */
public final class BlastAutoAquaTest {
    private static void equal(int actual, int expected, String label) {
        if (actual != expected)
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        PathFinder.setMapSize(9, 9);
        boolean[] passable = new boolean[81];
        for (int y = 1; y < 8; y++) {
            for (int x = 1; x < 8; x++) passable[x + y * 9] = true;
        }

        // The optimal landing is ordinary floor between three traps.
        java.util.ArrayList<Integer> traps = new java.util.ArrayList<>();
        traps.add(3 + 3 * 9);
        traps.add(5 + 3 * 9);
        traps.add(3 + 5 * 9);
        PathFinder.buildDistanceMap(4 + 4 * 9, passable, 2);
        equal(BlastAutoAqua.countTargets(PathFinder.distance, traps, true), 3,
                "ordinary-floor landing certainly removes all three traps");
        PathFinder.buildDistanceMap(3 + 3 * 9, passable, 2);
        equal(BlastAutoAqua.countTargets(PathFinder.distance, traps, true), 1,
                "landing directly on a trap is weaker");
        equal(BlastAutoAqua.countTargets(PathFinder.distance, traps, false), 2,
                "distance-two splash is probabilistic");

        // A wall blocks a radius-two splash even when the target is geometrically close.
        for (int y = 1; y < 8; y++) passable[4 + y * 9] = false;
        PathFinder.buildDistanceMap(3 + 4 * 9, passable, 2);
        equal(PathFinder.distance[5 + 4 * 9], Integer.MAX_VALUE,
                "spray cannot cross solid wall");

        check(BlastAutoAqua.waterableTerrain(Terrain.TRAP), "visible trap waterable");
        check(BlastAutoAqua.waterableTerrain(Terrain.SECRET_TRAP), "hidden trap waterable");
        check(BlastAutoAqua.waterableTerrain(Terrain.EMPTY), "empty floor waterable");
        check(BlastAutoAqua.waterableTerrain(Terrain.WATER), "existing water is valid landing");
        check(!BlastAutoAqua.waterableTerrain(Terrain.CHASM), "cannot flood chasm");
        check(!BlastAutoAqua.waterableTerrain(Terrain.PEDESTAL), "preserve treasure pedestal");
        check(!BlastAutoAqua.waterableTerrain(Terrain.WALL), "cannot flood a wall");

        BlastAutoAqua.Score base = new BlastAutoAqua.Score(2, false, 0, 3, 1, 2);
        check(BlastAutoAqua.better(new BlastAutoAqua.Score(3, false, 0, 0, 0, 8), base),
                "certain removals outrank travel cost");
        check(BlastAutoAqua.better(new BlastAutoAqua.Score(2, true, 0, 0, 0, 8), base),
                "opening chest route breaks certain-removal tie");
        check(BlastAutoAqua.better(new BlastAutoAqua.Score(2, false, 1, 0, 0, 8), base),
                "approaching a chest breaks tie");
        check(BlastAutoAqua.better(new BlastAutoAqua.Score(2, false, 0, 4, 0, 8), base),
                "new reachability breaks tie");
        check(BlastAutoAqua.better(new BlastAutoAqua.Score(2, false, 0, 3, 2, 8), base),
                "probabilistic extra removals break tie");
        check(BlastAutoAqua.better(new BlastAutoAqua.Score(2, false, 0, 3, 1, 1), base),
                "shorter approach breaks remaining tie");
        check(!BlastAutoAqua.better(new BlastAutoAqua.Score(1, true, 5, 99, 99, 1), base),
                "a merely likely outcome must not outrank guaranteed removals");
        System.out.println("BlastAutoAquaTest passed");
    }
}
