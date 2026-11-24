package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
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

import java.util.ArrayDeque;

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
            Item item = rewardBeyondDoor(level, room, target);
            if (item instanceof ScrollOfTransmutation) return 30;
            if (item instanceof PotionOfExperience) return 20;
            return 10;
        }
        if (room instanceof CrystalChoiceRoom && !chest) {
            Item item = rewardBeyondDoor(level, room, target);
            return item == null ? 10 : 10 + rank(item);
        }
        return 0;
    }

    /** Find a reward reachable immediately behind this door, without crossing another crystal door. */
    private static Item rewardBeyondDoor(Level level, Room room, int door) {
        boolean[] visited = new boolean[level.length()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
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
                if (item instanceof ScrollOfTransmutation) value = 30;
                else if (item instanceof PotionOfExperience) value = 20;
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
