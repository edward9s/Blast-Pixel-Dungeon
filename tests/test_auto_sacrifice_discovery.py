#!/usr/bin/env python3
"""Guard known-fire activation and nonblocking sacrifice patrol priority."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SACRIFICE = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoSacrifice.java").read_text(encoding="utf-8")
PLAY = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")


class SacrificeDiscoveryTests(unittest.TestCase):
    def test_discovery_gate_precedes_sacrifice_actions(self):
        gate = "knownFireCell(fire.cur, level.visited, level.mapped, hero.fieldOfView)"
        self.assertIn(gate, SACRIFICE)
        self.assertLess(SACRIFICE.index(gate), SACRIFICE.index("BlastAutoBombAI.actAgainstSacrifice"))
        self.assertLess(SACRIFICE.index(gate), SACRIFICE.index("approachUnengagedEnemy(hero"))
        self.assertLess(SACRIFICE.index(gate), SACRIFICE.index("int site = -1;"))
        self.assertIn("visited[cell] || mapped[cell] || visible[cell]", SACRIFICE)
        self.assertIn("flames[cell] > 0 && knownCell(", SACRIFICE)

    def test_active_lure_retains_priority_before_combat(self):
        self.assertLess(PLAY.index("BlastAutoSacrifice.chooseAction(hero"),
                        PLAY.index("BlastAutoCombat.chooseAction(hero"))
        self.assertIn("return searchOutside(hero, room, safe, distances);", SACRIFICE)
        self.assertIn("return Action.QUEUED;", SACRIFICE)

    def test_idle_patrol_runs_after_pickup_and_interaction(self):
        self.assertIn("return approach != Action.NONE ? approach : Action.PATROL;", SACRIFICE)
        self.assertIn("if (sacrifice == BlastAutoSacrifice.Action.PATROL)", PLAY)
        self.assertLess(PLAY.index("BlastAutoPickup.chooseAction(hero"),
                        PLAY.index("BlastAutoSacrifice.patrol(hero"))
        self.assertLess(PLAY.index("BlastAutoInteract.chooseAction(hero"),
                        PLAY.index("BlastAutoSacrifice.patrol(hero"))
        self.assertLess(PLAY.index("BlastAutoSacrifice.patrol(hero"),
                        PLAY.index("int step = roamStep(hero"))
        self.assertIn('debugDecision = "sacrifice-patrol";', PLAY)


if __name__ == "__main__":
    unittest.main()
