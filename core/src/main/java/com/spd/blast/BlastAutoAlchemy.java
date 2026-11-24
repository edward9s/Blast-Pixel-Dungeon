package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Recipe;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.PotionBandolier;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.ScrollHolder;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.AlchemistsToolkit;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MeatPie;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Pasty;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfLevitation;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.ExoticPotion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfStormClouds;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.Alchemize;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAugmentation;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfDetectMagic;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfEnchantment;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant.Seed;
import com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry;
import com.shatteredpixel.shatteredpixeldungeon.plants.Starflower;
import com.shatteredpixel.shatteredpixeldungeon.plants.Stormvine;

import java.util.ArrayList;
import java.util.Collections;

/** Idle alchemy using a known pot or a ready equipped toolkit. */
final class BlastAutoAlchemy {

    private static final int LEVITATION_RESERVE = 2;
    private static final Recipe PIE = new MeatPie.Recipe();
    private static final Recipe SELL = new Alchemize.Recipe();
    private static final Recipe AQUA = new AquaBrew.Recipe();
    private static final Recipe CLOUDS = new ExoticPotion.PotionToExotic();
    private static final Recipe POTION = new Potion.SeedToPotion();
    private static final Recipe SCROLL = new Scroll.StoneToScroll();

    private BlastAutoAlchemy() {}

    /** Current sight, previously explored tiles, and mapped tiles all reveal a pot. */
    static boolean knownPot(Level level) {
        return knownPot(level.map, level.heroFOV, level.visited, level.mapped);
    }

