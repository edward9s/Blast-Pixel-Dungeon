#!/usr/bin/env python3
"""Verify that the game menu pauses auto-play without pausing the journal."""
import ast
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GAME_PATCH = ROOT / "integration/shattered/patches/patch_game_scene.py"
HERO_PATCH = ROOT / "integration/shattered/patches/patch_hero.py"
AUTO_PLAY = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"


def injected_java(path, label):
    tree = ast.parse(path.read_text(encoding="utf-8"))
    calls = [
        node for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id in ("replace", "replace_once")
        and len(node.args) == 4
        and isinstance(node.args[3], ast.Constant)
        and node.args[3].value == label
    ]
    if len(calls) != 1:
        raise AssertionError(f"Expected exactly one {label} hook")
    return ast.literal_eval(calls[0].args[2])


class AutoPlayMenuPauseTests(unittest.TestCase):

    def test_game_menu_and_settings_pause_but_journal_does_not(self):
        code = injected_java(GAME_PATCH, "manual targeting and game menu during auto-play")
        self.assertIn("if (scene == null) return false;", code)
        self.assertIn("for (Gizmo member : scene.members)", code)
        self.assertIn("member instanceof WndGame || member instanceof WndSettings", code)
        self.assertNotIn("WndJournal", code)

    def test_pause_precedes_queued_bombs_and_automatic_actions(self):
        code = injected_java(HERO_PATCH, "Hero.act automatic actions")
        self.assertLess(code.index("BlastAutoPlay.pauseForManualInput(this)"),
                        code.index("BlastAutoPlay.performBombInput(this)"))
        self.assertLess(code.index("BlastAutoPlay.performBombInput(this)"),
                        code.index("BlastAutoPlay.chooseAction(this)"))

    def test_closed_menu_resumes_without_disabling_setting(self):
        source = AUTO_PLAY.read_text(encoding="utf-8")
        self.assertIn("if (!GameScene.manualInputPending()) return false;", source)
        self.assertIn("if (!waitingForInput || GameScene.manualInputPending()) return;", source)
        self.assertIn("if (BlastSettings.testAutoPlay() && hero != null && hero.ready) hero.next();",
                      source)


if __name__ == "__main__":
    unittest.main()
