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
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.Key;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CeremonialCandle;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CorpseDust;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.Embers;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.Ring;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEnergy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfSharpshooting;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfWealth;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfUpgrade;
import com.shatteredpixel.shatteredpixeldungeon.items.trinkets.TrinketCatalyst;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

import java.util.IdentityHashMap;
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
    private static final IdentityHashMap<Item, Integer> retries = new IdentityHashMap<>();

    private BlastAutoPickup() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        attemptedItem = null;
        attemptedCell = -1;
        retries.clear();
    }

    /** Sell equipment picked up for sale as part of the successful native pickup. */
    public static void onPickupSuccess(Hero hero, Item item, int cell) {
        if (hero == currentHero && Dungeon.level == currentLevel
                && item == attemptedItem && cell == attemptedCell) {
            attemptedItem = null;
            attemptedCell = -1;
            BlastAutoPlay.onPickupProgress();
            BlastAutoEquipment.onPickupCompleted(hero, item);
        }
    }

    /** Only native pickup failure starts a retry cooldown. */
    public static void onPickupFailure(Hero hero, Item item, int cell) {
        if (hero == currentHero && Dungeon.level == currentLevel
                && item == attemptedItem && cell == attemptedCell) {
            BlastAutoEquipment.onPickupFailed(item);
            attemptedItem = null;
            attemptedCell = -1;
            retries.put(item, RETRY_TURNS);
        }
    }

    static void advanceRetries() {
        retries.replaceAll((item, turns) -> turns - 1);
        retries.values().removeIf(turns -> turns <= 0);
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

        int nearest = -1;
        int shortest = Integer.MAX_VALUE;
        int bestPriority = -1;
        Bag backpack = hero.belongings.backpack;
        boolean lastSlot = backpack.capacity() - backpack.items.size() == 1;
        Item nearestItem = null;
        Heap nearestHeap = null;
        for (Heap heap : level.heaps.valueList()) {
            if (heap.type != Heap.Type.HEAP || heap.isEmpty()) continue;

            int cell = heap.pos;
            if (!level.insideMap(cell) || BlastAutoTrapClearer.holdingDoorOpen(cell)
                    || BlastAutoToxicRoom.holdingDoorOpen(cell)
                    || !knownLoot(heap.seen, hero.fieldOfView[cell])
                    || !safe[cell] || distances[cell] == Integer.MAX_VALUE) continue;

            for (Item item : heap.items) {
                int priority = pickupPriority(item);
                if (priority < bestPriority || (priority == bestPriority
                        && distances[cell] >= shortest)) continue;
                if (BlastAutoCrystalRooms.isStaged(item)
                        || BlastAutoWandmaker.isPlacedCandle(level, cell, item)
                        || !(isPickupType(item, instantPickup)
                            || BlastAutoEquipment.canPickup(hero, item))
                        || !canCollect(hero, item, instantPickup)
                        || (lastSlot && !BlastAutoEquipment.useLastSlot(hero, item))) continue;
                nearest = cell;
                shortest = distances[cell];
                bestPriority = priority;
                nearestItem = item;
                nearestHeap = heap;
            }
        }

        if (nearest < 0) return false;
        if (nearest == hero.pos) {
            preparePickup(hero, nearestHeap, nearestItem);
            hero.curAction = new HeroAction.PickUp(nearest);
        } else {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(nearest, safe, distances));
        }
        return true;
    }

    private static int pickupPriority(Item item) {
        if (item instanceof SpiritBow) return 3;
        if (item instanceof Key) return 2;
        if (item instanceof PotionOfStrength || item instanceof ScrollOfUpgrade
                || item instanceof CeremonialCandle) return 1;
        return 0;
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
                    !BlastAutoWandmaker.isPlacedCandle(level, cell, item)
                    && (isPickupType(item, instantPickup)
                            || BlastAutoEquipment.canPickup(hero, item))
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
        if (BlastAutoTrapClearer.holdingDoorOpen(cell)
                || BlastAutoToxicRoom.holdingDoorOpen(cell)) status = "door-held";
        else if (BlastAutoWandmaker.isPlacedCandle(level, cell, item)) status = "ritual-candle-placed";
        else if (uncollectibleBomb(item)) status = "lit-bomb";
        else if (item instanceof Wand && item.cursed) status = "cursed-wand";
        else if (item instanceof Wand && !BlastAutoCombat.supportsWand((Wand) item))
            status = "unsupported-wand";
        else if (BlastAutoEquipment.blocksCursedPickup(hero, item)) status = "cursed";
        else if (!isPickupType(item, instantPickup)
                && !BlastAutoEquipment.canPickup(hero, item)) status = "type";
        else if (retries.containsKey(item)) status = "cooldown";
        else if (!canCollect(hero, item, instantPickup)) {
            if (item instanceof DriedRose.Petal) status = "rose-unavailable";
            else if (item instanceof Dewdrop) status = "dew-full";
            else if (hero.buff(LostInventory.class) != null
                    && !item.keptThroughLostInventory()) status = "lost-inventory";
            else status = "full";
        }
        else if (hero.belongings.backpack.capacity() - hero.belongings.backpack.items.size() == 1
                && !BlastAutoEquipment.useLastSlot(hero, item)) status = "reserved-slot";
        else if (!safe[cell]) status = "unsafe";
        else if (!pickupSafe[cell]) status = "blocked";
        else if (distances[cell] == Integer.MAX_VALUE) status = "unreachable";
        else status = "ready";
        String type = item instanceof Gold ? "Gold" : item.getClass().getSimpleName();
        String result = type + "@" + cell + " status=" + status;
        if ("type".equals(status) || "cursed".equals(status)) {
            String reason = BlastAutoEquipment.pickupRejection(hero, item);
            if (reason != null) return result + " " + reason + " " + inventoryCapacity(hero);
        }
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

    /** Keep native pickup callbacks consistent across ordinary and room looting. */
    static void preparePickup(Hero hero, Heap heap, Item item) {
        if (currentHero != hero || currentLevel != Dungeon.level) {
            reset();
            currentHero = hero;
            currentLevel = Dungeon.level;
        }
        exposeForPickup(heap, item);
        attemptedItem = item;
        attemptedCell = heap.pos;
        BlastAutoEquipment.onPickupScheduled(hero, item);
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

    private static boolean uncollectibleBomb(Item item) {
        return item instanceof BlastDeathBomb || (item instanceof Bomb && ((Bomb) item).fuse != null);
    }

    static boolean isPickupType(Item item, Predicate<Item> instantPickup) {
        if (item instanceof SpiritBow || item instanceof CorpseDust || item instanceof Embers) return true;
        if (item instanceof Bomb || item instanceof BlastDeathBomb) return !uncollectibleBomb(item);
        if (item instanceof Wand) return !item.cursed && BlastAutoCombat.supportsWand((Wand) item);
        if (item instanceof Ring) {
            return item instanceof RingOfWealth
                    || item instanceof RingOfSharpshooting
                    || item instanceof RingOfHaste
                    || item instanceof RingOfEnergy;
        }
        return item instanceof TrinketCatalyst || item.stackable || instantPickup.test(item);
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

    static boolean canCollect(Hero hero, Item item, Predicate<Item> instantPickup) {
        if (retries.containsKey(item) || uncollectibleBomb(item)
                || (item instanceof Wand && item.cursed)
                || BlastAutoEquipment.blocksCursedPickup(hero, item)) return false;
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

        if (item instanceof Bomb.DoubleBomb) {
            // The native pickup gives ordinary Bomb items, not a DoubleBomb stack.
            Bomb bomb = new Bomb();
            bomb.blastSetManiacCharge(((Bomb) item).blastManiacCharge());
            item = bomb;
        }
        Bag backpack = hero.belongings.backpack;
        if (backpack.canHold(item)) return true;
        for (Item held : backpack) {
            if (held instanceof Bag && ((Bag) held).canHold(item)) return true;
        }
        return false;
    }
}
