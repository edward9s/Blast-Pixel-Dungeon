#!/usr/bin/env python3
"""Guard the small auto-alchemy integration without changing native UI behavior."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
ALCHEMY = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoAlchemy.java")
AUTO = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java")
FOOD = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoFood.java")


class AutoAlchemyTests(unittest.TestCase):
    def test_visible_visited_or_mapped_pot_without_navigation(self):
        source = ALCHEMY.read_text(encoding="utf-8")
        self.assertIn("static boolean knownPot(Level level)", source)
        self.assertIn("level.map, level.heroFOV, level.visited, level.mapped", source)
        self.assertIn("terrain[cell] == Terrain.ALCHEMY", source)
        self.assertIn("visible[cell] || visited[cell] || mapped[cell]", source)
        self.assertIn("!knownPot(Dungeon.level)", source)
        self.assertNotIn("PathFinder", source)
        self.assertNotIn("HeroAction.Move", source)
        self.assertNotIn("switchScene(", source)
        self.assertNotIn("buildDistanceMap", source)

    def test_safe_idle_priority(self):
        source = AUTO.read_text(encoding="utf-8")
        self.assertEqual(source.count("BlastAutoAlchemy.brewIfNearby(hero, safe[hero.pos])"), 1)
        self.assertLess(source.index("BlastAutoFood.eatIfNeeded(hero)"),
                        source.index("BlastAutoAlchemy.brewIfNearby("))
        self.assertLess(source.index("BlastAutoAlchemy.brewIfNearby("),
                        source.index("BlastAutoPickup.chooseAction("))
        alchemy = ALCHEMY.read_text(encoding="utf-8")
        self.assertIn("!safeHere || BlastAutoPlay.hasVisibleEnemy(hero)", alchemy)

    def test_recipe_and_inventory_integrity(self):
        source = ALCHEMY.read_text(encoding="utf-8")
        self.assertIn("new MeatPie.Recipe()", source)
        self.assertIn("PIE.testIngredients(ingredients)", source)
        self.assertIn("Dungeon.energy < cost", source)
        self.assertLess(source.index("Dungeon.energy < cost"),
                        source.index("PIE.brew(ingredients)"))
        self.assertIn("ingredient.detachAll(hero.belongings.backpack)", source)
        self.assertIn("if (!result.collect()) Dungeon.level.drop(result, hero.pos)", source)
        self.assertIn("Dungeon.energy -= cost", source)
        self.assertIn("Catalog.countUses(EnergyCrystal.class, cost)", source)
        self.assertIn("Statistics.itemsCrafted++", source)
        self.assertIn("Badges.validateItemsCrafted()", source)
        self.assertIn("Item.updateQuickslot()", source)
        self.assertIn("hero.spendAndNext(Actor.TICK)", source)

    def test_ingredient_classification_is_shared(self):
        source = ALCHEMY.read_text(encoding="utf-8")
        self.assertIn("food instanceof Pasty", source)
        self.assertIn("food.getClass() == Food.class", source)
        self.assertIn("BlastAutoFood.pieMeat(food)", source)
        self.assertIn("if (food.quantity() <= 0) continue", source)
        food = FOOD.read_text(encoding="utf-8")
        self.assertIn("static boolean pieMeat(Food food)", food)


if __name__ == "__main__":
    unittest.main()
