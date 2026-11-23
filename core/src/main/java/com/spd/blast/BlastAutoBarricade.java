package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;

/** Approach discovered barricades and clear them with ordinary Bomb Maniac charges. */
final class BlastAutoBarricade {
    enum Action { NONE, MOVE, DETONATE }

    private BlastAutoBarricade() {}

    static boolean reachesSafely(boolean[] blast, int barricade, int origin,
                                 boolean protectedHero) {
        if (barricade < 0 || barricade >= blast.length
                || origin < 0 || origin >= blast.length) {
            throw new IllegalArgumentException("Invalid barricade blast cell");
        }
        return blast[barricade] && (protectedHero || !blast[origin]);
    }

    /** Shared safety policy for blasts aimed at a destructible terrain cell. */
    static boolean canDetonate(boolean[] blast, int target, int origin,
                                       boolean protectedHero) {
        if (!reachesSafely(blast, target, origin, protectedHero)) return false;
        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive() || !blast[mob.pos]) continue;
            // Keep automatic combat's sleeping-enemy and protected-NPC rules.
            if (mob.alignment == Char.Alignment.ALLY
                    || (mob.alignment == Char.Alignment.ENEMY && !BlastAutoPlay.isAwake(mob))
                    || (BlastAutoPlay.isAutoAttackExcluded(mob)
                        && !BlastAutoBombAI.ignoresBombCollateral(mob))) {
                return false;
            }
        }
        return true;
    }

    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid barricade navigation map");
        }
        if (!BlastSettings.testAutoPlay() || BlastRemoteBomb.isTimedMode()) return Action.NONE;
        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0) return Action.NONE;

        boolean protectedHero = hero.buff(BlastIndestructible.class) != null
                || hero.buff(BlastTestBuffs.Indestructible.class) != null;
        boolean remoteThrow = BlastSettings.testRemoteThrow();
        if (!protectedHero && !remoteThrow) return Action.NONE;
        Bomb ordinaryBomb = maniac.bombAt(0);

        // A remote throw can clear a visible barricade immediately, even without
        // bomb immunity, provided the Hero stands outside the blast.
        if (remoteThrow) {
            int target = -1;
            int nearest = Integer.MAX_VALUE;
            for (int cell = 0; cell < level.length(); cell++) {
                if (level.map[cell] != Terrain.BARRICADE || !hero.fieldOfView[cell]) continue;
                int landing = BlastBombTrajectory.throwPos(hero.pos, cell);
                if (!level.insideMap(landing) || level.pit[landing]) continue;
                boolean[] blast = ordinaryBomb.blastWarningCells(landing);
                if (!canDetonate(blast, cell, hero.pos, protectedHero)) continue;
                int distance = level.distance(hero.pos, cell);
                if (distance < nearest) {
                    nearest = distance;
                    target = cell;
                }
            }
            if (target >= 0) {
                return maniac.blastAutoThrowOrdinaryAndDetonate(target)
                        ? Action.DETONATE : Action.NONE;
            }
        }

        // Without immunity, never walk next to a barricade just to bomb ourselves.
        if (!protectedHero) return Action.NONE;

        int bestStand = -1;
        int bestSteps = Integer.MAX_VALUE;
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.map[cell] != Terrain.BARRICADE
                    || !(hero.fieldOfView[cell] || level.mapped[cell] || level.visited[cell])) {
                continue;
            }
            for (int offset : PathFinder.NEIGHBOURS8) {
                int stand = cell + offset;
                if (!level.insideMap(stand) || !safe[stand]
                        || distances[stand] == Integer.MAX_VALUE
                        || distances[stand] >= bestSteps || level.pit[stand]) continue;

                int center = remoteThrow ? BlastBombTrajectory.throwPos(stand, cell) : stand;
                if (!level.insideMap(center) || level.pit[center]) continue;
                boolean[] blast = ordinaryBomb.blastWarningCells(center);
                if (!canDetonate(blast, cell, stand, true)) continue;
                bestStand = stand;
                bestSteps = distances[stand];
            }
        }
        if (bestStand < 0) return Action.NONE;
        if (bestStand != hero.pos) {
            if (hero.rooted) return Action.NONE;
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(bestStand, safe, distances));
            return Action.MOVE;
        }
        if (remoteThrow) {
            // The direct shot above already checked all visible barricades.
            return Action.NONE;
        }
        if (maniac.blastAutoPlaceOrdinaryAndDetonate()) {
            hero.spendAndNext(1f);
            return Action.DETONATE;
        }
        return Action.NONE;
    }
}
