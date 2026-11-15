package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BurningTrap;

/** Verify auto-play avoids armed traps without blocking disarmed ones. */
public final class BlastAutoTrapSafetyTest {
    private static void check(boolean expected, boolean actual, String label) {
        if (actual != expected) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(true, BlastAutoPlay.isTrapSafe(null), "no trap");

        BurningTrap trap = new BurningTrap();
        check(false, BlastAutoPlay.isTrapSafe(trap), "armed trap");

        trap.active = false;
        check(true, BlastAutoPlay.isTrapSafe(trap), "inactive trap");

        trap.active = true;
        check(false, BlastAutoPlay.isTrapSafe(trap), "reactivated trap");
        System.out.println("BlastAutoTrapSafetyTest passed");
    }
}
