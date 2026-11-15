package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bandit;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;

/** Regression checks for sleeping-enemy protection and bounded LOS memory. */
public final class BlastAutoCombatTest {

    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }


    private static void rank(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": " + actual);
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

        // An attacking melee opponent takes precedence over a thief or spellcaster.
        rank(0, BlastAutoPlay.combatPriority(new Rat(), true),
                "immediate melee danger");
        rank(1, BlastAutoPlay.combatPriority(new Thief(), false),
                "thief priority");
        rank(1, BlastAutoPlay.combatPriority(new Bandit(), false),
                "bandit inherits thief priority");
        rank(1, BlastAutoPlay.combatPriority(new Warlock(), false),
                "ranged priority");
        rank(2, BlastAutoPlay.combatPriority(new Rat(), false),
                "non-threatening melee opponent");

        // A nearer regular melee enemy cannot override a distant thief or caster.
        check(true, BlastAutoPlay.betterTarget(1, 5, 6, 2, 0, 1),
                "special priority before regular melee");
        check(true, BlastAutoPlay.betterTarget(0, 1, 1, 1, 2, 2),
                "immediate danger before special priority");
        check(true, BlastAutoPlay.betterTarget(1, 2, 5, 1, 4, 2),
                "shorter safe path among equal-priority targets");
        check(false, BlastAutoPlay.betterTarget(1, 4, 2, 1, 2, 5),
                "longer safe path loses equal-priority tie");

        // Enemy spellcasters must be reached in melee; a long-reach weapon
        // is not enough to trigger attacks on them from a distance.
        check(false, BlastAutoPlay.canMeleeTarget(true, false, true),
                "ranged enemy outside melee range");
        check(true, BlastAutoPlay.canMeleeTarget(true, true, true),
                "adjacent ranged enemy");
        check(true, BlastAutoPlay.canMeleeTarget(false, false, true),
                "existing reach-weapon attacks on ordinary mobs");
        check(false, BlastAutoPlay.canMeleeTarget(false, true, false),
                "native melee attack still required");

        System.out.println("BlastAutoCombatTest passed");
    }
}
