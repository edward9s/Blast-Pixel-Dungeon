package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.VaultFlameTraps;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.BlobImmunity;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfPurity;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultFlamesTreasureRoom;
import com.watabou.utils.PathFinder;

import java.util.Arrays;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultLaser;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultSentry;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.VaultLevel;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.ConeAOE;

/** One-turn vault machinery forecast; never assumes test invulnerability protects the hero. */
final class BlastAutoVault {
    private static final int FLAME_LOOKAHEAD = 16;

    private BlastAutoVault() {}

    /**
     * Find a safe staging tile next to a temporarily blocked route to unexplored
     * floor. A caller at the staging tile must advance one turn and re-plan.
     */
    static int stagingCell(Hero hero, Level level, boolean[] safe, int[] distances) {
        if (!(level instanceof VaultLevel)) return -1;
        boolean[] danger = imminent(hero, level, false);
        int best = -1;
        int shortest = Integer.MAX_VALUE;
        for (int cell = 0; cell < danger.length; cell++) {
            if (!danger[cell] || level.solid[cell] || level.pit[cell]) continue;
            boolean unexploredBeyond = false;
            for (int offset : PathFinder.NEIGHBOURS8) {
                int next = cell + offset;
                if (level.insideMap(next) && !level.solid[next] && !level.pit[next]
                        && !level.visited[next] && !level.mapped[next]
                        && !hero.fieldOfView[next]) {
                    unexploredBeyond = true;
                    break;
                }
            }
            if (!unexploredBeyond) continue;
            for (int offset : PathFinder.NEIGHBOURS8) {
                int stand = cell + offset;
                if (!level.insideMap(stand) || !safe[stand]
                        || distances[stand] == Integer.MAX_VALUE) continue;
                if (distances[stand] < shortest) {
                    best = stand;
                    shortest = distances[stand];
                }
            }
        }
        return best;
    }

