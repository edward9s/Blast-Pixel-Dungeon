package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.VaultFlameTraps;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.BlobImmunity;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfPurity;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultCircleScanTreasureRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultFlamesTreasureRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultHardLaserTreasureRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultLaserTreasureRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultManyScansRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultTreasureRoom;
import com.watabou.utils.PathFinder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultLaser;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultSentry;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.VaultLevel;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.ConeAOE;

/** One-turn vault machinery forecast; never assumes test invulnerability protects the hero. */
final class BlastAutoVault {
    private static final int HAZARD_LOOKAHEAD = 16;

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

    private static boolean periodic(VaultFlameTraps flames, int cell) {
        return flames.afterTriggerCooldowns != null && flames.afterTriggerCooldowns[cell] > 0
                && flames.afterTriggerCooldowns[cell] > flames.triggersAfterCooldown[cell];
    }

    static boolean nearTimedHazard(Hero hero, Level level) {
        if (!(level instanceof VaultLevel)) return false;
        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        if (flames != null && !hero.isImmune(VaultFlameTraps.class)) {
            for (int offset : PathFinder.NEIGHBOURS9) {
                int cell = hero.pos + offset;
                if (level.insideMap(cell) && periodic(flames, cell)) return true;
            }
        }
        if (timedTreasureRoom(hero, level) != null) return true;
        for (Mob mob : level.mobs) {
            if (!mob.isAlive()) continue;
            if (mob instanceof VaultSentry) {
                VaultSentry sentry = (VaultSentry) mob;
                if (level.distance(hero.pos, sentry.pos) <= Math.ceil(sentry.scanLength) + 1) return true;
            }
            if (!(mob instanceof VaultLaser)) continue;
            VaultLaser laser = (VaultLaser) mob;
            if (laser.afterShotCooldown == Integer.MAX_VALUE && laser.curCooldown > HAZARD_LOOKAHEAD) continue;
            validateLaser(laser);
            for (int direction : laser.laserDirs) {
                Ballistica beam = laserBeam(laser, direction);
                for (int cell : beam.subPath(1, beam.dist)) {
                    if (level.distance(hero.pos, cell) <= 1) return true;
                }
            }
        }
        return false;
    }

