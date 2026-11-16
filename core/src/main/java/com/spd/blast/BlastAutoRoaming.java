package com.spd.blast;

import com.watabou.utils.Random;
import java.util.Arrays;

/** Explore reachable unknown boundaries without revisiting unproductive corners. */
final class BlastAutoRoaming {
    private static final int MAX_STALLED_ROAMS = 3;

    private int destination = -1;
    private boolean[] failedFrontiers;
    private boolean[] solidSnapshot;
    private int progressGoal = -1;
    private int bestGoalDistance = Integer.MAX_VALUE;
    private int stalledRoams;

    void reset() {
        destination = -1;
        failedFrontiers = null;
        solidSnapshot = null;
        resetProgress();
    }

    private void resetProgress() {
        progressGoal = -1;
        bestGoalDistance = Integer.MAX_VALUE;
        stalledRoams = 0;
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
            resetProgress();
        }

        if (destination == position) {
            // Unrevealed neighbours after arrival mean this corner is unproductive.
            if (hasUnknownNeighbor(destination, width, visited, mapped, visible)) {
                failedFrontiers[destination] = true;
            }
            destination = -1;
            resetProgress();
        }

        // Detect repeated visits to the same region without getting closer.
        // This also bounds loops caused by another automatic action interrupting roaming.
        if (destination >= 0 && safe[destination]
                && distances[destination] != Integer.MAX_VALUE) {
            int remaining = distances[destination];
            if (progressGoal != destination || remaining < bestGoalDistance) {
                progressGoal = destination;
                bestGoalDistance = remaining;
                stalledRoams = 0;
            } else if (++stalledRoams >= MAX_STALLED_ROAMS) {
                failedFrontiers[destination] = true;
                destination = -1;
            }
            if (destination >= 0) return destination;
        }
        destination = -1;
        resetProgress();

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
        if (destination >= 0) {
            progressGoal = destination;
            bestGoalDistance = shortest;
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
