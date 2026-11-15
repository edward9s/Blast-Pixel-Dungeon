package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ArcaneBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.RegrowthBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ShrapnelBomb;

/** Headless checks for non-destructive bomb selection and local Regrowth targeting. */
public final class BlastAutoBombAITest {
    private static void check(boolean expected, Bomb bomb) {
        if (BlastAutoBombAI.itemPreserving(bomb) != expected) {
            throw new AssertionError("Unexpected auto-bomb eligibility: " + bomb.getClass().getName());
        }
    }

    private static void regrowth(boolean expected, boolean rooted, boolean effectAtTarget) {
        if (BlastAutoBombAI.regrowthReady(rooted, effectAtTarget) != expected) {
            throw new AssertionError("Unexpected Regrowth targeting policy");
        }
    }

    public static void main(String[] args) {
        check(true, new RegrowthBomb());
        check(true, new ArcaneBomb());
        check(true, new ShrapnelBomb());
        check(false, new Bomb());

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
