package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ToxicGasRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.tiles.CustomTilemap;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

/** Choose Aqua Brew landing points using the actual GeyserTrap water footprint. */
final class BlastAutoAqua {
    enum Action { NONE, MOVE, THROW }
    private static final int THROW_RANGE = 8;
    private static final int SPLASH_RANGE = 2;
    private static final int SAFE_THROW_DISTANCE = 2;

    static final class Score {
        final int certain;
        final boolean opensGoal;
        final int goalProgress;
        final int newlyReachable;
        final int possible;
        final int approach;

        Score(int certain, boolean opensGoal, int goalProgress,
              int newlyReachable, int possible, int approach) {
            this.certain = certain;
            this.opensGoal = opensGoal;
            this.goalProgress = goalProgress;
            this.newlyReachable = newlyReachable;
            this.possible = possible;
            this.approach = approach;
        }
    }

    private BlastAutoAqua() {}

    /** More certain removals, then access gained, possible removals, and movement. */
    static boolean better(Score candidate, Score previous) {
        if (previous == null) return true;
        if (candidate.certain != previous.certain)
            return candidate.certain > previous.certain;
        if (candidate.opensGoal != previous.opensGoal)
            return candidate.opensGoal;
        if (candidate.goalProgress != previous.goalProgress)
            return candidate.goalProgress > previous.goalProgress;
        if (candidate.newlyReachable != previous.newlyReachable)
            return candidate.newlyReachable > previous.newlyReachable;
        if (candidate.possible != previous.possible)
            return candidate.possible > previous.possible;
        return candidate.approach < previous.approach;
    }

    /** GeyserTrap uses the same native distance map: 0-1 certain, 2 is 2/3 chance. */
    static int countTargets(int[] sprayDistance, Iterable<Integer> trapCells, boolean certain) {
        if (trapCells == null) throw new IllegalArgumentException("Missing Aqua Brew traps");
        int count = 0;
        for (int cell : trapCells) {
            if (cell < 0 || cell >= sprayDistance.length)
                throw new IllegalArgumentException("Invalid Aqua Brew target cell");
            if (certain ? sprayDistance[cell] < 2 : sprayDistance[cell] == 2) count++;
        }
        return count;
    }

    /** Mirror Level.setCellToWater's permitted trap and floor terrain. */
    static boolean waterableTerrain(int terrain) {
        return terrain == Terrain.EMPTY || terrain == Terrain.GRASS
                || terrain == Terrain.EMBERS || terrain == Terrain.EMPTY_SP
                || terrain == Terrain.HIGH_GRASS || terrain == Terrain.FURROWED_GRASS
                || terrain == Terrain.EMPTY_DECO || terrain == Terrain.WATER
                || terrain == Terrain.TRAP || terrain == Terrain.SECRET_TRAP
                || terrain == Terrain.INACTIVE_TRAP;
    }

    static boolean canFlood(Level level, int cell) {
        if (!level.insideMap(cell) || !waterableTerrain(level.map[cell])) return false;
        int x = cell % level.width(), y = cell / level.width();
        for (CustomTilemap tile : level.customTiles) {
            int tileX = x - tile.tileX, tileY = y - tile.tileY;
            if (tileX >= 0 && tileY >= 0 && tileX < tile.tileW && tileY < tile.tileH
                    && !tile.allowWater(tileX, tileY)) return false;
        }
        return true;
    }

    /** A geyser can shove every adjacent character up to two steps. */
    private static boolean safeGeyser(Level level, int center, Hero hero) {
        for (Char ch : Actor.chars()) {
            if (ch == hero || !ch.isAlive()
                    || level.distance(center, ch.pos) > SPLASH_RANGE) continue;
            if (ch.alignment == Char.Alignment.ALLY || !(ch instanceof Mob)
                    || BlastAutoPlay.isAutoAttackExcluded((Mob) ch)) return false;
        }
        return true;
    }

    /** Closest safe position with an unobstructed native projectile landing. */
    private static int throwingStand(Hero hero, Level level, int center,
                                     boolean[] safe, int[] distances) {
        int x = center % level.width(), y = center / level.width();
        int nearest = -1, steps = Integer.MAX_VALUE;
        for (int dy = -THROW_RANGE; dy <= THROW_RANGE; dy++) {
            int row = y + dy;
            if (row < 0 || row >= level.height()) continue;
            for (int dx = -THROW_RANGE; dx <= THROW_RANGE; dx++) {
                int col = x + dx;
                if (col < 0 || col >= level.width()) continue;
                int stand = col + row * level.width();
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                if (distance < SAFE_THROW_DISTANCE || distance > THROW_RANGE
                        || !safe[stand] || distances[stand] == Integer.MAX_VALUE
                        || distances[stand] >= steps
                        || (hero.rooted && stand != hero.pos)
                        || new Ballistica(stand, center, Ballistica.PROJECTILE).collisionPos != center)
                    continue;
                nearest = stand;
                steps = distances[stand];
            }
        }
        return nearest;
    }

