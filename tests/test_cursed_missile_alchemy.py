#!/usr/bin/env python3
"""Smoke-test cursed thrown-weapon melting without widening other alchemy rules."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_cursed_missile_alchemy.py"

RECIPE = """public abstract class Recipe {
\tpublic static boolean usableInRecipe(Item item){
\t\tif (item instanceof EquipableItem){
\t\t\treturn item.cursedKnown && !item.cursed &&
\t\t\t\t\titem instanceof MissileWeapon && item.isUpgradable();
\t\t} else if (item instanceof Wand) {
\t\t\treturn item.cursedKnown && !item.cursed;
\t\t} else {
\t\t\treturn !item.cursed;
\t\t}
\t}
}
"""
LIQUID_METAL = """public class LiquidMetal {
\tpublic static class Recipe {
\t\tpublic boolean testIngredients(ArrayList<Item> ingredients) {
\t\t\treturn ingredients.size() == 1
\t\t\t\t\t&& ingredients.get(0) instanceof MissileWeapon
\t\t\t\t\t&& ingredients.get(0).cursedKnown
\t\t\t\t\t&& !ingredients.get(0).cursed;
\t\t}
\t}
}
"""


class CursedMissileAlchemyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("patch_cursed_missile_alchemy", PATCH)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def patch(self, recipe_text=RECIPE, liquid_metal_text=LIQUID_METAL):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            recipe = root / "Recipe.java"
            liquid_metal = root / "LiquidMetal.java"
            recipe.write_text(recipe_text, encoding="utf-8")
            liquid_metal.write_text(liquid_metal_text, encoding="utf-8")
            self.module.patch(recipe, liquid_metal)
            return recipe.read_text(encoding="utf-8"), liquid_metal.read_text(encoding="utf-8")

    def test_allows_known_cursed_missile_weapons(self):
        recipe, liquid_metal = self.patch()
        self.assertIn(
            "return item.cursedKnown &&\n\t\t\t\t\titem instanceof MissileWeapon && item.isUpgradable();",
            recipe,
        )
        self.assertIn(
            "&& ingredients.get(0) instanceof MissileWeapon\n"
            "\t\t\t\t\t&& ingredients.get(0).cursedKnown;",
            liquid_metal,
        )
        self.assertNotIn("&& !ingredients.get(0).cursed;", liquid_metal)

    def test_preserves_other_curse_and_identification_requirements(self):
        recipe, _ = self.patch()
        self.assertIn("return item.cursedKnown && !item.cursed;", recipe)
        self.assertIn("return !item.cursed;", recipe)

    def test_fails_before_writing_if_an_upstream_anchor_changes(self):
        with self.assertRaises(SystemExit):
            self.patch(recipe_text="public class Recipe {}")
        with self.assertRaises(SystemExit):
            self.patch(liquid_metal_text="public class LiquidMetal {}")

    def test_fails_if_run_twice(self):
        with self.assertRaises(SystemExit):
            self.patch(*self.patch())


if __name__ == "__main__":
    unittest.main()
