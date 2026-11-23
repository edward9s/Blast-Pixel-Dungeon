package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SentryRoom;
import com.watabou.utils.PathFinder;

/** Neutralize a sentry's red floors with Aqua Brew or make a timed haste crossing. */
final class BlastAutoSentryRoom {
    private static int activeEntrance = -1;
    private static int activeTreasure = -1;

    private BlastAutoSentryRoom() {}

    static void reset() {
        activeEntrance = -1;
        activeTreasure = -1;
    }

    static boolean enoughHaste(float remaining, int toChest, int toExit, float stepTime) {
        return stepTime > 0 && toChest >= 0 && toExit >= 0
                && toChest != Integer.MAX_VALUE && toExit != Integer.MAX_VALUE
                && ((long) toChest + toExit + 4) * stepTime < remaining;
    }

    private static SentryRoom knownRoom(Level level, Hero hero) {
        if (!(level instanceof RegularLevel)) return null;
        SentryRoom room = (SentryRoom) ((RegularLevel) level).room(SentryRoom.class);
        if (room == null || room.entrance() == null) return null;
        int door = level.pointToCell(room.entrance());
        return level.insideMap(door)
                && (level.visited[door] || level.mapped[door] || hero.fieldOfView[door])
                ? room : null;
    }

    private static boolean activeSentry(Level level, SentryRoom room) {
        for (Mob mob : level.mobs) {
            if (mob instanceof SentryRoom.Sentry && mob.isAlive()
                    && room.inside(level.cellToPoint(mob.pos))) return true;
        }
        return false;
    }

    private static Heap chest(Level level, SentryRoom room, Hero hero) {
        for (Heap heap : level.heaps.valueList())
            if (heap.type == Heap.Type.CHEST
                    && (heap.seen || hero.fieldOfView[heap.pos])
                    && room.inside(level.cellToPoint(heap.pos))) return heap;
        return null;
    }

    /** Allow only the sentry's own EMPTY_SP cells, retaining other safety checks. */
    private static boolean[] crossing(Hero hero, SentryRoom room, boolean[] safe,
                                      Blob[] hazards, boolean[] barriers) {
        Level level = Dungeon.level;
        boolean[] map = safe.clone();
        for (int y = room.top + 1; y < room.bottom; y++) {
            for (int x = room.left + 1; x < room.right; x++) {
                int cell = x + y * level.width();
                if (level.map[cell] != Terrain.EMPTY_SP || !barriers[cell]
                        || level.solid[cell] || level.pit[cell] || !level.passable[cell]
                        || BlastAutoEnvironment.hazardousAt(cell, hazards)
                        || AutoPlayPlantSafety.isHarmful(level.plants.get(cell))) continue;
                Char occupant = Actor.findChar(cell);
                if (occupant == null || occupant == hero) map[cell] = true;
            }
        }
        return map;
    }

    private static int[] reachable(int origin, boolean[] map) {
        PathFinder.buildDistanceMap(origin, map);
        return PathFinder.distance.clone();
    }

    private static Item eligibleLoot(Hero hero, Heap heap) {
        if (heap == null || heap.type != Heap.Type.HEAP || heap.isEmpty()) return null;
        return BlastAutoPickup.firstEligibleItem(heap, item ->
                BlastAutoPickup.canCollect(hero, item,
                        BlastAutoPickupPolicy::isInstantPickup));
    }

