package com.spd.blast;

import java.util.function.IntPredicate;

/** Tracks unsafe retreats and temporary failed steps per automatic task. */
final class BlastAutoProgressGuard {
    enum Intent { OTHER, TRAVEL, PICKUP, CRYSTAL, INTERACT, ROAM, HARVEST, FARM, VAULT, RETREAT }

    private static final int HISTORY_SIZE = 9;
    private static final int MAX_BLOCKED_CELLS = 12;
    private static final int FAILED_MOVE_LIMIT = 3;
    private static final int TRANSIENT_BLOCK_TURNS = 12;

    private final int[] positions = new int[HISTORY_SIZE];
    private final Intent[] arrivals = new Intent[HISTORY_SIZE];
    private final int[] blockedCells = new int[MAX_BLOCKED_CELLS];
    private final Intent[] blockedIntents = new Intent[MAX_BLOCKED_CELLS];
    private final int[] blockTurns = new int[MAX_BLOCKED_CELLS];
    private int historySize;
    private int blockedCount;
    private int lastOrigin = -1;
    private int lastDestination = -1;
    private Intent lastIntent = Intent.OTHER;
    private Intent nextIntent = Intent.OTHER;
    private int failedCell = -1;
    private Intent failedIntent = Intent.OTHER;
    private int failedMoves;

    void reset() {
        clearHistory();
        blockedCount = 0;
        failedCell = -1;
        failedIntent = Intent.OTHER;
        failedMoves = 0;
    }

    void clearHistory() {
        historySize = 0;
        lastOrigin = -1;
        lastDestination = -1;
        nextIntent = Intent.OTHER;
        failedCell = -1;
        failedIntent = Intent.OTHER;
        failedMoves = 0;
    }

    /** Compare the actual location with the previously requested movement. */
    void observe(int position) {
        expireTransientBlocks();
        if (lastDestination < 0) return;
        if (position == lastDestination && position != lastOrigin) {
            failedCell = -1;
            failedIntent = Intent.OTHER;
            failedMoves = 0;
            if (historySize == 0) append(lastOrigin, Intent.OTHER);
            append(position, lastIntent);
            detectLoop();
        } else {
            if (position == lastOrigin && lastIntent != Intent.OTHER
                    && lastIntent != Intent.RETREAT) {
                if (failedCell != lastDestination || failedIntent != lastIntent) {
                    failedCell = lastDestination;
                    failedIntent = lastIntent;
                    failedMoves = 0;
                }
                if (++failedMoves >= FAILED_MOVE_LIMIT) {
                    block(lastDestination, lastIntent);
                    if (lastIntent == Intent.VAULT) {
                        BlastLog.debug("auto-vault blocked=movement-failed from="
                                + lastOrigin + " to=" + lastDestination);
                    }
                    failedMoves = 0;
                }
            } else {
                failedCell = -1;
                failedIntent = Intent.OTHER;
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

    /** Age only ordinary failures; hazard exclusions last until danger clears. */
    private void expireTransientBlocks() {
        for (int index = blockedCount - 1; index >= 0; index--) {
            if (blockedIntents[index] != Intent.RETREAT && --blockTurns[index] == 0) {
                removeBlocked(index);
            }
        }
    }

    /** Do not immediately re-explore the cell from which we escaped a hazard. */
    void hazardousRetreat(int cell) {
        block(cell, Intent.RETREAT);
    }

    /** Release a retreat exclusion when its cell is safe again. */
    void refreshHazards(int length, IntPredicate dangerous) {
        if (length <= 0 || dangerous == null) {
            throw new IllegalArgumentException("Invalid hazard navigation grid");
        }
        for (int index = blockedCount - 1; index >= 0; index--) {
            if (blockedIntents[index] != Intent.RETREAT) continue;
            int cell = blockedCells[index];
            if (cell >= length) {
                throw new IllegalStateException("Hazard exclusion outside current level");
            }
            if (!dangerous.test(cell)) removeBlocked(index);
        }
    }

    /** Apply hazards to all idle tasks; ordinary failed steps affect only their owner. */
    boolean[] safeFor(boolean[] safe, int position, Intent intent) {
        if (safe == null || position < 0 || position >= safe.length
                || intent == null || intent == Intent.OTHER || intent == Intent.RETREAT) {
            throw new IllegalArgumentException("Invalid automatic navigation request");
        }
        boolean[] filtered = safe;
        for (int index = 0; index < blockedCount; index++) {
            int cell = blockedCells[index];
            if (cell >= safe.length) {
                throw new IllegalStateException("Blocked cell outside current level");
            }
            if ((blockedIntents[index] != Intent.RETREAT && blockedIntents[index] != intent)
                    || cell == position || !safe[cell]) continue;
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
        for (int period = 2; period <= 4; period++) {
            if (historySize < 2 * period + 1) continue;
            boolean repeated = true;
            for (int step = 0; step < period + 1; step++) {
                if (positions[historySize - 1 - step]
                        != positions[historySize - 1 - period - step]) {
                    repeated = false;
                    break;
                }
            }
            if (!repeated) continue;
            // Block one repeated idle step without blocking combat or retreats.
            for (int index = historySize - period; index < historySize; index++) {
                Intent intent = arrivals[index];
                if (intent == Intent.OTHER || intent == Intent.RETREAT) continue;
                block(positions[index], intent);
                if (intent == Intent.VAULT) {
                    BlastLog.debug("auto-vault blocked=repeated-route cell=" + positions[index]);
                }
                historySize = 0;
                return;
            }
            break;
        }
    }

    private void block(int cell, Intent intent) {
        if (cell < 0 || intent == null || intent == Intent.OTHER) {
            throw new IllegalArgumentException("Invalid blocked step");
        }
        for (int index = 0; index < blockedCount; index++) {
            if (blockedCells[index] == cell && blockedIntents[index] == intent) {
                if (intent != Intent.RETREAT) blockTurns[index] = TRANSIENT_BLOCK_TURNS;
                return;
            }
        }
        if (blockedCount == MAX_BLOCKED_CELLS) {
            // Bound retained state even on levels with many independent obstacles.
            System.arraycopy(blockedCells, 1, blockedCells, 0, MAX_BLOCKED_CELLS - 1);
            System.arraycopy(blockedIntents, 1, blockedIntents, 0, MAX_BLOCKED_CELLS - 1);
            System.arraycopy(blockTurns, 1, blockTurns, 0, MAX_BLOCKED_CELLS - 1);
            blockedCount--;
        }
        blockedCells[blockedCount] = cell;
        blockedIntents[blockedCount] = intent;
        blockTurns[blockedCount] = intent == Intent.RETREAT ? 0 : TRANSIENT_BLOCK_TURNS;
        blockedCount++;
    }

    private void removeBlocked(int index) {
        int tail = blockedCount - index - 1;
        if (tail > 0) {
            System.arraycopy(blockedCells, index + 1, blockedCells, index, tail);
            System.arraycopy(blockedIntents, index + 1, blockedIntents, index, tail);
            System.arraycopy(blockTurns, index + 1, blockTurns, index, tail);
        }
        blockedCount--;
    }
}
