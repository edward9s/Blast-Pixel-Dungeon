package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;

/** Headless checks for barricade explosion targeting and hero safety. */
public final class BlastAutoBarricadeTest {
    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(true, BlastAutoBarricade.demolitionTarget(Terrain.BARRICADE, false, false),
                "ordinary barricade remains an auto-demolition objective");
        check(true, BlastAutoBarricade.demolitionTarget(Terrain.BOOKSHELF, true, false),
                "runestone-room bookshelf can be demolished");
        check(false, BlastAutoBarricade.demolitionTarget(Terrain.BOOKSHELF, false, false),
                "ordinary library bookshelves must be preserved");
        check(false, BlastAutoBarricade.demolitionTarget(Terrain.WALL, true, false),
                "stone wall is not a wooden demolition objective");
        check(false, BlastAutoBarricade.demolitionTarget(Terrain.DOOR, true, false),
                "normal doors remain outside the barricade task");
        check(true, BlastAutoBarricade.demolitionTarget(Terrain.EMPTY, false, true),
                "web on ordinary floor is a demolition objective");
        check(false, BlastAutoBarricade.demolitionTarget(Terrain.EMPTY, false, false),
                "bare floor is not a demolition objective");
        check(true, BlastAutoBarricade.demolitionTarget(Terrain.BOOKSHELF, false, true),
                "a web overlay is removable even on otherwise protected terrain");
        check(true, (Terrain.flags[Terrain.BOOKSHELF] & Terrain.FLAMABLE) != 0,
                "native ordinary bombs can destroy bookshelf terrain");

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
