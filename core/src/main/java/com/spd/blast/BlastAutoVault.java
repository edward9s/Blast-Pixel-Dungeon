package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.VaultFlameTraps;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.BlobImmunity;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfPurity;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultFlamesTreasureRoom;
import com.watabou.utils.PathFinder;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultLaser;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultSentry;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.VaultLevel;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.ConeAOE;

/** One-turn vault machinery forecast; never assumes test invulnerability protects the hero. */
final class BlastAutoVault {
    private BlastAutoVault() {}

    /**
     * Find a safe staging tile next to a temporarily blocked route to unexplored
     * floor. A caller at the staging tile must advance one turn and re-plan.
     */
    static int stagingCell(Hero hero, Level level, boolean[] safe, int[] distances) {
        if (!(level instanceof VaultLevel)) return -1;
        boolean[] danger = imminent(hero, level);
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

    /**
     * Find a route across temporarily active vents without walking into fire.
     * A blocked first step tells the caller to wait and retry next turn.
     */
    static int nextFlameStep(Hero hero, Level level, int goal, boolean[] safe) {
        if (!(level instanceof VaultLevel) || !level.insideMap(goal)) return -1;
        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        if (flames == null || flames.cur == null || flames.cur.length != level.length())
            return -1;

        boolean[] traversable = safe.clone();
        for (int cell = 0; cell < traversable.length; cell++) {
            if (flames.cur[cell] > 0 && level.insideMap(cell) && level.passable[cell]
                    && !level.pit[cell] && (level.visited[cell] || level.mapped[cell]
                    || hero.fieldOfView[cell])
                    && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(cell) == null) {
                traversable[cell] = true;
            }
        }
        if (!traversable[goal]) return -1;
        PathFinder.buildDistanceMap(goal, traversable);
        int[] remaining = PathFinder.distance;
        if (remaining[hero.pos] == Integer.MAX_VALUE) return -1;
        int step = -1;
        int shortest = remaining[hero.pos];
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (!level.insideMap(cell) || !traversable[cell]
                    || remaining[cell] >= shortest) continue;
            shortest = remaining[cell];
            step = cell;
        }
        return step;
    }

    static boolean waitForFlames(Hero hero, Level level, boolean[] safe) {
        if (!(level instanceof VaultLevel)) return false;
        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        if (flames == null || flames.cur == null || !safe[hero.pos]) return false;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (!level.insideMap(cell) || safe[cell] || !level.passable[cell]
                    || level.pit[cell] || flames.cur[cell] <= 0) continue;
            return true;
        }
        return false;
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

    static boolean[] imminent(Hero hero, Level level) {
        boolean[] danger = new boolean[level.length()];
        if (!(level instanceof VaultLevel)) return danger;

        VaultFlameTraps flames = (VaultFlameTraps) level.blobs.get(VaultFlameTraps.class);
        if (flames != null) {
            if (flames.cur == null || flames.cur.length != danger.length
                    || (flames.afterTriggerCooldowns != null
                    && (flames.afterTriggerCooldowns.length != danger.length
                    || flames.curCooldowns == null
                    || flames.curCooldowns.length != danger.length)))
                throw new IllegalStateException("Invalid vault flame grid");
            for (int cell = 0; cell < danger.length; cell++) {
                // The current small flame becomes a damaging large flame after the move.
                // An unlit vent only produces its harmless warning flame next turn.
                if (hero.buff(BlobImmunity.class) == null && flames.cur[cell] > 0)
                    danger[cell] = true;
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
