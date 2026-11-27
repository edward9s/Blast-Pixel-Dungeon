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
        return BlastAutoSpecialRooms.knownEntrance(level, hero, room) ? room : null;
    }

    private static boolean[] flightMap(Hero hero, SecretChestChasmRoom room,
                                       boolean[] safe, Blob[] hazards,
                                       boolean[] specialBarriers) {
        return BlastAutoSpecialRooms.roomPath(hero, room, safe, hazards, specialBarriers,
                cell -> flightTile(Dungeon.level, room, cell));
    }

    static boolean enoughFlight(float remaining, int toGoal, int actionTurns,
                                int toExit, boolean lastPotion, float timePerStep) {
        if (toGoal == Integer.MAX_VALUE || (lastPotion && toExit == Integer.MAX_VALUE))
            return false;
        long steps = (long) toGoal + actionTurns + 2 + (lastPotion ? toExit : 0);
        return steps * timePerStep < remaining;
    }

    static boolean canLand(Levitation buff, int steps, float timePerStep) {
        // Only the steps before reaching solid ground need levitation.
        return steps != Integer.MAX_VALUE
                && (steps <= 1 || !buff.detachesWithinDelay((steps - 1) * timePerStep));
    }

    private static Item eligibleLoot(Hero hero, Heap heap) {
        if (heap == null || heap.type != Heap.Type.HEAP || heap.isEmpty()) return null;
        return BlastAutoPickup.firstEligibleItem(heap, item ->
                BlastAutoPickup.canCollectFromHeap(hero, item,
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
            boolean chest = heap.type == Heap.Type.LOCKED_CHEST && keys > 0;
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

    private static BlastAutoSpecialRooms.Action renew(Hero hero, PotionOfLevitation potion,
                                                      Levitation buff, int destination) {
        BlastLog.debug("auto-room action=chasm-renew-levitation goal=" + destination
                + " remaining=" + buff.cooldown() + " potions=" + potion.quantity());
        potion.execute(hero, Potion.AC_DRINK);
        return BlastAutoSpecialRooms.Action.USE;
    }

    private static BlastAutoSpecialRooms.Action move(Hero hero, int destination, boolean[] flight,
            int[] reach, Levitation buff, float timePerStep, PotionOfLevitation spare) {
        int step = BlastAutoPlay.firstStep(destination, flight, reach);
        if (spare != null && spare.quantity() > 0 && Dungeon.level.pit[step]
                && buff.detachesWithinDelay(timePerStep)) {
            return renew(hero, spare, buff, destination);
        }
        hero.curAction = new HeroAction.Move(step);
        return BlastAutoSpecialRooms.Action.MOVE;
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
        if (goal < 0 && !last && !hero.rooted) {
            // Allow the route a fresh potion would cover, but finish using the current buff first.
            goal = target(hero, room, flight, reach, exit, Levitation.DURATION - 1f,
                    spare.quantity() == 1, perStep);
        }
        if (goal >= 0) {
            if (goal == hero.pos) return actAt(hero);
            if (!hero.rooted) {
                return move(hero, goal, flight, reach, buff, perStep, spare);
            }
        }
        boolean overPit = level.pit[hero.pos];
        int landing = reach[exit] == Integer.MAX_VALUE ? -1 : exit;
        // When a spare can finish the trip to the exit, avoid stranding on an island.
        if (landing < 0 || (overPit && last && !canLand(buff, reach[exit], perStep))) {
            int shortest = Integer.MAX_VALUE;
            for (int cell = 0; cell < flight.length; cell++) {
                if (!flight[cell] || level.pit[cell] || reach[cell] == Integer.MAX_VALUE
                        || reach[cell] >= shortest) continue;
                shortest = reach[cell];
                landing = cell;
            }
        }
        if (landing < 0 || hero.rooted) {
            // Keep floating if the hero is temporarily unable to move or reach solid ground.
            if (overPit && !last && buff.detachesWithinDelay(2 * Actor.TICK)) {
                return renew(hero, spare, buff, landing);
            }
            return BlastAutoSpecialRooms.Action.NONE;
        }
        if (hero.pos != landing) {
            return move(hero, landing, flight, reach, buff, perStep, overPit ? spare : null);
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
        if (potion == null || potion.quantity() <= 0)
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
