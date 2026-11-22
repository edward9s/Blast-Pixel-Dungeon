package com.spd.blast;

/** Headless checks for barricade explosion targeting and hero safety. */
public final class BlastAutoBarricadeTest {
    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        boolean[] footprint = {false, true, true, false};
        check(true, BlastAutoBarricade.reachesSafely(footprint, 2, 0, false),
                "distant throw outside blast");
        check(false, BlastAutoBarricade.reachesSafely(footprint, 2, 1, false),
                "unprotected hero inside blast");
        check(true, BlastAutoBarricade.reachesSafely(footprint, 2, 1, true),
                "protected hero inside blast");
        check(false, BlastAutoBarricade.reachesSafely(footprint, 3, 0, true),
                "blast must reach barricade");
        try {
            BlastAutoBarricade.reachesSafely(footprint, 4, 0, true);
            throw new AssertionError("Out-of-map barricade should fail fast");
        } catch (IllegalArgumentException expected) {
            // Invalid cells must not be passed to a bomb.
        }
        System.out.println("BlastAutoBarricadeTest passed");
    }
}
