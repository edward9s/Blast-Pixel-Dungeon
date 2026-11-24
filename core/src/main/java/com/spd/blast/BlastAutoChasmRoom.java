package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Levitation;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretChestChasmRoom;
import com.watabou.utils.PathFinder;

/** Cross the four-key, four-locked-chest secret room without ending a float over chasm. */
final class BlastAutoChasmRoom {
    private static int activeEntrance = -1;

    private BlastAutoChasmRoom() {}

    static void reset() {
        activeEntrance = -1;
    }

    static boolean flightTile(Level level, SecretChestChasmRoom room, int cell) {
        if (room == null || !level.insideMap(cell) || level.map[cell] != Terrain.CHASM)
            return false;
        int x = cell % level.width(), y = cell / level.width();
        return x > room.left && x < room.right && y > room.top && y < room.bottom;
    }

    static SecretChestChasmRoom flightRoom(Level level, Hero hero) {
        if (!(level instanceof RegularLevel) || hero.buff(Levitation.class) == null) return null;
        SecretChestChasmRoom room = (SecretChestChasmRoom)
                ((RegularLevel) level).room(SecretChestChasmRoom.class);
        if (room == null || room.entrance() == null) return null;
        int entrance = level.pointToCell(room.entrance());
        boolean inside = room.inside(level.cellToPoint(hero.pos));
        return (inside || activeEntrance == entrance && hero.pos == entrance)
                ? room : null;
    }

    private static SecretChestChasmRoom knownRoom(Level level, Hero hero) {
        if (!(level instanceof RegularLevel)) return null;
        SecretChestChasmRoom room = (SecretChestChasmRoom)
                ((RegularLevel) level).room(SecretChestChasmRoom.class);
        if (room == null || room.entrance() == null) return null;
        int door = level.pointToCell(room.entrance());
        return level.insideMap(door)
                && (level.visited[door] || level.mapped[door] || hero.fieldOfView[door])
                ? room : null;
    }

    /** The puzzle has no internal traps; only known chasm cells gain flight access. */
    private static boolean[] flightMap(Hero hero, SecretChestChasmRoom room,
                                       boolean[] safe, Blob[] hazards,
                                       boolean[] specialBarriers) {
        Level level = Dungeon.level;
        boolean[] flight = safe.clone();
        for (int y = room.top + 1; y < room.bottom; y++) {
            for (int x = room.left + 1; x < room.right; x++) {
                int cell = x + y * level.width();
                if (!flightTile(level, room, cell)
                        || specialBarriers[cell]
                        || BlastAutoEnvironment.hazardousAt(cell, hazards)
                        || AutoPlayPlantSafety.isHarmful(level.plants.get(cell))
                        || Actor.findChar(cell) != null) continue;
                flight[cell] = true;
            }
        }
        return flight;
    }

    static boolean enoughFlight(float remaining, int toGoal, int actionTurns,
                                int toExit, boolean lastPotion, float timePerStep) {
        if (toGoal == Integer.MAX_VALUE || (lastPotion && toExit == Integer.MAX_VALUE))
            return false;
        long steps = (long) toGoal + actionTurns + 2 + (lastPotion ? toExit : 0);
        return steps * timePerStep < remaining;
    }

    private static Item eligibleLoot(Hero hero, Heap heap) {
        if (heap == null || heap.type != Heap.Type.HEAP || heap.isEmpty()) return null;
        return BlastAutoPickup.firstEligibleItem(heap, item ->
                BlastAutoPickup.canCollect(hero, item,
                        BlastAutoPickupPolicy::isInstantPickup));
    }

    private static boolean isKey(Heap heap) {
        if (heap == null || heap.type != Heap.Type.HEAP) return false;
        for (Item item : heap.items) if (item instanceof GoldenKey) return true;
        return false;
    }

    private static int[] distancesFrom(int start, boolean[] map) {
        PathFinder.buildDistanceMap(start, map);
        return PathFinder.distance.clone();
    }

    /** Select a nearby key, affordable locked chest, or its opened loot. */
    private static int target(Hero hero, SecretChestChasmRoom room, boolean[] flight,
                              int[] reach, int exit, float duration,
                              boolean lastPotion, float timePerStep) {
        Level level = Dungeon.level;
        int keys = Dungeon.branch == 0 ? Notes.keyCount(new GoldenKey(Dungeon.depth)) : 0;
        int best = -1, bestDistance = Integer.MAX_VALUE;
        for (Heap heap : level.heaps.valueList()) {
            if (!room.inside(level.cellToPoint(heap.pos)) || reach[heap.pos] == Integer.MAX_VALUE
                    || level.pit[heap.pos] || Actor.findChar(heap.pos) != null
                    && heap.pos != hero.pos) continue;
            boolean key = isKey(heap);
            boolean chest = heap.type == Heap.Type.LOCKED_CHEST && keys > 0
                    && BlastAutoPickup.hasCollectibleLoot(hero, heap);
            boolean loot = eligibleLoot(hero, heap) != null;
            if (!key && !chest && !loot) continue;
            // Keys are always useful, even when a newly discovered chest is still locked.
            int back = lastPotion ? distancesFrom(heap.pos, flight)[exit] : 0;
            int cost = reach[heap.pos];
            if (!enoughFlight(duration, cost, 1, back, lastPotion, timePerStep)) continue;
            if (cost < bestDistance || (cost == bestDistance && key)) {
                bestDistance = cost;
                best = heap.pos;
            }
        }
        return best;
    }

