#!/usr/bin/env python3
"""Verify that ordinary auto-play harvests crops before optional Regrowth farming."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
AUTO_PLAY = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"
FARMING = ROOT / "core/src/main/java/com/spd/blast/BlastAutoFarming.java"
HARVEST = ROOT / "core/src/main/java/com/spd/blast/BlastAutoHarvest.java"


class AutoPlayHarvestTests(unittest.TestCase):
    def test_harvest_is_part_of_auto_play_without_farming_setting(self):
        source = AUTO_PLAY.read_text(encoding="utf-8")
        action = source.split("public static boolean chooseAction(Hero hero)", 1)[1].split(
            "static boolean highEvasion(", 1
        )[0]
        pickup = action.index("BlastAutoPickup.chooseAction(hero")
        harvest = action.index("if (harvest(hero, safe, distances)) return false;")
        farming = action.index("BlastAutoFarming.chooseAction(hero, safe, distances)")
        roaming = action.index("roamStep(hero, safe, distances)")
        self.assertLess(pickup, harvest)
        self.assertLess(harvest, farming)
        self.assertLess(farming, roaming)

        harvest_action = source.split("private static boolean harvest(", 1)[1].split(
            "static boolean hasVisibleEnemy(", 1
        )[0]
        self.assertIn("harvesting.chooseDestination(", harvest_action)
        self.assertIn("BlastAutoFarming.recordHarvest(next)", harvest_action)
        self.assertIn("roaming.clearDestination()", harvest_action)
        self.assertNotIn("BlastSettings.autoFarming()", harvest_action)

    def test_harvest_observes_safety_rules_and_existing_goal(self):
        source = AUTO_PLAY.read_text(encoding="utf-8")
        harvest_action = source.split("private static boolean harvest(", 1)[1].split(
            "static boolean hasVisibleEnemy(", 1
        )[0]
        self.assertIn("hero.flying || hero.rooted || hasVisibleEnemy(hero)", harvest_action)
        self.assertIn("hero.fieldOfView, hero.canSelfTrample()", harvest_action)
        self.assertIn("BlastAutoHarvest.harvestable(level.map[cell], level.plants.get(cell))",
                      harvest_action)
        self.assertEqual(source.count("harvesting.reset();"), 2)

        classification = HARVEST.read_text(encoding="utf-8")
        self.assertIn("terrain == Terrain.HIGH_GRASS", classification)
        self.assertIn("!AutoPlayPlantSafety.isHarmful(plant)", classification)
        self.assertIn("safe[destination]", classification)
        self.assertIn("!visible[cell] || !safe[cell]", classification)

    def test_farming_only_chooses_regrowth_and_tracks_harvest(self):
        farming = FARMING.read_text(encoding="utf-8")
        action = farming.split("static Action chooseAction(", 1)[1].split(
            "static void recordHarvest(", 1
        )[0]
        self.assertIn("!BlastSettings.autoFarming()", action)
        self.assertIn("BlastAutoPlay.hasVisibleEnemy(hero)", action)
        self.assertNotIn("nearestCrop(", farming)
        self.assertNotIn("harvesting.chooseDestination(", farming)
        self.assertIn("if (planted)", action)
        self.assertIn("canPlantAgain(planted, harvested, regrowthActive(level))", action)
        self.assertIn("if (BlastAutoHarvest.harvestable(", farming)


if __name__ == "__main__":
    unittest.main()
