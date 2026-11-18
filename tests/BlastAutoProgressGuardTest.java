package com.spd.blast;

import java.util.Arrays;

/** Regression checks for hazards and movement loops across automatic tasks. */
public final class BlastAutoProgressGuardTest {
    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    private static void move(BlastAutoProgressGuard guard, int from, int to,
                             BlastAutoProgressGuard.Intent intent) {
        guard.intent(intent);
        guard.record(from, to);
        guard.observe(to);
    }

    public static void main(String[] args) {
        boolean[] safe = new boolean[49];
        Arrays.fill(safe, true);
        BlastAutoProgressGuard guard = new BlastAutoProgressGuard();
        guard.hazardousRetreat(24);
        boolean[] idle = guard.idleSafe(safe, 23);
        check(false, idle[24], "gas doorway excluded from idle goals");
        check(true, safe[24], "combat path remains intact");
        guard.refreshHazards(7, 49, cell -> cell == 25);
        check(false, guard.idleSafe(safe, 23)[24], "adjacent gas keeps doorway blocked");
        guard.refreshHazards(7, 49, cell -> false);
        check(true, guard.idleSafe(safe, 23)[24], "cleared gas permits revisiting door");

        BlastAutoProgressGuard cycle = new BlastAutoProgressGuard();
        move(cycle, 23, 24, BlastAutoProgressGuard.Intent.ROAM);
        move(cycle, 24, 23, BlastAutoProgressGuard.Intent.RETREAT);
        move(cycle, 23, 24, BlastAutoProgressGuard.Intent.ROAM);
        move(cycle, 24, 23, BlastAutoProgressGuard.Intent.RETREAT);
        check(false, cycle.idleSafe(safe, 23)[24], "cross-task two-cell oscillation excluded");

        BlastAutoProgressGuard triple = new BlastAutoProgressGuard();
        for (int i = 0; i < 2; i++) {
            move(triple, 23, 24, BlastAutoProgressGuard.Intent.HARVEST);
            move(triple, 24, 30, BlastAutoProgressGuard.Intent.FARM);
            move(triple, 30, 23, BlastAutoProgressGuard.Intent.ROAM);
        }
        check(false, triple.idleSafe(safe, 23)[24], "three-cell cycle excluded");

        BlastAutoProgressGuard combat = new BlastAutoProgressGuard();
        for (int i = 0; i < 3; i++) {
            move(combat, 23, 24, BlastAutoProgressGuard.Intent.OTHER);
            move(combat, 24, 23, BlastAutoProgressGuard.Intent.OTHER);
        }
        check(true, combat.idleSafe(safe, 23)[24], "combat routes are not blacklisted");

        BlastAutoProgressGuard failure = new BlastAutoProgressGuard();
        for (int i = 0; i < 3; i++) {
            failure.intent(BlastAutoProgressGuard.Intent.ROAM);
            failure.record(23, 24);
            failure.observe(23);
        }
        check(false, failure.idleSafe(safe, 23)[24], "three blocked move attempts excluded");

        BlastAutoProgressGuard useful = new BlastAutoProgressGuard();
        move(useful, 23, 24, BlastAutoProgressGuard.Intent.ROAM);
        useful.record(24, -1);
        move(useful, 24, 23, BlastAutoProgressGuard.Intent.ROAM);
        move(useful, 23, 24, BlastAutoProgressGuard.Intent.ROAM);
        check(true, useful.idleSafe(safe, 23)[24], "intervening action resets cycle");

        guard.reset();
        check(true, guard.idleSafe(safe, 23)[24], "reset releases blocked locations");
        System.out.println("BlastAutoProgressGuardTest passed");
    }
}
