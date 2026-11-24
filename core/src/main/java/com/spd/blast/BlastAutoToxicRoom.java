package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ToxicGasRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;

/** Quench permanent gas vents, then keep the toxic-room entrance ventilated. */
final class BlastAutoToxicRoom {
    private static final int THROW_RANGE = 8;
    private static final int SAFE_DISTANCE = 3;

    private BlastAutoToxicRoom() {}

    /** ToxicVent is inactive: its ToxicGasSeed blob is the actual permanent source. */
    static boolean sourceTerrain(int terrain, boolean toxicVent, boolean seeded) {
        return terrain == Terrain.INACTIVE_TRAP && toxicVent && seeded;
    }

    static boolean activeVent(Level level, Trap trap) {
        if (!(trap instanceof ToxicGasRoom.ToxicVent)) return false;
        int cell = trap.pos;
        if (!level.insideMap(cell)) return false;
        Blob source = level.blobs.get(ToxicGasRoom.ToxicGasSeed.class);
        return sourceTerrain(level.map[cell], true,
                source != null && source.volume > 0 && source.cur[cell] > 0);
    }

    /** Keep ventilation only while a source is being quenched or gas remains. */
    static boolean holdDoor(boolean sourceActive, boolean hasAqua, boolean gasInside) {
        return sourceActive ? hasAqua : gasInside;
    }

    /** Permanently destroy the doorway only after every gas source stops emitting. */
    static boolean shouldDemolishDoor(boolean sourceActive, boolean gasInside) {
        return !sourceActive && gasInside;
    }

    static boolean breakableDoor(int terrain) {
        return terrain == Terrain.DOOR || terrain == Terrain.OPEN_DOOR;
    }

    private static ToxicGasRoom knownRoom(Level level) {
        if (!(level instanceof RegularLevel)) return null;
        ToxicGasRoom room = (ToxicGasRoom) ((RegularLevel) level).room(ToxicGasRoom.class);
        if (room == null || room.entrance() == null) return null;
        int door = level.pointToCell(room.entrance());
        if (!level.insideMap(door)
                || !(level.visited[door] || level.mapped[door]
                    || level.heroFOV[door])) return null;
        return room;
    }

    private static boolean anySource(Level level, ToxicGasRoom room) {
        Blob seed = level.blobs.get(ToxicGasRoom.ToxicGasSeed.class);
        if (seed == null || seed.volume <= 0) return false;
        // The seed blob is authoritative: an undiscovered or already-triggered
        // vent must not be mistaken for a quenched vent.
        for (int y = room.top + 1; y < room.bottom; y++) {
            for (int x = room.left + 1; x < room.right; x++) {
                if (seed.cur[x + y * level.width()] > 0) return true;
            }
        }
        return false;
    }

    private static boolean gasInside(Level level, ToxicGasRoom room) {
        Blob gas = level.blobs.get(ToxicGas.class);
        if (gas == null || gas.volume <= 0) return false;
        for (int y = room.top + 1; y < room.bottom; y++) {
            for (int x = room.left + 1; x < room.right; x++) {
                if (gas.cur[x + y * level.width()] > 0) return true;
            }
        }
        return false;
    }

    /** A floor heap wedges an open door and is retrieved after the gas clears. */
    static boolean holdingDoorOpen(int cell) {
        Level level = Dungeon.level;
        ToxicGasRoom room = knownRoom(level);
        if (room == null || cell != level.pointToCell(room.entrance())
                || level.map[cell] != Terrain.OPEN_DOOR || level.heaps.get(cell) == null)
            return false;
        AquaBrew aqua = Dungeon.hero.belongings.getItem(AquaBrew.class);
        return holdDoor(anySource(level, room), aqua != null && aqua.quantity() > 0,
                gasInside(level, room));
    }

    private static int safeThrowStand(Hero hero, Level level, ToxicGasRoom room,
                                      int door, boolean[] safe, int[] distances) {
        int best = -1, bestSteps = Integer.MAX_VALUE;
        int x = door % level.width(), y = door / level.width();
        for (int dy = -THROW_RANGE; dy <= THROW_RANGE; dy++) {
            int ty = y + dy;
            if (ty < 0 || ty >= level.height()) continue;
            for (int dx = -THROW_RANGE; dx <= THROW_RANGE; dx++) {
                int tx = x + dx;
                if (tx < 0 || tx >= level.width()) continue;
                int stand = tx + ty * level.width();
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                if (distance < SAFE_DISTANCE || distance > THROW_RANGE
                        || room.inside(level.cellToPoint(stand))
                        || !safe[stand] || distances[stand] == Integer.MAX_VALUE
                        || distances[stand] >= bestSteps
                        || (hero.rooted && stand != hero.pos)
                        || new Ballistica(stand, door, Ballistica.PROJECTILE).collisionPos != door)
                    continue;
                best = stand;
                bestSteps = distances[stand];
            }
        }
        return best;
    }

    /** Do not destroy loose loot when opening ventilation with a bomb. */
    private static boolean containsLooseItems(Level level, boolean[] blast) {
        for (Heap heap : level.heaps.valueList()) {
            if (!heap.isEmpty() && blast[heap.pos]) return true;
        }
        return false;
    }

    /**
     * A normal Bomb destroys flammable DOOR and OPEN_DOOR terrain permanently.
     * Share the barricade explosion's allied / neutral / sleeping mob policy.
     */
    private static BlastAutoSpecialRooms.Action demolishDoor(Hero hero, Level level,
            ToxicGasRoom room, int door, boolean[] safe, int[] distances) {
        if (!BlastSettings.testAutoPlay() || BlastRemoteBomb.isTimedMode())
            return BlastAutoSpecialRooms.Action.NONE;
        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0)
            return BlastAutoSpecialRooms.Action.NONE;

