package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.LostInventory;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfExperience;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfDivineInspiration;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTransmutation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfMetamorphosis;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.*;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.Arrays;

/** Scores the three ordinary crystal-choice puzzles without changing their rewards. */
final class BlastAutoCrystalRooms {
    private static Level phaseLevel;
    private static SpecialRoom phaseRoom;
    private static HeroAction.Move phaseMove;
    private static boolean leaving;
    private static boolean lootPending;
    private static Item toStage;
    private static final Set<Item> staged = Collections.newSetFromMap(
            new IdentityHashMap<Item, Boolean>());

    private BlastAutoCrystalRooms() {}

    static void resetPhase() {
        phaseRoom = null;
        phaseMove = null;
        leaving = false;
        phaseLevel = null;
        lootPending = false;
        toStage = null;
        staged.clear();
    }

    /** Phase through remaining crystal locks after spending the last crystal key. */
    static void updatePhase(Hero hero) {
        Level level = Dungeon.level;
        if (phaseLevel != level) resetPhase();
        phaseLevel = level;
        toStage = null;
        phaseMove = null;
        SpecialRoom room = null;
        SpecialRoom inside = null;
        boolean canPhase = Dungeon.branch == 0
                && Notes.keyCount(new CrystalKey(Dungeon.depth)) == 0
                && hero.buff(LostInventory.class) == null;
        if (level instanceof RegularLevel) {
            RegularLevel regular = (RegularLevel) level;
            CrystalPathRoom path = (CrystalPathRoom) regular.room(CrystalPathRoom.class);
            CrystalChoiceRoom choice = (CrystalChoiceRoom) regular.room(CrystalChoiceRoom.class);
            int pathDoors = path == null ? 0 : remainingCrystalDoors(level, path);
            if (path != null && pathDoors <= 3) {
                if (hasUncollectedLoot(hero, level, path)) room = path;
                if (pathDoors > 0 && path.inside(level.cellToPoint(hero.pos))) inside = path;
            }
            int choiceDoors = choice == null ? 0 : remainingCrystalDoors(level, choice);
            if (choice != null && choiceDoors <= 1) {
                if (room == null && hasUncollectedLoot(hero, level, choice)) room = choice;
                if (choiceDoors > 0 && choice.inside(level.cellToPoint(hero.pos))) inside = choice;
            }
        }
        lootPending = room != null;
        boolean ready = false;
        if (canPhase && room != null && room.entrance() != null) {
            int entrance = level.pointToCell(room.entrance());
            if (level.visited[entrance] || level.mapped[entrance] || hero.fieldOfView[entrance]) {
                ArrayList<Item> prizes = new ArrayList<>();
                for (Heap heap : level.heaps.valueList()) {
                    if (!room.inside(level.cellToPoint(heap.pos))) continue;
                    for (Item item : heap.items) {
                        if (eligiblePrize(hero, item)) prizes.add(item);
                    }
                }
                int required = requiredSlots(hero, prizes);
                int free = hero.belongings.backpack.capacity() - hero.belongings.backpack.items.size();
                if (required <= free) ready = true;
                else {
                    toStage = chooseStagingItem(hero, level, prizes, required - free);
                    if (toStage == null) {
                        BlastLog.debug("auto-crystal action=inventory-full required="
                                + required + " free=" + free);
                    }
                }
            }
        }
        // Recover the exit route after reloading a save inside the room.
        if (canPhase && phaseRoom == null && inside != null && inside.entrance() != null) {
            int entrance = level.pointToCell(inside.entrance());
            PathFinder.buildDistanceMap(hero.pos, level.passable);
            if (PathFinder.distance[entrance] == Integer.MAX_VALUE) phaseRoom = inside;
        }
        if (phaseRoom != null && (!ready || phaseRoom != room)
                && containsActive(hero.pos)) {
            leaving = true;
            toStage = null;
        } else {
            leaving = false;
            phaseRoom = ready ? room : null;
        }
    }

    private static boolean hasUncollectedLoot(Hero hero, Level level, Room room) {
        for (Heap heap : level.heaps.valueList()) {
            if (room.inside(level.cellToPoint(heap.pos))
                    && heap.items.stream().anyMatch(item -> heap.type == Heap.Type.HEAP
                            ? eligiblePrize(hero, item) : !staged.contains(item))) return true;
        }
        return false;
    }

    private static boolean eligiblePrize(Hero hero, Item item) {
        return !staged.contains(item) && !(item instanceof Wand && item.cursed)
                && !BlastAutoEquipment.blocksCursedPickup(hero, item);
    }

