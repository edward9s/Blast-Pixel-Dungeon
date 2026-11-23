package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ArcaneBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ShrapnelBomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretSummoningRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.SummoningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.watabou.utils.PathFinder;

/** Trigger summoning traps when special bombs are registered and the Hero is indestructible. */
final class BlastAutoSummoningRoom {
    private static int pendingCell = -1;
    private static int waveWait;

    private BlastAutoSummoningRoom() {}

    static void reset() {
        pendingCell = -1;
        waveWait = 0;
    }

    static boolean summoningTerrain(int terrain, boolean activeSummoningTrap) {
        return activeSummoningTrap
                && (terrain == Terrain.SECRET_TRAP || terrain == Terrain.TRAP);
    }

    /** Only registered special bombs and an active indestructibility buff qualify. */
    static boolean canTriggerWave(boolean registeredArcane, boolean registeredShrapnel,
                                  boolean indestructible) {
        return indestructible && (registeredArcane || registeredShrapnel);
    }

    private static SecretSummoningRoom knownRoom(Level level, Hero hero) {
        if (!(level instanceof RegularLevel)) return null;
        SecretSummoningRoom room = (SecretSummoningRoom)
                ((RegularLevel) level).room(SecretSummoningRoom.class);
        if (room == null || room.entrance() == null) return null;
        int door = level.pointToCell(room.entrance());
        return level.insideMap(door)
                && (level.visited[door] || level.mapped[door] || hero.fieldOfView[door])
                ? room : null;
    }

    private static boolean inside(Level level, SecretSummoningRoom room, int cell) {
        return room != null && level.insideMap(cell)
                && room.inside(level.cellToPoint(cell));
    }

    private static boolean hasHostiles(Level level, SecretSummoningRoom room) {
        for (Mob mob : level.mobs) {
            if (mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                    && inside(level, room, mob.pos)) return true;
        }
        return false;
    }

    private static boolean isActiveSummoning(Level level, int cell) {
        Trap trap = level.traps.get(cell);
        return summoningTerrain(level.map[cell],
                trap instanceof SummoningTrap && trap.active);
    }

    /** Delay may place monsters two ticks after the SummoningTrap is pressed. */
    static boolean awaitWave(Hero hero) {
        if (pendingCell < 0) return false;
        Level level = Dungeon.level;
        SecretSummoningRoom room = knownRoom(level, hero);
        if (room == null || hero.pos != pendingCell) {
            pendingCell = -1;
            return false;
        }
        if (hasHostiles(level, room)) {
            pendingCell = -1;
            return false;
        }
        if (++waveWait > 3) {
            pendingCell = -1;
            return false;
        }
        hero.spendAndNext(Actor.TICK);
        return true;
    }

    static BlastAutoSpecialRooms.Action chooseAction(Hero hero, boolean[] safe,
            int[] distances, Blob[] hazards, boolean[] specialBarriers) {
        Level level = Dungeon.level;
        SecretSummoningRoom room = knownRoom(level, hero);
        if (room == null || !BlastSettings.testAutoPlay()
                || hero.belongings.lostInventory())
            return BlastAutoSpecialRooms.Action.NONE;
        if (safe.length != level.length() || distances.length != level.length()
                || specialBarriers.length != level.length())
            throw new IllegalArgumentException("Invalid secret summoning room maps");

        BlastBombManiac maniac = BlastBombManiac.current();
        boolean indestructible = hero.buff(BlastIndestructible.class) != null
                || hero.buff(BlastTestBuffs.Indestructible.class) != null;
        if (maniac == null
                || !canTriggerWave(maniac.typeIndex(ArcaneBomb.class) >= 0,
                        maniac.typeIndex(ShrapnelBomb.class) >= 0, indestructible)
                || hasHostiles(level, room) || pendingCell >= 0)
            return BlastAutoSpecialRooms.Action.NONE;

        boolean inside = inside(level, room, hero.pos);
        int door = level.pointToCell(room.entrance());
        boolean[] path = safe.clone();
        for (int y = room.top + 1; y < room.bottom; y++) {
            for (int x = room.left + 1; x < room.right; x++) {
                int cell = x + y * level.width();
                if (!isActiveSummoning(level, cell)
                        || !(level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell])
                        || specialBarriers[cell]
                        || BlastAutoEnvironment.hazardousAt(cell, hazards)
                        || AutoPlayPlantSafety.isHarmful(level.plants.get(cell))
                        || Actor.findChar(cell) != null) continue;
                path[cell] = true;
            }
        }
        // Outside the room, plan from its entrance before claiming control.
        // A cleared room has no actionable trap and must be left to roaming.
        int origin = inside ? hero.pos : door;
        PathFinder.buildDistanceMap(origin, path);
        int[] crossing = PathFinder.distance.clone();
        int best = -1, steps = Integer.MAX_VALUE;
        for (int y = room.top + 1; y < room.bottom; y++) {
            for (int x = room.left + 1; x < room.right; x++) {
                int cell = x + y * level.width();
                if (!isActiveSummoning(level, cell) || !path[cell]
                        || crossing[cell] == Integer.MAX_VALUE
                        || crossing[cell] >= steps) continue;
                steps = crossing[cell];
                best = cell;
            }
        }
        if (best < 0 || hero.rooted) return BlastAutoSpecialRooms.Action.NONE;
        if (!inside && hero.pos != door)
            return BlastAutoSpecialRooms.approachEntrance(
                    hero, best, door, safe, distances);
        int step = BlastAutoPlay.firstStep(best, path, crossing);
        if (isActiveSummoning(level, step)) {
            pendingCell = step;
            waveWait = 0;
        }
        BlastLog.debug("auto-summon action=trigger target=" + best + " step=" + step);
        hero.curAction = new HeroAction.Move(step);
        return BlastAutoSpecialRooms.Action.MOVE;
    }
}
