package com.spd.blast;

/** Headless checks for Regrowth targeting and bomb blast footprint decisions. */
public final class BlastAutoBombAITest {
    private static void regrowth(boolean expected, boolean rooted, boolean effectAtTarget) {
        if (BlastAutoBombAI.regrowthReady(rooted, effectAtTarget) != expected) {
            throw new AssertionError("Unexpected Regrowth targeting policy");
        }
    }

    public static void main(String[] args) {
        // Neither rooted nor under Regrowth: the target is eligible immediately.
        regrowth(true, false, false);
        regrowth(false, true, false);
        regrowth(false, false, true);
        regrowth(false, true, true);

        // A bomb can hit an enemy within the blast even if the throw landed
        // short of the enemy because of a wall or another obstruction.
        boolean[] blast = {false, true, true, false};
        if (!BlastAutoBombAI.blastReachesTarget(blast, 2)
                || BlastAutoBombAI.blastReachesTarget(blast, 3)) {
            throw new AssertionError("Bomb targeting must use the blast footprint");
        }
        try {
            BlastAutoBombAI.blastReachesTarget(blast, 4);
            throw new AssertionError("Invalid target should fail fast");
        } catch (IllegalArgumentException expected) {
            // The blast map must cover the queried cell.
        }
        System.out.println("BlastAutoBombAITest passed");
    }
}
