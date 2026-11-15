package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SacrificialFire;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.PathFinder;

/** Lure pursuing enemies into SacrificialFire's marking area before attacking. */
public final class BlastAutoSacrifice {

    enum Action { NONE, MOVE, WAIT }

    private static final int MAX_FIRE_ROUTE = 6;
    private static final int MAX_ENEMY_DISTANCE = 7;
    private static final int MAX_STALLED_TURNS = 3;
    private static final float RETRY_INTERVAL = 8f;

    private static Hero currentHero;
    private static Level currentLevel;
    private static Mob trackedEnemy;
    private static int previousDistance;
    private static int stalledTurns;
    private static float retryAfter;

    private BlastAutoSacrifice() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        trackedEnemy = null;
        previousDistance = Integer.MAX_VALUE;
        stalledTurns = 0;
        retryAfter = 0f;
    }

    /**
     * Uses the existing safe-cell and distance maps. WAIT has already advanced
     * the actor, MOVE has queued a normal HeroAction; NONE keeps original combat.
     */
    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (currentHero != hero || currentLevel != level) {
            reset();
            currentHero = hero;
            currentLevel = level;
        }
        if (hero.rooted || Actor.now() < retryAfter) return Action.NONE;

        SacrificialFire fire = (SacrificialFire) level.blobs.get(SacrificialFire.class);
        if (fire == null || fire.volume <= 0) return Action.NONE;
        if (fire.cur == null || fire.cur.length != level.length()
                || safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalStateException("Invalid sacrificial fire navigation state");
        }

        // A marked enemy within one tile of active flame is an eligible sacrifice.
        // Do not attack unmarked enemies: the marking blob must act first.
        Mob attackable = null;
        int attackDistance = Integer.MAX_VALUE;
        for (Mob mob : level.mobs) {
            if (!eligible(hero, mob) || !nearFlame(level, fire.cur, mob.pos)
                    || mob.buff(SacrificialFire.Marked.class) == null
                    || !hero.canAttack(mob)) continue;
            int distance = level.distance(hero.pos, mob.pos);
            if (distance < attackDistance) {
                attackable = mob;
                attackDistance = distance;
            }
        }
        if (attackable != null) {
            hero.curAction = new HeroAction.Attack(attackable);
            trackedEnemy = null;
            return Action.MOVE;
        }

        // Pick a reachable fire cell close to both hero and a pursuing enemy.
        // No separate path search is needed.
        int site = -1;
        Mob target = null;
        int bestScore = Integer.MAX_VALUE;
        for (int cell = 0; cell < fire.cur.length; cell++) {
            if (fire.cur[cell] <= 0 || !safe[cell]
                    || distances[cell] == Integer.MAX_VALUE
                    || distances[cell] > MAX_FIRE_ROUTE
                    || !(level.visited[cell] || hero.fieldOfView[cell])) continue;
            for (Mob mob : level.mobs) {
                if (!eligible(hero, mob) || !canLure(hero, mob, level, fire.cur)) continue;
                int enemyDistance = level.distance(cell, mob.pos);
                if (enemyDistance > MAX_ENEMY_DISTANCE) continue;
                int score = distances[cell] + enemyDistance;
                if (score < bestScore) {
                    bestScore = score;
                    site = cell;
                    target = mob;
                }
            }
        }
        if (site < 0) return Action.NONE;

        if (hero.pos != site) {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(site, safe, distances));
            trackedEnemy = null;
            stalledTurns = 0;
            return Action.MOVE;
        }

        int distance = level.distance(hero.pos, target.pos);
        if (trackedEnemy != target || distance < previousDistance) {
            stalledTurns = 0;
        } else {
            stalledTurns++;
        }
        trackedEnemy = target;
        previousDistance = distance;
        if (stalledTurns >= MAX_STALLED_TURNS) {
            trackedEnemy = null;
            retryAfter = Actor.now() + RETRY_INTERVAL;
            return Action.NONE;
        }

        // Stay on the fire: melee pursuers stop adjacent, inside the marked area.
        // A bounded wait avoids endless idling against stationary ranged mobs.
        hero.spendAndNext(Actor.TICK);
        return Action.WAIT;
    }

    private static boolean eligible(Hero hero, Mob mob) {
        return mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                && mob.invisible <= 0 && hero.fieldOfView[mob.pos]
                && !hero.isCharmedBy(mob) && !mob.isInvulnerable(Hero.class);
    }

    static boolean canLure(Hero hero, Mob mob, Level level, int[] flame) {
        return (mob.state == mob.HUNTING && mob.isTargeting(hero))
                || nearFlame(level, flame, mob.pos);
    }

    /** SacrificialFire applies Marked to its own tile and all eight neighbors. */
    static boolean nearFlame(Level level, int[] flame, int cell) {
        for (int offset : PathFinder.NEIGHBOURS9) {
            int adjacent = cell + offset;
            if (level.insideMap(adjacent) && flame[adjacent] > 0) return true;
        }
        return false;
    }
}
