package com.spd.blast;

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
        System.out.println("BlastAutoSentryRoomTest passed");
    }
}
