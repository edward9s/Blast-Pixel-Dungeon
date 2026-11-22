package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Levitation;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicalFireRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.TrapsRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;

/** Clears the magical-fire barrier and opens trapped treasure rooms with native items. */
final class BlastAutoSpecialRooms {
    enum Action { NONE, MOVE, USE }
    private static final int THROW_RANGE = 8;
    private static TrapsRoom activeFlightRoom;
    private static int flightTreasure = -1;
    private static boolean attemptedFire;
    private static int lastFireVolume;

    private BlastAutoSpecialRooms() {}

    static void reset() {
        activeFlightRoom = null;
        flightTreasure = -1;
        attemptedFire = false;
        lastFireVolume = 0;
    }

    static boolean flightTerrain(int terrain) {
        return terrain == Terrain.TRAP || terrain == Terrain.SECRET_TRAP
                || terrain == Terrain.CHASM;
    }

    static TrapsRoom flightRoom(Level level, Hero hero) {
        if (!(level instanceof RegularLevel) || hero.buff(Levitation.class) == null) return null;
        if (activeFlightRoom != null) return activeFlightRoom;
        TrapsRoom room = (TrapsRoom) ((RegularLevel) level).room(TrapsRoom.class);
        return room != null && room.inside(level.cellToPoint(hero.pos)) ? room : null;
    }

    static boolean flightTile(Level level, TrapsRoom room, int cell) {
        if (!level.insideMap(cell) || !flightTerrain(level.map[cell])) return false;
        int x = cell % level.width();
        int y = cell / level.width();
        return x > room.left && x < room.right && y > room.top && y < room.bottom;
    }

