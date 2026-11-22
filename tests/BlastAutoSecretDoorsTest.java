package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import java.util.Arrays;

/** Regression: a discovered short corridor still leads to a hidden-door search. */
public final class BlastAutoSecretDoorsTest {
    private static final int WIDTH = 7;
    private static final int LENGTH = WIDTH * WIDTH;
    private static final int START = 22;
    private static final int END = 24;
    private static final int DOOR = 25;

    private static void equal(int expected, int actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + ": expected " + expected + ", actual " + actual);
        }
    }

    private static final class Map {
        final int[] terrain = new int[LENGTH];
        final boolean[] visited = new boolean[LENGTH];
        final boolean[] mapped = new boolean[LENGTH];
        final boolean[] visible = new boolean[LENGTH];
        final boolean[] safe = new boolean[LENGTH];
        final int[] distances = new int[LENGTH];

        Map() {
            Arrays.fill(distances, Integer.MAX_VALUE);
            terrain[DOOR] = Terrain.SECRET_DOOR;
            visited[DOOR] = true;
            safe[START] = true;
            safe[23] = true;
            safe[END] = true;
            distances[START] = 0;
            distances[23] = 1;
            distances[END] = 2;
        }

        int stand(int position) {
            return BlastAutoSecretDoors.nearestStand(position, WIDTH,
                    terrain, visited, mapped, visible, safe, distances);
        }
    }

    public static void main(String[] args) {
        Map corridor = new Map();
        equal(END, corridor.stand(START), "returns to an already revealed corridor");
        equal(-1, corridor.stand(END), "adjacent search handles the final tile");

        Map unknown = new Map();
        unknown.visited[DOOR] = false;
        equal(-1, unknown.stand(START), "unobserved hidden doors stay unknown");

        Map mapped = new Map();
        mapped.visited[DOOR] = false;
        mapped.mapped[DOOR] = true;
        equal(END, mapped.stand(START), "mapped hidden wall remains a target");

        Map visible = new Map();
        visible.visited[DOOR] = false;
        visible.visible[DOOR] = true;
        equal(END, visible.stand(START), "visible hidden wall is a target");

        Map discovered = new Map();
        discovered.terrain[DOOR] = Terrain.DOOR;
        equal(-1, discovered.stand(START), "revealed doors are no longer targets");

        Map blocked = new Map();
        blocked.safe[END] = false;
        equal(-1, blocked.stand(START), "blocked approaches are never selected");

        Map unreachable = new Map();
        unreachable.distances[END] = Integer.MAX_VALUE;
        equal(-1, unreachable.stand(START), "unreachable approaches are ignored");

        System.out.println("BlastAutoSecretDoorsTest passed");
    }
}
