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
        Map stalledMap = new Map();
        BlastAutoRoaming stalled = new BlastAutoRoaming();
        equal(16, stalledMap.select(stalled, 24), "initial goal for stall test");
        equal(16, stalledMap.select(stalled, 24), "first stationary attempt");
        equal(16, stalledMap.select(stalled, 24), "second stationary attempt");
        equal(32, stalledMap.select(stalled, 24), "third stalled attempt skips goal");
        stalledMap.safe[8] = true;
        stalledMap.distances[8] = 5;
        equal(16, stalledMap.select(stalled, 24),
                "changed navigability releases stale failed frontier");

        Map oscillatingMap = new Map();
        oscillatingMap.safe[23] = true;
        BlastAutoRoaming oscillating = new BlastAutoRoaming();
        equal(16, oscillatingMap.select(oscillating, 24), "initial oscillating goal");
        oscillatingMap.distances[16] = 1;
        equal(16, oscillatingMap.select(oscillating, 23), "first approach");
        oscillatingMap.distances[16] = 2;
        equal(16, oscillatingMap.select(oscillating, 24), "first reversal");
        oscillatingMap.distances[16] = 1;
        equal(16, oscillatingMap.select(oscillating, 23), "repeat approach");
        oscillatingMap.distances[16] = 2;
        equal(32, oscillatingMap.select(oscillating, 24), "oscillating goal abandoned");

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

        Map complete = new Map();
        Arrays.fill(complete.visited, true);
        BlastAutoRoaming ordinary = new BlastAutoRoaming();
        equal(32, complete.select(ordinary, 24),
                "completed exploration still roams to a distant cell");
        ordinary.clearDestination();
        complete.distances[16] = 3;
        complete.distances[32] = 2;
        equal(16, complete.select(ordinary, 24),
                "longer roaming trip preferred to short trip");
        ordinary.clearDestination();
        complete.distances[16] = 1;
        equal(32, complete.select(ordinary, 24),
                "two-step wandering preferred over one-step bounce");
        ordinary.clearDestination();
        complete.distances[32] = Integer.MAX_VALUE;
        equal(-1, complete.select(ordinary, 24),
                "avoid adjacent-only oscillation");

        Map mapped = new Map();
        mapped.mapped[15] = true;
        mapped.visited[33] = true;
        equal(32, mapped.select(new BlastAutoRoaming(), 24),
                "mapped cells do not count as frontiers");
        mapped.mapped[15] = false;
        mapped.visible[15] = true;
        equal(32, mapped.select(new BlastAutoRoaming(), 24),
                "visible cells do not count as frontiers");

        Map unreachable = new Map();
        unreachable.safe[16] = false;
        unreachable.distances[32] = Integer.MAX_VALUE;
        equal(-1, unreachable.select(new BlastAutoRoaming(), 24),
                "unreachable frontier or fallback excluded");

        Map handoff = new Map();
        BlastAutoRoaming interrupted = new BlastAutoRoaming();
        equal(16, handoff.select(interrupted, 24), "initial roam goal");
        interrupted.clearDestination();
        handoff.distances[32] = 1;
        equal(32, handoff.select(interrupted, 24),
                "farming cancels stale roaming destination");

        System.out.println("BlastAutoRoamingTest passed");
    }
}
