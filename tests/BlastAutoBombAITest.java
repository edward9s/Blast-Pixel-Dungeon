package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ArcaneBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.RegrowthBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ShrapnelBomb;

/** Headless checks that Regrowth shares the ordinary non-destructive bomb path. */
public final class BlastAutoBombAITest {
    private static void check(boolean expected, Bomb bomb) {
        if (BlastAutoBombAI.itemPreserving(bomb) != expected) {
            throw new AssertionError("Unexpected auto-bomb eligibility: " + bomb.getClass().getName());
        }
    }

    public static void main(String[] args) {
        check(true, new RegrowthBomb());
        check(true, new ArcaneBomb());
        check(true, new ShrapnelBomb());
        check(false, new Bomb());
        System.out.println("BlastAutoBombAITest passed");
    }
}
