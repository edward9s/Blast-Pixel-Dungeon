package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ArcaneBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ShrapnelBomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretSummoningRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.SummoningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.watabou.utils.PathFinder;

/** Intentionally trigger a secret summoning room, then use registered special bombs. */
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

    static boolean eligibleBomb(boolean registeredArcane, boolean registeredShrapnel,
                                boolean heldArcane, boolean heldShrapnel) {
        return registeredArcane || registeredShrapnel || heldArcane || heldShrapnel;
    }

    static boolean canBomb(boolean remoteThrow, boolean protectedHero) {
        return remoteThrow || protectedHero;
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

    private static boolean hasBombs(Hero hero, BlastBombManiac maniac) {
        return maniac != null && eligibleBomb(
                maniac.remaining() > 0 && maniac.typeIndex(ArcaneBomb.class) >= 0,
                maniac.remaining() > 0 && maniac.typeIndex(ShrapnelBomb.class) >= 0,
                hero.belongings.getItem(ArcaneBomb.class) != null,
                hero.belongings.getItem(ShrapnelBomb.class) != null);
    }

    /**
     * Inventory bombs must be imbued: Blast's Bomb patch deliberately removes
     * vanilla lit throws. Do this before intentionally summoning any monsters.
     */
    private static boolean prepareBomb(Hero hero, BlastBombManiac maniac) {
        if (maniac.typeIndex(ShrapnelBomb.class) >= 0
                || maniac.typeIndex(ArcaneBomb.class) >= 0) return false;
        Bomb held = hero.belongings.getItem(ShrapnelBomb.class);
        if (held == null) held = hero.belongings.getItem(ArcaneBomb.class);
        if (held == null) return false;
        int selected = maniac.selectedIndex();
        maniac.imbue(held);
        maniac.select(selected);
        BlastLog.debug("auto-summon action=imbue type=" + held.getClass().getSimpleName());
        hero.spendAndNext(Actor.TICK);
        return true;
    }

    /** Prioritize special-bomb bombardment before ordinary combat handles the wave. */
    static boolean bombard(Hero hero) {
        Level level = Dungeon.level;
        SecretSummoningRoom room = knownRoom(level, hero);
        if (room == null || !inside(level, room, hero.pos)
                || !BlastSettings.testAutoPlay() || BlastRemoteBomb.isTimedMode()
                || !hasHostiles(level, room)) return false;
        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0) return false;
        boolean remote = BlastSettings.testRemoteThrow();
        boolean protectedHero = hero.buff(BlastIndestructible.class) != null
                || hero.buff(BlastTestBuffs.Indestructible.class) != null;
        if (!canBomb(remote, protectedHero)) return false;

        int bestCount = 0, bestTarget = -1;
        Class<? extends Bomb> bestType = null;
        @SuppressWarnings("unchecked")
        Class<? extends Bomb>[] types = new Class[]{ArcaneBomb.class, ShrapnelBomb.class};
        for (Class<? extends Bomb> type : types) {
            int index = maniac.typeIndex(type);
            if (index < 0) continue;
            Bomb bomb = maniac.bombAt(index);
            for (Mob target : level.mobs) {
                if (!target.isAlive() || target.alignment != Char.Alignment.ENEMY
                        || !inside(level, room, target.pos) || !hero.fieldOfView[target.pos]
                        || target.invisible > 0 || target.isInvulnerable(Hero.class)) continue;
                int center = remote
                        ? BlastBombTrajectory.throwPos(hero.pos, target.pos) : hero.pos;
                if (!level.insideMap(center) || level.pit[center]) continue;
                boolean[] blast = bomb.blastWarningCells(center);
                if (!BlastAutoBarricade.canDetonate(blast, target.pos,
                        hero.pos, protectedHero)) continue;
                int affected = 0;
                for (Mob mob : level.mobs) {
                    if (mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                            && inside(level, room, mob.pos) && blast[mob.pos]) affected++;
                }
                if (affected > bestCount) {
                    bestCount = affected;
                    bestTarget = target.pos;
                    bestType = type;
                }
            }
        }
        if (bestTarget < 0) return false;
        boolean cast = remote
                ? maniac.blastAutoThrowTypeAndDetonate(bestTarget, bestType)
                : maniac.blastAutoPlaceTypeAndDetonate(bestType);
        if (!cast) return false;
        if (!remote) hero.spendAndNext(Actor.TICK);
        BlastLog.debug("auto-summon action=bomb type=" + bestType.getSimpleName()
                + " target=" + bestTarget + " hit=" + bestCount);
        return true;
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
                || BlastRemoteBomb.isTimedMode() || hero.belongings.lostInventory())
            return BlastAutoSpecialRooms.Action.NONE;
        if (safe.length != level.length() || distances.length != level.length()
                || specialBarriers.length != level.length())
            throw new IllegalArgumentException("Invalid secret summoning room maps");

        BlastBombManiac maniac = BlastBombManiac.current();
        boolean protectedHero = hero.buff(BlastIndestructible.class) != null
                || hero.buff(BlastTestBuffs.Indestructible.class) != null;
        if (!canBomb(BlastSettings.testRemoteThrow(), protectedHero)
                || !hasBombs(hero, maniac) || hasHostiles(level, room) || pendingCell >= 0)
            return BlastAutoSpecialRooms.Action.NONE;

        boolean inside = inside(level, room, hero.pos);
        int door = level.pointToCell(room.entrance());
        if (!inside && hero.pos != door) {
            if (!safe[door] || distances[door] == Integer.MAX_VALUE || hero.rooted)
                return BlastAutoSpecialRooms.Action.NONE;
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(door, safe, distances));
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        if (prepareBomb(hero, maniac)) return BlastAutoSpecialRooms.Action.USE;

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
        PathFinder.buildDistanceMap(hero.pos, path);
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
