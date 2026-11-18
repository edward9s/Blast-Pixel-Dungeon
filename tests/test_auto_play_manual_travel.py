#!/usr/bin/env python3
"""Regression checks for resuming a manual destination after autonomous combat."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
AUTO = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")
HERO_PATCH = (ROOT / "integration/shattered/patches/patch_hero.py").read_text(encoding="utf-8")


def method(name: str, next_name: str) -> str:
    return AUTO.split(name, 1)[1].split(next_name, 1)[0]


class InterruptedManualTravelTests(unittest.TestCase):
    def test_manual_move_interrupt_yields_only_to_unpaused_combat(self):
        transfer = method(
            "public static void interruptManualTravelForCombat(Hero hero) {",
            "private static void clearInterruptedTravel()",
        )
        for requirement in (
            "!BlastSettings.testAutoPlay()",
            "isPaused()",
            "!manualActionActive(hero)",
            "hero.curAction != null",
            "hero.lastAction instanceof HeroAction.Move",
            "hero.visibleEnemies() == 0",
        ):
            self.assertIn(requirement, transfer)
        self.assertIn("((HeroAction.Move) hero.lastAction).dst", transfer)
        self.assertLess(transfer.index("interruptedDestination = destination"),
                        transfer.index("manualAction = false"))
        self.assertIn("hero.lastAction = null;", transfer)

    def test_combat_still_precedes_destination_and_destination_precedes_idle(self):
        choose = method("private static boolean chooseActionOnce(Hero hero) {",
                        "/** Wraiths and targets")
        self.assertLess(choose.index("BlastAutoCombat.chooseAction"),
                        choose.index("resumeInterruptedTravel(hero"))
        self.assertLess(choose.index("pursueLastSeenTarget(hero"),
                        choose.index("resumeInterruptedTravel(hero"))
        self.assertLess(choose.index("resumeInterruptedTravel(hero"),
                        choose.index("BlastAutoPickup.chooseAction"))
        self.assertLess(choose.index("resumeInterruptedTravel(hero"),
                        choose.index("BlastAutoFarming.chooseAction"))
        self.assertIn("progress.intent(BlastAutoProgressGuard.Intent.ROAM)", choose)

    def test_destination_uses_safe_path_and_expires_when_unreachable(self):
        resume = method("private static boolean resumeInterruptedTravel(",
                        "public static boolean manualActionActive(Hero hero)")
        self.assertIn("hero.pos == interruptedDestination", resume)
        self.assertIn("safe[interruptedDestination]", resume)
        self.assertIn("distances[interruptedDestination] != Integer.MAX_VALUE", resume)
        self.assertIn("firstStep(interruptedDestination, safe, distances)", resume)
        self.assertIn("++blockedTravelTurns >= BLOCKED_TRAVEL_TURNS", resume)
        self.assertIn("clearInterruptedTravel();", resume)

    def test_new_move_replaces_goal_but_manual_actions_keep_it(self):
        self.assertEqual(
            AUTO.count("        clearInterruptedTravel();\n        pendingManualInput.set(null);"),
            2,  # setting change and floor/hero reset
        )
        begin = method("public static void beginManualAction(Hero hero) {",
                       "/** A newly seen enemy")
        self.assertIn("hero.curAction instanceof HeroAction.Move", begin)
        self.assertIn("hero.lastAction instanceof HeroAction.Move", begin)
        self.assertIn("clearInterruptedTravel();", begin)
        rest = method("if (input instanceof ManualRest) {", "ManualCell cell =")
        self.assertNotIn("clearInterruptedTravel();", rest)
        item = method("public static void beginItemAction(Hero hero) {",
                      "/** A new run")
        self.assertNotIn("clearInterruptedTravel();", item)

    def test_upstream_hero_hook_runs_after_enemy_detection(self):
        hook = HERO_PATCH.split("# Enemy discovery interrupts native manual movement.", 1)[1]
        hook = hook.split("# Use the normal action dispatch", 1)[0]
        self.assertIn("checkVisibleMobs();", hook)
        self.assertIn("BlastAutoPlay.interruptManualTravelForCombat(this)", hook)
        self.assertLess(hook.index("checkVisibleMobs();"),
                        hook.index("interruptManualTravelForCombat(this)"))


if __name__ == "__main__":
    unittest.main()
