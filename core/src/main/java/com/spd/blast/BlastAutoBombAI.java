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

    /** Returns true only when an automated bomb has started or spent a turn. */
    public static boolean act(Hero hero) {
        if (!BlastSettings.testAutoPlay()
                || BlastRemoteBomb.isTimedMode()
                || hero.buff(BlastRemoteBomb.class) == null
                || (hero.buff(BlastIndestructible.class) == null
                    && hero.buff(BlastTestBuffs.Indestructible.class) == null)) return false;

        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0) return false;

        Bomb bomb = maniac.selectedBomb();
        if (!itemPreserving(bomb)) return false;

        boolean regrowth = bomb instanceof RegrowthBomb;
        boolean remoteThrow = BlastSettings.testRemoteThrow();
        boolean[] blastAtHero = remoteThrow ? null : bomb.blastWarningCells(hero.pos);
        Mob target = findTarget(hero, blastAtHero, remoteThrow, regrowth);
        if (target == null) return false;

        // Do not affect rare variants, even as collateral or outside hero vision.
        int center = remoteThrow ? target.pos : hero.pos;
        boolean[] blast = remoteThrow ? bomb.blastWarningCells(center) : blastAtHero;
        if (containsRareMob(blast)) return false;

        if (remoteThrow) return maniac.blastAutoThrowAndDetonate(target.pos);

        if (maniac.blastAutoPlaceAndDetonate()) {
            // Automatic attacks must advance time even when remote placement is free.
            hero.spendAndNext(1f);
            return true;
        }
        return false;
    }

    static boolean itemPreserving(Bomb bomb) {
        return !bomb.explodesDestructively();
    }

    /** An existing Regrowth cloud or root already covers the target. */
    static boolean regrowthReady(boolean rooted, boolean regrowthAtTarget) {
        return !rooted && !regrowthAtTarget;
    }

    private static boolean regrowthAt(int cell) {
        return Blob.volumeAt(cell, BombRegrowth.class) > 0
                || Blob.volumeAt(cell, Regrowth.class) > 0;
    }

    private static Mob findTarget(Hero hero, boolean[] blastAtHero, boolean remoteThrow,
                                  boolean regrowth) {
        Mob chosen = null;
        int closest = Integer.MAX_VALUE;
        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive() || mob.alignment != Char.Alignment.ENEMY
                    || !hero.fieldOfView[mob.pos] || mob.invisible > 0
                    || hero.isCharmedBy(mob) || mob.isInvulnerable(Hero.class)
                    || !BlastAutoPlay.isAwake(mob) || isRare(mob)) continue;
            if (regrowth && !regrowthReady(mob.rooted, regrowthAt(mob.pos))) continue;

            if (remoteThrow) {
                if (BlastBombTrajectory.throwPos(hero.pos, mob.pos) != mob.pos) continue;
            } else if (!blastAtHero[mob.pos]) {
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

    private static boolean containsRareMob(boolean[] blast) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.isAlive() && isRare(mob) && blast[mob.pos]) return true;
        }
        return false;
    }

    private static boolean isRare(Mob mob) {
        return MobSpawner.RARE_ALTS.containsValue(mob.getClass());
    }
}
