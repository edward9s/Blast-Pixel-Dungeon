package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.LostInventory;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.Key;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

import java.util.function.Predicate;

/** Portable SPD floor-loot policy; Blast-specific bombs live elsewhere. */
public final class BlastAutoPickup {
    private static final int RETRY_TURNS = 20;
    // DriedRose's native petal upgrade cap.
    private static final int MAX_ROSE_LEVEL = 10;
    private static Hero currentHero;
    private static Level currentLevel;
    private static Item attemptedItem;
    private static int attemptedCell = -1;
    private static int retryTurns;

    private BlastAutoPickup() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        attemptedItem = null;
        attemptedCell = -1;
        retryTurns = 0;
    }

    /** Only native pickup failure starts a retry cooldown. */
    public static void onPickupFailure(Hero hero, Item item, int cell) {
        if (hero == currentHero && Dungeon.level == currentLevel
                && item == attemptedItem && cell == attemptedCell) {
            retryTurns = RETRY_TURNS;
        }
    }

    static boolean knownLoot(boolean seen, boolean visible) {
        return seen || visible;
    }

    /** One action at a time, using the existing distance map and native pickup. */
    public static boolean chooseAction(Hero hero, boolean[] safe, int[] distances,
                                       Predicate<Item> instantPickup) {
        Level level = Dungeon.level;
        if (currentHero != hero || currentLevel != level) {
            reset();
            currentHero = hero;
            currentLevel = level;
        }
        if (instantPickup == null) {
            throw new IllegalArgumentException("Instant-pickup policy is required");
        }
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid auto-pickup distance map");
        }
        if (retryTurns > 0) retryTurns--;

        int nearest = -1;
        int shortest = Integer.MAX_VALUE;
        Item nearestItem = null;
        Heap nearestHeap = null;
        for (Heap heap : level.heaps.valueList()) {
            if (heap.type != Heap.Type.HEAP || heap.isEmpty()) continue;

            int cell = heap.pos;
            if (!level.insideMap(cell) || BlastAutoTrapClearer.holdingDoorOpen(cell)
                    || BlastAutoToxicRoom.holdingDoorOpen(cell)
                    || !knownLoot(heap.seen, hero.fieldOfView[cell])
                    || !safe[cell] || distances[cell] == Integer.MAX_VALUE) continue;

            Item item = firstEligibleItem(heap, candidate ->
                    isPickupType(candidate, instantPickup)
                    && canCollect(hero, candidate, instantPickup)
                    && !(retryTurns > 0 && candidate == attemptedItem
                            && cell == attemptedCell));
            if (item == null) continue;
            if (distances[cell] < shortest) {
                nearest = cell;
                shortest = distances[cell];
                nearestItem = item;
                nearestHeap = heap;
            }
        }

        if (nearest < 0) return false;
        if (nearest == hero.pos) {
            // Native pickup consumes the top item, so expose the eligible item first.
            exposeForPickup(nearestHeap, nearestItem);
            // Record the candidate; the native failure callback alone starts cooldown.
            attemptedItem = nearestItem;
            attemptedCell = nearest;
            hero.curAction = new HeroAction.PickUp(nearest);
        } else {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(nearest, safe, distances));
        }
        return true;
    }

    /**
     * Explain the closest currently visible floor item without altering pickup state.
     * Debug-only: prioritizes nearby gold, so a missed coin is easy to identify.
     */
    static String nearbyDiagnostic(Hero hero, boolean[] safe, int[] distances,
                                   boolean[] pickupSafe, Predicate<Item> instantPickup) {
        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()
                || pickupSafe.length != level.length()) {
            throw new IllegalArgumentException("Invalid pickup diagnostic map");
        }
        Heap selected = null;
        Item selectedItem = null;
        int nearest = 3;
        for (Heap heap : level.heaps.valueList()) {
            int cell = heap.pos;
            if (heap.type != Heap.Type.HEAP || heap.isEmpty()
                    || !level.insideMap(cell) || !hero.fieldOfView[cell]) continue;
            int distance = level.distance(hero.pos, cell);
            if (distance > 2) continue;
            Item candidate = firstEligibleItem(heap, item ->
                    isPickupType(item, instantPickup)
                    && canCollect(hero, item, instantPickup));
            if (candidate == null) candidate = heap.peek();
            if (selected == null
                    || (candidate instanceof Gold && !(selectedItem instanceof Gold))
                    || ((candidate instanceof Gold) == (selectedItem instanceof Gold)
                        && distance < nearest)) {
                selected = heap;
                selectedItem = candidate;
                nearest = distance;
            }
        }
        if (selected == null) return null;

        int cell = selected.pos;
        Item item = selectedItem;
        String status;
        if (BlastAutoTrapClearer.holdingDoorOpen(cell)) status = "door-held";
        else if (!isPickupType(item, instantPickup)) status = "type";
        else if (!canCollect(hero, item, instantPickup)) {
            if (item instanceof DriedRose.Petal) status = "rose-unavailable";
            else if (item instanceof Dewdrop) status = "dew-full";
            else if (hero.buff(LostInventory.class) != null
                    && !item.keptThroughLostInventory()) status = "lost-inventory";
            else status = "full";
        }
        else if (!safe[cell]) status = "unsafe";
        else if (!pickupSafe[cell]) status = "blocked";
        else if (distances[cell] == Integer.MAX_VALUE) status = "unreachable";
        else if (retryTurns > 0 && item == attemptedItem && cell == attemptedCell) {
            status = "cooldown";
        } else status = "ready";
        String type = item instanceof Gold ? "Gold" : item.getClass().getSimpleName();
        String result = type + "@" + cell + " status=" + status;
        if ("full".equals(status) || "lost-inventory".equals(status)) {
            return result + " " + inventoryCapacity(hero);
        }
        return result;
    }

    /** Inspect capacity only for a rejected pickup while Debug Log is enabled. */
    private static String inventoryCapacity(Hero hero) {
        Bag backpack = hero.belongings.backpack;
        StringBuilder result = new StringBuilder("backpack=")
                .append(backpack.items.size()).append('/').append(backpack.capacity());
        for (Item item : backpack) {
            if (item instanceof Bag) {
                Bag bag = (Bag) item;
                result.append(' ').append(item.getClass().getSimpleName())
                        .append('=').append(bag.items.size()).append('/').append(bag.capacity());
            }
        }
        return result.toString();
    }

    /** Pick the first eligible item, even when an ineligible item covers it. */
    static Item firstEligibleItem(Heap heap, Predicate<Item> eligible) {
        for (Item item : heap.items) {
            if (eligible.test(item)) return item;
        }
        return null;
    }

    /** Reorder only during automatic pickup; native doPickUp still owns the result. */
    static void exposeForPickup(Heap heap, Item item) {
        if (heap.peek() == item) return;
        if (!heap.items.remove(item)) {
            throw new IllegalStateException("Automatic pickup item missing from heap");
        }
        heap.items.addFirst(item);
        if (heap.sprite != null) heap.sprite.view(heap).place(heap.pos);
    }

    static boolean isPickupType(Item item, Predicate<Item> instantPickup) {
        return item.stackable || instantPickup.test(item);
    }

    /** These items are consumed on pickup without entering the backpack. */
    static boolean bypassesBackpack(Item item, Predicate<Item> instantPickup) {
        return item instanceof Key || item instanceof Gold
                || item instanceof EnergyCrystal || item instanceof DriedRose.Petal
                || instantPickup.test(item);
    }

    static boolean canUpgradeRose(DriedRose rose) {
        return rose != null && rose.level() < MAX_ROSE_LEVEL;
    }

    private static boolean canCollect(Hero hero, Item item, Predicate<Item> instantPickup) {
        if (item instanceof DriedRose.Petal) {
            return canUpgradeRose(hero.belongings.getItem(DriedRose.class));
        }
        if (bypassesBackpack(item, instantPickup)) return true;
        if (item instanceof Dewdrop) {
            Waterskin waterskin = hero.belongings.getItem(Waterskin.class);
            if (waterskin != null && !waterskin.isFull()) return true;
            if (hero.HP < hero.HT) return true;

            int shieldCap = Math.round(hero.HT * 0.2f
                    * hero.pointsInTalent(Talent.SHIELDING_DEW));
            Barrier barrier = hero.buff(Barrier.class);
            return shieldCap > 0 && (barrier == null || barrier.shielding() < shieldCap);
        }

        Bag backpack = hero.belongings.backpack;
        if (backpack.canHold(item)) return true;
        for (Item held : backpack) {
            if (held instanceof Bag && ((Bag) held).canHold(item)) return true;
        }
        return false;
    }
}
