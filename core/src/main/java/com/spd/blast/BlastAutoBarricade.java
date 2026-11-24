package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Web;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretRunestoneRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;

/** Clear barricades, runestone-room bookshelf partitions, and visible webs with ordinary bombs. */
final class BlastAutoBarricade {
    enum Action { NONE, MOVE, DETONATE }

    private BlastAutoBarricade() {}

    /** Webs are temporary obstacles; ordinary bookshelves remain protected. */
    static boolean demolitionTarget(int terrain, boolean inRunestoneRoom, boolean web) {
        return web || terrain == Terrain.BARRICADE
                || (terrain == Terrain.BOOKSHELF && inRunestoneRoom);
    }

    private static int[] activeWeb(Level level) {
        Web web = (Web) level.blobs.get(Web.class);
        if (web == null || web.volume <= 0) return null;
        if (web.cur == null || web.cur.length != level.length()) {
            throw new IllegalStateException("Invalid web grid");
        }
        return web.cur;
    }

    private static boolean inRunestoneRoom(Level level, SecretRunestoneRoom room, int cell) {
        if (room == null) return false;
        int x = cell % level.width(), y = cell / level.width();
        return x > room.left && x < room.right && y > room.top && y < room.bottom;
    }


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

    static boolean risksLoot(Heap heap, boolean demolition) {
        for (Item item : heap.items) {
            if (demolition && BlastDemolition.destroysKey(item)) return true;
            if (heap.type == Heap.Type.HEAP && BlastBombHeap.destroysLooseItem(item)) return true;
            // A chain reaction can reach loot beyond our initial blast.
            if (BlastBombHeap.isContainer(heap) && BlastBombHeap.isExposedBomb(item)) return true;
        }
        return false;
    }

    private static boolean canClear(Level level, int[] web, boolean[] blast,
                                    int target, int origin, boolean protectedHero) {
        if (!canDetonate(blast, target, origin, protectedHero)) return false;
        if (web == null) return true;

        boolean hitsWeb = false;
        for (int cell = 0; cell < blast.length; cell++) {
            if (blast[cell] && web[cell] > 0) {
                hitsWeb = true;
                break;
            }
        }
        if (!hitsWeb) return true;

        boolean demolition = BlastSettings.testDemolition();
        for (Heap heap : level.heaps.valueList()) {
            if (blast[heap.pos] && risksLoot(heap, demolition)) return false;
        }
        return true;
    }

    /** Rooting bypasses normal idle tasks; clear adjacent webs without trying to move. */
    static Action clearAdjacentWeb(Hero hero) {
        Level level = Dungeon.level;
        int[] web = activeWeb(level);
        if (web == null || !BlastSettings.testAutoPlay() || BlastRemoteBomb.isTimedMode()
                || (hero.buff(BlastIndestructible.class) == null
                    && hero.buff(BlastTestBuffs.Indestructible.class) == null)) {
            return Action.NONE;
        }
        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0) return Action.NONE;

        boolean remoteThrow = BlastSettings.testRemoteThrow();
        Bomb ordinaryBomb = maniac.bombAt(0);
        boolean[] localBlast = remoteThrow ? null : ordinaryBomb.blastWarningCells(hero.pos);
        for (int offset : PathFinder.NEIGHBOURS8) {
            int target = hero.pos + offset;
            if (!level.insideMap(target) || web[target] <= 0
                    || !hero.fieldOfView[target]) continue;
            int center = remoteThrow
                    ? BlastBombTrajectory.throwPos(hero.pos, target) : hero.pos;
            if (!level.insideMap(center) || level.pit[center]) continue;
            boolean[] blast = remoteThrow ? ordinaryBomb.blastWarningCells(center) : localBlast;
            if (!canClear(level, web, blast, target, hero.pos, true)) continue;
            if (remoteThrow) {
                return maniac.blastAutoThrowOrdinaryAndDetonate(target)
                        ? Action.DETONATE : Action.NONE;
            }
            if (maniac.blastAutoPlaceOrdinaryAndDetonate()) {
                hero.spendAndNext(1f);
                return Action.DETONATE;
            }
            return Action.NONE;
        }
        return Action.NONE;
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
        int[] web = activeWeb(level);
        SecretRunestoneRoom runestoneRoom = level instanceof RegularLevel
                ? (SecretRunestoneRoom) ((RegularLevel) level).room(SecretRunestoneRoom.class)
                : null;

        // A remote throw can clear a visible barricade immediately, even without
        // bomb immunity, provided the Hero stands outside the blast.
        if (remoteThrow) {
            int target = -1;
            int nearest = Integer.MAX_VALUE;
            for (int cell = 0; cell < level.length(); cell++) {
                if (!hero.fieldOfView[cell]
                        || !demolitionTarget(level.map[cell],
                                inRunestoneRoom(level, runestoneRoom, cell),
                                web != null && web[cell] > 0)) continue;
                int landing = BlastBombTrajectory.throwPos(hero.pos, cell);
                if (!level.insideMap(landing) || level.pit[landing]) continue;
                boolean[] blast = ordinaryBomb.blastWarningCells(landing);
                if (!canClear(level, web, blast, cell, hero.pos, protectedHero)) continue;
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
            if (!demolitionTarget(level.map[cell],
                    inRunestoneRoom(level, runestoneRoom, cell),
                    web != null && web[cell] > 0 && hero.fieldOfView[cell])
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
                if (!canClear(level, web, blast, cell, stand, true)) continue;
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
