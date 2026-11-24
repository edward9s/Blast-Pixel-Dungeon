package com.spd.blast;

import java.util.Arrays;

/** Task-scoped movement failures and shared hazard retreat regression checks. */
public final class BlastAutoProgressGuardTest {
    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    private static boolean open(BlastAutoProgressGuard guard, boolean[] safe,
                                BlastAutoProgressGuard.Intent intent, int cell) {
        return guard.safeFor(safe, 23, intent)[cell];
    }

    private static void move(BlastAutoProgressGuard guard, int from, int to,
                             BlastAutoProgressGuard.Intent intent) {
        guard.intent(intent);
        guard.record(from, to);
        guard.observe(to);
    }

    private static void fail(BlastAutoProgressGuard guard,
                             BlastAutoProgressGuard.Intent intent) {
        guard.intent(intent);
        guard.record(23, 24);
        guard.observe(23);
    }

    public static void main(String[] args) {
        boolean[] safe = new boolean[49];
        Arrays.fill(safe, true);
        BlastAutoProgressGuard.Intent roam = BlastAutoProgressGuard.Intent.ROAM;
        BlastAutoProgressGuard.Intent pickup = BlastAutoProgressGuard.Intent.PICKUP;
        BlastAutoProgressGuard.Intent harvest = BlastAutoProgressGuard.Intent.HARVEST;

        BlastAutoProgressGuard hazard = new BlastAutoProgressGuard();
        hazard.hazardousRetreat(24);
        check(false, open(hazard, safe, roam, 24), "roaming avoids a hazardous doorway");
        check(false, open(hazard, safe, pickup, 24), "pickup avoids the same doorway");
        check(true, safe[24], "tactical safe map remains unchanged");
        hazard.refreshHazards(49, cell -> cell == 24);
        for (int i = 0; i < 12; i++) hazard.observe(23);
        check(false, open(hazard, safe, pickup, 24), "active hazard does not expire");
        hazard.refreshHazards(49, cell -> cell == 25);
        check(true, open(hazard, safe, roam, 24),
                "gas next to a now-safe tile cannot keep it blocked");
        check(true, open(hazard, safe, pickup, 24),
                "loot on a clear tile is accessible despite nearby gas");

        BlastAutoProgressGuard cycle = new BlastAutoProgressGuard();
        move(cycle, 23, 24, roam);
        move(cycle, 24, 23, BlastAutoProgressGuard.Intent.RETREAT);
        move(cycle, 23, 24, roam);
        move(cycle, 24, 23, BlastAutoProgressGuard.Intent.RETREAT);
        check(false, open(cycle, safe, roam, 24), "cross-task cycle blocks roaming");
        check(true, open(cycle, safe, pickup, 24), "cycle cannot block pickup");
        for (int i = 0; i < 12; i++) cycle.observe(23);
        check(true, open(cycle, safe, roam, 24), "cycle block expires");

        BlastAutoProgressGuard sameTask = new BlastAutoProgressGuard();
        for (int i = 0; i < 2; i++) {
            move(sameTask, 23, 24, pickup);
            move(sameTask, 24, 23, pickup);
        }
        check(false, open(sameTask, safe, pickup, 24), "same-task cycle blocks pickup");
        check(true, open(sameTask, safe, roam, 24), "same-task cycle leaves roaming open");

        BlastAutoProgressGuard triple = new BlastAutoProgressGuard();
        for (int i = 0; i < 2; i++) {
            move(triple, 23, 24, harvest);
            move(triple, 24, 30, BlastAutoProgressGuard.Intent.FARM);
            move(triple, 30, 23, roam);
        }
        check(false, open(triple, safe, harvest, 24), "three-cell cycle blocks its entering task");
        check(true, open(triple, safe, pickup, 24), "other tasks remain navigable");
        check(true, open(triple, safe, roam, 24), "roaming unaffected by harvest failure");

        BlastAutoProgressGuard failure = new BlastAutoProgressGuard();
        fail(failure, roam);
        fail(failure, roam);
        fail(failure, roam);
        check(false, open(failure, safe, roam, 24), "three failed roaming moves blocked");
        check(true, open(failure, safe, pickup, 24), "failed roaming cannot hide reachable loot");
        for (int i = 0; i < 12; i++) failure.observe(23);
        check(true, open(failure, safe, roam, 24), "failed roaming moves expire");

        BlastAutoProgressGuard pickupFailure = new BlastAutoProgressGuard();
        fail(pickupFailure, pickup);
        fail(pickupFailure, pickup);
        fail(pickupFailure, pickup);
        check(false, open(pickupFailure, safe, pickup, 24), "pickup retries bounded");
        check(true, open(pickupFailure, safe, roam, 24), "pickup failures cannot block roaming");

        BlastAutoProgressGuard mixedFailure = new BlastAutoProgressGuard();
        fail(mixedFailure, roam);
        fail(mixedFailure, roam);
        fail(mixedFailure, pickup);
        check(true, open(mixedFailure, safe, roam, 24), "different tasks do not share failure counts");
        check(true, open(mixedFailure, safe, pickup, 24), "different task starts a new count");

        // A temporary pickup exclusion and a hazardous exclusion can coexist on one cell.
        pickupFailure.hazardousRetreat(24);
        pickupFailure.refreshHazards(49, cell -> false);
        check(false, open(pickupFailure, safe, pickup, 24), "clearing hazard keeps pickup cooldown");
        check(true, open(pickupFailure, safe, roam, 24), "clearing hazard restores other tasks");

        BlastAutoProgressGuard useful = new BlastAutoProgressGuard();
        move(useful, 23, 24, roam);
        useful.record(24, -1);
        move(useful, 24, 23, roam);
        move(useful, 23, 24, roam);
        check(true, open(useful, safe, roam, 24), "intervening action breaks movement cycle");

        BlastAutoProgressGuard interruptedFailure = new BlastAutoProgressGuard();
        fail(interruptedFailure, roam);
        fail(interruptedFailure, roam);
        interruptedFailure.record(23, -1);
        fail(interruptedFailure, roam);
        check(true, open(interruptedFailure, safe, roam, 24),
                "nonmovement action resets consecutive failure count");

        BlastAutoProgressGuard combat = new BlastAutoProgressGuard();
        for (int i = 0; i < 3; i++) {
            move(combat, 23, 24, BlastAutoProgressGuard.Intent.OTHER);
            move(combat, 24, 23, BlastAutoProgressGuard.Intent.OTHER);
        }
        check(true, open(combat, safe, roam, 24), "combat movement never blocks idle tasks");

        hazard.reset();
        check(true, open(hazard, safe, pickup, 24), "reset clears hazard history");
        System.out.println("BlastAutoProgressGuardTest passed");
    }
}
