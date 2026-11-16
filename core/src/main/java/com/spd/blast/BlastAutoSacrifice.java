package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SacrificialFire;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalSpire;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalWisp;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM100;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM200;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DM300;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Elemental;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Eye;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.FungalSentry;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollSapper;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollTrickster;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Shaman;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogDzewa;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.YogFist;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

/** Lure pursuing enemies into SacrificialFire's marking area before attacking. */
public final class BlastAutoSacrifice {

    enum Action { NONE, QUEUED, WAIT }

    private static final int MAX_FIRE_ROUTE = 6;
    private static final int MAX_ENEMY_DISTANCE = 7;
    private static final int MAX_STALLED_TURNS = 3;
    private static final float MAX_WAIT_TICKS = 12f;
    private static final float RETRY_INTERVAL = 8f;

    private static Hero currentHero;
    private static Level currentLevel;
    private static Mob trackedEnemy;
    private static int previousDistance;
    private static int stalledTurns;
    private static float retryAfter;
    private static float waitingSince = Float.NaN;

    private BlastAutoSacrifice() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        trackedEnemy = null;
        previousDistance = Integer.MAX_VALUE;
        stalledTurns = 0;
        retryAfter = 0f;
        waitingSince = Float.NaN;
    }

    /**
     * Uses the existing safe-cell and distance maps. WAIT has already advanced
     * the actor, QUEUED has queued a normal HeroAction; NONE keeps original combat.
     */
    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (currentHero != hero || currentLevel != level) {
            reset();
            currentHero = hero;
            currentLevel = level;
        }
        if (hero.rooted) return Action.NONE;

        SacrificialFire fire = (SacrificialFire) level.blobs.get(SacrificialFire.class);
        if (fire == null || fire.volume <= 0) return Action.NONE;
        if (fire.cur == null || fire.cur.length != level.length()
                || safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalStateException("Invalid sacrificial fire navigation state");
        }

        // Visible ranged enemies bypass the altar. A thief also does, unless
        // an adjacent melee pursuer poses an immediate threat.
        boolean visibleThief = false;
        boolean immediateMeleeThreat = false;
        for (Mob mob : level.mobs) {
            if (!mob.isAlive() || mob.alignment != Char.Alignment.ENEMY
                    || BlastAutoPlay.isProtectedNpc(mob)
                    || mob.invisible > 0 || !hero.fieldOfView[mob.pos]
                    || hero.isCharmedBy(mob) || !BlastAutoPlay.isAwake(mob)) continue;
            if (isRangedEnemy(mob.getClass())) {
                trackedEnemy = null;
                waitingSince = Float.NaN;
                stalledTurns = 0;
                return Action.NONE;
            }
            if (mob instanceof Thief) visibleThief = true;
            if (level.adjacent(hero.pos, mob.pos)
                    && mob.state == mob.HUNTING && mob.isTargeting(hero)) {
                immediateMeleeThreat = true;
            }
        }
        if (visibleThief && !immediateMeleeThreat) {
            trackedEnemy = null;
            waitingSince = Float.NaN;
            stalledTurns = 0;
            return Action.NONE;
        }

        // A marked enemy within one tile of active flame is an eligible sacrifice.
        // Do not attack unmarked enemies: the marking blob must act first.
        Mob attackable = null;
        int attackDistance = Integer.MAX_VALUE;
        for (Mob mob : level.mobs) {
            if (!eligible(hero, mob) || !readyToSacrifice(
                        nearFlame(fire.cur, level.width(), mob.pos),
                        mob.buff(SacrificialFire.Marked.class) != null)
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
            waitingSince = Float.NaN;
            return Action.QUEUED;
        }

        if (Actor.now() < retryAfter) return Action.NONE;

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
                if (!eligible(hero, mob) || !shouldLure(
                        mob.state == mob.HUNTING && mob.isTargeting(hero),
                        nearFlame(fire.cur, level.width(), mob.pos))) continue;
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
            waitingSince = Float.NaN;
            return Action.QUEUED;
        }

        if (Float.isNaN(waitingSince)) waitingSince = Actor.now();
        int distance = level.distance(hero.pos, target.pos);
        if (trackedEnemy != target || distance < previousDistance) {
            stalledTurns = 0;
        } else {
            stalledTurns++;
        }
        trackedEnemy = target;
        previousDistance = distance;
        if (shouldAbandon(Actor.now() - waitingSince, stalledTurns)) {
            trackedEnemy = null;
            waitingSince = Float.NaN;
            retryAfter = Actor.now() + RETRY_INTERVAL;
            return Action.NONE;
        }

        // Stay on the fire: melee pursuers stop adjacent, inside the marked area.
        // A bounded wait avoids endless idling against stationary melee mobs.
        hero.spendAndNext(Actor.TICK);
        return Action.WAIT;
    }

    private static boolean eligible(Hero hero, Mob mob) {
        return mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                && !BlastAutoPlay.isProtectedNpc(mob)
                && mob.invisible <= 0 && hero.fieldOfView[mob.pos]
                && !hero.isCharmedBy(mob) && !mob.isInvulnerable(Hero.class)
                && BlastAutoPlay.isAwake(mob);
    }

    /** Native SPD enemy types with ranged spells, projectiles, or area attacks. */
    static boolean isRangedEnemy(Class<? extends Mob> type) {
        return DM100.class.isAssignableFrom(type)
                || DM200.class.isAssignableFrom(type)
                || DM300.class.isAssignableFrom(type)
                || Warlock.class.isAssignableFrom(type)
                || Shaman.class.isAssignableFrom(type)
                || GnollTrickster.class.isAssignableFrom(type)
                || Scorpio.class.isAssignableFrom(type)
                || Eye.class.isAssignableFrom(type)
                || Elemental.class.isAssignableFrom(type)
                || CrystalWisp.class.isAssignableFrom(type)
                || FungalSentry.class.isAssignableFrom(type)
                || GnollGeomancer.class.isAssignableFrom(type)
                || GnollSapper.class.isAssignableFrom(type)
                || CrystalSpire.class.isAssignableFrom(type)
                || Necromancer.class.isAssignableFrom(type)
                || Tengu.class.isAssignableFrom(type)
                || YogFist.class.isAssignableFrom(type)
                || YogDzewa.class.isAssignableFrom(type);
    }

    static boolean shouldAbandon(float waited, int stalledTurns) {
        return waited >= MAX_WAIT_TICKS || stalledTurns >= MAX_STALLED_TURNS;
    }

    static boolean readyToSacrifice(boolean inFireArea, boolean marked) {
        return inFireArea && marked;
    }

    static boolean shouldLure(boolean huntingHero, boolean inFireArea) {
        return huntingHero || inFireArea;
    }

    /** The Marked aura covers the flame and its eight neighbors, without row wrapping. */
    static boolean nearFlame(int[] flame, int width, int cell) {
        if (width <= 0 || flame.length % width != 0 || cell < 0 || cell >= flame.length) {
            throw new IllegalArgumentException("Invalid sacrificial fire grid");
        }
        int x = cell % width;
        int y = cell / width;
        int height = flame.length / width;
        for (int dy = -1; dy <= 1; dy++) {
            int yy = y + dy;
            if (yy < 0 || yy >= height) continue;
            for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx;
                if (xx >= 0 && xx < width && flame[yy * width + xx] > 0) return true;
            }
        }
        return false;
    }
}