    /** The room starts with six locks and generates three crystal keys. */
    private static int remainingCrystalDoors(Level level, Room room) {
        int remaining = 0;
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.map[cell] == Terrain.CRYSTAL_DOOR
                    && room.inside(level.cellToPoint(cell))) remaining++;
        }
        return remaining;
    }

    /**
     * Collect every remaining prize, including prizes in unrevealed final rooms.
     * Staged equipment is ignored so it cannot be immediately picked up again.
     */
    static boolean containsActive(int cell) {
        return hasRoute()
                && phaseRoom.inside(Dungeon.level.cellToPoint(cell));
    }

    static boolean hasRoute() {
        return phaseLevel == Dungeon.level && phaseRoom != null;
    }

    static boolean canCrossDoor(Hero hero, int cell) {
        return phaseMove != null && hero != null && hero.curAction == phaseMove
                && phaseMove.dst == cell && hasRoute()
                && Dungeon.level.map[cell] == Terrain.CRYSTAL_DOOR;
    }

    private static void move(Hero hero, int cell) {
        phaseMove = new HeroAction.Move(cell);
        hero.curAction = phaseMove;
    }

    static boolean isStaged(Item item) {
        return lootPending && staged.contains(item);
    }

    static boolean collect(Hero hero, boolean[] safe, int[] distances) {
        if (phaseLevel != Dungeon.level) return false;
        if (toStage != null) {
            Item item = toStage;
            toStage = null;
            staged.add(item);
            BlastLog.debug("auto-crystal action=stage item=" + item.getClass().getSimpleName());
            item.doDrop(hero);
            return true;
        }
        if (!hasRoute()) return false;
        Level level = Dungeon.level;
        if (leaving) return leave(hero, level, safe, distances);

        Heap target = null;
        Item prize = null;
        int destination = -1;
        int priority = Integer.MIN_VALUE;
        int distance = Integer.MAX_VALUE;
        for (Heap heap : level.heaps.valueList()) {
            if (!containsActive(heap.pos) || heap.isEmpty()) continue;
            boolean loose = heap.type == Heap.Type.HEAP;
            int stand = loose ? heap.pos : adjacentStand(level, heap.pos, safe, distances);
            if (stand < 0 || !safe[stand] || distances[stand] == Integer.MAX_VALUE) continue;
            for (Item item : heap.items) {
                if (staged.contains(item) || (loose && !BlastAutoPickup.canCollectFromHeap(
                        hero, item, BlastAutoPickupPolicy::isInstantPickup))) continue;
                int score = item instanceof ScrollOfTransmutation
                        || item instanceof ScrollOfMetamorphosis ? 30
                        : item instanceof PotionOfExperience
                        || item instanceof PotionOfDivineInspiration ? 20 : 10;
                if (score > priority || (score == priority && distances[stand] < distance)) {
                    target = heap;
                    prize = item;
                    destination = stand;
                    priority = score;
                    distance = distances[stand];
                }
            }
        }
        if (target == null) {
            return containsActive(hero.pos) && leave(hero, level, safe, distances);
        }
        if (hero.pos == destination) {
            if (target.type == Heap.Type.HEAP) {
                BlastAutoPickup.preparePickup(hero, target, prize);
            } else {
                hero.curAction = new HeroAction.OpenChest(target.pos);
            }
        } else if (!hero.rooted) {
            move(hero, BlastAutoPlay.firstStep(destination, safe, distances));
        } else return false;
        return true;
    }

    private static boolean leave(Hero hero, Level level, boolean[] safe, int[] distances) {
        int entrance = level.pointToCell(phaseRoom.entrance());
        if (hero.rooted || hero.pos == entrance) return false;

        boolean[] walking = safe.clone();
        for (int cell = 0; cell < walking.length; cell++) {
            if (!level.passable[cell] && cell != hero.pos) walking[cell] = false;
        }
        PathFinder.buildDistanceMap(hero.pos, walking);
        int[] walkingDistances = PathFinder.distance.clone();
        int exit = -1;
        if (walkingDistances[entrance] != Integer.MAX_VALUE) {
            exit = entrance;
        } else {
            int nearest = Integer.MAX_VALUE;
            for (int cell = 0; cell < walking.length; cell++) {
                if (walking[cell] && walkingDistances[cell] < nearest && !containsActive(cell)) {
                    exit = cell;
                    nearest = walkingDistances[cell];
                }
            }
        }
        if (exit >= 0) {
            leaveStep(hero, exit, walking, walkingDistances);
            return true;
        }

        BlastAutoBarricade.Action breach = BlastAutoBarricade.openRoomExit(
                hero, phaseRoom, entrance, walking, walkingDistances);
        if (breach != BlastAutoBarricade.Action.NONE) return true;

        if (!safe[entrance] || distances[entrance] == Integer.MAX_VALUE) return false;
        leaveStep(hero, entrance, safe, distances);
        return true;
    }

    private static void leaveStep(Hero hero, int exit, boolean[] safe, int[] distances) {
        int step = BlastAutoPlay.firstStep(exit, safe, distances);
        BlastLog.debug("auto-crystal action=leave from=" + hero.pos + " to=" + step
                + " exit=" + exit);
        move(hero, step);
    }

    /** Reserve space for distinct prizes after accounting for stacks and specialized bags. */
    private static int requiredSlots(Hero hero, ArrayList<Item> prizes) {
        int required = 0;
        ArrayList<Item> unstacked = new ArrayList<>();
        for (Item item : prizes) {
            if (BlastAutoPickup.bypassesBackpack(item, BlastAutoPickupPolicy::isInstantPickup))
                continue;
            boolean merged = false;
            if (item.stackable) {
                for (Item held : hero.belongings.backpack) {
                    if (item.isSimilar(held)) {
                        merged = true;
                        break;
                    }
                }
                for (Item earlier : unstacked) {
                    if (item.isSimilar(earlier)) {
                        merged = true;
                        break;
                    }
                }
            }
            if (merged) continue;
            unstacked.add(item);
            required++;
        }
        // Reserve existing specialized bag slots for compatible prizes.
        for (Item held : hero.belongings.backpack) {
            if (!(held instanceof Bag)) continue;
            Bag bag = (Bag) held;
            int slots = Math.max(0, bag.capacity() - bag.items.size());
            for (Item item : new ArrayList<>(unstacked)) {
                if (slots == 0) break;
                if (bag.canHold(item)) {
                    unstacked.remove(item);
                    required--;
                    slots--;
                }
            }
        }
        return required;
    }

    private static Item chooseStagingItem(Hero hero, Level level, ArrayList<Item> prizes,
                                          int needed) {
        int cell = hero.pos;
        Heap ground = level.heaps.get(cell);
        if (!level.passable[cell] || level.pit[cell] || level.traps.get(cell) != null
                || level.plants.get(cell) != null || (ground != null && ground.type != Heap.Type.HEAP)) {
            return null;
        }

        Item best = null;
        int candidates = 0;
        for (Item item : hero.belongings.backpack.items) {
            if (item instanceof Bag || item.unique || item.isEquipped(hero)
                    || item.level() > 0 || Challenges.isItemBlocked(item)) continue;
            // Keep stacks used in the capacity estimate and avoid merging a staged item
            // into a ground stack whose identity ordinary pickup can still target.
            if (item.stackable && (prizes.stream().anyMatch(prize -> prize.isSimilar(item))
                    || (ground != null && ground.items.stream().anyMatch(held -> held.isSimilar(item))))) {
                continue;
            }
            candidates++;
            if (best == null || stagingRank(item) < stagingRank(best)
                    || (stagingRank(item) == stagingRank(best) && item.value() < best.value())) {
                best = item;
            }
        }
        return candidates >= needed ? best : null;
    }

    private static int stagingRank(Item item) {
        return item instanceof MeleeWeapon || item instanceof Armor ? 0 : 1;
    }

    private static int adjacentStand(Level level, int target,
                                     boolean[] safe, int[] distances) {
        int best = -1;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = target + offset;
            if (level.insideMap(cell) && safe[cell]
                    && distances[cell] != Integer.MAX_VALUE
                    && (best < 0 || distances[cell] < distances[best])) best = cell;
        }
        return best;
    }

    static int rank(Item item) {
        if (item instanceof RingOfWealth || item instanceof RingOfSharpshooting
                || item instanceof RingOfEnergy) return 3;
        if (item instanceof EtherealChains || item instanceof HornOfPlenty
                || item instanceof TimekeepersHourglass || item instanceof MasterThievesArmband
                || item instanceof AlchemistsToolkit || item instanceof SkeletonKey
                || item instanceof DriedRose || item instanceof UnstableSpellbook) return 2;
        if (item instanceof WandOfCorrosion || item instanceof WandOfMagicMissile
                || item instanceof WandOfFrost || item instanceof WandOfPrismaticLight
                || item instanceof WandOfBlastWave || item instanceof WandOfLightning
                || item instanceof WandOfDisintegration || item instanceof WandOfTransfusion) return 1;
        return 0;
    }

    static int chestRank(Heap heap) {
        return heap == null || heap.peek() == null ? 0 : rank(heap.peek());
    }

    /** Positive rank means this option is preferred over ordinary keyed interactions. */
    static int priority(Level level, Hero hero, int target, boolean chest) {
        if (!(level instanceof RegularLevel)) return 0;
        Room room = null;
        for (Room candidate : ((RegularLevel) level).rooms()) {
            if (candidate.inside(level.cellToPoint(target))) {
                room = candidate;
                break;
            }
        }
        if (room == null) return 0;
        if (room instanceof CrystalVaultRoom) {
            return chest ? 10 + chestRank(level.heaps.get(target)) : 0;
        }
        if (room instanceof CrystalPathRoom && !chest) {
            return pathDoorPriority(level, (CrystalPathRoom) room, target);
        }
        if (room instanceof CrystalChoiceRoom && !chest) {
            Item item = rewardBeyondDoor(level, room, target);
            return item == null ? 10 : 10 + rank(item);
        }
        return 0;
    }

    /**
     * Evaluate the complete two-lock route from the room entrance to each prize.
     * A door inherits the priority of the prize whose cheapest route uses it next.
     */
    private static int pathDoorPriority(Level level, CrystalPathRoom room, int target) {
        if (level.map[target] != Terrain.CRYSTAL_DOOR || room.entrance() == null
                || Dungeon.branch != 0) return 0;
        int keys = Notes.keyCount(new CrystalKey(Dungeon.depth));
        if (keys <= 0) return 0;
        int start = level.pointToCell(room.entrance());
        int size = level.length();
        int[] cost = new int[size];
        int[] previous = new int[size];
        boolean[] done = new boolean[size];
        Arrays.fill(cost, Integer.MAX_VALUE);
        Arrays.fill(previous, -1);
        cost[start] = 0;
        // Room is tiny; a simple minimum-cost scan avoids shared pathfinder state.
        for (int iteration = 0; iteration < size; iteration++) {
            int current = -1;
            for (int cell = 0; cell < size; cell++) {
                if (!done[cell] && cost[cell] != Integer.MAX_VALUE
                        && (current < 0 || cost[cell] < cost[current])) current = cell;
            }
            if (current < 0) break;
            done[current] = true;
            for (int offset : PathFinder.NEIGHBOURS4) {
                int next = current + offset;
                if (!level.insideMap(next) || !room.inside(level.cellToPoint(next))
                        || level.solid[next] && level.map[next] != Terrain.CRYSTAL_DOOR)
                    continue;
                int step = level.map[next] == Terrain.CRYSTAL_DOOR ? 1000 : 1;
                if (cost[current] + step < cost[next]) {
                    cost[next] = cost[current] + step;
                    previous[next] = current;
                }
            }
        }
        int best = 0;
        for (Heap heap : level.heaps.valueList()) {
            if (!room.inside(level.cellToPoint(heap.pos)) || heap.peek() == null
                    || cost[heap.pos] == Integer.MAX_VALUE) continue;
            Item item = heap.peek();
            int value = item instanceof ScrollOfTransmutation
                    || item instanceof ScrollOfMetamorphosis ? 30
                    : item instanceof PotionOfExperience
                    || item instanceof PotionOfDivineInspiration ? 20 : 10;
            int firstDoor = -1;
            int requiredKeys = 0;
            for (int cell = heap.pos; cell != start && cell >= 0; cell = previous[cell]) {
                if (level.map[cell] == Terrain.CRYSTAL_DOOR) {
                    firstDoor = cell;
                    requiredKeys++;
                }
            }
            if (firstDoor == target && requiredKeys <= keys && value > best)
                best = value;
        }
        return best;
    }

    /** Inspect the reward directly behind a crystal-choice door. */
    private static Item rewardBeyondDoor(Level level, Room room, int door) {
        boolean[] visited = new boolean[level.length()];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int offset : PathFinder.NEIGHBOURS4) {
            int cell = door + offset;
            if (level.insideMap(cell) && room.inside(level.cellToPoint(cell))
                    && level.map[cell] == Terrain.EMPTY_SP) {
                queue.add(cell);
                visited[cell] = true;
            }
        }
        Item best = null;
        int bestRank = -1;
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            Heap heap = level.heaps.get(cell);
            if (heap != null && heap.peek() != null) {
                Item item = heap.peek();
                int value = rank(item);
                if (value > bestRank) {
                    bestRank = value;
                    best = item;
                }
            }
            for (int offset : PathFinder.NEIGHBOURS4) {
                int next = cell + offset;
                if (!level.insideMap(next) || visited[next]
                        || !room.inside(level.cellToPoint(next))
                        || level.solid[next] || level.map[next] == Terrain.CRYSTAL_DOOR) continue;
                visited[next] = true;
                queue.add(next);
            }
        }
        return best;
    }

    static boolean replace(int priority, int bestPriority, int steps, int bestSteps) {
        return priority > bestPriority || (priority == bestPriority
                && (steps < bestSteps || (steps == bestSteps && Random.Int(2) == 0)));
    }
}
