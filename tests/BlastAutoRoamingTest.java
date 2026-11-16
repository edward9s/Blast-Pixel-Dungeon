package com.spd.blast;

import java.util.Arrays;

/** Regression checks for frontier selection, failure memory, and idle roaming. */
public final class BlastAutoRoamingTest {
    private static void equal(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected " + expected + " got " + actual);
        }
    }

    private static final class Map {
        final int width = 7;
        final boolean[] safe = new boolean[49];
        final int[] distances = new int[49];
        final boolean[] visited = new boolean[49];
        final boolean[] mapped = new boolean[49];
        final boolean[] visible = new boolean[49];
        final boolean[] solid = new boolean[49];

        Map() {
            Arrays.fill(distances, Integer.MAX_VALUE);
            Arrays.fill(visited, true);
            visited[15] = false;
            visited[33] = false;
            safe[16] = true;
            safe[32] = true;
            safe[24] = true;
            distances[16] = 2;
            distances[32] = 4;
            distances[24] = 0;
        }

        int select(BlastAutoRoaming roaming, int position) {
            return roaming.chooseDestination(position, width, safe, distances,
                    visited, mapped, visible, solid);
        }
    }

    public static void main(String[] args) {
        Map map = new Map();
        BlastAutoRoaming roaming = new BlastAutoRoaming();
        equal(16, map.select(roaming, 24), "nearest frontier");

        map.distances[32] = 1;
        equal(16, map.select(roaming, 24), "retain current target");
        map.distances[32] = 4;

        map.distances[16] = 0;
        map.distances[32] = 3;
        equal(32, map.select(roaming, 16), "reject failed corner");

        map.distances[16] = 3;
        map.distances[32] = 0;
        equal(-1, map.select(roaming, 32), "exhausted frontiers");
        equal(-1, map.select(roaming, 32), "remain idle without new exploration");

        map.solid[17] = true;
        equal(16, map.select(roaming, 32), "retest after topology change");

        roaming.reset();
        Arrays.fill(map.visited, true);
        equal(-1, map.select(roaming, 24), "complete floor stays idle");

        map.visited[15] = false;
        map.mapped[15] = true;
        equal(-1, map.select(roaming, 24), "mapped cells are known");
        map.mapped[15] = false;
        map.visible[15] = true;
        equal(-1, map.select(roaming, 24), "visible cells are known");

        roaming.reset();
        map.visible[15] = false;
        map.safe[16] = false;
        equal(-1, map.select(roaming, 24), "unsafe frontier excluded");
        map.safe[16] = true;
        map.distances[16] = Integer.MAX_VALUE;
        equal(-1, map.select(roaming, 24), "unreachable frontier excluded");

        System.out.println("BlastAutoRoamingTest passed");
    }
}
