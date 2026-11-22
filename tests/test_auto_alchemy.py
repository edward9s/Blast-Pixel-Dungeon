#!/usr/bin/env python3
"""Guard the small auto-alchemy integration without changing native UI behavior."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from integration.shattered.patches.patch_auto_alchemists_toolkit import patch as patch_toolkit


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
        self.assertIn("boolean potKnown = knownPot(Dungeon.level)", source)
        self.assertNotIn("PathFinder", source)
        self.assertNotIn("HeroAction.Move", source)
        self.assertNotIn("switchScene(", source)
        self.assertNotIn("buildDistanceMap", source)

    def test_safe_idle_priority(self):
        source = AUTO.read_text(encoding="utf-8")
        self.assertEqual(source.count("BlastAutoAlchemy.brewIfPossible(hero, safe[hero.pos])"), 1)
        self.assertLess(source.index("BlastAutoFood.eatIfNeeded(hero)"),
                        source.index("BlastAutoAlchemy.brewIfPossible("))
        self.assertLess(source.index("BlastAutoAlchemy.brewIfPossible("),
                        source.index("BlastAutoPickup.chooseAction("))
        alchemy = ALCHEMY.read_text(encoding="utf-8")
        self.assertIn("!safeHere || BlastAutoPlay.hasVisibleEnemy(hero)", alchemy)

    def test_recipe_and_inventory_integrity(self):
        source = ALCHEMY.read_text(encoding="utf-8")
        self.assertIn("new MeatPie.Recipe()", source)
        self.assertIn("PIE.testIngredients(ingredients)", source)
        self.assertIn("Dungeon.energy < cost", source)
        self.assertIn("readyToolkit(hero)", source)
        self.assertIn("toolkit.canBrewNow(hero)", source)
        self.assertIn("shouldUseToolkit(potKnown, Dungeon.energy, toolkit.availableEnergy(), cost)", source)
        self.assertIn("if (useToolkit) cost = toolkit.consumeEnergy(cost)", source)
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

    def test_native_toolkit_warmup_gate_patch(self):
        fixture = ("class AlchemistsToolkit {\n"
                   "\tpublic int availableEnergy(){\n"
                   "\t\treturn charge;\n"
                   "\t}\n"
                   "}\n")
        with TemporaryDirectory() as temp:
            path = Path(temp) / "AlchemistsToolkit.java"
            path.write_text(fixture, encoding="utf-8")
            patch_toolkit(path)
            patched = path.read_text(encoding="utf-8")
            self.assertIn("public boolean canBrewNow(Hero hero)", patched)
            self.assertIn("isEquipped(hero) && !cursed", patched)
            self.assertIn("hero.buff(MagicImmune.class) == null", patched)
            self.assertIn("warmUpDelay <= 0", patched)
            with self.assertRaises(SystemExit):
                patch_toolkit(path)
            path.write_text("class AlchemistsToolkit {}\n", encoding="utf-8")
            with self.assertRaises(SystemExit):
                patch_toolkit(path)

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
