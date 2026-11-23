package com.spd.blast;

import com.watabou.utils.Rect;

/** Haste must outlast the round trip and treasure interactions. */
public final class BlastAutoSentryRoomTest {
    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(true, BlastAutoSentryRoom.enoughHaste(20f, 6, 6, 1f / 3f),
                "fast round trip");
        check(false, BlastAutoSentryRoom.enoughHaste(3f, 6, 6, 1f / 3f),
                "insufficient duration");
        check(false, BlastAutoSentryRoom.enoughHaste(20f, Integer.MAX_VALUE, 6, 1f / 3f),
                "unreachable chest");
        check(false, BlastAutoSentryRoom.enoughHaste(20f, 6, Integer.MAX_VALUE, 1f / 3f),
                "unreachable exit");
        check(false, BlastAutoSentryRoom.enoughHaste(20f, 6, 6, 0f), "invalid speed");
        // A previously entered sentry room must not pull roaming back to its door
        // after the chest has been opened or emptied.
        Rect room = new Rect(1, 1, 5, 5);
        boolean[] visited = new boolean[49];
        visited[1 + 3 * 7] = true; // Room boundary is not its interior.
        check(false, BlastAutoSentryRoom.visitedInterior(visited, 7, room),
                "entrance alone does not mark the treasure room explored");
        visited[2 + 3 * 7] = true;
        check(true, BlastAutoSentryRoom.visitedInterior(visited, 7, room),
                "entered room must not be approached as undiscovered");
        visited[2 + 3 * 7] = false;
        check(false, BlastAutoSentryRoom.visitedInterior(visited, 7, room),
                "unvisited room remains eligible for initial discovery");

        System.out.println("BlastAutoSentryRoomTest passed");
    }
}
