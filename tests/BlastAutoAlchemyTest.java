package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.food.ChargrilledMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.FrozenCarpaccio;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MeatPie;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MysteryMeat;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Pasty;
import com.shatteredpixel.shatteredpixeldungeon.items.food.SmallRation;
import com.shatteredpixel.shatteredpixeldungeon.items.food.StewedMeat;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/** Real SPD recipe and ingredient-policy tests without a game scene. */
public final class BlastAutoAlchemyTest {

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    private static void ingredients(Food pasty, Food ration, Food meat) {
        ArrayList<Item> inputs = BlastAutoAlchemy.pieIngredients(
                Arrays.asList(meat, ration, pasty));
        check(inputs != null, "three valid inputs selected");
        check(inputs.get(0) == pasty, "pasty source kept");
        check(inputs.get(1) == ration, "ration source kept");
        check(inputs.get(2) == meat, "meat source kept");
        MeatPie.Recipe recipe = new MeatPie.Recipe();
        check(recipe.testIngredients(inputs), "native recipe accepts selected inputs");
        check(recipe.cost(inputs) == 6, "native energy cost");
    }

    private static void knownPotDiscovery() {
        int[] terrain = {Terrain.EMPTY, Terrain.ALCHEMY, Terrain.EMPTY};
        boolean[] visible = new boolean[3];
        boolean[] visited = new boolean[3];
        boolean[] mapped = new boolean[3];

        check(!BlastAutoAlchemy.knownPot(terrain, visible, visited, mapped),
                "unknown pot cannot trigger automatic alchemy");

        visible[1] = true;
        check(BlastAutoAlchemy.knownPot(terrain, visible, visited, mapped),
                "currently visible pot qualifies");
        visible[1] = false;

        visited[1] = true;
        check(BlastAutoAlchemy.knownPot(terrain, visible, visited, mapped),
                "previously seen pot qualifies after leaving sight");
        visited[1] = false;

        mapped[1] = true;
        check(BlastAutoAlchemy.knownPot(terrain, visible, visited, mapped),
                "magic-mapped pot qualifies");
        mapped[1] = false;

        visible[0] = true;
        visited[2] = true;
        check(!BlastAutoAlchemy.knownPot(terrain, visible, visited, mapped),
                "knowledge of unrelated tiles must not reveal an unseen pot");

        check(!BlastAutoAlchemy.knownPot(new int[] {Terrain.EMPTY},
                new boolean[] {true}, new boolean[] {true}, new boolean[] {true}),
                "mapped terrain without a pot must not trigger alchemy");
    }

    private static void toolkitEnergyPolicy() {
        check(!BlastAutoAlchemy.shouldUseToolkit(true, 6, 6, 6),
                "known pot uses global energy first");
        check(BlastAutoAlchemy.shouldUseToolkit(false, 0, 6, 6),
                "toolkit-only energy works without a pot");
        check(BlastAutoAlchemy.shouldUseToolkit(false, 3, 3, 6),
                "toolkit and global energy can be combined");
        check(BlastAutoAlchemy.shouldUseToolkit(false, 6, 0, 6),
                "ready empty toolkit can provide portable alchemy with global energy");
        check(BlastAutoAlchemy.shouldUseToolkit(true, 3, 3, 6),
                "toolkit supplements insufficient pot energy");
        check(!BlastAutoAlchemy.shouldUseToolkit(false, 2, 3, 6),
                "insufficient combined energy cannot brew");
        check(!BlastAutoAlchemy.shouldUseToolkit(true, 2, 3, 6),
                "known pot with insufficient combined energy cannot brew");
    }

    public static void main(String[] args) {
        knownPotDiscovery();
        toolkitEnergyPolicy();
        Pasty pasty = new Pasty();
        Food ration = new Food();
        MysteryMeat meat = new MysteryMeat();
        pasty.quantity(2);
        ration.quantity(2);
        meat.quantity(2);

        ingredients(pasty, ration, meat);
        ingredients(pasty, ration, new StewedMeat());
        ingredients(pasty, ration, new ChargrilledMeat());
        ingredients(pasty, ration, new FrozenCarpaccio());

        check(BlastAutoAlchemy.pieIngredients(Collections.emptyList()) == null,
                "no materials");
        check(BlastAutoAlchemy.pieIngredients(Arrays.asList(ration, meat)) == null,
                "pasty missing");
        check(BlastAutoAlchemy.pieIngredients(Arrays.asList(pasty, meat)) == null,
                "ration missing");
        check(BlastAutoAlchemy.pieIngredients(Arrays.asList(pasty, ration)) == null,
                "meat missing");
        check(BlastAutoAlchemy.pieIngredients(Arrays.asList(
                pasty, new SmallRation(), meat)) == null,
                "small ration is not a native pie ingredient");
        Food emptyRation = new Food();
        emptyRation.quantity(0);
        check(BlastAutoAlchemy.pieIngredients(Arrays.asList(
                pasty, emptyRation, meat)) == null,
                "zero-quantity food ignored");

        ArrayList<Item> inputs = BlastAutoAlchemy.pieIngredients(
                Arrays.asList(pasty, ration, meat));
        check(new MeatPie.Recipe().brew(inputs) instanceof MeatPie,
                "use native recipe output");
        check(pasty.quantity() == 1 && ration.quantity() == 1 && meat.quantity() == 1,
                "native recipe consumes exactly one of each");

        System.out.println("BlastAutoAlchemyTest passed");
    }
}
