package com.spd.blast;

import com.watabou.utils.Random;
import java.util.Arrays;

/** Explore reachable unknown boundaries without revisiting unproductive corners. */
final class BlastAutoRoaming {
    private int destination = -1;
    private boolean[] failedFrontiers;
    private boolean[] solidSnapshot;

    void reset() {
        destination = -1;
        failedFrontiers = null;
        solidSnapshot = null;
    }

    int chooseDestination(int position, int width, boolean[] safe, int[] distances,
                          boolean[] visited, boolean[] mapped, boolean[] visible,
                          boolean[] solid) {
        int length = safe.length;
        if (width < 3 || length % width != 0 || distances.length != length
                || visited.length != length || mapped.length != length
                || visible.length != length || solid.length != length
                || position < 0 || position >= length) {
            throw new IllegalArgumentException("Invalid auto-roaming map");
        }

        // Re-evaluate failed corners after solid obstacles change.
        if (failedFrontiers == null) {
            failedFrontiers = new boolean[length];
            solidSnapshot = solid.clone();
        } else if (!Arrays.equals(solidSnapshot, solid)) {
            Arrays.fill(failedFrontiers, false);
            System.arraycopy(solid, 0, solidSnapshot, 0, length);
        }

        if (destination == position) {
            // Unrevealed neighbours after arrival mean this corner is unproductive.
            if (hasUnknownNeighbor(destination, width, visited, mapped, visible)) {
                failedFrontiers[destination] = true;
            }
            destination = -1;
        }

        // Preserve the current goal until reached or no longer reachable.
        if (destination >= 0 && safe[destination]
                && distances[destination] != Integer.MAX_VALUE) {
            return destination;
        }
        destination = -1;

        int shortest = Integer.MAX_VALUE;
        int ties = 0;
        for (int cell = 0; cell < length; cell++) {
            int steps = distances[cell];
            if (cell == position || !safe[cell] || failedFrontiers[cell]
                    || steps <= 0 || steps > shortest
                    || !hasUnknownNeighbor(cell, width, visited, mapped, visible)) {
                continue;
            }
            if (steps < shortest) {
                shortest = steps;
                destination = cell;
                ties = 1;
            } else if (Random.Int(++ties) == 0) {
                destination = cell;
            }
        }
        // No viable frontier: let normal idle turns handle new activity.
        return destination;
    }

    private static boolean hasUnknownNeighbor(int cell, int width, boolean[] visited,
                                               boolean[] mapped, boolean[] visible) {
        int height = visited.length / width;
        int x = cell % width;
        int y = cell / width;
        for (int dy = -1; dy <= 1; dy++) {
            int ny = y + dy;
            if (ny <= 0 || ny >= height - 1) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                if (nx <= 0 || nx >= width - 1) continue;
                int adjacent = nx + ny * width;
                if (!visited[adjacent] && !mapped[adjacent] && !visible[adjacent]) {
                    return true;
                }
            }
        }
        return false;
    }
}
