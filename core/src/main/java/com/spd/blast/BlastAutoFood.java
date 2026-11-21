package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Challenges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.HornOfPlenty;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.ChargrilledMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.FrozenCarpaccio;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Pasty;
import com.shatteredpixel.shatteredpixeldungeon.items.food.StewedMeat;

/** Native automatic meals, preserving ingredients for meat pies. */
public final class BlastAutoFood {

    private static final ThreadLocal<Boolean> automaticConsumption = new ThreadLocal<>();
    private static Hero feedingHero;

    private BlastAutoFood() {}

    /** A manual action, pause, or level change ends the current meal. */
    static void stopFeeding() {
        feedingHero = null;
    }

    /** Mark native item use as automatic, including raw meat's effect suppression. */
    public static boolean isEatingAutomatically() {
        return Boolean.TRUE.equals(automaticConsumption.get());
    }

    /** Use only meat types accepted by SPD's MeatPie.Recipe. */
    static boolean pieMeat(Food food) {
        return food instanceof MysteryMeat || food instanceof StewedMeat
                || food instanceof ChargrilledMeat || food instanceof FrozenCarpaccio;
    }

    /** Preserve one ration and one meat per Pasty before comparing edible quantities. */
    static Food mostAbundant(Iterable<? extends Food> foods) {
        return mostAbundantWithin(foods, Float.POSITIVE_INFINITY, 1f);
    }

    /** Choose an available food without discarding any of its satiety. */
    static Food mostAbundantWithin(Iterable<? extends Food> foods,
                                  float remainingHunger, float foodMultiplier) {
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
                        && ((Blandfruit) food).potionAttrib == null)
                    || food.energy <= 0 || food.energy * foodMultiplier > remainingHunger) continue;

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

    /** Complete one meal across native eating turns; do not restart until Starving. */
    static boolean eatIfNeeded(Hero hero) {
        Hunger hunger = hero.buff(Hunger.class);
        if (hunger == null) {
            stopFeeding();
            return false;
        }
        if (feedingHero != hero) {
            if (!hunger.isStarving()) return false;
            feedingHero = hero;
        }

        int remainingHunger = hunger.hunger();
        if (remainingHunger <= 0) {
            stopFeeding();
            return false;
        }

        // Native horn EAT uses whole charges without exceeding the hunger level.
        int satietyPerCharge = (int) (Hunger.STARVING / 5f);
        if (Dungeon.isChallenged(Challenges.NO_FOOD)) satietyPerCharge /= 3;
        if (remainingHunger >= satietyPerCharge) {
            for (HornOfPlenty horn : hero.belongings.getAllItems(HornOfPlenty.class)) {
                if (horn.actions(hero).contains(HornOfPlenty.AC_EAT)) {
                    eatAutomatically(hero, horn, HornOfPlenty.AC_EAT);
                    return true;
                }
            }
        }

        float foodMultiplier = Dungeon.isChallenged(Challenges.NO_FOOD) ? 1f / 3f : 1f;
        Artifact.ArtifactBuff hornBuff = hero.buff(HornOfPlenty.hornRecharge.class);
        if (hornBuff != null && hornBuff.isCursed()) foodMultiplier *= 0.67f;
        Food food = mostAbundantWithin(hero.belongings.getAllItems(Food.class),
                remainingHunger, foodMultiplier);
        if (food == null) {
            stopFeeding();
            return false;
        }
        eatAutomatically(hero, food, Food.AC_EAT);
        return true;
    }
}
