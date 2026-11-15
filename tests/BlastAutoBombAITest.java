package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ArcaneBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.RegrowthBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ShrapnelBomb;

/** Regression checks for safe auto-bomb selection and Regrowth repetition. */
public final class BlastAutoBombAITest {
    private static void check(boolean expected, Bomb bomb) {
        if (BlastAutoBombAI.itemPreserving(bomb) != expected) {
            throw new AssertionError("Unexpected auto-bomb eligibility: " + bomb.getClass().getName());
        }
    }

    private static void ready(boolean expected, float now, float lastUse, boolean cloudActive) {
        if (BlastAutoBombAI.regrowthReady(now, lastUse, cloudActive) != expected) {
            throw new AssertionError("Unexpected Regrowth retry eligibility");
        }
    }

    public static void main(String[] args) {
        check(true, new RegrowthBomb());
        check(true, new ArcaneBomb());
        check(true, new ShrapnelBomb());
        check(false, new Bomb());

        ready(true, 0f, Float.NEGATIVE_INFINITY, false);
        ready(false, 5f, 0f, false);
        ready(false, 11f, 0f, false);
        ready(true, 12f, 0f, false);
        ready(false, 100f, 0f, true);
        System.out.println("BlastAutoBombAITest passed");
    }
}