        boolean protectedHero = hero.buff(BlastIndestructible.class) != null
                || hero.buff(BlastTestBuffs.Indestructible.class) != null;
        boolean remoteThrow = BlastSettings.testRemoteThrow();
        if (!remoteThrow && !protectedHero)
            return BlastAutoSpecialRooms.Action.NONE;

        Bomb ordinary = maniac.bombAt(0);
        int closestStand = -1;
        int shortest = Integer.MAX_VALUE;
        int width = level.width();
        int x = door % width, y = door / width;
        int radius = remoteThrow ? THROW_RANGE : 1;
        for (int dy = -radius; dy <= radius; dy++) {
            int ty = y + dy;
            if (ty < 0 || ty >= level.height()) continue;
            for (int dx = -radius; dx <= radius; dx++) {
                int tx = x + dx;
                if (tx < 0 || tx >= width) continue;
                int stand = tx + ty * width;
                if (stand == door || !safe[stand] || level.pit[stand]
                        || distances[stand] == Integer.MAX_VALUE
                        || distances[stand] >= shortest
                        || (hero.rooted && stand != hero.pos)
                        || room.inside(level.cellToPoint(stand))) continue;
                int landing = remoteThrow ? BlastBombTrajectory.throwPos(stand, door) : stand;
                if (!level.insideMap(landing) || level.pit[landing]) continue;
                boolean[] blast = ordinary.blastWarningCells(landing);
                if (!BlastAutoBarricade.canDetonate(blast, door, stand, protectedHero)
                        || containsLooseItems(level, blast)) continue;
                closestStand = stand;
                shortest = distances[stand];
            }
        }
        if (closestStand < 0) return BlastAutoSpecialRooms.Action.NONE;
        if (closestStand != hero.pos) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(closestStand, safe, distances));
            BlastLog.debug("auto-toxic action=approach-demolition stand=" + closestStand
                    + " door=" + door);
            return BlastAutoSpecialRooms.Action.MOVE;
        }

        if (remoteThrow) {
            if (!maniac.blastAutoThrowOrdinaryAndDetonate(door))
                return BlastAutoSpecialRooms.Action.NONE;
        } else {
            if (!maniac.blastAutoPlaceOrdinaryAndDetonate())
                return BlastAutoSpecialRooms.Action.NONE;
            hero.spendAndNext(1f);
        }
        BlastLog.debug("auto-toxic action=demolish-door target=" + door);
        return BlastAutoSpecialRooms.Action.USE;
    }

    static BlastAutoSpecialRooms.Action chooseAction(Hero hero, boolean[] safe,
            int[] distances, Blob[] hazards, boolean[] specialBarriers) {
        Level level = Dungeon.level;
        ToxicGasRoom room = knownRoom(level);
        if (room == null) return BlastAutoSpecialRooms.Action.NONE;
        if (safe.length != level.length() || distances.length != level.length())
            throw new IllegalArgumentException("Invalid toxic room navigation map");
        if (hero.belongings.lostInventory()) return BlastAutoSpecialRooms.Action.NONE;

        boolean sourceActive = anySource(level, room);
        boolean gas = gasInside(level, room);
        AquaBrew aqua = hero.belongings.getItem(AquaBrew.class);
        boolean hasAqua = aqua != null && aqua.quantity() > 0;

        if (sourceActive && hasAqua) {
            BlastAutoAqua.Action action = BlastAutoAqua.chooseAction(
                    hero, safe, distances, room, -1, hazards, specialBarriers);
            if (action == BlastAutoAqua.Action.MOVE)
                return BlastAutoSpecialRooms.Action.MOVE;
            if (action == BlastAutoAqua.Action.THROW)
                return BlastAutoSpecialRooms.Action.USE;
        }
        if (!holdDoor(sourceActive, hasAqua, gas))
            return BlastAutoSpecialRooms.Action.NONE;

        int door = level.pointToCell(room.entrance());
        int terrain = level.map[door];
        if (!breakableDoor(terrain)
                || level.heaps.get(door) != null || Actor.findChar(door) != null)
            return BlastAutoSpecialRooms.Action.NONE;

        // Permanent ventilation is safe only after Aqua Brew has stopped every vent.
        // Keep the existing temporary item-wedge solution as a fallback.
        if (shouldDemolishDoor(sourceActive, gas)) {
            BlastAutoSpecialRooms.Action bombing = demolishDoor(
                    hero, level, room, door, safe, distances);
            if (bombing != BlastAutoSpecialRooms.Action.NONE) return bombing;
        }
        Item spare = BlastAutoTrapClearer.chooseThrowItem(hero.belongings.backpack,
                candidate -> candidate.isEquipped(hero));
        if (spare == null) return BlastAutoSpecialRooms.Action.NONE;
        int stand = safeThrowStand(hero, level, room, door, safe, distances);
        if (stand < 0) return BlastAutoSpecialRooms.Action.NONE;
        if (stand != hero.pos) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(stand, safe, distances));
            BlastLog.debug("auto-toxic action=approach-door stand=" + stand);
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        if (spare.throwPos(hero, door) != door) return BlastAutoSpecialRooms.Action.NONE;
        BlastLog.debug("auto-toxic action=ventilate door=" + door
                + " source=" + sourceActive + " gas=" + gas);
        spare.setCurrent(hero);
        spare.cast(hero, door);
        return BlastAutoSpecialRooms.Action.USE;
    }
}
