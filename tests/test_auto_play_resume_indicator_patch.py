#!/usr/bin/env python3
"""Regression tests for interrupted manual movement with auto-play paused."""
from pathlib import Path
import runpy
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_auto_play_controls.py"
AUTO = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"
patch_resume_indicator = runpy.run_path(str(PATCH))["patch_resume_indicator"]

NATIVE_RESUME = (
    "\t@Override\n"
    "\tprotected void onClick() {\n"
    "\t\tsuper.onClick();\n"
    "\t\tif (Dungeon.hero.ready) {\n"
    "\t\t\tDungeon.hero.resume();\n"
    "\t\t}\n"
    "\t}\n"
)


class ResumeIndicatorPatchTests(unittest.TestCase):
    def test_resume_becomes_manual_action_without_unpausing_auto_play(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "ResumeIndicator.java"
            target.write_text(NATIVE_RESUME, encoding="utf-8")

            patch_resume_indicator(target)

            source = target.read_text(encoding="utf-8")
            self.assertIn("if (Dungeon.hero.ready)", source)
            self.assertIn("if (com.spd.blast.BlastSettings.testAutoPlay())", source)
            self.assertIn(
                "com.spd.blast.BlastAutoPlay.beginManualAction(Dungeon.hero);", source
            )
            self.assertLess(
                source.index("BlastAutoPlay.beginManualAction"),
                source.index("Dungeon.hero.resume();"),
            )
            self.assertEqual(source.count("Dungeon.hero.resume();"), 1)
            self.assertNotIn("BlastAutoPlay.togglePause()", source)

    def test_manual_action_bypasses_paused_gate_until_it_finishes(self):
        source = AUTO.read_text(encoding="utf-8")
        self.assertIn(
            "return isPaused() && !manualActionActive(hero) && pendingBombInputs.isEmpty();",
            source,
        )
        self.assertIn("hero.curAction != null", source)

    def test_unexpected_upstream_change_fails_fast(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "ResumeIndicator.java"
            target.write_text(NATIVE_RESUME.replace("Dungeon.hero.resume();", "other();"),
                              encoding="utf-8")

            with self.assertRaises(SystemExit):
                patch_resume_indicator(target)


if __name__ == "__main__":
    unittest.main()
