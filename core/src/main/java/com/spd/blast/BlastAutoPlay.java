package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

/** Autonomous testing, one ordinary HeroAction per actor turn. */
public final class BlastAutoPlay {

    private static final int ATTACKER_MEMORY_TURNS = 8;

    private static Hero currentHero;
    private static Level currentLevel;
    private static int roamCell = -1;
    private static Char lastAttacker;
    private static int attackerCell = -1;
    private static int attackerTurns;

    private BlastAutoPlay() {}

    public static void onSettingChanged() {
        currentHero = null;
        currentLevel = null;
        roamCell = -1;
        lastAttacker = null;
        attackerCell = -1;
        attackerTurns = 0;

        Hero hero = Dungeon.hero;
        if (hero == null || Dungeon.level == null) return;
        if (BlastSettings.testAutoPlay()) {
            if (hero.ready) hero.next();
        } else {
            hero.curAction = null;
            hero.lastAction = null;
        }
    }

    public static void onDamage(Hero hero, Object source) {
        if (!BlastSettings.testAutoPlay() || !(source instanceof Char)) return;
        Char attacker = (Char) source;
        if (attacker == hero || attacker.alignment == Char.Alignment.ALLY) return;
        resetForLevel(hero);
        lastAttacker = attacker;
        attackerCell = attacker.pos;
        attackerTurns = ATTACKER_MEMORY_TURNS;
    }

    public static void chooseAction(Hero hero) {
        resetForLevel(hero);
        hero.curAction = null;
        hero.lastAction = null;
        hero.resting = false;

        boolean[] safe = safeCells(hero);
        Mob invulnerable = null;
        Mob attackable = null;
        int attackDistance = Integer.MAX_VALUE;
        int approachStep = -1;
        int approachLength = Integer.MAX_VALUE;

        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive() || mob.alignment == Char.Alignment.ALLY
                    || mob.invisible > 0 || !hero.fieldOfView[mob.pos]) {
                continue;
            }

            if (mob.isInvulnerable(Hero.class)) {
                if (invulnerable == null
                        || Dungeon.level.distance(hero.pos, mob.pos)
                                < Dungeon.level.distance(hero.pos, invulnerable.pos)) {
                    invulnerable = mob;
                }
                continue;
            }

            if (hero.isCharmedBy(mob)) continue;

            int distance = Dungeon.level.distance(hero.pos, mob.pos);
            if (hero.canAttack(mob)) {
                if (attackable == null || distance < attackDistance) {
                    attackable = mob;
                    attackDistance = distance;
                }
                continue;
            }

