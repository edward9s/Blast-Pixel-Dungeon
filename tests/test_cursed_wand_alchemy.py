#!/usr/bin/env python3
"""Smoke-test cursed-wand alchemy without changing other ingredient rules."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_cursed_wand_alchemy.py"

# Recipe.java after patch_cursed_missile_alchemy.py has already been applied.
RECIPE = """public abstract class Recipe {
\tpublic static boolean usableInRecipe(Item item){
\t\tif (item instanceof EquipableItem){
\t\t\treturn item.cursedKnown &&
\t\t\t\t\titem instanceof MissileWeapon && item.isUpgradable();
\t\t} else if (item instanceof Wand) {
\t\t\treturn item.cursedKnown && !item.cursed;
\t\t} else {
\t\t\treturn !item.cursed;
\t\t}
\t}
}
"""
ARCANE_RESIN = """public class ArcaneResin {
\tpublic static class Recipe {
\t\tpublic boolean testIngredients(ArrayList<Item> ingredients) {
\t\t\treturn ingredients.size() == 1
\t\t\t\t\t&& ingredients.get(0) instanceof Wand
\t\t\t\t\t&& ingredients.get(0).cursedKnown
\t\t\t\t\t&& !ingredients.get(0).cursed;
\t\t}

\t\tpublic int cost(ArrayList<Item> ingredients) {
\t\t\treturn 5;
\t\t}

\t\tprivate int resinQuantity(Wand w){
\t\t\tint level = w.level() - w.resinBonus;
\t\t\tint quantity = 2*(level+1);
\t\t\treturn quantity;
\t\t}
\t}
}
"""


class CursedWandAlchemyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("patch_cursed_wand_alchemy", PATCH)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def patch(self, recipe_source=RECIPE, resin_source=ARCANE_RESIN):
        with tempfile.TemporaryDirectory() as temporary:
            recipe_path = Path(temporary) / "Recipe.java"
            resin_path = Path(temporary) / "ArcaneResin.java"
            recipe_path.write_text(recipe_source, encoding="utf-8")
            resin_path.write_text(resin_source, encoding="utf-8")
            self.module.patch(recipe_path, resin_path)
            return recipe_path.read_text(encoding="utf-8"), resin_path.read_text(encoding="utf-8")

    def test_allows_wands_regardless_of_curse_if_curse_is_known(self):
        recipe, resin = self.patch()
        self.assertIn(
            "} else if (item instanceof Wand) {\n\t\t\treturn item.cursedKnown;",
            recipe,
        )
        self.assertIn(
            "&& ingredients.get(0) instanceof Wand\n"
            "\t\t\t\t\t&& ingredients.get(0).cursedKnown;",
            resin,
        )
        self.assertNotIn("&& !ingredients.get(0).cursed;", resin)

    def test_keeps_other_restrictions_and_resin_yield(self):
        recipe, resin = self.patch()
        self.assertIn(
            "item instanceof MissileWeapon && item.isUpgradable();", recipe
        )
        self.assertIn("return !item.cursed;", recipe)
        self.assertIn("return 5;", resin)
        self.assertIn("int level = w.level() - w.resinBonus;", resin)
        self.assertIn("int quantity = 2*(level+1);", resin)

    def test_missing_or_repeated_anchors_fail(self):
        with self.assertRaises(SystemExit):
            self.patch(recipe_source="public class Recipe {}")
        with self.assertRaises(SystemExit):
            self.patch(resin_source="public class ArcaneResin {}")
        with self.assertRaises(SystemExit):
            self.patch(*self.patch())


if __name__ == "__main__":
    unittest.main()