    /** Isolated from Level so that discovery rules can be regression tested. */
    static boolean knownPot(int[] terrain, boolean[] visible,
                            boolean[] visited, boolean[] mapped) {
        for (int cell = 0; cell < terrain.length; cell++) {
            if (terrain[cell] == Terrain.ALCHEMY
                    && (visible[cell] || visited[cell] || mapped[cell])) return true;
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

    private static AlchemistsToolkit readyToolkit(Hero hero) {
        AlchemistsToolkit toolkit = null;
        if (hero.belongings.artifact() instanceof AlchemistsToolkit) {
            toolkit = (AlchemistsToolkit) hero.belongings.artifact();
        } else if (hero.belongings.misc() instanceof AlchemistsToolkit) {
            toolkit = (AlchemistsToolkit) hero.belongings.misc();
        }
        return toolkit != null && toolkit.canBrewNow(hero) ? toolkit : null;
    }

    /** Prefer the known pot when global energy suffices; otherwise use portable alchemy. */
    static boolean shouldUseToolkit(boolean potKnown, int globalEnergy,
                                    int toolkitEnergy, int cost) {
        return (!potKnown || globalEnergy < cost)
                && globalEnergy + toolkitEnergy >= cost;
    }

    /** Craft at most once per actor turn. */
    static boolean brewIfPossible(Hero hero, boolean safeHere) {
        if (!safeHere || BlastAutoPlay.hasVisibleEnemy(hero)) return false;

        boolean potKnown = knownPot(Dungeon.level);
        AlchemistsToolkit toolkit = readyToolkit(hero);
        if (!potKnown && toolkit == null) return false;

        ArrayList<Seed> seeds = hero.belongings.getAllItems(Seed.class);
        ArrayList<Runestone> stones = hero.belongings.getAllItems(Runestone.class);
        if (brew(hero, potKnown, toolkit, seeds, PIE,
                pieIngredients(hero.belongings.getAllItems(Food.class)), 0)) return true;

        if (hero.belongings.getItem(Alchemize.class) == null && canStore(hero, new Alchemize())) {
            Seed seed = null;
            for (Seed candidate : seeds) {
                if (fuelSeed(candidate) && (seed == null || candidate.quantity() > seed.quantity())) {
                    seed = candidate;
                }
            }
            if (seed != null) {
                for (Runestone stone : stones) {
                    if (stone.quantity() > 0 && !importantStone(stone)
                            && brew(hero, potKnown, toolkit, seeds, SELL,
                                    ingredients(seed, stone), 0)) return true;
                }
            }
        }

        if (hero.belongings.getItem(AquaBrew.class) == null && canStore(hero, new AquaBrew())) {
            PotionOfStormClouds clouds = hero.belongings.getItem(PotionOfStormClouds.class);
            if (clouds != null) {
                if (brew(hero, potKnown, toolkit, seeds, AQUA, ingredients(clouds), 0)) return true;
            } else {
                PotionOfLevitation levitation = hero.belongings.getItem(PotionOfLevitation.class);
                if (levitation != null && levitation.isIdentified()
                        && levitation.quantity() > LEVITATION_RESERVE
                        && canStore(hero, new PotionOfStormClouds())
                        && brew(hero, potKnown, toolkit, seeds, CLOUDS, ingredients(levitation),
                                AQUA.cost(ingredients(new PotionOfStormClouds())))) return true;
            }
        }

        ScrollHolder scrolls = hero.belongings.getItem(ScrollHolder.class);
        if (hasSlot(scrolls)) {
            for (boolean important : new boolean[]{true, false}) {
                for (Runestone stone : stones) {
                    if (stone.quantity() >= 2 && importantStone(stone) == important
                            && brew(hero, potKnown, toolkit, seeds, SCROLL,
                                    ingredients(stone, stone), 0)) return true;
                }
            }
        }

        if (hasSlot(hero.belongings.getItem(PotionBandolier.class))) {
            ArrayList<Item> mixed = new ArrayList<>(3);
            for (Seed seed : seeds) {
                // Matching seeds use the native single-type result. Never use the wandmaker's seed.
                if (seed.quantity() >= 3 && !(seed instanceof Rotberry.Seed)
                        && brew(hero, potKnown, toolkit, seeds, POTION,
                                ingredients(seed, seed, seed), 0)) return true;
                if (fuelSeed(seed) && Potion.SeedToPotion.types.containsKey(seed.getClass())) {
                    for (int i = 0; i < seed.quantity() && mixed.size() < 3; i++) mixed.add(seed);
                }
            }
            if (mixed.size() == 3 && brew(hero, potKnown, toolkit, seeds, POTION, mixed, 0)) return true;
        }

        return false;
    }

    private static boolean hasSlot(Bag bag) {
        return bag != null && bag.items.size() < bag.capacity();
    }

    private static boolean canStore(Hero hero, Item result) {
        return BlastAutoPickup.canCollect(hero, result, BlastAutoPickupPolicy::isInstantPickup);
    }

    private static boolean importantStone(Runestone stone) {
        return stone instanceof StoneOfEnchantment || stone instanceof StoneOfDetectMagic
                || stone instanceof StoneOfAugmentation;
    }

    private static boolean fuelSeed(Seed seed) {
        return seed.quantity() > 0 && !(seed instanceof Rotberry.Seed
                || seed instanceof Starflower.Seed || seed instanceof Stormvine.Seed);
    }

    private static ArrayList<Item> ingredients(Item... items) {
        ArrayList<Item> result = new ArrayList<>(items.length);
        Collections.addAll(result, items);
        return result;
    }

    /** Plan the full energy cost before using any seeds or recipe ingredients. */
    private static ArrayList<Seed> energySeeds(int missing, ArrayList<Seed> seeds,
                                                ArrayList<Item> ingredients) {
        ArrayList<Seed> fuel = new ArrayList<>();
        for (Seed seed : seeds) {
            if (missing <= 0) break;
            if (!fuelSeed(seed)) continue;
            int unit = seed.energyVal() / seed.quantity();
            int available = seed.quantity() - Collections.frequency(ingredients, seed);
            while (missing > 0 && available-- > 0) {
                fuel.add(seed);
                missing -= unit;
            }
        }
        return missing > 0 ? null : fuel;
    }

    private static boolean brew(Hero hero, boolean potKnown, AlchemistsToolkit toolkit,
                                ArrayList<Seed> seeds, Recipe recipe, ArrayList<Item> ingredients,
                                int reserveEnergy) {
        if (ingredients == null || !recipe.testIngredients(ingredients)) return false;

        int cost = recipe.cost(ingredients);
        int portable = toolkit == null ? 0 : toolkit.availableEnergy();
        // Prefer spending existing energy. Only make up the shortfall from seeds.
        ArrayList<Seed> fuel = energySeeds(cost + reserveEnergy - Dungeon.energy - portable,
                seeds, ingredients);
        if (fuel == null) return false;

        Item result = recipe.brew(ingredients);
        if (result == null) throw new IllegalStateException("Validated alchemy recipe produced no item");

        // Recipes decrement live stacks, including shared stacks used for several inputs.
        for (Item ingredient : ingredients) {
            if (ingredient.quantity() == 0 && hero.belongings.contains(ingredient)) {
                ingredient.detachAll(hero.belongings.backpack);
            }
        }
        for (Seed seed : fuel) {
            Item consumed = seed.detach(hero.belongings.backpack);
            Dungeon.energy += consumed.energyVal();
        }
        boolean useToolkit = toolkit != null
                && shouldUseToolkit(potKnown, Dungeon.energy, portable, cost);
        if (useToolkit) cost = toolkit.consumeEnergy(cost);
        Dungeon.energy -= cost;
        Catalog.countUses(EnergyCrystal.class, cost);
        if (!result.collect()) Dungeon.level.drop(result, hero.pos);
        Statistics.itemsCrafted++;
        Badges.validateItemsCrafted();
        Item.updateQuickslot();
        hero.spendAndNext(Actor.TICK);
        return true;
    }
}
