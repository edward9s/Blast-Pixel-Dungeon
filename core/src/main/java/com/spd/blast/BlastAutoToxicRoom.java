package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.BlobImmunity;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfPurity;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ToxicGasRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.Arrays;

/** Quench permanent gas vents, then keep the toxic-room entrance ventilated. */
final class BlastAutoToxicRoom {
    private static final int THROW_RANGE = 8;
    private static final int SAFE_DISTANCE = 3;
    private static int purityEntrance = -1;
    private static int[] purityTargets = new int[0];
    private static int purityIndex;
    private static int attemptedPurityTreasure = -1;

    private BlastAutoToxicRoom() {}

    private static void endPurityRun() {
        purityEntrance = -1;
        purityTargets = new int[0];
        purityIndex = 0;
    }

    static void reset() {
        endPurityRun();
        attemptedPurityTreasure = -1;
    }

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

    /** Only a pending Magical Catalyst chest or opened heap justifies purity. */
    private static int catalystTreasure(Level level, ToxicGasRoom room) {
        for (Heap heap : level.heaps.valueList()) {
            if ((heap.type != Heap.Type.CHEST && heap.type != Heap.Type.HEAP)
                    || heap.isEmpty() || !room.inside(level.cellToPoint(heap.pos)))
                continue;
            for (Item item : heap.items) {
                if (item instanceof TrinketCatalyst) return heap.pos;
            }
        }
        return -1;
    }

    private static boolean collectibleCatalyst(Hero hero, Heap heap) {
        return heap != null && BlastAutoPickup.firstEligibleItem(heap,
                item -> item instanceof TrinketCatalyst
                        && BlastAutoPickup.canCollect(hero, item,
                                BlastAutoPickupPolicy::isInstantPickup)) != null;
    }

    /** The native gas room contains two chests and one skeleton heap. */
    private static Heap[] roomLoot(Hero hero, Level level, ToxicGasRoom room) {
        ArrayList<Heap> targets = new ArrayList<>();
        for (Heap heap : level.heaps.valueList()) {
            if (heap.isEmpty() || !room.inside(level.cellToPoint(heap.pos))) continue;
            if (heap.type == Heap.Type.CHEST || heap.type == Heap.Type.SKELETON
                    || (heap.type == Heap.Type.HEAP && firstRoomLoot(hero, heap) != null)) {
                targets.add(heap);
            }
        }
        return targets.toArray(new Heap[0]);
    }

    private static Item firstRoomLoot(Hero hero, Heap heap) {
        return BlastAutoPickup.firstEligibleItem(heap,
                item -> (BlastAutoPickup.isPickupType(item,
                            BlastAutoPickupPolicy::isInstantPickup)
                        || BlastAutoEquipment.canPickup(hero, item))
                        && BlastAutoPickup.canCollect(hero, item,
                                BlastAutoPickupPolicy::isInstantPickup));
    }

    /** Reserve one interaction for opening and one per collectible item. */
    private static int roomActions(Hero hero, Heap heap) {
        if (heap == null) return 0;
        int actions = heap.type == Heap.Type.HEAP ? 0 : 1;
        for (Item item : heap.items) {
            if ((BlastAutoPickup.isPickupType(item,
                        BlastAutoPickupPolicy::isInstantPickup)
                    || BlastAutoEquipment.canPickup(hero, item))
                    && BlastAutoPickup.canCollect(hero, item,
                            BlastAutoPickupPolicy::isInstantPickup)) actions++;
        }
        return actions;
    }

    /**
     * Select the shortest complete trip through up to three room loot locations.
     * Indices 1..N refer to targets and index 0 to the entry/exit. Return null
     * if any complete return trip is impossible.
     */
    static int[] shortestOrder(int[][] steps) {
        if (steps == null || steps.length < 2 || steps.length > 4)
            throw new IllegalArgumentException("Expected 1..3 toxic-room targets");
        for (int[] row : steps) {
            if (row == null || row.length != steps.length)
                throw new IllegalArgumentException("Invalid toxic-room distance matrix");
        }
        int count = steps.length - 1;
        int[] order = new int[count];
        for (int i = 0; i < count; i++) order[i] = i;
        int[] best = null;
        long bestLength = Long.MAX_VALUE;
        do {
            long length = 0;
            int from = 0;
            boolean reachable = true;
            for (int target : order) {
                int segment = steps[from][target + 1];
                if (segment < 0 || segment == Integer.MAX_VALUE) {
                    reachable = false;
                    break;
                }
                length += segment;
                from = target + 1;
            }
            int returnSteps = steps[from][0];
            if (reachable && returnSteps >= 0 && returnSteps != Integer.MAX_VALUE) {
                length += returnSteps;
                if (length < bestLength) {
                    bestLength = length;
                    best = order.clone();
                }
            }
        } while (nextOrder(order));
        return best;
    }

