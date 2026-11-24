package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.*;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfExperience;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.*;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTransmutation;
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
    private BlastAutoCrystalRooms() {}

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
            return pathDoorPriority(level, room, target);
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
    private static int pathDoorPriority(Level level, Room room, int target) {
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
            int value = item instanceof ScrollOfTransmutation ? 30
                    : item instanceof PotionOfExperience ? 20 : 10;
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

    static boolean replace(int priority, int bestPriority, int steps, int bestSteps) {
        return priority > bestPriority || (priority == bestPriority
                && (steps < bestSteps || (steps == bestSteps && Random.Int(2) == 0)));
    }
}
