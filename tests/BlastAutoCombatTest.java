package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;

/** Regression checks for sleeping-enemy protection and bounded LOS memory. */
public final class BlastAutoCombatTest {

    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        Rat rat = new Rat();
        check(false, BlastAutoPlay.isAwake(rat), "sleeping enemies are ignored");
        rat.state = rat.WANDERING;
        check(true, BlastAutoPlay.isAwake(rat), "wandering enemies can be targeted");
        rat.state = rat.HUNTING;
        check(true, BlastAutoPlay.isAwake(rat), "hunting enemies can be targeted");

        check(true, BlastAutoPlay.canPursue(1, 3, true), "brief LOS loss");
        check(true, BlastAutoPlay.canPursue(8, 2, true), "last memory turn");
        check(false, BlastAutoPlay.canPursue(9, 2, true), "bounded memory");
        check(false, BlastAutoPlay.canPursue(1, 1, true), "already beside last seen cell");
        check(false, BlastAutoPlay.canPursue(1, 3, false), "unreachable location");

        System.out.println("BlastAutoCombatTest passed");
    }
}