    private static boolean nextOrder(int[] order) {
        int i = order.length - 2;
        while (i >= 0 && order[i] >= order[i + 1]) i--;
        if (i < 0) return false;
        int j = order.length - 1;
        while (order[j] <= order[i]) j--;
        int value = order[i];
        order[i] = order[j];
        order[j] = value;
        for (int left = i + 1, right = order.length - 1; left < right; left++, right--) {
            value = order[left];
            order[left] = order[right];
            order[right] = value;
        }
        return true;
    }

    private static int routeLength(int[][] steps, int[] order) {
        int distance = 0;
        int from = 0;
        for (int target : order) {
            distance += steps[from][target + 1];
            from = target + 1;
        }
        return distance + steps[from][0];
    }

    /** Immunity permits toxic gas only, never another active environmental hazard. */
    private static boolean onlyToxicHazard(int cell, Blob[] hazards) {
        for (Blob hazard : hazards) {
            if (hazard == null) break;
            if (hazard.cur[cell] > 0 && !(hazard instanceof ToxicGas)) return false;
        }
        return true;
    }

    /** Allow the gas room itself while retaining all other movement restrictions. */
    private static boolean[] purityRoute(Hero hero, Level level, ToxicGasRoom room, int door,
                                          boolean[] safe, Blob[] hazards, boolean[] barriers) {
        boolean[] route = safe.clone();
        for (int y = room.top; y <= room.bottom; y++) {
            for (int x = room.left; x <= room.right; x++) {
                int cell = x + y * level.width();
                boolean interior = x > room.left && x < room.right
                        && y > room.top && y < room.bottom;
                if (!interior && cell != door) continue;
                if (!level.insideMap(cell) || !level.passable[cell] || level.pit[cell]
                        || (level.solid[cell] && level.map[cell] != Terrain.DOOR)
                        || barriers[cell] || !BlastAutoPlay.isTrapSafe(level.traps.get(cell))
                        || AutoPlayPlantSafety.isHarmful(level.plants.get(cell))
                        || !onlyToxicHazard(cell, hazards)) continue;
                Char occupant = Actor.findChar(cell);
                if (occupant == null || occupant == hero) route[cell] = true;
            }
        }
        return route;
    }

    private static int[] distancesFrom(int start, boolean[] map) {
        PathFinder.buildDistanceMap(start, map);
        return PathFinder.distance.clone();
    }

    /** Drink from the doorway, or a clean adjacent tile if gas reached it. */
    private static int purityStand(Level level, ToxicGasRoom room, int door,
                                   boolean[] safe, int[] distances) {
        if (safe[door] && distances[door] != Integer.MAX_VALUE) return door;
        int best = -1;
        int shortest = Integer.MAX_VALUE;
        for (int offset : PathFinder.NEIGHBOURS4) {
            int cell = door + offset;
            if (!level.insideMap(cell) || room.inside(level.cellToPoint(cell))
                    || !safe[cell] || distances[cell] >= shortest) continue;
            best = cell;
            shortest = distances[cell];
        }
        return best;
    }

    /** Budget for the trip, opening the chest, picking up loot, and returning. */
    static boolean enoughPurity(float duration, int forward, int backward,
                                int interactions, float stepTime) {
        return duration > 0 && forward >= 0 && backward >= 0
                && forward != Integer.MAX_VALUE && backward != Integer.MAX_VALUE
                && interactions >= 1 && stepTime > 0
                && ((long) forward + backward) * stepTime + interactions + 2 < duration;
    }

