package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;

/** Portable SPD floor-loot policy; Blast-specific bombs live elsewhere. */
public final class BlastAutoPickup {
    private static final int RETRY_TURNS = 20;
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

    /** One action at a time, using the existing distance map and native pickup. */
    public static boolean chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (currentHero != hero || currentLevel != level) {
            reset();
            currentHero = hero;
            currentLevel = level;
        }
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid auto-pickup distance map");
        }
        if (retryTurns > 0) retryTurns--;

        int nearest = -1;
        int shortest = Integer.MAX_VALUE;
        Item nearestItem = null;
        for (Heap heap : level.heaps.valueList()) {
            if (heap.type != Heap.Type.HEAP || !heap.seen || heap.isEmpty()) continue;

            // The native pickup takes the top item, not an arbitrary lower item.
            Item item = heap.peek();
            if (!isPickupType(item) || !canCollect(hero, item)) continue;

            int cell = heap.pos;
            if (!level.insideMap(cell) || !safe[cell]
                    || distances[cell] == Integer.MAX_VALUE) continue;
            if (retryTurns > 0 && item == attemptedItem && cell == attemptedCell) continue;
            if (distances[cell] < shortest) {
                nearest = cell;
                shortest = distances[cell];
                nearestItem = item;
            }
        }

        if (nearest < 0) return false;
        if (nearest == hero.pos) {
            // One failed pickup must not cause a rapid retry loop.
            attemptedItem = nearestItem;
            attemptedCell = nearest;
            retryTurns = RETRY_TURNS;
            hero.curAction = new HeroAction.PickUp(nearest);
        } else {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(nearest, safe, distances));
        }
        return true;
    }

    static boolean isPickupType(Item item) {
        return item instanceof Gold || item instanceof Dewdrop || item instanceof Plant.Seed
                || item instanceof Scroll || item instanceof Potion
                || item instanceof Runestone;
    }

    /** Avoid chasing full-inventory items or unusable dew. */
    private static boolean canCollect(Hero hero, Item item) {
        // Gold credits the wallet directly; it never occupies backpack space.
        if (item instanceof Gold) return true;
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