    /** A started sprint has priority over combat and routine exploration. */
    static BlastAutoSpecialRooms.Action continueRun(Hero hero, boolean[] safe,
            int[] distances, Blob[] hazards, boolean[] barriers) {
        Level level = Dungeon.level;
        Haste haste = hero.buff(Haste.class);
        SentryRoom room = knownRoom(level, hero);
        if (haste == null || room == null || !activeSentry(level, room)) {
            reset();
            return BlastAutoSpecialRooms.Action.NONE;
        }
        int exit = level.pointToCell(room.entrance());
        if (activeEntrance < 0 && room.inside(level.cellToPoint(hero.pos))) {
            Heap discovered = chest(level, room, hero);
            activeEntrance = exit;
            activeTreasure = discovered == null ? -1 : discovered.pos;
        }
        if (activeEntrance != exit || activeTreasure < 0)
            return BlastAutoSpecialRooms.Action.NONE;

        boolean[] map = crossing(hero, room, safe, hazards, barriers);
        int[] travel = reachable(hero.pos, map);
        int back = reachable(activeTreasure, map)[exit];
        Heap heap = level.heaps.get(activeTreasure);
        Item loot = eligibleLoot(hero, heap);
        boolean pending = heap != null && (heap.type == Heap.Type.CHEST || loot != null);
        if (pending && hero.speed() >= 2.9f
                && enoughHaste(haste.cooldown(), travel[activeTreasure], back,
                        1f / hero.speed())) {
            if (hero.pos != activeTreasure) {
                if (hero.rooted) return BlastAutoSpecialRooms.Action.NONE;
                hero.curAction = new HeroAction.Move(
                        BlastAutoPlay.firstStep(activeTreasure, map, travel));
            } else if (heap.type == Heap.Type.CHEST) {
                hero.curAction = new HeroAction.OpenChest(activeTreasure);
            } else {
                BlastAutoPickup.exposeForPickup(heap, loot);
                hero.curAction = new HeroAction.PickUp(activeTreasure);
            }
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        if (hero.pos != exit && travel[exit] != Integer.MAX_VALUE && !hero.rooted) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(exit, map, travel));
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        reset();
        return BlastAutoSpecialRooms.Action.NONE;
    }

    static BlastAutoSpecialRooms.Action chooseAction(Hero hero, boolean[] safe,
            int[] distances, Blob[] hazards, boolean[] barriers) {
        Level level = Dungeon.level;
        SentryRoom room = knownRoom(level, hero);
        if (room == null || !activeSentry(level, room)
                || hero.belongings.lostInventory()) return BlastAutoSpecialRooms.Action.NONE;
        if (safe.length != level.length() || distances.length != level.length()
                || barriers.length != level.length())
            throw new IllegalArgumentException("Invalid sentry room maps");

        int exit = level.pointToCell(room.entrance());
        if (!safe[exit] || distances[exit] == Integer.MAX_VALUE)
            return BlastAutoSpecialRooms.Action.NONE;
        Heap treasure = chest(level, room, hero);
        // A missing chest can mean undiscovered, opened, or emptied. Leave the
        // room reachable through ordinary roaming and pickup in all cases.
        if (treasure == null) return BlastAutoSpecialRooms.Action.NONE;
        if (safe[treasure.pos] && distances[treasure.pos] != Integer.MAX_VALUE)
            return BlastAutoSpecialRooms.Action.NONE;

        BlastAutoAqua.Action aqua = BlastAutoAqua.chooseAction(
                hero, safe, distances, room, treasure.pos, hazards, barriers);
        if (aqua == BlastAutoAqua.Action.MOVE) return BlastAutoSpecialRooms.Action.MOVE;
        if (aqua == BlastAutoAqua.Action.THROW) return BlastAutoSpecialRooms.Action.USE;

        Haste haste = hero.buff(Haste.class);
        PotionOfHaste potion = hero.belongings.getItem(PotionOfHaste.class);
        if (haste == null && (potion == null || potion.quantity() == 0 || !potion.isKnown()))
            return BlastAutoSpecialRooms.Action.NONE;
        boolean[] map = crossing(hero, room, safe, hazards, barriers);
        int forward = reachable(exit, map)[treasure.pos];
        int backward = reachable(treasure.pos, map)[exit];
        float speed = hero.speed() * (haste == null ? 3f : 1f);
        if (speed < 2.9f || !enoughHaste(
                haste == null ? Haste.DURATION - 1f : haste.cooldown(),
                forward, backward, 1f / speed)) return BlastAutoSpecialRooms.Action.NONE;

        if (hero.pos != exit) {
            if (hero.rooted) return BlastAutoSpecialRooms.Action.NONE;
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(exit, safe, distances));
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        activeEntrance = exit;
        activeTreasure = treasure.pos;
        if (haste == null) {
            BlastLog.debug("auto-sentry action=haste chest=" + treasure.pos);
            potion.execute(hero, Potion.AC_DRINK);
            return BlastAutoSpecialRooms.Action.USE;
        }
        return continueRun(hero, safe, distances, hazards, barriers);
    }
}
