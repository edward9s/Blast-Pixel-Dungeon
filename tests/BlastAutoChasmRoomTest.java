package com.spd.blast;

/** Floating chasm-vault planning must reserve the return journey. */
public final class BlastAutoChasmRoomTest {
    private static void check(boolean actual, String label) {
        if (!actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(BlastAutoChasmRoom.enoughFlight(20f, 5, 1, 8, true, 1f),
                "single final potion can reach the chest and return");
        check(!BlastAutoChasmRoom.enoughFlight(20f, 8, 1, 9, true, 1f),
                "last potion must not strand the hero in a chasm");
        check(BlastAutoChasmRoom.enoughFlight(20f, 8, 1, 9, false, 1f),
                "a spare potion allows landing and resuming from a dry island");
        check(!BlastAutoChasmRoom.enoughFlight(20f, Integer.MAX_VALUE,
                1, 0, false, 1f), "unreachable target is rejected");
        check(!BlastAutoChasmRoom.enoughFlight(20f, 4, 1,
                Integer.MAX_VALUE, true, 1f), "last potion needs a return path");
        check(!BlastAutoChasmRoom.enoughFlight(12f, 4, 1, 5, true, 1f),
                "the remaining floating duration is enforced");
        check(!BlastAutoChasmRoom.enoughFlight(20f, 6, 1, 7, true, 1.5f),
                "slower actors need more floating time");
        System.out.println("BlastAutoChasmRoomTest passed");
    }
}
