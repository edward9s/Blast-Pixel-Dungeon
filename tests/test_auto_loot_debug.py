#!/usr/bin/env python3
"""Source regression checks for the opt-in auto-loot diagnostic trace."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PLAY = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")
PICKUP = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPickup.java").read_text(encoding="utf-8")
SETTINGS = (ROOT / "core/src/main/java/com/spd/blast/BlastSettings.java").read_text(encoding="utf-8")
LOG = (ROOT / "core/src/main/java/com/spd/blast/BlastLog.java").read_text(encoding="utf-8")


class AutoLootDebugTest(unittest.TestCase):
    def test_existing_debug_setting_controls_trace(self):
        self.assertIn('GameSettings.getBoolean(DEBUG_LOG, false)', SETTINGS)
        self.assertIn("if (BlastSettings.debugLog()) {", PLAY)
        self.assertIn("BlastAutoPickup.nearbyDiagnostic(", PLAY)
        self.assertIn("if (!BlastSettings.debugLog() || nearbyDebugLoot == null)", PLAY)
        self.assertIn("BlastLog.debug(trace);", PLAY)
        self.assertIn("if (BlastSettings.debugLog())", LOG)

    def test_nearby_visible_loot_only_and_no_pickup_side_effects(self):
        diagnostic = PICKUP.split("static String nearbyDiagnostic(", 1)[1].split(
            "static boolean isPickupType(", 1
        )[0]
        self.assertIn("hero.fieldOfView[cell]", diagnostic)
        self.assertIn("distance > 2", diagnostic)
        self.assertIn("heap.peek() instanceof Gold", diagnostic)
        self.assertIn("!isPickupType(item, instantPickup)", diagnostic)
        self.assertIn("!canCollect(hero, item, instantPickup)", diagnostic)
        self.assertIn("!pickupSafe[cell]", diagnostic)
        self.assertIn("distances[cell] == Integer.MAX_VALUE", diagnostic)
        self.assertIn('status = "cooldown"', diagnostic)
        self.assertNotIn("hero.curAction =", diagnostic)
        self.assertNotIn("retryTurns =", diagnostic)

    def test_reasons_match_actual_priority_order(self):
        decisions = PLAY.split("private static boolean chooseActionOnce(", 1)[1].split(
            "private static void logNearbyLoot(", 1
        )[0]
        checks = [
            'debugDecision = "ranged-bomb";',
            'debugDecision = "sacrifice";',
            'debugDecision = "combat";',
            'debugDecision = "pursuit";',
            'debugDecision = "manual-route";',
            'debugDecision = "pickup";',
            'debugDecision = "farm";',
            'debugDecision = "roam";'
        ]
        indices = [decisions.index(mark) for mark in checks]
        self.assertEqual(indices, sorted(indices))
        self.assertIn("action instanceof HeroAction.PickUp", PLAY)
        self.assertIn('chosen=" + debugDecision + " action=" + command', PLAY)

    def test_suppress_identical_lines_but_repeat_periodically(self):
        self.assertIn("if (trace.equals(lastLootTrace))", PLAY)
        self.assertIn("if (++repeatedLootTrace < 10) return;", PLAY)
        self.assertIn("lastLootTrace = null;", PLAY)


if __name__ == "__main__":
    unittest.main()
