#!/usr/bin/env python3
"""Smoke tests for Blast's reverse runestone alchemy integration patch."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_stone_to_scroll.py"
SETTINGS = ROOT / "core/src/main/java/com/spd/blast/BlastSettings.java"

SCROLL = """public class Scroll {
\tpublic static class ScrollToStone extends Recipe {
\t\tprivate static HashMap<Class<?extends Scroll>, Class<?extends Runestone>> stones = new HashMap<>();
\t}
}
"""
RECIPE = """public abstract class Recipe {
\tprivate static Recipe[] twoIngredientRecipes = new Recipe[]{
\t\tnew Bomb.EnhanceBomb(),
\t};
}
"""


class ReverseRunestoneAlchemyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("patch_stone_to_scroll", PATCH)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def patch(self, scroll_source=SCROLL, recipe_source=RECIPE):
        with tempfile.TemporaryDirectory() as temporary:
            scroll = Path(temporary) / "Scroll.java"
            recipe = Path(temporary) / "Recipe.java"
            scroll.write_text(scroll_source, encoding="utf-8")
            recipe.write_text(recipe_source, encoding="utf-8")
            self.module.patch(scroll, recipe)
            return scroll.read_text(encoding="utf-8"), recipe.read_text(encoding="utf-8")

    def test_reverse_recipe_and_flag(self):
        scroll, recipe = self.patch()
        self.assertIn("ScrollToStone.stones.keySet()", scroll)
        self.assertIn("first.getClass() == second.getClass()", scroll)
        self.assertIn("ingredients.size() != 2", scroll)
        self.assertIn("new Scroll.StoneToScroll()", recipe)
        self.assertIn("scroll.identify();", scroll)
        self.assertIn("stone.quantity(stone.quantity() - 1);", scroll)
        self.assertIn("return scroll.isKnown() ? scroll : new Scroll.PlaceHolder();", scroll)
        self.assertIn("ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY", scroll)
        self.assertIn("ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY = false;", SETTINGS.read_text(encoding="utf-8"))

    def test_rejects_duplicate_application(self):
        scroll, recipe = self.patch()
        with self.assertRaises(SystemExit):
            self.patch(scroll, recipe)

    def test_rejects_missing_upstream_anchor(self):
        with self.assertRaises(SystemExit):
            self.patch(scroll_source="public class Scroll {}\n")
        with self.assertRaises(SystemExit):
            self.patch(recipe_source="public abstract class Recipe {}\n")


if __name__ == "__main__":
    unittest.main()