            PathFinder.Path path = pathNear(hero, mob.pos, safe);
            if (path != null && !path.isEmpty() && path.size() < approachLength) {
                approachLength = path.size();
                approachStep = path.getFirst();
            }
        }

        if (invulnerable == null && attackerTurns > 0 && lastAttacker != null
                && lastAttacker.isAlive() && lastAttacker.isInvulnerable(Hero.class)) {
            int step = retreatStep(hero, lastAttacker.pos, safe);
            if (step >= 0) hero.curAction = new HeroAction.Move(step);
            return;
        }

        if (invulnerable != null) {
            int step = retreatStep(hero, invulnerable.pos, safe);
            if (step >= 0) hero.curAction = new HeroAction.Move(step);
            return;
        }

        if (attackable != null) {
            hero.curAction = new HeroAction.Attack(attackable);
            return;
        }

        if (approachStep >= 0) {
            hero.curAction = new HeroAction.Move(approachStep);
            return;
        }

        if (attackerTurns > 0) {
            attackerTurns--;
            if (lastAttacker != null && lastAttacker.isAlive()
                    && lastAttacker.alignment != Char.Alignment.ALLY) {
                PathFinder.Path path = pathNear(hero, attackerCell, safe);
                if (path != null && !path.isEmpty()) {
                    hero.curAction = new HeroAction.Move(path.getFirst());
                    return;
                }
            }
        }

        int step = roamStep(hero, safe);
        if (step >= 0) hero.curAction = new HeroAction.Move(step);
    }

    private static void resetForLevel(Hero hero) {
        if (currentHero == hero && currentLevel == Dungeon.level) return;
        currentHero = hero;
        currentLevel = Dungeon.level;
        roamCell = -1;
        lastAttacker = null;
        attackerCell = -1;
        attackerTurns = 0;
    }

    private static boolean[] safeCells(Hero hero) {
        Level level = Dungeon.level;
        boolean[] safe = Dungeon.findPassable(hero, level.passable, hero.fieldOfView, true).clone();

        for (int cell = 0; cell < safe.length; cell++) {
            boolean known = level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell];
            boolean traversable = safe[cell]
                    || (known && BlastMovement.canPhase(cell))
                    || (BlastMovement.canPassSheep(cell) && level.passable[cell]);
            safe[cell] = known && level.insideMap(cell) && traversable
                    && !level.pit[cell]
                    && level.traps.get(cell) == null
                    && (level.plants.get(cell) == null || level.map[cell] == Terrain.HIGH_GRASS);
        }

        for (Char character : Actor.chars()) {
            if (character != hero && !BlastMovement.canPassSheep(character.pos)) {
                safe[character.pos] = false;
            }
        }
        safe[hero.pos] = true;
        return safe;
    }

    /** The occupied target cell may be blocked; approach a reachable neighbour. */
    private static PathFinder.Path pathNear(Hero hero, int target, boolean[] safe) {
        PathFinder.Path shortest = null;
        if (safe[target] && target != hero.pos) {
            shortest = PathFinder.find(hero.pos, target, safe);
        }
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = target + offset;
            if (!Dungeon.level.insideMap(cell) || !safe[cell] || cell == hero.pos) continue;
            PathFinder.Path path = PathFinder.find(hero.pos, cell, safe);
            if (path != null && !path.isEmpty()
                    && (shortest == null || path.size() < shortest.size())) {
                shortest = path;
            }
        }
        return shortest;
    }

    /** Retreat without entering traps, chasms or non-grass plants. */
    private static int retreatStep(Hero hero, int threat, boolean[] safe) {
        int distance = Dungeon.level.distance(hero.pos, threat);
        int step = -1;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (!Dungeon.level.insideMap(cell) || !safe[cell]) continue;
            int nextDistance = Dungeon.level.distance(cell, threat);
            if (nextDistance > distance) {
                distance = nextDistance;
                step = cell;
            }
        }
        if (step >= 0) return step;

        // A wall can make the first escape step sideways; find a longer escape.
        PathFinder.buildDistanceMap(hero.pos, safe);
        int goal = -1;
        int farthest = Dungeon.level.distance(hero.pos, threat);
        for (int cell = 0; cell < safe.length; cell++) {
            if (!safe[cell] || PathFinder.distance[cell] == Integer.MAX_VALUE) continue;
            int nextDistance = Dungeon.level.distance(cell, threat);
            if (nextDistance > farthest) {
                farthest = nextDistance;
                goal = cell;
            }
        }
        if (goal < 0) return -1;
        PathFinder.Path path = PathFinder.find(hero.pos, goal, safe);
        return path == null || path.isEmpty() ? -1 : path.getFirst();
    }

    private static int roamStep(Hero hero, boolean[] safe) {
        if (roamCell >= 0 && roamCell != hero.pos && safe[roamCell]) {
            PathFinder.Path path = PathFinder.find(hero.pos, roamCell, safe);
            if (path != null && !path.isEmpty()) return path.getFirst();
        }

        // Random reachable destinations, favouring the edge of explored terrain.
        PathFinder.buildDistanceMap(hero.pos, safe);
        int frontier = -1;
        int frontierCount = 0;
        int distant = -1;
        int distantCount = 0;
        int nearby = -1;
        int nearbyCount = 0;
        Level level = Dungeon.level;
        for (int cell = 0; cell < level.length(); cell++) {
            if (!safe[cell] || cell == hero.pos
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) continue;

            if (Random.Int(++nearbyCount) == 0) nearby = cell;
            if (PathFinder.distance[cell] < 3) continue;
            if (Random.Int(++distantCount) == 0) distant = cell;

            for (int offset : PathFinder.NEIGHBOURS8) {
                int adjacent = cell + offset;
                if (level.insideMap(adjacent) && !level.visited[adjacent]
                        && !level.mapped[adjacent] && !hero.fieldOfView[adjacent]) {
                    if (Random.Int(++frontierCount) == 0) frontier = cell;
                    break;
                }
            }
        }

        roamCell = frontier >= 0 ? frontier : distant >= 0 ? distant : nearby;
        if (roamCell < 0) return -1;
        PathFinder.Path path = PathFinder.find(hero.pos, roamCell, safe);
        return path == null || path.isEmpty() ? -1 : path.getFirst();
    }
}
