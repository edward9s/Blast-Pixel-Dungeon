#!/usr/bin/env python3
"""Smoke tests for exotic-potion-to-regular-potion alchemy."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_exotic_potion_reverse.py"

POTION = """public class ExoticPotion {
\tpublic static class PotionToExotic extends Recipe {
\t}
}
"""
RECIPE = """public class Recipe {
\tprivate static Recipe[] oneIngredientRecipes = new Recipe[]{
\t\tnew ExoticPotion.PotionToExotic(),
\t};
}
"""
GUIDE = """public class QuickRecipe {
\t\t\tcase 3:
\t\t\t\treturn result;
\t\t\tcase 4:
\t\t\t\treturn result;
}
"""


class ReverseExoticPotionAlchemyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("reverse_exotic_potion", PATCH)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def patch(self, potion_source=POTION, recipe_source=RECIPE, guide_source=GUIDE):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            paths = (root / "ExoticPotion.java", root / "Recipe.java", root / "QuickRecipe.java")
            for path, source in zip(paths, (potion_source, recipe_source, guide_source)):
                path.write_text(source, encoding="utf-8")
            self.module.patch(*paths)
            return tuple(path.read_text(encoding="utf-8") for path in paths)

    def test_recipe_uses_original_mapping_and_four_energy(self):
        potion, recipe, guide = self.patch()
        self.assertIn("class ExoticToPotion extends Recipe", potion)
        self.assertIn("exoToReg.containsKey(ingredients.get(0).getClass())", potion)
        self.assertIn("return 4;", potion)
        self.assertIn("exotic.quantity(exotic.quantity() - 1);", potion)
        self.assertIn("potion.identify();", potion)
        self.assertIn("exotic.isKnown()", potion)
        self.assertIn("new Potion.PlaceHolder()", potion)
        self.assertIn("new ExoticPotion.ExoticToPotion()", recipe)
        self.assertIn("ExoticPotion.exoToReg.keySet()", guide)
        self.assertIn("r.sampleOutput(inputs)", guide)
        self.assertIn("case 3:", guide)
        self.assertIn("case 4:", guide)

    def test_patch_rejects_duplicate_application(self):
        with self.assertRaises(SystemExit):
            self.patch(*self.patch())

    def test_missing_source_anchors_are_rejected(self):
        for index in range(3):
            sources = [POTION, RECIPE, GUIDE]
            sources[index] = "unexpected upstream source"
            with self.assertRaises(SystemExit):
                self.patch(*sources)


if __name__ == "__main__":
    unittest.main()