    /**
     * A laser's current ray blocks only the next action. Wait on a safe tile
     * when the ray prevents reaching otherwise traversable known floor.
     */
    static boolean waitForLaser(Hero hero, Level level, boolean[] safe,
                                int[] distances) {
        if (!(level instanceof VaultLevel) || !safe[hero.pos]) return false;
        boolean[] blocked = new boolean[level.length()];
        boolean firing = false;
        for (Mob mob : level.mobs) {
            if (!(mob instanceof VaultLaser) || !mob.isAlive()) continue;
            VaultLaser laser = (VaultLaser) mob;
            if (laser.curCooldown > 1) continue;
            if (laser.laserDirs == null || laser.laserDirs.length == 0
                    || laser.laserDirIdx < 0 || laser.laserDirIdx >= laser.laserDirs.length)
                throw new IllegalStateException("Invalid vault laser sequence");
            Ballistica beam = new Ballistica(laser.pos,
                    laser.laserDirs[laser.laserDirIdx], Ballistica.STOP_SOLID);
            for (int cell : beam.subPath(1, beam.dist)) blocked[cell] = true;
            firing = true;
        }
        if (!firing) return false;

        // A ray must actually separate the hero from a known, passable goal.
        // Do not wait merely because an unrelated laser fires elsewhere.
        boolean[] open = safe.clone();
        for (int cell = 0; cell < open.length; cell++) {
            if (blocked[cell] && !level.solid[cell] && !level.pit[cell]
                    && (level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell])
                    && level.passable[cell]) open[cell] = true;
        }
        PathFinder.buildDistanceMap(hero.pos, open);
        for (int cell = 0; cell < open.length; cell++) {
            if (!blocked[cell] || !open[cell] || distances[cell] != Integer.MAX_VALUE
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) continue;
            return true;
        }
        return false;
    }

    private static boolean periodic(VaultFlameTraps flames, int cell) {
        return flames.afterTriggerCooldowns != null && flames.afterTriggerCooldowns[cell] > 0
                && flames.afterTriggerCooldowns[cell] > flames.triggersAfterCooldown[cell];
    }

    static boolean nearPeriodicFlames(Hero hero, Level level) {
        if (!(level instanceof VaultLevel) || hero.isImmune(VaultFlameTraps.class)) return false;
        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        if (flames == null) return false;
        for (int offset : PathFinder.NEIGHBOURS9) {
            int cell = hero.pos + offset;
            if (level.insideMap(cell) && periodic(flames, cell)) return true;
        }
        return false;
    }

    static void excludeVentDestinations(Level level, int[] turns) {
        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        for (int cell = 0; cell < turns.length; cell++) {
            if (periodic(flames, cell)) turns[cell] = Integer.MAX_VALUE;
        }
    }

    static final class FlameRoutes {
        final int[] turns;
        final int[] firstStep;

        FlameRoutes(int length) {
            turns = new int[length];
            firstStep = new int[length];
            Arrays.fill(turns, Integer.MAX_VALUE);
            Arrays.fill(firstStep, -1);
        }

        int toward(int origin, int goal, boolean[] passable) {
            if (firstStep[goal] >= 0) return firstStep[goal];
            if (!passable[goal]) return -1;
            PathFinder.buildDistanceMap(goal, passable);
            int closest = -1;
            int remaining = PathFinder.distance[origin];
            for (int cell = 0; cell < turns.length; cell++) {
                if (turns[cell] == 0 || firstStep[cell] < 0) continue;
                int distance = PathFinder.distance[cell];
                if (distance < remaining || (distance == remaining && closest >= 0
                        && turns[cell] < turns[closest])) {
                    closest = cell;
                    remaining = distance;
                }
            }
            return closest < 0 ? -1 : firstStep[closest];
        }
    }

    /** Each edge is a move or a wait, checked against the fire at that time. */
    static FlameRoutes flameRoutes(Hero hero, Level level, boolean[] passable) {
        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        int length = passable.length;
        int moveTicks = Math.max(1, (int) Math.ceil(1f / hero.speed()));
        FlameRoutes routes = new FlameRoutes(length);
        routes.turns[hero.pos] = 0;
        routes.firstStep[hero.pos] = hero.pos;

        boolean[][] burning = new boolean[FLAME_LOOKAHEAD][length];
        for (int cell = 0; cell < length; cell++) {
            if (!passable[cell] || !periodic(flames, cell)) continue;
            int fire = flames.cur[cell];
            int cooldown = flames.curCooldowns[cell];
            int period = flames.afterTriggerCooldowns[cell];
            for (int tick = 0; tick < FLAME_LOOKAHEAD; tick++) {
                burning[tick][cell] = fire > 0;
                if (fire > 0) fire--;
                if (cooldown <= 0) cooldown = period;
                if (--cooldown == 0) fire += flames.triggersAfterCooldown[cell];
            }
        }

        int[] current = new int[length];
        int[] next = new int[length];
        Arrays.fill(current, -1);
        current[hero.pos] = hero.pos;
        for (int tick = 0; tick <= FLAME_LOOKAHEAD - moveTicks; tick += moveTicks) {
            Arrays.fill(next, -1);
            for (int from = 0; from < length; from++) {
                if (current[from] < 0) continue;
                for (int offset : PathFinder.NEIGHBOURS9) {
                    int to = from + offset;
                    if (!level.insideMap(to) || !passable[to] || next[to] >= 0
                            || !safeForMove(burning, to, tick, moveTicks)) continue;
                    int first = tick == 0 ? to : current[from];
                    next[to] = first;
                    if (routes.turns[to] == Integer.MAX_VALUE) {
                        routes.turns[to] = tick + moveTicks;
                        routes.firstStep[to] = first;
                    }
                }
            }
            int[] previous = current;
            current = next;
            next = previous;
        }
        return routes;
    }

    private static boolean safeForMove(boolean[][] burning, int cell, int tick, int moveTicks) {
        for (int i = tick; i < tick + moveTicks; i++) {
            if (burning[i][cell]) return false;
        }
        return true;
    }

    static boolean drinkPurity(Hero hero, Level level) {
        if (!(level instanceof RegularLevel) || hero.buff(BlobImmunity.class) != null
                || hero.belongings.lostInventory()) return false;
        VaultFlamesTreasureRoom room = (VaultFlamesTreasureRoom)
                ((RegularLevel) level).room(VaultFlamesTreasureRoom.class);
        if (room == null || room.entrance() == null) return false;
        int entrance = level.pointToCell(room.entrance());
        if (level.distance(hero.pos, entrance) > 1) return false;
        PotionOfPurity potion = hero.belongings.getItem(PotionOfPurity.class);
        if (potion == null || potion.quantity() <= 0 || !potion.isKnown()) return false;
        potion.execute(hero, Potion.AC_DRINK);
        return true;
    }

    static boolean[] imminent(Hero hero, Level level, boolean planFlames) {
        boolean[] danger = new boolean[level.length()];
        if (!(level instanceof VaultLevel)) return danger;

        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        if (flames != null && !hero.isImmune(VaultFlameTraps.class)) {
            if (flames.cur == null || flames.cur.length != danger.length
                    || (flames.afterTriggerCooldowns != null
                    && (flames.afterTriggerCooldowns.length != danger.length
                    || flames.curCooldowns == null
                    || flames.curCooldowns.length != danger.length)))
                throw new IllegalStateException("Invalid vault flame grid");
            for (int cell = 0; cell < danger.length; cell++) {
                // Leave periodic vents to the timed route rather than ordinary movement.
                danger[cell] = periodic(flames, cell) ? !planFlames : flames.cur[cell] > 0;
            }
        }

        for (Mob mob : level.mobs) {
            if (!mob.isAlive()) continue;
            if (mob instanceof VaultLaser) {
                VaultLaser laser = (VaultLaser) mob;
                if (laser.curCooldown > 1) continue;
                if (laser.laserDirs == null || laser.laserDirs.length == 0)
                    throw new IllegalStateException("Vault laser has no directions");
                Ballistica beam = new Ballistica(laser.pos,
                        laser.laserDirs[laser.laserDirIdx], Ballistica.STOP_SOLID);
                for (int cell : beam.subPath(1, beam.dist)) danger[cell] = true;
            } else if (mob instanceof VaultSentry) {
                VaultSentry sentry = (VaultSentry) mob;
                if (sentry.curCooldown > 1) continue;
                if (sentry.scanDirs == null || sentry.scanDirs.length == 0)
                    throw new IllegalStateException("Vault sentry has no scan directions");
                for (int direction : sentry.scanDirs[sentry.scanDirIdx]) {
                    Ballistica aim = new Ballistica(sentry.pos, direction, Ballistica.WONT_STOP);
                    ConeAOE scan = new ConeAOE(aim, sentry.scanLength, sentry.scanWidth,
                            Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);
                    if (scan.cells.isEmpty() && aim.path.size() >= 2)
                        scan.cells.add(aim.path.get(1));
                    for (int cell : scan.cells) {
                        // When the sentry FOV has not been initialized, conservatively block the cone.
                        danger[cell] = true;
                    }
                }
            }
        }
        return danger;
    }
}
