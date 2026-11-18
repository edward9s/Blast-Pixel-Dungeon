#!/usr/bin/env python3
"""Guard native interrupted-movement semantics while Blast auto-play is paused."""
import ast
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
AUTO = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")
GAME_PATCH = ROOT / "integration/shattered/patches/patch_game_scene.py"
CONTROLS_PATCH = ROOT / "integration/shattered/patches/patch_auto_play_controls.py"


def method(signature: str, next_signature: str) -> str:
    return AUTO.split(signature, 1)[1].split(next_signature, 1)[0]


def insertion(path: Path, label: str) -> str:
    calls = [
        node for node in ast.walk(ast.parse(path.read_text(encoding="utf-8")))
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id == "replace"
        and len(node.args) == 4
        and isinstance(node.args[3], ast.Constant)
        and node.args[3].value == label
    ]
    if len(calls) != 1:
        raise AssertionError(f"Expected one {label} replacement, found {len(calls)}")
    return ast.literal_eval(calls[0].args[2])


class NativePausedManualActionsTests(unittest.TestCase):
    def test_attack_tap_leaves_native_last_action_to_hero_handle(self):
        code = method("public static boolean performManualInput(Hero hero) {",
                      "/** An explicit item action")
        self.assertNotIn("hero.lastAction = null;", code)
        self.assertIn("hero.handle(cell.destination)", code)
        begin = method("public static void beginManualAction(Hero hero) {",
                       "/** A newly seen enemy")
        self.assertIn("hero.curAction instanceof HeroAction.Move", begin)
        self.assertIn("hero.lastAction instanceof HeroAction.Move", begin)
        self.assertNotIn("clearInterruptedTravel();\n        manualAction = true;", begin)

    def test_wait_and_bomb_do_not_destroy_suspended_route(self):
        wait = method("if (input instanceof ManualRest) {", "ManualCell cell =")
        bomb = method("public static boolean performBombInput(Hero hero) {",
                      "/** Retry a blocked action")
        self.assertNotIn("hero.lastAction = null;", wait)
        self.assertNotIn("clearInterruptedTravel();", wait)
        self.assertNotIn("hero.lastAction = null;", bomb)

    def test_item_action_keeps_native_resume_tag_and_autonomous_goal(self):
        item = method("public static void beginItemAction(Hero hero) {",
                      "/** A new run")
        self.assertIn("hero.curAction = null;", item)
        self.assertNotIn("hero.lastAction = null;", item)
        self.assertNotIn("clearInterruptedTravel();", item)

    def test_pause_makes_auto_goal_visible_as_native_resume_tag(self):
        toggle = method("public static void togglePause() {",
                        "/** The Hero.act caller")
        self.assertIn("hero.lastAction = new HeroAction.Move(interruptedDestination);", toggle)
        self.assertLess(toggle.index("pausedHero = hero;"),
                        toggle.index("hero.lastAction = new HeroAction.Move"))
        self.assertIn("interruptedDestination = ((HeroAction.Move) hero.lastAction).dst", toggle)
        self.assertLess(toggle.index("interruptedDestination = ((HeroAction.Move) hero.lastAction).dst"),
                        toggle.index("pausedHero = null;"))
        self.assertIn("if (resume && hero.ready && !GameScene.manualInputPending())", toggle)

    def test_native_cancel_is_available_only_when_paused_or_disabled(self):
        cancellation = insertion(GAME_PATCH, "autoplay running ignores queued move during cancellation")
        self.assertIn("!com.spd.blast.BlastAutoPlay.isPaused()", cancellation)
        self.assertIn("return cancelCellSelector()", cancellation)
        window = insertion(GAME_PATCH, "windows retain native paused cancellation")
        self.assertIn("|| com.spd.blast.BlastAutoPlay.isPaused()", window)
        self.assertIn("cancel();", window)

    def test_resume_tag_still_calls_native_hero_resume(self):
        source = CONTROLS_PATCH.read_text(encoding="utf-8")
        self.assertIn("BlastAutoPlay.beginManualAction(Dungeon.hero)", source)
        self.assertIn("Dungeon.hero.resume();", source)


if __name__ == "__main__":
    unittest.main()
