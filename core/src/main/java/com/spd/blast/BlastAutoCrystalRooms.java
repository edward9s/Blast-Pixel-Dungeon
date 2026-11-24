package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
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
    private static boolean phaseWasEnabled;
    private static boolean phaseActive;
    private static final Set<Item> staged = Collections.newSetFromMap(
            new IdentityHashMap<Item, Boolean>());

    private BlastAutoCrystalRooms() {}

    static void resetPhase() {
        if (phaseActive) BlastSettings.setTestFreeMovement(phaseWasEnabled);
        phaseLevel = null;
        phaseActive = false;
        staged.clear();
    }

    /** Phase through remaining crystal locks after spending the last crystal key. */
    static void updatePhase(Hero hero) {
        Level level = Dungeon.level;
        if (phaseActive && phaseLevel != level) resetPhase();
        CrystalPathRoom room = level instanceof RegularLevel
                ? (CrystalPathRoom) ((RegularLevel) level).room(CrystalPathRoom.class) : null;
        boolean pending = false;
        if (room != null && room.entrance() != null && Dungeon.branch == 0
                && Notes.keyCount(new CrystalKey(Dungeon.depth)) == 0
                && remainingCrystalDoors(level, room) <= 3
                && (level.visited[level.pointToCell(room.entrance())]
                    || level.mapped[level.pointToCell(room.entrance())]
                    || hero.fieldOfView[level.pointToCell(room.entrance())])) {
            for (Heap heap : level.heaps.valueList()) {
                if (room.inside(level.cellToPoint(heap.pos))
                        && heap.peek() != null) {
                    pending = true;
                    break;
                }
            }
        }
        if (pending && !phaseActive) {
            phaseWasEnabled = BlastSettings.testFreeMovement();
            phaseLevel = level;
            phaseActive = true;
            BlastSettings.setTestFreeMovement(true);
            BlastLog.debug("auto-crystal action=enable-phase");
        } else if (!pending && phaseActive) {
            resetPhase();
            BlastLog.debug("auto-crystal action=restore-phase");
        }
    }

    /** The room starts with six locks and generates three crystal keys. */
    private static int remainingCrystalDoors(Level level, CrystalPathRoom room) {
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
    static boolean collect(Hero hero, boolean[] safe, int[] distances) {
        if (!phaseActive || phaseLevel != Dungeon.level) return false;
        Level level = Dungeon.level;
        CrystalPathRoom room = (CrystalPathRoom) ((RegularLevel) level).room(CrystalPathRoom.class);
        if (room == null) return false;

        ArrayList<Item> prizes = new ArrayList<>();
        Heap target = null;
        int priority = Integer.MIN_VALUE;
        int distance = Integer.MAX_VALUE;
        for (Heap heap : level.heaps.valueList()) {
            if (!room.inside(level.cellToPoint(heap.pos)) || heap.type != Heap.Type.HEAP
                    || heap.isEmpty()) continue;
            for (Item item : heap.items) {
                if (staged.contains(item)) continue;
                prizes.add(item);
                int score = item instanceof ScrollOfTransmutation
                        || item instanceof ScrollOfMetamorphosis ? 30
                        : item instanceof PotionOfExperience
                        || item instanceof PotionOfDivineInspiration ? 20 : 10;
                if (safe[heap.pos] && distances[heap.pos] != Integer.MAX_VALUE
                        && (score > priority || score == priority
                            && distances[heap.pos] < distance)) {
                    target = heap;
                    priority = score;
                    distance = distances[heap.pos];
                }
            }
        }
        if (prizes.isEmpty() || target == null) return false;

        // Reserve all missing ordinary inventory slots before approaching the prizes.
        int required = 0;
        for (Item item : prizes) {
            if (BlastAutoPickup.bypassesBackpack(item, BlastAutoPickupPolicy::isInstantPickup)
                    || hero.belongings.backpack.canHold(item)) continue;
            boolean fitsBag = false;
            for (Item held : hero.belongings.backpack) {
                if (held instanceof Bag && ((Bag) held).canHold(item)) {
                    fitsBag = true;
                    break;
                }
            }
            if (!fitsBag) required++;
        }
        int free = hero.belongings.backpack.capacity()
                - hero.belongings.backpack.items.size();
        int toDrop = Math.max(0, required - free);
        if (toDrop > 0) {
            Item candidate = null;
            for (Item item : hero.belongings.backpack) {
                if (!(item instanceof MeleeWeapon || item instanceof Armor)
                        || item.isEquipped(hero) || staged.contains(item)
                        || item.level() > 0) continue;
                if (candidate == null || item.level() < candidate.level())
                    candidate = item;
            }
            if (candidate != null) {
                staged.add(candidate);
                BlastLog.debug("auto-crystal action=stage item="
                        + candidate.getClass().getSimpleName()
                        + " required=" + required + " free=" + free);
                candidate.doDrop(hero);
                return true;
            }
            BlastLog.debug("auto-crystal action=inventory-full required="
                    + required + " free=" + free);
        }
        if (hero.pos == target.pos) {
            Item item = null;
            for (Item prize : target.items) {
                if (!staged.contains(prize)) {
                    item = prize;
                    break;
                }
            }
            if (item == null) return false;
            if (!BlastAutoPickup.canCollect(hero, item,
                    BlastAutoPickupPolicy::isInstantPickup)) return false;
            BlastAutoPickup.preparePickup(hero, target, item);
            hero.curAction = new HeroAction.PickUp(target.pos);
        } else if (!hero.rooted) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(target.pos, safe, distances));
        } else return false;
        return true;
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
