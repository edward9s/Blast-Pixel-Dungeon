package com.spd.blast;

import com.watabou.utils.Random;
import java.util.Arrays;

/** Explore unknown boundaries first, then roam across known reachable terrain. */
final class BlastAutoRoaming {
    private static final int MIN_DISTANT_STEPS = 3;

    private int destination = -1;
    private boolean frontierDestination;
    private boolean[] exhaustedFrontiers;
    private boolean[] solidSnapshot;

    void reset() {
        clearDestination();
        exhaustedFrontiers = null;
        solidSnapshot = null;
    }

    void clearDestination() {
        destination = -1;
        frontierDestination = false;
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

        // Only a topology change reopens frontiers that failed to reveal new ground.
        // Temporary movement failures are owned by BlastAutoProgressGuard.
        if (exhaustedFrontiers == null) {
            exhaustedFrontiers = new boolean[length];
            solidSnapshot = solid.clone();
        } else if (!Arrays.equals(solidSnapshot, solid)) {
            Arrays.fill(exhaustedFrontiers, false);
            System.arraycopy(solid, 0, solidSnapshot, 0, length);
        }

        if (destination == position) {
            if (frontierDestination
                    && hasUnknownNeighbor(destination, width, visited, mapped, visible)) {
                exhaustedFrontiers[destination] = true;
            }
            clearDestination();
        }

        // Keep a reachable goal. The common navigation guard handles stalled moves.
        if (destination >= 0 && safe[destination]
                && distances[destination] != Integer.MAX_VALUE) return destination;
        clearDestination();

        int shortest = Integer.MAX_VALUE;
        int ties = 0;
        for (int cell = 0; cell < length; cell++) {
            int steps = distances[cell];
            if (cell == position || !safe[cell] || exhaustedFrontiers[cell]
                    || steps <= 0 || steps == Integer.MAX_VALUE || steps > shortest
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
        if (destination >= 0) {
            frontierDestination = true;
            return destination;
        }

        // When exploration is complete, pick a distant known safe destination.
        // Avoid one-cell shuttling where there is no meaningful route.
        int distant = -1;
        int distantCount = 0;
        int other = -1;
        int otherCount = 0;
        for (int cell = 0; cell < length; cell++) {
            int steps = distances[cell];
            if (cell == position || !safe[cell] || exhaustedFrontiers[cell]
                    || steps <= 1 || steps == Integer.MAX_VALUE) continue;
            if (steps >= MIN_DISTANT_STEPS) {
                if (Random.Int(++distantCount) == 0) distant = cell;
            } else if (Random.Int(++otherCount) == 0) {
                other = cell;
            }
        }
        destination = distant >= 0 ? distant : other;
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
