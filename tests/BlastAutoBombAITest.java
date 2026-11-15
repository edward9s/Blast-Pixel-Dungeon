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
        System.out.println("BlastAutoBombAITest passed");
    }
}
