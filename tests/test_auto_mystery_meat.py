#!/usr/bin/env python3
"""Regression checks for automatic MysteryMeat eating and native effect isolation."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_auto_mystery_meat.py"
APPLY = ROOT / "integration/shattered/apply.sh"
FOOD = ROOT / "core/src/main/java/com/spd/blast/BlastAutoFood.java"

NATIVE_SATISFY = (
    "\t@Override\n"
    "\tprotected void satisfy(Hero hero) {\n"
    "\t\tsuper.satisfy(hero);\n"
    "\t\teffect(hero);\n"
    "\t}"
)


class AutomaticMysteryMeatTests(unittest.TestCase):

    def patch_source(self, contents):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "MysteryMeat.java"
            path.write_text(contents, encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(PATCH), str(path)],
                capture_output=True, text=True,
            )
            return result, path.read_text(encoding="utf-8")

    def test_native_meat_keeps_hunger_and_manual_effect(self):
        original = (
            "public class MysteryMeat extends Food {\n"
            + NATIVE_SATISFY
            + "\n\tpublic static void effect(Hero hero) { applyRandomDebuff(hero); }\n}\n"
        )
        result, patched = self.patch_source(original)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(
            "\t\tsuper.satisfy(hero);\n"
            "\t\tif (!com.spd.blast.BlastAutoFood.isEatingAutomatically()) {\n"
            "\t\t\teffect(hero);\n\t\t}",
            patched,
        )
        self.assertIn("public static void effect(Hero hero) { applyRandomDebuff(hero); }", patched)
        self.assertEqual(patched.count("effect(hero);"), 1)

    def test_changed_anchor_fails_without_writing(self):
        original = NATIVE_SATISFY.replace("effect(hero);", "effect(otherHero);")
        result, patched = self.patch_source(original)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(patched, original)

    def test_already_patched_source_fails(self):
        first, patched = self.patch_source(NATIVE_SATISFY)
        self.assertEqual(first.returncode, 0)
        second, unchanged = self.patch_source(patched)
        self.assertNotEqual(second.returncode, 0)
        self.assertEqual(unchanged, patched)

    def test_auto_food_scope_and_selection(self):
        food = FOOD.read_text(encoding="utf-8")
        self.assertIn("public final class BlastAutoFood", food)
        self.assertIn("public static boolean isEatingAutomatically()", food)
        self.assertIn("automaticConsumption.set(true);", food)
        self.assertIn("item.execute(hero, action);", food)
        self.assertIn("automaticConsumption.remove();", food)
        self.assertIn("finally {", food)
        self.assertIn("food instanceof MysteryMeat", food)
        self.assertIn("food instanceof Blandfruit", food)
        self.assertIn("if (!hunger.isStarving()) return false;", food)
        self.assertIn("feedingHero = hero;", food)
        self.assertIn("food.energy * foodMultiplier > remainingHunger", food)
        self.assertIn("preferSatiety && food instanceof MeatPie", food)
        self.assertIn("remainingHunger < Hunger.STARVING || wellFed", food)
        self.assertIn("hero.buff(WellFed.class) != null", food)
        self.assertIn("pieAtStarving(foods, remainingHunger", food)
        self.assertIn("eatAutomatically(hero, pie, Food.AC_EAT)", food)
        self.assertNotIn("food == null && hunger.isStarving()", food)
        self.assertIn("food.energy > mostSatiety", food)

    def test_horn_eating_precedes_inventory_food(self):
        food = FOOD.read_text(encoding="utf-8")
        method = food.split("static boolean eatIfNeeded(Hero hero) {", 1)[1]
        self.assertIn("if (!hunger.isStarving()) return false;", method)
        self.assertIn("hero.belongings.getAllItems(HornOfPlenty.class)", method)
        self.assertIn("horn.actions(hero).contains(HornOfPlenty.AC_EAT)", method)
        self.assertIn("eatAutomatically(hero, horn, HornOfPlenty.AC_EAT)", method)
        self.assertLess(method.index("eatAutomatically(hero, horn"),
                        method.index("Food pie = pieAtStarving("))
        self.assertLess(method.index("Food pie = pieAtStarving("),
                        method.index("Food food = bestFitWithin("))
        self.assertIn("eatAutomatically(hero, food, Food.AC_EAT)", method)
        self.assertIn("if (remainingHunger >= satietyPerCharge)", method)
        self.assertIn("stopFeeding();", method)

    def test_pasty_and_native_meat_pie_ingredients_are_reserved(self):
        food = FOOD.read_text(encoding="utf-8")
        self.assertIn("food instanceof Pasty", food)
        self.assertIn("food.getClass() == Food.class", food)
        for meat_type in ("MysteryMeat", "StewedMeat", "ChargrilledMeat",
                          "FrozenCarpaccio"):
            self.assertIn("food instanceof " + meat_type, food)
        self.assertIn("rations - pasties", food)
        self.assertIn("meats - pasties", food)
        self.assertIn("available > mostAvailable", food)

    def test_patch_used_by_both_builds(self):
        apply = APPLY.read_text(encoding="utf-8")
        self.assertEqual(apply.count('python "$patches/patch_auto_mystery_meat.py"'), 1)
        self.assertIn('"$root/items/food/MysteryMeat.java"', apply)


if __name__ == "__main__":
    unittest.main()
