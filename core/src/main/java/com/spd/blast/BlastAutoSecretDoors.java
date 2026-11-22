package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;

/** Route to an investigated wall before roaming abandons a revealed dead end. */
final class BlastAutoSecretDoors {
    private BlastAutoSecretDoors() {}

    /** Select the closest safe stand beside a previously observed hidden door. */
    static int nearestStand(int origin, int width, int[] terrain,
                           boolean[] visited, boolean[] mapped, boolean[] visible,
                           boolean[] safe, int[] distances) {
        int length = terrain.length;
        if (width < 3 || length % width != 0 || origin < 0 || origin >= length
                || visited.length != length || mapped.length != length
                || visible.length != length || safe.length != length
                || distances.length != length) {
            throw new IllegalArgumentException("Invalid secret-door navigation map");
        }

        int originX = origin % width;
        int originY = origin / width;
        int height = length / width;
        int bestStand = -1;
        int shortest = Integer.MAX_VALUE;

        for (int cell = 0; cell < length; cell++) {
            if (terrain[cell] != Terrain.SECRET_DOOR
                    || !(visited[cell] || mapped[cell] || visible[cell])) continue;

            int x = cell % width;
            int y = cell / width;
            // The nearby-search step owns adjacent doors. In darkness, moving
            // among adjacent stands cannot reveal a door until sight returns.
            if (Math.abs(x - originX) <= 1 && Math.abs(y - originY) <= 1) continue;

            for (int dy = -1; dy <= 1; dy++) {
                int standY = y + dy;
                if (standY <= 0 || standY >= height - 1) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    int standX = x + dx;
                    if ((dx == 0 && dy == 0) || standX <= 0 || standX >= width - 1) continue;
                    int stand = standX + standY * width;
                    if (!safe[stand] || distances[stand] <= 0
                            || distances[stand] >= shortest) continue;
                    bestStand = stand;
                    shortest = distances[stand];
                }
            }
        }
        return bestStand;
    }
}
