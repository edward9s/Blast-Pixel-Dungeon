package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Regrowth;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.MobSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.RegrowthBomb;

/** Blast-only bomb tactics, isolated from the portable auto-play controller. */
public final class BlastAutoBombAI {
    private BlastAutoBombAI() {}

    /** Hit ranged enemies immediately, including enemies outside the hero's vision. */
    public static boolean actAgainstRanged(Hero hero) {
        return act(hero, true);
    }

    /** Preserve ordinary bomb attacks for visible non-ranged enemies. */
    public static boolean actAgainstOtherEnemies(Hero hero) {
        return act(hero, false);
    }

    /** Returns true only when an automated bomb has started or spent a turn. */
    private static boolean act(Hero hero, boolean rangedTargets) {
        if (!BlastSettings.testAutoPlay()
                || BlastRemoteBomb.isTimedMode()
                || hero.buff(BlastRemoteBomb.class) == null
                || (hero.buff(BlastIndestructible.class) == null
                    && hero.buff(BlastTestBuffs.Indestructible.class) == null)) return false;

        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0) return false;

        Bomb bomb = maniac.selectedBomb();

        boolean regrowth = bomb instanceof RegrowthBomb;
        boolean remoteThrow = BlastSettings.testRemoteThrow();
        boolean[] blastAtHero = remoteThrow ? null : bomb.blastWarningCells(hero.pos);
        Mob target = findTarget(hero, bomb, blastAtHero, remoteThrow, regrowth, rangedTargets);
        if (target == null) return false;

        // Avoid rare variants and NPCs, including collateral outside hero vision.
        int center = remoteThrow ? BlastBombTrajectory.throwPos(hero.pos, target.pos) : hero.pos;
        boolean[] blast = remoteThrow ? bomb.blastWarningCells(center) : blastAtHero;
        if (containsProtectedMob(blast)) return false;

        if (remoteThrow) return maniac.blastAutoThrowAndDetonate(target.pos);

        if (maniac.blastAutoPlaceAndDetonate()) {
            // Automatic attacks must advance time even when remote placement is free.
            hero.spendAndNext(1f);
            return true;
        }
        return false;
    }

    static boolean blastReachesTarget(boolean[] blast, int targetCell) {
        if (targetCell < 0 || targetCell >= blast.length) {
            throw new IllegalArgumentException("Invalid bomb target: " + targetCell);
        }
        return blast[targetCell];
    }

    /** An existing Regrowth cloud or root already covers the target. */
    static boolean regrowthReady(boolean rooted, boolean regrowthAtTarget) {
        return !rooted && !regrowthAtTarget;
    }

    private static boolean regrowthAt(int cell) {
        return Blob.volumeAt(cell, BombRegrowth.class) > 0
                || Blob.volumeAt(cell, Regrowth.class) > 0;
    }

    private static Mob findTarget(Hero hero, Bomb bomb, boolean[] blastAtHero,
                                  boolean remoteThrow, boolean regrowth, boolean rangedTargets) {
        Mob chosen = null;
        int closest = Integer.MAX_VALUE;
        for (Mob mob : Dungeon.level.mobs) {
            boolean ranged = BlastAutoSacrifice.isRangedEnemy(mob.getClass());
            if (ranged != rangedTargets || (!ranged && !hero.fieldOfView[mob.pos])) continue;
            if (!mob.isAlive() || mob.alignment != Char.Alignment.ENEMY
                    || mob.invisible > 0
                    || hero.isCharmedBy(mob) || mob.isInvulnerable(Hero.class)
                    || !BlastAutoPlay.isAwake(mob) || isRare(mob)
                    || BlastAutoPlay.isProtectedNpc(mob)) continue;
            if (regrowth && !regrowthReady(mob.rooted, regrowthAt(mob.pos))) continue;

            if (remoteThrow) {
                int landing = BlastBombTrajectory.throwPos(hero.pos, mob.pos);
                if (!Dungeon.level.insideMap(landing)) continue;
                // An obstructed throw still counts if the actual blast reaches the enemy.
                if (landing != mob.pos
                        && !blastReachesTarget(bomb.blastWarningCells(landing), mob.pos)) {
                    continue;
                }
            } else if (!blastReachesTarget(blastAtHero, mob.pos)) {
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

    private static boolean containsProtectedMob(boolean[] blast) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.isAlive() && blast[mob.pos]
                    && (isRare(mob) || BlastAutoPlay.isProtectedNpc(mob))) return true;
        }
        return false;
    }

    private static boolean isRare(Mob mob) {
        return MobSpawner.RARE_ALTS.containsValue(mob.getClass());
    }
}
