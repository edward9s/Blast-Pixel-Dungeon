package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.MobSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.RegrowthBomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

/**
 * Blast Pixel Dungeon-only bomb tactics. The generic auto-play controller
 * calls this as an optional pre-combat action and owns no bomb-specific state.
 */
public final class BlastAutoBombAI {
    private static final int MAX_RETREAT_TURNS = 10;
    private static final int REGROWTH_COOLDOWN = 16;

    private static Hero currentHero;
    private static Level currentLevel;
    private static Bomb regrowthBomb;
    private static int regrowthCell = -1;
    private static int retreatTurns;
    private static int regrowthCooldown;

    private BlastAutoBombAI() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        regrowthBomb = null;
        regrowthCell = -1;
        retreatTurns = 0;
        regrowthCooldown = 0;
    }

    /** A player override abandons the optional retreat and resumes normal AI later. */
    public static void cancelRegrowth() {
        if (regrowthBomb != null) {
            clearRegrowth();
            regrowthCooldown = REGROWTH_COOLDOWN;
        }
    }

    /** Returns true only after taking over the actor turn (or starting an animation). */
    public static boolean act(Hero hero, boolean[] safe, int[] distances) {
        if (!BlastSettings.testAutoPlay()) return false;
        if (currentHero != hero || currentLevel != Dungeon.level) {
            reset();
            currentHero = hero;
            currentLevel = Dungeon.level;
        }
        if (regrowthCooldown > 0) regrowthCooldown--;

        // A placed Regrowth Bomb is a short, optional sequence. No deadlock or
        // indefinite escape attempt may prevent ordinary combat or exploration.
        if (regrowthBomb != null) return retreatFromRegrowth(hero, safe, distances);

        if (BlastRemoteBomb.isTimedMode()
                || hero.buff(BlastRemoteBomb.class) == null
                || (hero.buff(BlastIndestructible.class) == null
                    && hero.buff(BlastTestBuffs.Indestructible.class) == null)) return false;

        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0) return false;

        Bomb bomb = maniac.selectedBomb();
        if (bomb.explodesDestructively()) return false;

        boolean remoteThrow = BlastSettings.testRemoteThrow();
        boolean regrowth = bomb instanceof RegrowthBomb;
        if (regrowth && regrowthCooldown > 0) return false;

        boolean[] blastAtHero = !remoteThrow || regrowth
                ? bomb.blastWarningCells(hero.pos) : null;
        Mob target = findTarget(hero, bomb, blastAtHero, remoteThrow && !regrowth);
        if (target == null) return false;

        // An automated explosion must never affect a rare variant, including
        // one which is not currently visible to the hero.
        int center = remoteThrow && !regrowth ? target.pos : hero.pos;
        boolean[] blast = center == hero.pos
                ? blastAtHero : bomb.blastWarningCells(center);
        if (containsRareMob(blast)) return false;

        if (regrowth) {
            // Find an exit *before* placing; a sealed corridor must not trap AI.
            int exit = nearestEscape(safe, distances, blast);
            if (hero.rooted || exit < 0 || distances[exit] > MAX_RETREAT_TURNS) return false;
            Bomb placed = maniac.blastAutoPlace();
            if (placed == null) return false;
            regrowthBomb = placed;
            regrowthCell = hero.pos;
            retreatTurns = 0;
            hero.spendAndNext(1f);
            return true;
        }

        if (remoteThrow) return maniac.blastAutoThrowAndDetonate(target.pos);

        if (maniac.blastAutoPlaceAndDetonate()) {
            // The manual remote placement is free; automatic attacks take a turn.
            hero.spendAndNext(1f);
            return true;
        }
        return false;
    }

    private static Mob findTarget(Hero hero, Bomb bomb, boolean[] atHero,
                                  boolean ranged) {
        Mob chosen = null;
        int closest = Integer.MAX_VALUE;
        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive() || mob.alignment != Char.Alignment.ENEMY
                    || !hero.fieldOfView[mob.pos] || mob.invisible > 0
                    || hero.isCharmedBy(mob) || mob.isInvulnerable(Hero.class)
                    || isRare(mob)) continue;

            if (ranged) {
                if (BlastBombTrajectory.throwPos(hero.pos, mob.pos) != mob.pos) continue;
            } else if (!atHero[mob.pos]) {
                continue;
            }

            int distance = Dungeon.level.distance(hero.pos, mob.pos);
            if (chosen == null
                    || (mob instanceof Necromancer && !(chosen instanceof Necromancer))
                    || ((mob instanceof Necromancer) == (chosen instanceof Necromancer)
                        && distance < closest)) {
                chosen = mob;
                closest = distance;
            }
        }
        return chosen;
    }

    private static boolean retreatFromRegrowth(Hero hero, boolean[] safe, int[] distances) {
        if (regrowthBomb.fuse == null || Dungeon.level != currentLevel) {
            abandonRegrowth();
            return false;
        }
        boolean[] blast = regrowthBomb.blastWarningCells(regrowthCell);
        if (!blast[hero.pos]) {
            Bomb bomb = regrowthBomb;
            int cell = regrowthCell;
            clearRegrowth();
            regrowthCooldown = REGROWTH_COOLDOWN;
            if (containsRareMob(blast)) return false;
            BlastBombManiac maniac = BlastBombManiac.current();
            if (maniac == null || !maniac.blastAutoDetonate(bomb, cell)) return false;
            hero.spendAndNext(1f);
            return true;
        }

        if (hero.rooted || ++retreatTurns > MAX_RETREAT_TURNS) {
            abandonRegrowth();
            return false;
        }
        int exit = nearestEscape(safe, distances, blast);
        if (exit < 0 || distances[exit] > MAX_RETREAT_TURNS - retreatTurns + 1) {
            abandonRegrowth();
            return false;
        }
        hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(exit, safe, distances));
        return false;
    }

    /** Reuse the existing hero distance map; no extra pathfinding pass. */
    private static int nearestEscape(boolean[] safe, int[] distances, boolean[] blast) {
        if (safe.length != distances.length || safe.length != blast.length) {
            throw new IllegalArgumentException("Mismatched escape-map dimensions");
        }
        int result = -1;
        int shortest = Integer.MAX_VALUE;
        for (int cell = 0; cell < safe.length; cell++) {
            int distance = distances[cell];
            if (!safe[cell] || blast[cell] || distance == 0
                    || distance >= shortest || distance == Integer.MAX_VALUE) continue;
            result = cell;
            shortest = distance;
        }
        return result;
    }

    private static boolean containsRareMob(boolean[] blast) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.isAlive() && isRare(mob) && blast[mob.pos]) return true;
        }
        return false;
    }

    private static boolean isRare(Mob mob) {
        return MobSpawner.RARE_ALTS.containsValue(mob.getClass());
    }

    /** Leave an undetonated bomb alone if the escape route becomes unavailable. */
    private static void abandonRegrowth() {
        clearRegrowth();
        regrowthCooldown = REGROWTH_COOLDOWN;
    }

    private static void clearRegrowth() {
        regrowthBomb = null;
        regrowthCell = -1;
        retreatTurns = 0;
    }
}