    /** Score newly reachable terrain after a guaranteed removal, without random assumptions. */
    private static Score score(Hero hero, Level level, int[] sprayDistance,
                               ArrayList<Integer> trapCells, boolean[] safe, int[] distances,
                               int goal, int beforeGoalDistance, int stand,
                               Blob[] hazards, boolean[] specialBarriers) {
        int certain = countTargets(sprayDistance, trapCells, true);
        int possible = countTargets(sprayDistance, trapCells, false);
        if (certain == 0) return null;

        boolean[] opened = safe.clone();
        for (int cell : trapCells) {
            if (sprayDistance[cell] < 2
                    && !BlastAutoEnvironment.hazardousAt(cell, hazards)
                    && !specialBarriers[cell] && Actor.findChar(cell) == null
                    && !AutoPlayPlantSafety.isHarmful(level.plants.get(cell))) {
                opened[cell] = true;
            }
        }
        PathFinder.buildDistanceMap(hero.pos, opened);
        int gained = 0, nearest = Integer.MAX_VALUE;
        for (int cell = 0; cell < opened.length; cell++) {
            if (PathFinder.distance[cell] == Integer.MAX_VALUE) continue;
            if (distances[cell] == Integer.MAX_VALUE) gained++;
            if (goal >= 0) nearest = Math.min(nearest, level.distance(cell, goal));
        }
        boolean opensGoal = goal >= 0 && distances[goal] == Integer.MAX_VALUE
                && PathFinder.distance[goal] != Integer.MAX_VALUE;
        int progress = goal >= 0 ? beforeGoalDistance - nearest : 0;
        return new Score(certain, opensGoal, progress, gained, possible, distances[stand]);
    }

    /**
     * A landing can be normal floor, water, or a trap; only confirmed wettable
     * traps are scored. The 2/3-probability fringe never counts as guaranteed.
     */
    static Action chooseAction(Hero hero, boolean[] safe, int[] distances,
                               Room room, int goal, Blob[] hazards,
                               boolean[] specialBarriers) {
        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()
                || specialBarriers.length != level.length()
                || (goal >= level.length())) {
            throw new IllegalArgumentException("Invalid Aqua Brew navigation map");
        }
        if (hero.belongings.lostInventory()) return Action.NONE;
        AquaBrew aqua = hero.belongings.getItem(AquaBrew.class);
        if (aqua == null || aqua.quantity() <= 0) return Action.NONE;

        ArrayList<Integer> trapCells = new ArrayList<>();
        boolean[] centers = new boolean[level.length()];
        int width = level.width();
        for (Trap trap : level.traps.valueList()) {
            int cell = trap.pos;
            if (!level.insideMap(cell) || safe[cell]
                    || !(level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell])
                    || (room != null && !room.inside(level.cellToPoint(cell)))
                    || (!BlastAutoTrapClearer.canDouse(trap, level.map[cell])
                        && !(room instanceof ToxicGasRoom
                             && BlastAutoToxicRoom.activeVent(level, trap)))
                    || level.plants.get(cell) != null || Actor.findChar(cell) != null
                    || !canFlood(level, cell)) continue;
            trapCells.add(cell);
            int x = cell % width, y = cell / width;
            for (int dy = -SPLASH_RANGE; dy <= SPLASH_RANGE; dy++) {
                int row = y + dy;
                if (row < 0 || row >= level.height()) continue;
                for (int dx = -SPLASH_RANGE; dx <= SPLASH_RANGE; dx++) {
                    int col = x + dx;
                    if (col >= 0 && col < width) centers[col + row * width] = true;
                }
            }
        }
        if (trapCells.isEmpty()) return Action.NONE;

        int beforeGoalDistance = Integer.MAX_VALUE;
        if (goal >= 0) {
            for (int cell = 0; cell < level.length(); cell++) {
                if (distances[cell] != Integer.MAX_VALUE) {
                    beforeGoalDistance = Math.min(beforeGoalDistance, level.distance(cell, goal));
                }
            }
        }
        boolean[] sprayPassable = new boolean[level.length()];
        for (int i = 0; i < sprayPassable.length; i++)
            sprayPassable[i] = !level.solid[i];

        int bestCenter = -1, bestStand = -1;
        Score best = null;
        for (int center = 0; center < centers.length; center++) {
            if (!centers[center] || !level.insideMap(center) || level.pit[center]
                    || level.solid[center] || !canFlood(level, center)
                    || Actor.findChar(center) != null || level.heaps.get(center) != null
                    || level.plants.get(center) != null
                    || !safeGeyser(level, center, hero)) continue;
            int stand = throwingStand(hero, level, center, safe, distances);
            if (stand < 0) continue;

            // Exactly matches GeyserTrap.activate(): traverses non-solid cells,
            // with all eight neighboring directions and a maximum of two steps.
            PathFinder.buildDistanceMap(center, sprayPassable, SPLASH_RANGE);
            int[] coverage = PathFinder.distance.clone();
            if (countTargets(coverage, trapCells, true) == 0) continue;
            Score candidate = score(hero, level, coverage, trapCells, safe, distances,
                    goal, beforeGoalDistance, stand, hazards, specialBarriers);
            if (better(candidate, best)) {
                best = candidate;
                bestCenter = center;
                bestStand = stand;
            }
        }
        if (best == null) return Action.NONE;
        if (hero.pos != bestStand) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(bestStand, safe, distances));
            BlastLog.debug("auto-aqua action=move landing=" + bestCenter
                    + " certain=" + best.certain + " possible=" + best.possible
                    + " unlocked=" + best.newlyReachable + " stand=" + bestStand);
            return Action.MOVE;
        }
        if (aqua.throwPos(hero, bestCenter) != bestCenter) return Action.NONE;
        BlastLog.debug("auto-aqua action=throw landing=" + bestCenter
                + " certain=" + best.certain + " possible=" + best.possible
                + " openedGoal=" + best.opensGoal + " unlocked=" + best.newlyReachable);
        aqua.setCurrent(hero);
        aqua.cast(hero, bestCenter);
        return Action.THROW;
    }
}