    private static boolean[] flightMap(Hero hero, TrapsRoom room, boolean[] safe,
                                       Blob[] hazards, boolean[] specialBarriers) {
        Level level = Dungeon.level;
        boolean[] crossing = safe.clone();
        for (int y = room.top + 1; y < room.bottom; y++) {
            for (int x = room.left + 1; x < room.right; x++) {
                int cell = x + y * level.width();
                if (!flightTerrain(level.map[cell])
                        || !(level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell])
                        || level.solid[cell] || specialBarriers[cell]
                        || BlastAutoEnvironment.hazardousAt(cell, hazards)
                        || AutoPlayPlantSafety.isHarmful(level.plants.get(cell))
                        || Actor.findChar(cell) != null) continue;
                crossing[cell] = true;
            }
        }
        return crossing;
    }

    private static Heap knownChest(Level level, TrapsRoom room, Hero hero) {
        for (Heap heap : level.heaps.valueList()) {
            if (heap.type == Heap.Type.CHEST
                    && (heap.seen || hero.fieldOfView[heap.pos])
                    && room.inside(level.cellToPoint(heap.pos))) return heap;
        }
        return null;
    }

    static boolean finishFlight(boolean atEntrance, boolean chestClosed, boolean retreat) {
        return atEntrance && (!chestClosed || retreat);
    }

    /** An active crossing has priority over optional looting or roaming. */
    static Action continueFlight(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        Levitation floating = hero.buff(Levitation.class);
        TrapsRoom room = flightRoom(level, hero);
        if (room == null || floating == null) {
            activeFlightRoom = null;
            flightTreasure = -1;
            return Action.NONE;
        }
        int exit = level.pointToCell(room.entrance());
        if (!level.insideMap(exit) || !safe[exit]
                || distances[exit] == Integer.MAX_VALUE) return Action.NONE;
        if (activeFlightRoom == null) {
            // Recover the route after loading a save inside a trap room.
            activeFlightRoom = room;
            Heap chest = knownChest(level, room, hero);
            flightTreasure = chest == null ? -1 : chest.pos;
        }
        float timePerStep = Math.max(1f, 1f / hero.speed());
        int returnSteps = distances[exit];
        boolean retreat = floating.detachesWithinDelay((returnSteps + 3) * timePerStep);
        Heap chest = flightTreasure < 0 ? null : level.heaps.get(flightTreasure);
        // Drinking happens at the entrance: do not cancel a new crossing there.
        if (finishFlight(hero.pos == exit,
                chest != null && chest.type == Heap.Type.CHEST, retreat)) {
            activeFlightRoom = null;
            flightTreasure = -1;
            return Action.NONE;
        }
        if (!retreat && chest != null && chest.type == Heap.Type.CHEST
                && safe[flightTreasure] && distances[flightTreasure] != Integer.MAX_VALUE) {
            PathFinder.buildDistanceMap(flightTreasure, safe);
            int back = PathFinder.distance[exit];
            retreat = back == Integer.MAX_VALUE || floating.detachesWithinDelay(
                    (distances[flightTreasure] + back + 3) * timePerStep);
            if (!retreat) {
                if (hero.pos == flightTreasure) {
                    hero.curAction = new HeroAction.OpenChest(flightTreasure);
                } else if (!hero.rooted) {
                    hero.curAction = new HeroAction.Move(
                            BlastAutoPlay.firstStep(flightTreasure, safe, distances));
                }
                return hero.curAction == null ? Action.NONE : Action.MOVE;
            }
        }

        // One native pickup attempt is enough to retrieve eligible loose loot.
        if (!retreat && chest != null && chest.type == Heap.Type.HEAP
                && hero.pos == flightTreasure
                && BlastAutoPickup.chooseAction(hero, safe, distances,
                        BlastAutoPickupPolicy::isInstantPickup)) return Action.MOVE;

        if (hero.pos == exit) {
            // The chest route may have become blocked before the first step.
            activeFlightRoom = null;
            flightTreasure = -1;
            return Action.NONE;
        }
        if (!hero.rooted) {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(exit, safe, distances));
            return Action.MOVE;
        }
        return Action.NONE;
    }

    static Action chooseAction(Hero hero, boolean[] safe, int[] distances,
                               Blob[] hazards, boolean[] specialBarriers) {
        Level level = Dungeon.level;
        if (!(level instanceof RegularLevel)) return Action.NONE;
        if (safe.length != level.length() || distances.length != level.length()
                || specialBarriers.length != level.length()) {
            throw new IllegalArgumentException("Invalid special-room map");
        }
        if (hero.belongings.lostInventory()) return Action.NONE;

        Action fire = clearEternalFire(hero, safe, distances);
        if (fire != Action.NONE) return fire;

        TrapsRoom room = (TrapsRoom) ((RegularLevel) level).room(TrapsRoom.class);
        if (room == null || room.entrance() == null) return Action.NONE;
        Heap chest = knownChest(level, room, hero);
        if (chest == null) return Action.NONE;
        int exit = level.pointToCell(room.entrance());
        if (!level.insideMap(exit) || !safe[exit]
                || distances[exit] == Integer.MAX_VALUE) return Action.NONE;
        if (safe[chest.pos] && distances[chest.pos] != Integer.MAX_VALUE) return Action.NONE;

        PotionOfLevitation potion = hero.belongings.getItem(PotionOfLevitation.class);
        if (potion != null && potion.quantity() > 0 && potion.isKnown()) {
            boolean[] crossing = flightMap(hero, room, safe, hazards, specialBarriers);
            PathFinder.buildDistanceMap(exit, crossing);
            int toChest = PathFinder.distance[chest.pos];
            PathFinder.buildDistanceMap(chest.pos, crossing);
            int back = PathFinder.distance[exit];
            float timePerStep = Math.max(1f, 1f / hero.speed());
            if (toChest != Integer.MAX_VALUE && back != Integer.MAX_VALUE
                    && (toChest + back + 4) * timePerStep < Levitation.DURATION) {
                if (hero.pos != exit) {
                    if (hero.rooted) return Action.NONE;
                    hero.curAction = new HeroAction.Move(
                            BlastAutoPlay.firstStep(exit, safe, distances));
                    return Action.MOVE;
                }
                activeFlightRoom = room;
                flightTreasure = chest.pos;
                BlastLog.debug("auto-room action=levitate chest=" + chest.pos);
                potion.execute(hero, Potion.AC_DRINK);
                return Action.USE;
            }
        }
        return floodTraps(hero, room, safe, distances);
    }

    /** Prefer one Aqua Brew for multiple traps before throwing ordinary gear. */
    private static Action floodTraps(Hero hero, TrapsRoom room,
                                     boolean[] safe, int[] distances) {
        AquaBrew aqua = hero.belongings.getItem(AquaBrew.class);
        if (aqua == null || aqua.quantity() == 0) return Action.NONE;
        Level level = Dungeon.level;
        int bestCell = -1, bestStand = -1, bestCount = 1, bestSteps = Integer.MAX_VALUE;
        for (Trap trap : level.traps.valueList()) {
            int center = trap.pos;
            if (!trap.active || level.map[center] != Terrain.TRAP
                    || !room.inside(level.cellToPoint(center))
                    || !(level.visited[center] || level.mapped[center] || hero.fieldOfView[center])
                    || !safeGeyser(center, level)) continue;
            int count = 0;
            for (Trap other : level.traps.valueList()) {
                if (other.active && level.distance(center, other.pos) <= 1) count++;
            }
            if (count < bestCount) continue;
            for (int stand = 0; stand < level.length(); stand++) {
                int steps = distances[stand];
                if (!safe[stand] || steps == Integer.MAX_VALUE
                        || (count == bestCount && steps >= bestSteps)
                        || level.distance(stand, center) < 3
                        || level.distance(stand, center) > THROW_RANGE
                        || new Ballistica(stand, center, Ballistica.PROJECTILE).collisionPos != center) continue;
                bestCount = count;
                bestSteps = steps;
                bestStand = stand;
                bestCell = center;
            }
        }
        if (bestCell < 0) return Action.NONE;
        if (hero.pos != bestStand) {
            if (hero.rooted) return Action.NONE;
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(bestStand, safe, distances));
            return Action.MOVE;
        }
        if (aqua.throwPos(hero, bestCell) != bestCell) return Action.NONE;
        BlastLog.debug("auto-room action=aqua-traps target=" + bestCell + " count=" + bestCount);
        aqua.setCurrent(hero);
        aqua.cast(hero, bestCell);
        return Action.USE;
    }

    /** Account for the geyser's two-cell shove and hostile NPC restrictions. */
    private static boolean safeGeyser(int center, Level level) {
        for (Char ch : Actor.chars()) {
            if (!ch.isAlive() || ch == Dungeon.hero || level.distance(center, ch.pos) > 2) continue;
            if (ch.alignment == Char.Alignment.ALLY || !(ch instanceof Mob)
                    || BlastAutoPlay.isAutoAttackExcluded((Mob) ch)) return false;
        }
        return true;
    }

    /** Frost and water both quench EternalFire; only target a visible flame. */
    private static Action clearEternalFire(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        Blob fire = level.blobs.get(MagicalFireRoom.EternalFire.class);
        if (fire == null || fire.volume == 0) {
            attemptedFire = false;
            return Action.NONE;
        }
        if (attemptedFire && fire.volume >= lastFireVolume) return Action.NONE;
        PotionOfFrost frost = hero.belongings.getItem(PotionOfFrost.class);
        Item item = frost != null && frost.isKnown() && frost.quantity() > 0
                ? frost : hero.belongings.getItem(AquaBrew.class);
        if (item == null || item.quantity() == 0) return Action.NONE;
        boolean aqua = item instanceof AquaBrew;
        int minimumDistance = aqua ? 3 : 2;
        int bestTarget = -1, bestStand = -1, bestSteps = Integer.MAX_VALUE;
        for (int cell = 0; cell < fire.cur.length; cell++) {
            if (fire.cur[cell] <= 0 || !hero.fieldOfView[cell]
                    || (aqua && !safeGeyser(cell, level))) continue;
            for (int stand = 0; stand < level.length(); stand++) {
                int steps = distances[stand];
                if (!safe[stand] || steps == Integer.MAX_VALUE || steps >= bestSteps
                        || level.distance(stand, cell) < minimumDistance
                        || level.distance(stand, cell) > THROW_RANGE
                        || new Ballistica(stand, cell, Ballistica.PROJECTILE).collisionPos != cell) continue;
                bestTarget = cell;
                bestStand = stand;
                bestSteps = steps;
            }
        }
        if (bestStand < 0) return Action.NONE;
        if (hero.pos != bestStand) {
            if (hero.rooted) return Action.NONE;
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(bestStand, safe, distances));
            return Action.MOVE;
        }
        if (item.throwPos(hero, bestTarget) != bestTarget) return Action.NONE;
        attemptedFire = true;
        lastFireVolume = fire.volume;
        BlastLog.debug("auto-room action=" + (aqua ? "aqua-fire" : "frost-fire")
                + " target=" + bestTarget);
        item.setCurrent(hero);
        item.cast(hero, bestTarget);
        return Action.USE;
    }
}
