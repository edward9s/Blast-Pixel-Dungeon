#!/usr/bin/env python3
"""Check the native Hero integration's no-action auto-play fallback."""
import ast
from pathlib import Path
import unittest


PATCH = Path(__file__).resolve().parents[1] / "integration/shattered/patches/patch_hero.py"


def hero_action_patch():
    tree = ast.parse(PATCH.read_text(encoding="utf-8"))
    matches = [
        node for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id == "replace_once"
        and len(node.args) == 4
        and isinstance(node.args[3], ast.Constant)
        and node.args[3].value == "Hero.act automatic actions"
    ]
    if len(matches) != 1:
        raise AssertionError("Expected exactly one Hero.act automatic action patch")
    before = ast.literal_eval(matches[0].args[1])
    after = ast.literal_eval(matches[0].args[2])
    return before, after


class AutoPlayIdleWaitTests(unittest.TestCase):
    def test_idle_wait_advances_one_real_turn(self):
        before, after = hero_action_patch()
        self.assertEqual(before, "\t\tboolean actResult;\n\t\tif (curAction == null) {")
        self.assertTrue(after.endswith(before))
        self.assertEqual(after.count("spendAndNext(TICK);"), 1)
        self.assertLess(after.index("BlastAutoPlay.chooseAction(this)"),
                        after.index("&& curAction == null) {"))
        self.assertLess(after.index("&& curAction == null) {"),
                        after.index("spendAndNext(TICK);"))
        self.assertLess(after.index("spendAndNext(TICK);"),
                        after.index("boolean actResult;"))

    def test_wait_respects_manual_actions_and_targeting(self):
        _, after = hero_action_patch()
        self.assertLess(after.index("BlastAutoPlay.pauseForManualInput(this)"),
                        after.index("BlastAutoPlay.chooseAction(this)"))
        self.assertEqual(after.count("!com.spd.blast.BlastAutoPlay.manualActionActive(this)"), 3)
        self.assertIn("BlastSettings.testAutoPlay()\n"
                      "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.isPaused()\n"
                      "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.manualActionActive(this)\n"
                      "\t\t\t\t&& curAction == null", after)


if __name__ == "__main__":
    unittest.main()
