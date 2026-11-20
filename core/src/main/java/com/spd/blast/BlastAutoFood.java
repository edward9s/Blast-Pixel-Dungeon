package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;

/** Native eating during idle auto-play; raw meat is made safe by the native meat patch. */
public final class BlastAutoFood {

    private static final ThreadLocal<Boolean> automaticConsumption = new ThreadLocal<>();

    private BlastAutoFood() {}

    /** Suppress only the manual-item reset caused by native Food.execute. */
    public static boolean isEatingAutomatically() {
        return Boolean.TRUE.equals(automaticConsumption.get());
    }

    static Food mostAbundant(Iterable<? extends Food> foods) {
        Food selected = null;
        for (Food food : foods) {
            if (food.quantity() <= 0 || (food instanceof Blandfruit
                        && ((Blandfruit) food).potionAttrib == null)) continue;
            if (selected == null || food.quantity() > selected.quantity()) {
                selected = food;
            }
        }
        return selected;
    }

    /** Returns true when native eating has started its own animated turn. */
    static boolean eatIfStarving(Hero hero) {
        Hunger hunger = hero.buff(Hunger.class);
        if (hunger == null || !hunger.isStarving()) return false;

        Food food = mostAbundant(hero.belongings.getAllItems(Food.class));
        if (food == null) return false;

        automaticConsumption.set(true);
        try {
            food.execute(hero, Food.AC_EAT);
        } finally {
            automaticConsumption.remove();
        }
        return true;
    }
}
