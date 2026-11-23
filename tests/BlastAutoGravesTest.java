package com.spd.blast;

/** Tomb opening prerequisites: registered bomb type and curse scroll. */
public final class BlastAutoGravesTest {
    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(true, BlastAutoGraves.canOpen(true, false, true), "arcane and scroll");
        check(true, BlastAutoGraves.canOpen(false, true, true), "shrapnel and scroll");
        check(false, BlastAutoGraves.canOpen(false, false, true), "scroll without bomb");
        check(false, BlastAutoGraves.canOpen(true, false, false), "bomb without scroll");
        System.out.println("BlastAutoGravesTest passed");
    }
}