    /** One potion opens both chests and the skeleton before the hero withdraws. */
    static BlastAutoSpecialRooms.Action continuePurityRun(Hero hero, boolean[] safe,
            Blob[] hazards, boolean[] barriers) {
        if (purityTargets.length == 0) return BlastAutoSpecialRooms.Action.NONE;
        Level level = Dungeon.level;
        ToxicGasRoom room = knownRoom(level);
        BlobImmunity immunity = hero.buff(BlobImmunity.class);
        if (room == null || immunity == null || hero.belongings.lostInventory()
                || !level.insideMap(purityEntrance)) {
            endPurityRun();
            return BlastAutoSpecialRooms.Action.NONE;
        }

        int door = level.pointToCell(room.entrance());
        boolean[] route = purityRoute(hero, level, room, door, safe, hazards, barriers);
        int[] forward = distancesFrom(hero.pos, route);
        float stepTime = Math.max(1f, 1f / hero.speed());

        while (purityIndex < purityTargets.length) {
            int target = purityTargets[purityIndex];
            if (!level.insideMap(target)) {
                throw new IllegalStateException("Invalid toxic-room loot target");
            }
            Heap heap = level.heaps.get(target);
            if (heap == null || (heap.type == Heap.Type.HEAP
                    && firstRoomLoot(hero, heap) == null)) {
                purityIndex++;
                continue;
            }

            int[] back = distancesFrom(target, route);
            if (!hero.rooted && enoughPurity(immunity.cooldown(), forward[target],
                    back[purityEntrance], roomActions(hero, heap), stepTime)) {
                if (hero.pos != target) {
                    hero.curAction = new HeroAction.Move(
                            BlastAutoPlay.firstStep(target, route, forward));
                } else if (heap.type == Heap.Type.CHEST
                        || heap.type == Heap.Type.SKELETON) {
                    hero.curAction = new HeroAction.OpenChest(target);
                } else if (heap.type == Heap.Type.HEAP) {
                    Item loot = firstRoomLoot(hero, heap);
                    if (loot == null) throw new IllegalStateException("Lost toxic-room loot");
                    BlastAutoPickup.exposeForPickup(heap, loot);
                    hero.curAction = new HeroAction.PickUp(target);
                } else {
                    throw new IllegalStateException("Unexpected toxic-room container");
                }
                return BlastAutoSpecialRooms.Action.MOVE;
            }
            break;
        }

        // All loot collected or insufficient immunity remains: return outside.
        if (hero.pos == purityEntrance) {
            endPurityRun();
            return BlastAutoSpecialRooms.Action.NONE;
        }
        if (!hero.rooted && forward[purityEntrance] != Integer.MAX_VALUE) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(purityEntrance, route, forward));
            return BlastAutoSpecialRooms.Action.MOVE;
        }
        endPurityRun();
        return BlastAutoSpecialRooms.Action.NONE;
    }

    /** The catalyst enables an unidentified Purity to recover all three caches. */
    private static BlastAutoSpecialRooms.Action claimCatalyst(Hero hero, Level level,
            ToxicGasRoom room, boolean[] safe, int[] distances,
            Blob[] hazards, boolean[] barriers) {
        if (!gasInside(level, room)) return BlastAutoSpecialRooms.Action.NONE;
        int catalyst = catalystTreasure(level, room);
        if (catalyst < 0 || catalyst == attemptedPurityTreasure)
            return BlastAutoSpecialRooms.Action.NONE;
        Heap[] loot = roomLoot(hero, level, room);
        if (loot.length == 0 || loot.length > 3
                || !collectibleCatalyst(hero, level.heaps.get(catalyst)))
            return BlastAutoSpecialRooms.Action.NONE;

        boolean allSafe = true;
        for (Heap heap : loot) {
            if (!safe[heap.pos] || distances[heap.pos] == Integer.MAX_VALUE) {
                allSafe = false;
                break;
            }
        }
        // Ordinary auto-interaction/pickup can handle the entire room already.
        if (allSafe) return BlastAutoSpecialRooms.Action.NONE;

        BlobImmunity immunity = hero.buff(BlobImmunity.class);
        PotionOfPurity potion = hero.belongings.getItem(PotionOfPurity.class);
        if (immunity == null && (potion == null || potion.quantity() <= 0))
            return BlastAutoSpecialRooms.Action.NONE;

        int door = level.pointToCell(room.entrance());
        int stand = purityStand(level, room, door, safe, distances);
        if (stand < 0) return BlastAutoSpecialRooms.Action.NONE;
        boolean[] route = purityRoute(hero, level, room, door, safe, hazards, barriers);
        int[][] steps = new int[loot.length + 1][loot.length + 1];
        for (int i = 0; i < steps.length; i++) {
            int origin = i == 0 ? stand : loot[i - 1].pos;
            int[] reachable = distancesFrom(origin, route);
            for (int j = 0; j < steps.length; j++) {
                int destination = j == 0 ? stand : loot[j - 1].pos;
                steps[i][j] = reachable[destination];
            }
        }
        int[] order = shortestOrder(steps);
        if (order == null) return BlastAutoSpecialRooms.Action.NONE;
        int actions = 0;
        for (Heap heap : loot) actions += roomActions(hero, heap);
        float duration = immunity == null
                ? BlobImmunity.DURATION - 1f : immunity.cooldown();
        if (!enoughPurity(duration, routeLength(steps, order), 0, actions,
                Math.max(1f, 1f / hero.speed())))
            return BlastAutoSpecialRooms.Action.NONE;
        if (hero.pos != stand) {
            return BlastAutoSpecialRooms.approachEntrance(
                    hero, catalyst, stand, safe, distances);
        }

        purityEntrance = stand;
        purityTargets = new int[order.length];
        for (int i = 0; i < order.length; i++) {
            purityTargets[i] = loot[order[i]].pos;
        }
        purityIndex = 0;
        attemptedPurityTreasure = catalyst;
        if (immunity == null) {
            BlastLog.debug("auto-toxic action=purity-room containers="
                    + Arrays.toString(purityTargets) + " identified=" + potion.isKnown());
            potion.execute(hero, Potion.AC_DRINK);
            return BlastAutoSpecialRooms.Action.USE;
        }
        return continuePurityRun(hero, safe, hazards, barriers);
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
        BlastAutoSpecialRooms.Action purity = claimCatalyst(
                hero, level, room, safe, distances, hazards, specialBarriers);
        if (purity != BlastAutoSpecialRooms.Action.NONE) return purity;

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
