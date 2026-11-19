package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DemonSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Bandit;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Imp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Wandmaker;

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

        // Both enemy types use the same damage comparison, even at point blank.
        check(true, BlastAutoCombat.preferRemote(3f, 0f), "remote attack before movement");
        check(true, BlastAutoCombat.preferRemote(6f, 5f), "stronger remote attack");
        check(true, BlastAutoCombat.preferRemote(5f, 5f), "remote wins damage tie");
        check(false, BlastAutoCombat.preferRemote(3f, 5f), "stronger melee attack");
        check(false, BlastAutoCombat.preferRemote(0f, 0f), "no damaging remote attack");
        check(false, BlastAutoCombat.corrosionDistanceSafe(3), "nearby corrosion");
        check(true, BlastAutoCombat.corrosionDistanceSafe(4), "minimum corrosion distance");

        check(false, BlastAutoPlay.highEvasion(false, 19, 10),
                "ordinary evasion");
        check(true, BlastAutoPlay.highEvasion(false, 20, 10),
                "exactly twice hero accuracy");
        check(true, BlastAutoPlay.highEvasion(true, 1, 100),
                "wraith special case");

        check(true, BlastAutoPlay.avoidEvasiveNeutral(Char.Alignment.NEUTRAL, true),
                "high-evasion neutral is ignored");
        check(false, BlastAutoPlay.avoidEvasiveNeutral(Char.Alignment.ENEMY, true),
                "high-evasion enemy is eligible for wands");
        check(false, BlastAutoPlay.avoidEvasiveNeutral(Char.Alignment.NEUTRAL, false),
                "ordinary neutral behavior is unchanged");

        check(true, BlastAutoPlay.isAutoAttackExcluded(new Shopkeeper()),
                "shopkeeper must not be auto-attacked");
        check(true, BlastAutoPlay.isAutoAttackExcluded(new Ghost()),
                "ghost quest giver must not be auto-attacked");
        check(true, BlastAutoPlay.isAutoAttackExcluded(new Wandmaker()),
                "wandmaker must not be auto-attacked");
        check(true, BlastAutoPlay.isAutoAttackExcluded(new Blacksmith()),
                "blacksmith must not be auto-attacked");
        check(true, BlastAutoPlay.isAutoAttackExcluded(new Imp()),
                "imp must not be auto-attacked");
        check(false, BlastAutoPlay.isAutoAttackExcluded(new Rat()),
                "ordinary enemies remain eligible");
        check(true, BlastAutoPlay.isAutoAttackExcluded(new DemonSpawner()),
                "demon spawners must not be auto-attacked");

        System.out.println("BlastAutoCombatTest passed");
    }
}
