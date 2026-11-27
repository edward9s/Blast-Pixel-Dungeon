#!/usr/bin/env python3
"""Source regression checks for the opt-in auto-loot diagnostic trace."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PLAY = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")
PICKUP = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPickup.java").read_text(encoding="utf-8")
SETTINGS = (ROOT / "core/src/main/java/com/spd/blast/BlastSettings.java").read_text(encoding="utf-8")
LOG = (ROOT / "core/src/main/java/com/spd/blast/BlastLog.java").read_text(encoding="utf-8")
INTERLEVEL_PATCH = (
    ROOT / "integration/shattered/patches/patch_interlevel_debug.py"
).read_text(encoding="utf-8")
APPLY = (ROOT / "integration/shattered/apply.sh").read_text(encoding="utf-8")
SAVE_TRANSFER = (
    ROOT / "core/src/main/java/com/spd/blast/BlastSaveTransfer.java"
).read_text(encoding="utf-8")


class AutoLootDebugTest(unittest.TestCase):
    def test_existing_debug_setting_controls_trace(self):
        self.assertIn('GameSettings.getBoolean(DEBUG_LOG, false)', SETTINGS)
        self.assertIn("if (BlastSettings.debugLog()) {", PLAY)
        self.assertIn("BlastAutoPickup.nearbyDiagnostic(", PLAY)
        self.assertIn("if (!BlastSettings.debugLog() || nearbyDebugLoot == null)", PLAY)
        self.assertIn("BlastLog.debug(trace);", PLAY)
        self.assertIn("if (BlastSettings.debugLog())", LOG)

    def test_debug_file_is_per_save_and_exported(self):
        self.assertIn('private static final String FILE_NAME = "blast-debug.txt";', LOG)
        self.assertIn("GamesInProgress.gameFolder(slot) + \"/\" + FILE_NAME", LOG)
        self.assertIn("FileUtils.getFileHandle(path)", LOG)
        self.assertIn("file.write(append)", LOG)
        self.assertIn("writer.flush();", LOG)
        self.assertIn("open(false);", LOG)
        self.assertIn("FileUtils.deleteFile(GamesInProgress.gameFolder(slot)", LOG)
        self.assertIn("if (!value) BlastLog.stop();", SETTINGS)
        self.assertIn("BlastLog.beginGame(GamesInProgress.curSlot)", INTERLEVEL_PATCH)
        self.assertEqual(
            INTERLEVEL_PATCH.count("BlastLog.beginGame(GamesInProgress.curSlot)"), 2
        )
        self.assertIn("patch_interlevel_debug.py", APPLY)
        self.assertIn("replaceSnapshot(sourceDir, targetDir, false)", SAVE_TRANSFER)

    def test_nearby_visible_loot_only_and_no_pickup_side_effects(self):
        diagnostic = PICKUP.split("static String nearbyDiagnostic(", 1)[1].split(
            "static void preparePickup(", 1
        )[0]
        self.assertIn("hero.fieldOfView[cell]", diagnostic)
        self.assertIn("distance > 2", diagnostic)
        self.assertIn("candidate instanceof Gold", diagnostic)
        self.assertIn("!isPickupType(item, instantPickup)", diagnostic)
        self.assertIn("!canCollectFromHeap(hero, item, instantPickup)", diagnostic)
        self.assertIn('status = "full"', diagnostic)
        self.assertIn('status = "lost-inventory"', diagnostic)
        self.assertIn('status = "dew-full"', diagnostic)
        self.assertIn('status = "rose-unavailable"', diagnostic)
        self.assertIn('"backpack="', diagnostic)
        self.assertIn('bag.items.size()', diagnostic)
        self.assertIn('bag.capacity()', diagnostic)
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
