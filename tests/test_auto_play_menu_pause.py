#!/usr/bin/env python3
"""Verify the edge Tag controls auto-play pause without menu-triggered pauses."""
import ast
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
GAME_PATCH = ROOT / "integration/shattered/patches/patch_game_scene.py"
HERO_PATCH = ROOT / "integration/shattered/patches/patch_hero.py"
AUTO_PLAY = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"
AUTO_TAG = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlayTag.java"
KEY_PATCH = ROOT / "integration/shattered/patches/patch_tag_keybindings.py"


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


class AutoPlayPauseTagTests(unittest.TestCase):

    def test_menu_does_not_pause(self):
        code = injected_java(GAME_PATCH, "manual targeting during auto-play")
        self.assertIn("if (scene == null) return false;", code)
        self.assertIn("cellSelector.listener != null", code)
        self.assertIn("scene.inventory.isSelecting()", code)
        self.assertNotIn("WndGame", code)
        self.assertNotIn("WndSettings", code)
        self.assertNotIn("WndJournal", code)

    def test_tag_visibility_and_layout(self):
        source = GAME_PATCH.read_text(encoding="utf-8")
        self.assertIn("blastAutoPlay.visible = blastAutoPlay.active =", source)
        self.assertIn("tagBlastAutoPlay != blastAutoPlay.visible", source)
        self.assertIn("if (scene.tagBlastAutoPlay)", source)
        self.assertIn("scene.blastAutoPlay.flip(tagsOnLeft)", source)

    def test_paused_state_precedes_auto_actions(self):
        code = injected_java(HERO_PATCH, "Hero.act automatic actions")
        self.assertLess(code.index("BlastAutoPlay.pauseForManualInput(this)"),
                        code.index("BlastAutoPlay.performBombInput(this)"))
        self.assertLess(code.index("BlastAutoPlay.performBombInput(this)"),
                        code.index("BlastAutoPlay.chooseAction(this)"))
        self.assertEqual(code.count("BlastAutoPlay.isPaused()"), 2)
        hero_patch = HERO_PATCH.read_text(encoding="utf-8")
        self.assertIn("!com.spd.blast.BlastAutoPlay.isPaused()", hero_patch)

    def test_pause_restores_native_map_input(self):
        code = injected_java(HERO_PATCH, "Hero.act automatic actions")
        self.assertLess(code.index("BlastAutoPlay.pauseForManualInput(this)"),
                        code.index("BlastAutoPlay.pauseForUser(this)"))
        self.assertIn("BlastAutoPlay.pauseForUser(this)) {\\n\\t\\t\\tready();", code)
        self.assertLess(code.index("BlastAutoPlay.pauseForUser(this)"),
                        code.index("BlastAutoPlay.performBombInput(this)"))
        pause = AUTO_PLAY.read_text(encoding="utf-8").split(
            "public static boolean pauseForUser(Hero hero)", 1
        )[1].split("public static boolean pauseForManualInput", 1)[0]
        self.assertNotIn("hero.ready = true", pause)
        self.assertIn("return isPaused() && !manualActionActive(hero)", pause)

    def test_manual_actions_and_resume(self):
        source = AUTO_PLAY.read_text(encoding="utf-8")
        self.assertIn("if (!isPaused() || manualActionActive(hero) || !pendingBombInputs.isEmpty()) return false;", source)
        self.assertIn("public static boolean pauseForUser(Hero hero)", source)
        self.assertIn("if (resume && Dungeon.hero.ready && !GameScene.manualInputPending())", source)
        self.assertIn("BlastSettings.testAutoPlay() && !isPaused() && hero != null && hero.ready", source)

    def test_tag_binding(self):
        tag = AUTO_TAG.read_text(encoding="utf-8")
        keys = KEY_PATCH.read_text(encoding="utf-8")
        self.assertIn("BlastAutoPlay.togglePause()", tag)
        self.assertIn("return SPDAction.TAG_BLAST_AUTO_PLAY;", tag)
        self.assertIn("Input.Keys.P", keys)
        self.assertIn("tag_blast_auto_play", keys)


if __name__ == "__main__":
    unittest.main()
