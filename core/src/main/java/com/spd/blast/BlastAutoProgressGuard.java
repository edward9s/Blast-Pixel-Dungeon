package com.spd.blast;

import java.util.function.IntPredicate;

/** Remembers failed idle routes across independent auto-play tasks. */
final class BlastAutoProgressGuard {
    enum Intent { OTHER, PICKUP, INTERACT, ROAM, HARVEST, FARM, RETREAT }

    private static final int HISTORY_SIZE = 7;
    private static final int MAX_BLOCKED_CELLS = 12;
    private static final int FAILED_MOVE_LIMIT = 3;

    private final int[] positions = new int[HISTORY_SIZE];
    private final Intent[] arrivals = new Intent[HISTORY_SIZE];
    private final int[] blockedCells = new int[MAX_BLOCKED_CELLS];
    private final boolean[] hazardBlocks = new boolean[MAX_BLOCKED_CELLS];
    private int historySize;
    private int blockedCount;
    private int lastOrigin = -1;
    private int lastDestination = -1;
    private Intent lastIntent = Intent.OTHER;
    private Intent nextIntent = Intent.OTHER;
    private int failedCell = -1;
    private int failedMoves;

    void reset() {
        clearHistory();
        blockedCount = 0;
        failedCell = -1;
        failedMoves = 0;
    }

    void clearHistory() {
        historySize = 0;
        lastOrigin = -1;
        lastDestination = -1;
        nextIntent = Intent.OTHER;
        failedCell = -1;
        failedMoves = 0;
    }

    /** Compare the actual location with the previously requested movement. */
    void observe(int position) {
        if (lastDestination < 0) return;
        if (position == lastDestination && position != lastOrigin) {
            failedCell = -1;
            failedMoves = 0;
            if (historySize == 0) append(lastOrigin, Intent.OTHER);
            append(position, lastIntent);
            detectLoop();
        } else {
            if (position == lastOrigin && lastIntent != Intent.OTHER
                    && lastIntent != Intent.RETREAT) {
                if (failedCell != lastDestination) {
                    failedCell = lastDestination;
                    failedMoves = 0;
                }
                if (++failedMoves >= FAILED_MOVE_LIMIT) {
                    block(lastDestination, false);
                    failedMoves = 0;
                }
            } else {
                failedCell = -1;
                failedMoves = 0;
            }
            historySize = 0;
        }
        lastOrigin = -1;
        lastDestination = -1;
    }

    void intent(Intent intent) {
        if (intent == null) throw new IllegalArgumentException("Null automatic movement intent");
        nextIntent = intent;
    }

    /** A nonmovement action breaks a move-only loop; it may have made progress. */
    void record(int origin, int destination) {
        if (destination < 0 || destination == origin) {
            clearHistory();
            return;
        }
        lastOrigin = origin;
        lastDestination = destination;
        lastIntent = nextIntent;
        nextIntent = Intent.OTHER;
    }

    /** Do not immediately re-explore the cell from which we escaped a hazard. */
    void hazardousRetreat(int cell) {
        block(cell, true);
    }

    /** Release hazard exclusions only after the local danger has disappeared. */
    void refreshHazards(int width, int length, IntPredicate dangerous) {
        if (width <= 0 || length <= 0 || length % width != 0 || dangerous == null) {
            throw new IllegalArgumentException("Invalid hazard navigation grid");
        }
        for (int index = blockedCount - 1; index >= 0; index--) {
            if (!hazardBlocks[index]) continue;
            int cell = blockedCells[index];
            int x = cell % width;
            int y = cell / width;
            boolean danger = false;
            for (int dy = -1; dy <= 1 && !danger; dy++) {
                int yy = y + dy;
                if (yy < 0 || yy >= length / width) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    int xx = x + dx;
                    if (xx >= 0 && xx < width && dangerous.test(yy * width + xx)) {
                        danger = true;
                        break;
                    }
                }
            }
            if (!danger) removeBlocked(index);
        }
    }

    /** Tactical routing and hazard escapes still use the original safe map. */
    boolean[] idleSafe(boolean[] safe, int position) {
        if (safe == null || position < 0 || position >= safe.length) {
            throw new IllegalArgumentException("Invalid idle navigation map");
        }
        boolean[] filtered = safe;
        for (int index = 0; index < blockedCount; index++) {
            int cell = blockedCells[index];
            if (cell >= safe.length) {
                throw new IllegalStateException("Blocked cell outside current level");
            }
            if (cell == position || !safe[cell]) continue;
            if (filtered == safe) filtered = safe.clone();
            filtered[cell] = false;
        }
        return filtered;
    }

    private void append(int position, Intent intent) {
        if (historySize == HISTORY_SIZE) {
            System.arraycopy(positions, 1, positions, 0, HISTORY_SIZE - 1);
            System.arraycopy(arrivals, 1, arrivals, 0, HISTORY_SIZE - 1);
            historySize--;
        }
        positions[historySize] = position;
        arrivals[historySize] = intent;
        historySize++;
    }

    private void detectLoop() {
        for (int period = 2; period <= 3; period++) {
            if (historySize < 2 * period + 1) continue;
            boolean repeated = true;
            boolean idle = false;
            boolean mixedIntents = false;
            Intent priorIntent = null;
            for (int step = 0; step < period + 1; step++) {
                if (positions[historySize - 1 - step]
                        != positions[historySize - 1 - period - step]) {
                    repeated = false;
                    break;
                }
            }
            if (!repeated) continue;
            for (int step = historySize - 2 * period; step < historySize; step++) {
                Intent current = arrivals[step];
                if (current != Intent.OTHER && current != Intent.RETREAT) {
                    idle = true;
                }
                if (priorIntent != null && priorIntent != current) mixedIntents = true;
                priorIntent = current;
            }
            if (idle && mixedIntents) {
                block(positions[historySize - period], false);
                historySize = 0;
            }
            break;
        }
    }

    private void block(int cell, boolean hazard) {
        if (cell < 0) throw new IllegalArgumentException("Invalid blocked cell");
        for (int index = 0; index < blockedCount; index++) {
            if (blockedCells[index] == cell) {
                hazardBlocks[index] |= hazard;
                return;
            }
        }
        if (blockedCount == MAX_BLOCKED_CELLS) {
            // Bound retained state even on levels with many independent obstacles.
            System.arraycopy(blockedCells, 1, blockedCells, 0, MAX_BLOCKED_CELLS - 1);
            System.arraycopy(hazardBlocks, 1, hazardBlocks, 0, MAX_BLOCKED_CELLS - 1);
            blockedCount--;
        }
        blockedCells[blockedCount] = cell;
        hazardBlocks[blockedCount] = hazard;
        blockedCount++;
    }

    private void removeBlocked(int index) {
        int tail = blockedCount - index - 1;
        if (tail > 0) {
            System.arraycopy(blockedCells, index + 1, blockedCells, index, tail);
            System.arraycopy(hazardBlocks, index + 1, hazardBlocks, index, tail);
        }
        blockedCount--;
    }
}
