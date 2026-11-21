package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.food.Blandfruit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.ChargrilledMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.FrozenCarpaccio;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MeatPie;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Pasty;
import com.shatteredpixel.shatteredpixeldungeon.items.food.SmallRation;
import com.shatteredpixel.shatteredpixeldungeon.items.food.StewedMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;

import java.util.Arrays;
import java.util.Collections;

/** Regression checks for native starvation food selection and pie ingredient reserves. */
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

        same(null, BlastAutoFood.mostAbundant(Collections.emptyList()), "no food");
        same(null, BlastAutoFood.mostAbundantWithin(Collections.emptyList(), 450, 1f),
                "no usable food within hunger budget");
        same(null, BlastAutoFood.mostAbundant(Collections.singletonList(rawFruit)),
                "uncooked blandfruit must not trigger an empty eating action");
        same(rawMeat, BlastAutoFood.mostAbundant(Arrays.asList(rawMeat, rawFruit)),
                "raw meat is an automatic food candidate");
        same(rawMeat, BlastAutoFood.mostAbundant(Arrays.asList(
                rawMeat, rawFruit, ration, smallRation)),
                "largest available stack wins, including raw meat");
        same(cookedFruit, BlastAutoFood.mostAbundant(Arrays.asList(
                smallRation, cookedFruit)), "cooked fruit is edible");
        ration.quantity(3);
        same(ration, BlastAutoFood.mostAbundant(Arrays.asList(ration, smallRation)),
                "equal quantity preserves inventory order");
        rawMeat.quantity(3);
        same(rawMeat, BlastAutoFood.mostAbundant(Arrays.asList(rawMeat, smallRation)),
                "raw meat wins equal-stack ties when first");
        rawMeat.quantity(50);
        ration.quantity(0);
        same(smallRation, BlastAutoFood.mostAbundant(Arrays.asList(ration, smallRation)),
                "empty stack is ignored");

        Pasty pasty = new Pasty();
        pasty.quantity(3);
        MeatPie pie = new MeatPie();
        pie.quantity(1);
        same(null, BlastAutoFood.mostAbundant(Collections.singletonList(pasty)),
                "never eat pasty automatically");
        same(pie, BlastAutoFood.mostAbundant(Arrays.asList(pasty, pie)),
                "finished meat pie remains edible");

        ration.quantity(3);
        rawMeat.quantity(3);
        smallRation.quantity(1);
        same(smallRation, BlastAutoFood.mostAbundant(Arrays.asList(
                pasty, ration, rawMeat, smallRation)),
                "all three rations and meats remain reserved for three pasties");

        ration.quantity(5);
        rawMeat.quantity(4);
        same(ration, BlastAutoFood.mostAbundant(Arrays.asList(pasty, ration, rawMeat)),
                "eat surplus ration before protected meat (2 versus 1)");

        ration.quantity(3);
        rawMeat.quantity(8);
        same(rawMeat, BlastAutoFood.mostAbundant(Arrays.asList(pasty, ration, rawMeat)),
                "eat only surplus meat when ration supply exactly matches pasties");

        ChargrilledMeat grilled = new ChargrilledMeat();
        grilled.quantity(4);
        StewedMeat stewed = new StewedMeat();
        stewed.quantity(2);
        FrozenCarpaccio frozen = new FrozenCarpaccio();
        frozen.quantity(1);
        rawMeat.quantity(1);
        pasty.quantity(8);
        ration.quantity(0);
        same(null, BlastAutoFood.mostAbundant(Arrays.asList(
                pasty, rawMeat, grilled, stewed, frozen, ration)),
                "all native pie-compatible meat variants count toward the reserve");
        pasty.quantity(5);
        same(grilled, BlastAutoFood.mostAbundant(Arrays.asList(
                pasty, rawMeat, grilled, stewed, frozen)),
                "surplus mixed meats are edible without consuming protected total");

        // A meal may span several native turns, but never spend more satiety than is missing.
        ration.quantity(2);
        rawMeat.quantity(2);
        pasty.quantity(0);
        same(rawMeat, BlastAutoFood.mostAbundantWithin(Arrays.asList(ration, rawMeat), 150, 1f),
                "fit 150-point meat rather than waste a 300-point ration");
        same(null, BlastAutoFood.mostAbundantWithin(Collections.singletonList(ration), 150, 1f),
                "do not eat a ration whose energy would overflow");
        same(ration, BlastAutoFood.mostAbundantWithin(Collections.singletonList(ration), 300, 1f),
                "exactly matching ration remains edible");
        same(ration, BlastAutoFood.mostAbundantWithin(Collections.singletonList(ration), 100, 1f / 3f),
                "diet challenge uses actual reduced food satiety");
        same(null, BlastAutoFood.mostAbundantWithin(Collections.singletonList(rawMeat), 100, 0.67f),
                "cursed horn satiety still must fit remaining hunger");
        same(rawMeat, BlastAutoFood.mostAbundantWithin(Collections.singletonList(rawMeat), 101, 0.67f),
                "cursed horn reduced food satiety may fit");
        same(null, BlastAutoFood.mostAbundantWithin(Collections.singletonList(pie), 450, 1f),
                "do not waste a high-energy meat pie at normal satiety");
        System.out.println("BlastAutoFoodTest passed");
    }
}
