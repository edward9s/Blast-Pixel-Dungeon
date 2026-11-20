package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.ChargrilledMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.FrozenCarpaccio;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MeatPie;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Pasty;
import com.shatteredpixel.shatteredpixeldungeon.items.food.StewedMeat;

/** Native Starving-time eating, preserving ingredients for meat pies. */
public final class BlastAutoFood {

    private static final ThreadLocal<Boolean> automaticConsumption = new ThreadLocal<>();

    private BlastAutoFood() {}

    /** Mark native item use as automatic, including raw meat's effect suppression. */
    public static boolean isEatingAutomatically() {
        return Boolean.TRUE.equals(automaticConsumption.get());
    }

    /** Use only meat types accepted by SPD's MeatPie.Recipe. */
    private static boolean pieMeat(Food food) {
        return food instanceof MysteryMeat || food instanceof StewedMeat
                || food instanceof ChargrilledMeat || food instanceof FrozenCarpaccio;
    }

    /** Preserve one ration and one meat per Pasty before comparing edible quantities. */
    static Food mostAbundant(Iterable<? extends Food> foods) {
        int pasties = 0;
        int rations = 0;
        int meats = 0;
        for (Food food : foods) {
            int quantity = food.quantity();
            if (food instanceof Pasty) pasties += quantity;
            else if (food.getClass() == Food.class) rations += quantity;
            else if (pieMeat(food)) meats += quantity;
        }

        int availableRations = Math.max(0, rations - pasties);
        int availableMeats = Math.max(0, meats - pasties);
        Food selected = null;
        int mostAvailable = 0;
        for (Food food : foods) {
            if (food.quantity() <= 0 || food instanceof Pasty
                    || (food instanceof Blandfruit
                        && ((Blandfruit) food).potionAttrib == null)) continue;

            int available = food.quantity();
            if (food.getClass() == Food.class) {
                available = Math.min(available, availableRations);
            } else if (pieMeat(food)) {
                available = Math.min(available, availableMeats);
            }
            if (available > mostAvailable) {
                selected = food;
                mostAvailable = available;
            }
        }
        return selected;
    }

    /** Keep native Food/Horn actions on the hero's actor turn. */
    private static void eatAutomatically(Hero hero, Item item, String action) {
        automaticConsumption.set(true);
        try {
            item.execute(hero, action);
        } finally {
            automaticConsumption.remove();
        }
    }

    /** Returns true when native eating has started its own animated turn. */
    static boolean eatIfStarving(Hero hero) {
        Hunger hunger = hero.buff(Hunger.class);
        if (hunger == null || !hunger.isStarving()) return false;

        // Native actions encode equipped status, available charges, and magic immunity.
        for (HornOfPlenty horn : hero.belongings.getAllItems(HornOfPlenty.class)) {
            if (horn.actions(hero).contains(HornOfPlenty.AC_EAT)) {
                eatAutomatically(hero, horn, HornOfPlenty.AC_EAT);
                return true;
            }
        }

        Food food = mostAbundant(hero.belongings.getAllItems(Food.class));
        if (food == null) return false;
        eatAutomatically(hero, food, Food.AC_EAT);
        return true;
    }
}
