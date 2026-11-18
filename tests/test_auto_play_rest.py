#!/usr/bin/env python3
"""Regression tests for native wait/rest while auto-play is enabled."""
from pathlib import Path
import runpy
import tempfile
import unittest

from test_auto_play_manual_priority import injection, method, AUTO, HERO, SCENE

ROOT = Path(__file__).resolve().parents[1]
CONTROLS = ROOT / "integration/shattered/patches/patch_auto_play_controls.py"


class AutoPlayRestTests(unittest.TestCase):
    def test_native_rest_is_dispatched_before_auto_play(self):
        code = injection(HERO, "Hero.act automatic actions")
        self.assertLess(code.index("performManualInput(this)"), code.index("pauseForUser(this)"))
        self.assertIn("performManualInput(this)) {\n\t\t\treturn false;", code)
        for action in ("pauseForUser(this)", "chooseAction(this)", "&& curAction == null"):
            guard = code[:code.index(action)].rsplit("if (", 1)[1]
            self.assertIn("&& !resting", guard)
        self.assertTrue(code.endswith("boolean actResult;\n\t\tif (curAction == null) {"))

    def test_rest_replaces_movement_and_spends_native_turn(self):
        source = AUTO.read_text(encoding="utf-8")
        request = method(source, "public static void requestManualRest(boolean fullRest) {")
        execute = method(source, "public static boolean performManualInput(Hero hero) {")
        self.assertIn("pendingManualInput.set(new ManualRest(hero, level, fullRest))", request)
        self.assertIn("if (hero.ready) hero.next();", request)
        self.assertIn("pendingManualInput.getAndSet(null)", execute)
        self.assertIn("input.hero != hero || input.level != Dungeon.level", execute)
        self.assertLess(execute.index("hero.curAction = null"), execute.index("hero.rest("))
        self.assertIn("hero.rest(((ManualRest) input).fullRest);\n            return true;", execute)
        self.assertIn("hero.resting = false;", execute)
        self.assertIn("hero.resting || pendingManualInput.get() != null", source)

    def test_toolbar_wait_and_rest_keep_vanilla_fallback(self):
        patch = runpy.run_path(str(CONTROLS))["patch_toolbar"]
        fixture = "\n".join(["Dungeon.hero.ready"] * 11
                            + ["Dungeon.hero.rest(false);"] * 2
                            + ["Dungeon.hero.rest(true);"] * 3)
        fixture += "\n\t\t\t\t\t\tinformer.onSelect(null);\n\t\t\t\t\t\tDungeon.hero.search(true);"
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Toolbar.java"
            path.write_text(fixture, encoding="utf-8")
            patch(path)
            result = path.read_text(encoding="utf-8")
        for value, count in (("false", 2), ("true", 3)):
            self.assertEqual(result.count(f"requestManualRest({value});"), count)
            self.assertEqual(result.count(f"else Dungeon.hero.rest({value});"), count)

    def test_native_cancel_can_interrupt_rest(self):
        code = injection(SCENE, "autoplay running ignores queued move during cancellation")
        self.assertIn("Dungeon.hero == null || !Dungeon.hero.resting", code)
        self.assertIn("!com.spd.blast.BlastAutoPlay.isPaused()", code)


if __name__ == "__main__":
    unittest.main()
