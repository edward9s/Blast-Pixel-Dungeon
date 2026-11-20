package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MeatPie;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Pasty;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

/** Idle auto-play crafting at an adjacent native alchemy pot; never navigates to one. */
final class BlastAutoAlchemy {

    private static final MeatPie.Recipe PIE = new MeatPie.Recipe();

    private BlastAutoAlchemy() {}

    static boolean besidePot(Level level, int position) {
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = position + offset;
            if (level.insideMap(cell) && level.distance(position, cell) <= 1
                    && level.map[cell] == Terrain.ALCHEMY) return true;
        }
        return false;
    }

    /** Select exactly one native pie ingredient of each kind from the inventory. */
    static ArrayList<Item> pieIngredients(Iterable<? extends Food> foods) {
        Food pasty = null;
        Food ration = null;
        Food meat = null;
        for (Food food : foods) {
            if (food.quantity() <= 0) continue;
            if (pasty == null && food instanceof Pasty) pasty = food;
            else if (ration == null && food.getClass() == Food.class) ration = food;
            else if (meat == null && BlastAutoFood.pieMeat(food)) meat = food;
        }
        if (pasty == null || ration == null || meat == null) return null;

        ArrayList<Item> ingredients = new ArrayList<>(3);
        ingredients.add(pasty);
        ingredients.add(ration);
        ingredients.add(meat);
        return ingredients;
    }

    /** Craft one pie per actor turn, only after higher-priority actions are clear. */
    static boolean brewIfNearby(Hero hero, boolean safeHere) {
        if (!safeHere || BlastAutoPlay.hasVisibleEnemy(hero)
                || !besidePot(Dungeon.level, hero.pos)) return false;

        ArrayList<Item> ingredients = pieIngredients(hero.belongings.getAllItems(Food.class));
        if (ingredients == null || !PIE.testIngredients(ingredients)) return false;

        int cost = PIE.cost(ingredients);
        if (Dungeon.energy < cost) return false;

        Item result = PIE.brew(ingredients);
        if (!(result instanceof MeatPie)) {
            throw new IllegalStateException("Native MeatPie recipe produced no pie");
        }

        // Native alchemy detaches ingredients before brewing. Here the live stacks
        // have already been decremented by the recipe, so remove empty ones.
        for (Item ingredient : ingredients) {
            if (ingredient.quantity() == 0) ingredient.detachAll(hero.belongings.backpack);
        }
        Dungeon.energy -= cost;
        Catalog.countUses(EnergyCrystal.class, cost);
        if (!result.collect()) Dungeon.level.drop(result, hero.pos);
        Statistics.itemsCrafted++;
        Badges.validateItemsCrafted();
        Item.updateQuickslot();

        // A bounded one-tick action prevents large food stacks from spinning
        // through unlimited crafts without yielding to enemies or manual input.
        hero.spendAndNext(Actor.TICK);
        return true;
    }
}
