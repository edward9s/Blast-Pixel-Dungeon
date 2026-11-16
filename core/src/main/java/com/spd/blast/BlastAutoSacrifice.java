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
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SacrificeRoom;
import com.watabou.utils.Point;

/** Keep the sacrifice objective active while recruiting and luring melee enemies. */
public final class BlastAutoSacrifice {

    enum Action { NONE, QUEUED, WAIT }

    private static final int MAX_STALLED_TURNS = 3;
    private static final float MAX_WAIT_TICKS = 12f;
    private static final float REPOSITION_TICKS = 8f;
    private static final BlastAutoRoaming outsideRoaming = new BlastAutoRoaming();

    private static Hero currentHero;
    private static Level currentLevel;
    private static Mob trackedEnemy;
    private static int previousDistance;
    private static int stalledTurns;
    private static float repositionUntil;
    private static float waitingSince = Float.NaN;
    private static boolean patrollingOutside;

    private BlastAutoSacrifice() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        trackedEnemy = null;
        previousDistance = Integer.MAX_VALUE;
        stalledTurns = 0;
        repositionUntil = 0f;
        waitingSince = Float.NaN;
        patrollingOutside = false;
        outsideRoaming.reset();
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
        if (!(level instanceof RegularLevel)) return Action.NONE;
        SacrificeRoom room = ((RegularLevel) level).room(SacrificeRoom.class);
        if (room == null || room.entrance() == null) return Action.NONE;

        // Visible ranged opponents and thieves remain urgent combat exceptions.
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

        // Attack only when the enemy has been marked within the flame aura.
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
            patrollingOutside = false;
            outsideRoaming.clearDestination();
            return Action.QUEUED;
        }

        // A stalled lure changes tactics instead of allowing ordinary attacks.
        if (Actor.now() < repositionUntil) {
            return searchOutside(hero, room, safe, distances);
        }

        // A following enemy must be brought back regardless of its distance.
        int site = -1;
        Mob target = null;
        int bestScore = Integer.MAX_VALUE;
        for (int cell = 0; cell < fire.cur.length; cell++) {
            if (fire.cur[cell] <= 0 || !safe[cell]
                    || distances[cell] == Integer.MAX_VALUE
                    || !(level.visited[cell] || hero.fieldOfView[cell])) continue;
            for (Mob mob : level.mobs) {
                if (!eligible(hero, mob) || !shouldLure(
                        mob.state == mob.HUNTING && mob.isTargeting(hero),
                        nearFlame(fire.cur, level.width(), mob.pos))) continue;
                int score = distances[cell] + level.distance(cell, mob.pos);
                if (score < bestScore) {
                    bestScore = score;
                    site = cell;
                    target = mob;
                }
            }
        }

        if (site < 0) {
            trackedEnemy = null;
            waitingSince = Float.NaN;
            // If the altar cannot be reached, defend against adjacent attackers.
            if (immediateMeleeThreat) return Action.NONE;
            Action approach = approachUnengagedEnemy(hero, fire.cur, safe, distances);
            return approach != Action.NONE ? approach : searchOutside(hero, room, safe, distances);
        }

        if (hero.pos != site) {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(site, safe, distances));
            trackedEnemy = null;
            stalledTurns = 0;
            waitingSince = Float.NaN;
            patrollingOutside = false;
            outsideRoaming.clearDestination();
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
        if (shouldReposition(Actor.now() - waitingSince, stalledTurns)) {
            trackedEnemy = null;
            waitingSince = Float.NaN;
            repositionUntil = Actor.now() + REPOSITION_TICKS;
            patrollingOutside = false;
            outsideRoaming.clearDestination();
            return searchOutside(hero, room, safe, distances);
        }

        hero.spendAndNext(Actor.TICK);
        return Action.WAIT;
    }

    /** Return through the room entrance, then patrol known exterior terrain. */
    private static Action searchOutside(Hero hero, SacrificeRoom room,
                                        boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        Point entrance = room.entrance();
        int exitCell = outsideEntrance(entrance.x, entrance.y,
                room.left, room.top, room.right, room.bottom,
                level.width(), level.height());
        if (exitCell < 0 || !safe[exitCell]
                || distances[exitCell] == Integer.MAX_VALUE) return Action.NONE;

        if (!patrollingOutside) {
            if (hero.pos != exitCell) {
                hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(exitCell, safe, distances));
                return Action.QUEUED;
            }
            patrollingOutside = true;
            outsideRoaming.clearDestination();
        }

        boolean[] exterior = safe.clone();
        for (int cell = 0; cell < exterior.length; cell++) {
            if (insideRoom(cell, level.width(), room.left, room.top, room.right, room.bottom)) {
                exterior[cell] = false;
            }
        }
        int destination = outsideRoaming.chooseDestination(hero.pos, level.width(), exterior,
                distances, level.visited, level.mapped, hero.fieldOfView, level.solid);
        if (destination >= 0) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(destination, safe, distances));
            return Action.QUEUED;
        }
        hero.spendAndNext(Actor.TICK);
        return Action.WAIT;
    }

    /** Draw the attention of a visible wandering melee enemy without striking it. */
    private static Action approachUnengagedEnemy(Hero hero, int[] flames,
                                                  boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        Mob target = null;
        int nearest = Integer.MAX_VALUE;
        for (Mob mob : level.mobs) {
            if (!eligible(hero, mob) || shouldLure(
                    mob.state == mob.HUNTING && mob.isTargeting(hero),
                    nearFlame(flames, level.width(), mob.pos))) continue;
            int distance = level.distance(hero.pos, mob.pos);
            if (distance > 2 && distance < nearest) {
                nearest = distance;
                target = mob;
            }
        }
        if (target == null) return Action.NONE;
        int destination = -1;
        int shortest = Integer.MAX_VALUE;
        for (int cell = 0; cell < safe.length; cell++) {
            if (!safe[cell] || distances[cell] <= 0
                    || distances[cell] == Integer.MAX_VALUE
                    || level.distance(cell, target.pos) != 2) continue;
            if (distances[cell] < shortest) {
                destination = cell;
                shortest = distances[cell];
            }
        }
        if (destination < 0) return Action.NONE;
        hero.curAction = new HeroAction.Move(
                BlastAutoPlay.firstStep(destination, safe, distances));
        return Action.QUEUED;
    }

    /** Return the cell immediately outside the single generated room entrance. */
    static int outsideEntrance(int x, int y, int left, int top, int right, int bottom,
                                int width, int height) {
        if (width <= 0 || height <= 0 || left >= right || top >= bottom) {
            throw new IllegalArgumentException("Invalid sacrifice room bounds");
        }
        if (x == left && y > top && y < bottom) x--;
        else if (x == right && y > top && y < bottom) x++;
        else if (y == top && x > left && x < right) y--;
        else if (y == bottom && x > left && x < right) y++;
        else throw new IllegalArgumentException("Invalid sacrifice room entrance");
        return x >= 0 && x < width && y >= 0 && y < height ? x + y * width : -1;
    }

    static boolean insideRoom(int cell, int width, int left, int top, int right, int bottom) {
        int x = cell % width;
        int y = cell / width;
        return x > left && x < right && y > top && y < bottom;
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

    static boolean shouldReposition(float waited, int stalledTurns) {
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
