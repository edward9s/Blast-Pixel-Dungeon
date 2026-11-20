package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.SmallRation;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;

import java.util.Arrays;
import java.util.Collections;

/** Regression checks for automatic starvation food selection. */
public final class BlastAutoFoodTest {

    private static void same(Food expected, Food actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        Food ration = new Food();
        ration.quantity(2);
        SmallRation smallRation = new SmallRation();
        smallRation.quantity(3);
        MysteryMeat rawMeat = new MysteryMeat();
        rawMeat.quantity(50);
        Blandfruit rawFruit = new Blandfruit();
        rawFruit.quantity(40);
        Blandfruit cookedFruit = new Blandfruit();
        cookedFruit.potionAttrib = new PotionOfHealing();
        cookedFruit.quantity(4);

        same(null, BlastAutoFood.mostAbundant(Collections.emptyList()),
                "no food");
        same(null, BlastAutoFood.mostAbundant(Arrays.asList(rawMeat, rawFruit)),
                "raw food must not trigger an empty eating action");
        same(smallRation, BlastAutoFood.mostAbundant(Arrays.asList(
                rawMeat, rawFruit, ration, smallRation)),
                "largest edible stack wins, even against more raw food");
        same(cookedFruit, BlastAutoFood.mostAbundant(Arrays.asList(
                smallRation, cookedFruit)),
                "cooked fruit is edible");
        ration.quantity(3);
        same(ration, BlastAutoFood.mostAbundant(Arrays.asList(ration, smallRation)),
                "equal quantity preserves inventory order");
        ration.quantity(0);
        same(smallRation, BlastAutoFood.mostAbundant(Arrays.asList(ration, smallRation)),
                "empty stack is ignored");

        System.out.println("BlastAutoFoodTest passed");
    }
}
