#!/usr/bin/env python3
"""Verify that auto-play runs native intentional searches beside hidden doors."""
from pathlib import Path
import unittest


AUTO = (Path(__file__).resolve().parents[1]
        / "core/src/main/java/com/spd/blast/BlastAutoPlay.java")


class SecretDoorSearchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = AUTO.read_text(encoding="utf-8")
        cls.search = cls.source.split(
            "private static boolean searchNearbySecretDoor(Hero hero) {", 1
        )[1].split("\n    }", 1)[0]

    def test_search_after_combat_before_idle_actions(self):
        source = self.source
        search = source.index("if (searchNearbySecretDoor(hero)) return true;")
        self.assertLess(source.index("BlastAutoCombat.chooseAction(hero"), search)
        self.assertLess(search, source.index("pursueLastSeenTarget(hero, safe, distances)"))
        self.assertLess(search, source.index("resumeInterruptedTravel(hero"))
        self.assertLess(search, source.index("BlastAutoPickup.chooseAction(hero"))

    def test_only_adjacent_visible_hidden_doors(self):
        search = self.search
        self.assertIn("SPDSettings.intro()", search)
        self.assertIn("PathFinder.NEIGHBOURS8", search)
        self.assertIn("level.insideMap(cell)", search)
        self.assertIn("hero.fieldOfView[cell]", search)
        self.assertIn("level.map[cell] == Terrain.SECRET_DOOR", search)

    def test_return_to_seen_dead_end_before_roaming(self):
        source = self.source
        approach = source.index("BlastAutoSecretDoors.nearestStand(")
        self.assertLess(source.index("BlastAutoInteract.chooseAction(hero"), approach)
        self.assertLess(approach, source.index("BlastAutoSacrifice.patrol(hero"))
        self.assertLess(approach, source.index("BlastAutoFarming.chooseAction(hero"))
        self.assertLess(approach, source.index("BlastAutoTrapClearer.chooseAction(hero"))
        self.assertLess(approach, source.index("roamStep(hero, idleSafe, idleDistances)"))
        self.assertIn("roaming.clearDestination();", source[approach:approach + 500])
        self.assertIn("Intent.ROAM", source[approach:approach + 500])

    def test_native_search_spends_turn_without_second_wait(self):
        search = self.search
        self.assertIn("hero.search(true);", search)
        self.assertIn("hero.search(true);\n                return true;", search)
        self.assertNotIn("spendAndNext(", search)
        self.assertNotIn("new HeroAction", search)


if __name__ == "__main__":
    unittest.main()
