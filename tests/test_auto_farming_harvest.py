#!/usr/bin/env python3
"""Verify that auto-farming harvests before applying bomb-only requirements."""
from pathlib import Path
import unittest

SOURCE = (
    Path(__file__).resolve().parents[1]
    / "core/src/main/java/com/spd/blast/BlastAutoFarming.java"
)


class AutoFarmingHarvestTests(unittest.TestCase):
    def test_grass_harvest_does_not_depend_on_bomb_mode_or_inventory(self):
        code = SOURCE.read_text(encoding="utf-8")
        action = code.split("static Action chooseAction(", 1)[1].split(
            "static boolean regrowthReady(", 1
        )[0]
        harvest = action.index("int nearest = nearestCrop(hero, safe, distances);")
        move = action.index("return Action.MOVE;", harvest)
        planting = action.index("BlastRemoteBomb.isTimedMode()", harvest)
        self.assertLess(harvest, move)
        self.assertLess(move, planting)
        self.assertLess(move, action.index("BlastBombManiac.current()", harvest))
        self.assertLess(move, action.index("findRegrowthType(maniac)", harvest))

    def test_harvest_still_requires_safe_ground_and_no_awake_enemy(self):
        code = SOURCE.read_text(encoding="utf-8")
        action = code.split("static Action chooseAction(", 1)[1].split(
            "static boolean regrowthReady(", 1
        )[0]
        harvest = action.index("int nearest = nearestCrop(hero, safe, distances);")
        self.assertLess(action.index("hero.flying || hasVisibleEnemy(hero)"), harvest)
        crop = code.split("private static int nearestCrop(", 1)[1].split(
            "static int localGrowable(", 1
        )[0]
        self.assertIn("!safe[cell]", crop)
        self.assertIn("!harvestable(level.map[cell], level.plants.get(cell))", crop)


if __name__ == "__main__":
    unittest.main()