    private static BlastAutoSpecialRooms.Action actAt(Hero hero) {
        Heap heap = Dungeon.level.heaps.get(hero.pos);
        Item loot = eligibleLoot(hero, heap);
        if (loot != null) {
            BlastAutoPickup.preparePickup(hero, heap, loot);
            hero.curAction = new HeroAction.PickUp(hero.pos);
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        if (heap != null && heap.type == Heap.Type.LOCKED_CHEST
                && Dungeon.branch == 0
                && Notes.keyCount(new GoldenKey(Dungeon.depth)) > 0) {
            hero.curAction = new HeroAction.OpenChest(hero.pos);
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        return BlastAutoSpecialRooms.Action.NONE;
    }

    /** Active floating takes priority over food, collection and exploration. */
    static BlastAutoSpecialRooms.Action continueFlight(Hero hero, boolean[] safe,
                                                       int[] distances,
                                                       Blob[] hazards,
                                                       boolean[] specialBarriers) {
        Level level = Dungeon.level;
        Levitation buff = hero.buff(Levitation.class);
        SecretChestChasmRoom room = flightRoom(level, hero);
        if (buff == null || room == null) {
            activeEntrance = -1;
            return BlastAutoSpecialRooms.Action.NONE;
        }
        int exit = level.pointToCell(room.entrance());
        boolean[] flight = flightMap(hero, room, safe, hazards, specialBarriers);
        int[] reach = distancesFrom(hero.pos, flight);
        float perStep = 1f / hero.speed();
        PotionOfLevitation spare = hero.belongings.getItem(PotionOfLevitation.class);
        boolean last = spare == null || spare.quantity() == 0;
        int goal = target(hero, room, flight, reach, exit,
                buff.cooldown(), last, perStep);
        if (goal >= 0) {
            if (goal == hero.pos) return actAt(hero);
            if (!hero.rooted) {
                hero.curAction = new HeroAction.Move(
                        BlastAutoPlay.firstStep(goal, flight, reach));
                return BlastAutoSpecialRooms.Action.MOVE;
            }
        }
        // All rewards are collected: return to solid ground, never wait over a pit.
        // A short remaining buff does not make the entrance an invalid destination.
        int landing = -1;
        if (reach[exit] != Integer.MAX_VALUE) {
            landing = exit;
        } else {
            int shortest = Integer.MAX_VALUE;
            for (int cell = 0; cell < flight.length; cell++) {
                if (!flight[cell] || level.pit[cell] || reach[cell] == Integer.MAX_VALUE
                        || reach[cell] >= shortest) continue;
                shortest = reach[cell];
                landing = cell;
            }
        }
        if (landing < 0) {
            // No known landing tile: do not deliberately burn levitation over a pit.
            return BlastAutoSpecialRooms.Action.NONE;
        }
        if (hero.pos != landing) {
            if (hero.rooted) return BlastAutoSpecialRooms.Action.NONE;
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(landing, flight, reach));
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        if (level.pit[hero.pos]) {
            throw new IllegalStateException("Chasm flight selected a pit as landing");
        }
        // End flight management on solid ground; regular auto-play can resume.
        activeEntrance = -1;
        return BlastAutoSpecialRooms.Action.NONE;
    }

    static BlastAutoSpecialRooms.Action chooseAction(Hero hero, boolean[] safe,
            int[] distances, Blob[] hazards, boolean[] specialBarriers) {
        Level level = Dungeon.level;
        SecretChestChasmRoom room = knownRoom(level, hero);
        if (room == null) return BlastAutoSpecialRooms.Action.NONE;
        if (safe.length != level.length() || distances.length != level.length()
                || specialBarriers.length != level.length())
            throw new IllegalArgumentException("Invalid secret chasm room maps");
        if (hero.belongings.lostInventory()) return BlastAutoSpecialRooms.Action.NONE;

        int entrance = level.pointToCell(room.entrance());
        boolean inside = room.inside(level.cellToPoint(hero.pos));
        if (!inside && (distances[entrance] == Integer.MAX_VALUE || !safe[entrance]))
            return BlastAutoSpecialRooms.Action.NONE;
        PotionOfLevitation potion = hero.belongings.getItem(PotionOfLevitation.class);
        if (potion == null || !potion.isKnown() || potion.quantity() == 0)
            return BlastAutoSpecialRooms.Action.NONE;
        if (hero.buff(Levitation.class) != null)
            return BlastAutoSpecialRooms.Action.NONE;

        boolean[] flight = flightMap(hero, room, safe, hazards, specialBarriers);
        int origin = inside ? hero.pos : entrance;
        int[] reach = distancesFrom(origin, flight);
        float perStep = Math.max(1f, 1f / hero.speed());
        boolean lastPotion = potion.quantity() == 1;
        int goal = target(hero, room, flight, reach, entrance,
                Levitation.DURATION - 1f, lastPotion, perStep);
        // Having entered the room does not justify spending another potion
        // when no key, unopened chest, or collectible loot remains.
        if (goal < 0) return BlastAutoSpecialRooms.Action.NONE;
        if (hero.pos != origin)
            return BlastAutoSpecialRooms.approachEntrance(
                    hero, goal, origin, safe, distances);

        activeEntrance = entrance;
        BlastLog.debug("auto-room action=chasm-levitate goal=" + goal
                + " remaining=" + potion.quantity());
        potion.execute(hero, Potion.AC_DRINK);
        return BlastAutoSpecialRooms.Action.USE;
    }
}