    static boolean imminentAttackAt(Hero hero, Level level) {
        if (!(level instanceof VaultLevel)) return false;
        for (Mob mob : level.mobs) {
            if (!mob.isAlive()) continue;
            if (mob instanceof VaultLaser) {
                VaultLaser laser = (VaultLaser) mob;
                if (laser.curCooldown > 1) continue;
                validateLaser(laser);
                Ballistica beam = laserBeam(laser, laser.laserDirs[laser.laserDirIdx]);
                if (beam.subPath(1, beam.dist).contains(hero.pos)) return true;
            } else if (mob instanceof VaultSentry) {
                VaultSentry sentry = (VaultSentry) mob;
                if (sentry.curCooldown <= 1 && scanCells(sentry, sentry.scanDirIdx).contains(hero.pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static VaultTreasureRoom timedTreasureRoom(Hero hero, Level level) {
        if (!(level instanceof VaultLevel)) return null;
        Room room = ((RegularLevel) level).room(hero.pos);
        if (room instanceof VaultLaserTreasureRoom || room instanceof VaultHardLaserTreasureRoom
                || room instanceof VaultCircleScanTreasureRoom || room instanceof VaultManyScansRoom) {
            return (VaultTreasureRoom) room;
        }
        return null;
    }

    static int timedTreasureGoal(Hero hero, Level level, boolean[] passable) {
        VaultTreasureRoom room = timedTreasureRoom(hero, level);
        if (room == null || room.entrance() == null) return -1;
        int target = -1;
        int nearest = Integer.MAX_VALUE;
        for (Heap heap : level.heaps.valueList()) {
            if (!passable[heap.pos] || !(heap.seen || hero.fieldOfView[heap.pos])
                    || !room.inside(level.cellToPoint(heap.pos))) continue;
            boolean pending = heap.type == Heap.Type.CHEST
                    ? BlastAutoPickup.hasCollectibleLoot(hero, heap)
                    : heap.type == Heap.Type.HEAP && BlastAutoPickup.firstEligibleItem(heap,
                            item -> (BlastAutoPickup.isPickupType(item, BlastAutoPickupPolicy::isInstantPickup)
                                    || BlastAutoEquipment.canPickup(hero, item))
                                    && BlastAutoPickup.canCollect(hero, item,
                                            BlastAutoPickupPolicy::isInstantPickup)) != null;
            int distance = level.distance(hero.pos, heap.pos);
            if (pending && distance < nearest) {
                target = heap.pos;
                nearest = distance;
            }
        }
        return target >= 0 ? target : level.pointToCell(room.entrance());
    }

    private static Ballistica laserBeam(VaultLaser laser, int direction) {
        return new Ballistica(laser.pos, direction, Ballistica.STOP_SOLID);
    }

    private static void validateLaser(VaultLaser laser) {
        if (laser.laserDirs == null || laser.laserDirs.length == 0
                || laser.laserDirIdx < 0 || laser.laserDirIdx >= laser.laserDirs.length) {
            throw new IllegalStateException("Invalid vault laser sequence");
        }
    }

    static void excludeVentDestinations(Hero hero, Level level, int[] turns) {
        if (hero.isImmune(VaultFlameTraps.class)) return;
        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        if (flames == null) return;
        for (int cell = 0; cell < turns.length; cell++) {
            if (periodic(flames, cell)) turns[cell] = Integer.MAX_VALUE;
        }
    }

    static final class TimedRoutes {
        final int[] turns;
        final int[] firstStep;

        TimedRoutes(int length) {
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

    /** Each move or wait is checked against the hazards for the turns it takes. */
    static TimedRoutes timedRoutes(Hero hero, Level level, boolean[] passable) {
        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        int length = passable.length;
        int moveTicks = Math.max(1, (int) Math.ceil(1f / hero.speed()));
        TimedRoutes routes = new TimedRoutes(length);
        routes.turns[hero.pos] = 0;
        routes.firstStep[hero.pos] = hero.pos;

        boolean[][] danger = new boolean[HAZARD_LOOKAHEAD][length];
        addLaserForecast(level, danger);
        addScanForecast(level, danger);
        if (flames != null && !hero.isImmune(VaultFlameTraps.class)) {
            addFlameForecast(flames, passable, danger);
        }

        int[] current = new int[length];
        int[] next = new int[length];
        Arrays.fill(current, -1);
        current[hero.pos] = hero.pos;
        for (int tick = 0; tick <= HAZARD_LOOKAHEAD - moveTicks; tick += moveTicks) {
            Arrays.fill(next, -1);
            for (int from = 0; from < length; from++) {
                if (current[from] < 0) continue;
                for (int offset : PathFinder.NEIGHBOURS9) {
                    int to = from + offset;
                    if (!level.insideMap(to) || !passable[to] || next[to] >= 0
                            || !safeForMove(danger, to, tick, moveTicks)) continue;
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

    private static void addFlameForecast(VaultFlameTraps flames, boolean[] passable,
                                         boolean[][] danger) {
        int length = passable.length;
        for (int cell = 0; cell < length; cell++) {
            if (!passable[cell] || !periodic(flames, cell)) continue;
            int fire = flames.cur[cell];
            int cooldown = flames.curCooldowns[cell];
            int period = flames.afterTriggerCooldowns[cell];
            for (int tick = 0; tick < HAZARD_LOOKAHEAD; tick++) {
                if (fire > 0) danger[tick][cell] = true;
                if (fire > 0) fire--;
                if (cooldown <= 0) cooldown = period;
                if (--cooldown == 0) fire += flames.triggersAfterCooldown[cell];
            }
        }
    }

    private static void addLaserForecast(Level level, boolean[][] danger) {
        for (Mob mob : level.mobs) {
            if (!(mob instanceof VaultLaser) || !mob.isAlive()) continue;
            VaultLaser laser = (VaultLaser) mob;
            if (laser.curCooldown > danger.length) continue;
            validateLaser(laser);
            int cooldown = laser.curCooldown;
            int direction = laser.laserDirIdx;
            int shots = laser.blastShotsFired();
            for (int tick = 0; tick < danger.length; tick++) {
                if (--cooldown > 0) continue;
                Ballistica beam = laserBeam(laser, laser.laserDirs[direction]);
                for (int cell : beam.subPath(1, beam.dist)) danger[tick][cell] = true;
                direction = (direction + 1) % laser.laserDirs.length;
                if (++shots < laser.shotsAfterCooldown) {
                    cooldown = 1;
                } else {
                    shots = 0;
                    cooldown = laser.afterShotCooldown;
                }
            }
        }
    }

    private static void addScanForecast(Level level, boolean[][] danger) {
        for (Mob mob : level.mobs) {
            if (!(mob instanceof VaultSentry) || !mob.isAlive()) continue;
            VaultSentry sentry = (VaultSentry) mob;
            if (sentry.curCooldown > danger.length) continue;
            int cooldown = sentry.curCooldown;
            int direction = sentry.scanDirIdx;
            int scans = sentry.blastScansMade();
            for (int tick = 0; tick < danger.length; tick++) {
                if (--cooldown > 0) continue;
                for (int cell : scanCells(sentry, direction)) danger[tick][cell] = true;
                direction = (direction + 1) % sentry.scanDirs.length;
                if (++scans < sentry.scansAfterCooldown) {
                    cooldown = 1;
                } else {
                    scans = 0;
                    cooldown = sentry.afterScanCooldown;
                }
            }
        }
    }

    private static Set<Integer> scanCells(VaultSentry sentry, int direction) {
        if (sentry.scanDirs == null || direction < 0 || direction >= sentry.scanDirs.length) {
            throw new IllegalStateException("Invalid vault sentry scan sequence");
        }
        Set<Integer> cells = new HashSet<>();
        for (int target : sentry.scanDirs[direction]) {
            Ballistica aim = new Ballistica(sentry.pos, target, Ballistica.WONT_STOP);
            ConeAOE scan = new ConeAOE(aim, sentry.scanLength, sentry.scanWidth,
                    Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);
            if (scan.cells.isEmpty() && aim.path.size() >= 2) scan.cells.add(aim.path.get(1));
            cells.addAll(scan.cells);
        }
        return cells;
    }

    private static boolean safeForMove(boolean[][] danger, int cell, int tick, int moveTicks) {
        for (int i = tick; i < tick + moveTicks; i++) {
            if (danger[i][cell]) return false;
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

    static boolean[] imminent(Hero hero, Level level, boolean planTimedHazards) {
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
                danger[cell] = periodic(flames, cell) ? !planTimedHazards : flames.cur[cell] > 0;
            }
        }

        for (Mob mob : level.mobs) {
            if (!mob.isAlive()) continue;
            if (mob instanceof VaultLaser) {
                VaultLaser laser = (VaultLaser) mob;
                if (planTimedHazards || laser.curCooldown > 1) continue;
                validateLaser(laser);
                Ballistica beam = laserBeam(laser, laser.laserDirs[laser.laserDirIdx]);
                for (int cell : beam.subPath(1, beam.dist)) danger[cell] = true;
            } else if (mob instanceof VaultSentry) {
                VaultSentry sentry = (VaultSentry) mob;
                if (planTimedHazards || sentry.curCooldown > 1) continue;
                for (int cell : scanCells(sentry, sentry.scanDirIdx)) danger[cell] = true;
            }
        }
        return danger;
    }
}
