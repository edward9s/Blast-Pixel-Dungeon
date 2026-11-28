package com.spd.blast;

import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;
import java.util.Arrays;

/** Explore unknown boundaries first, then roam across known reachable terrain. */
final class BlastAutoRoaming {
    private static final int MIN_DISTANT_STEPS = 3;
    private static final int MAX_ROCK_STEPS = 3;

    private int destination = -1;
    private boolean frontierDestination;
    private boolean[] exhaustedFrontiers;
    private boolean[] solidSnapshot;
    private final PathFinder.Path crossing = new PathFinder.Path();
    private int crossingPosition = -1;

    void reset() {
        clearDestination();
        exhaustedFrontiers = null;
        solidSnapshot = null;
        crossing.clear();
        crossingPosition = -1;
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

        arrive(position, width, visited, mapped, visible);

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

    int miningStep(int position, int width, boolean[] safe, int[] distances,
                   boolean[] visited, boolean[] mapped, boolean[] visible,
                   boolean[] passable, boolean[] solid) {
        boolean[] floor = new boolean[safe.length];
        for (int cell = 0; cell < safe.length; cell++) {
            floor[cell] = safe[cell] && passable[cell];
        }
        if (!passable[position]) {
            arrive(position, width, visited, mapped, visible);
            if (!crossing.isEmpty() && crossing.getFirst() == position) {
                crossing.removeFirst();
                crossingPosition = position;
            }
            if (crossingPosition == position && !crossing.isEmpty()
                    && floor[crossing.getLast()] && crossing.stream().allMatch(cell -> safe[cell])) {
                return crossing.getFirst();
            }
            // Recover after loading in rock or after the planned landing becomes unsafe.
            crossing.clear();
            clearDestination();
            int landing = -1;
            int shortest = Integer.MAX_VALUE;
            for (int cell = 0; cell < floor.length; cell++) {
                if (floor[cell] && distances[cell] < shortest) {
                    shortest = distances[cell];
                    landing = cell;
                }
            }
            return landing < 0 ? -1 : PathFinder.getStep(position, landing, safe);
        }

        crossing.clear();
        boolean[] goals = floor.clone();
        for (int cell = 0; cell < safe.length; cell++) {
            if (safe[cell] && !goals[cell]) goals[cell] = adjacentFloor(cell, floor) >= 0;
        }
        int goal = chooseDestination(position, width, goals, distances, visited, mapped, visible, solid);
        if (goal < 0) return -1;
        PathFinder.Path route = PathFinder.find(position, goal, safe);
        if (route != null && boundedCrossing(route, floor)) {
            for (int cell : route) {
                crossing.add(cell);
                if (floor[cell]) break;
            }
            crossingPosition = position;
            return crossing.getFirst();
        }

        // A shortest route through thick rock cannot claim the roaming turn.
        clearDestination();
        PathFinder.buildDistanceMap(position, floor);
        goal = chooseDestination(position, width, floor, PathFinder.distance,
                visited, mapped, visible, solid);
        return goal < 0 ? -1 : PathFinder.getStep(position, goal, floor);
    }

    private static int adjacentFloor(int cell, boolean[] floor) {
        // Roaming only admits interior cells, so the neighbors are inside the array.
        for (int offset : PathFinder.NEIGHBOURS8) {
            if (floor[cell + offset]) return cell + offset;
        }
        return -1;
    }

    private static boolean boundedCrossing(PathFinder.Path route, boolean[] floor) {
        int steps = 0;
        for (int cell : route) {
            if (floor[cell]) steps = 0;
            else if (++steps > MAX_ROCK_STEPS) return false;
        }
        if (steps > 0) {
            int landing = adjacentFloor(route.getLast(), floor);
            if (landing < 0) return false;
            route.add(landing);
        }
        return true;
    }

    private void arrive(int position, int width, boolean[] visited, boolean[] mapped, boolean[] visible) {
        if (destination != position) return;
        if (frontierDestination && hasUnknownNeighbor(position, width, visited, mapped, visible)) {
            exhaustedFrontiers[position] = true;
        }
        clearDestination();
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
