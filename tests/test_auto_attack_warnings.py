#!/usr/bin/env python3
"""Regression checks for native telegraphed attack avoidance."""
from pathlib import Path
import unittest


PLAY = (Path(__file__).resolve().parents[1] /
        "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")


class AttackWarningTests(unittest.TestCase):
    def test_dodge_precedes_combat_and_special_room_loot(self):
        action = PLAY.split("private static boolean chooseActionOnce(Hero hero) {", 1)[1]
        dodge = action.split("// Finish toxic-room loot", 1)[0]
        self.assertIn("!hero.rooted && (collapsing || (attackWarningAt(hero.pos)", dodge)
        self.assertIn("PathFinder.NEIGHBOURS8", dodge)
        self.assertIn("Dungeon.level.insideMap(cell) && safe[cell]", dodge)
        self.assertIn("!BlastMovement.shouldUnlock(cell)", dodge)
        self.assertIn("hero.curAction = new HeroAction.Move(cell);", dodge)
        self.assertIn("return false;", dodge)
        self.assertNotIn("Indestructible", dodge)
        dodge_decision = action.index(
            'debugDecision = collapsing ? "pitfall-dodge" : "attack-warning-dodge"')
        self.assertLess(dodge_decision, action.index('debugDecision = "gas-ranged-bomb"'))
        self.assertLess(dodge_decision, action.index('debugDecision = "combat"'))

    def test_delayed_pitfall_uses_the_native_footprint(self):
        pending = PLAY.split("private static boolean[] pendingPitfallCells(Hero hero) {", 1)[1].split(
            "private static boolean[] safeCells", 1)[0]
        self.assertIn("hero.buffs(PitfallTrap.DelayedPit.class)", pending)
        self.assertIn("pit.depth != Dungeon.depth || pit.branch != Dungeon.branch", pending)
        self.assertIn("for (int cell : pit.positions)", pending)
        self.assertIn("danger[cell] = true;", pending)
        self.assertIn("if (pitfall[cell]) safe[cell] = false;", PLAY)

    def test_native_warning_expiry_and_synchronization(self):
        check = PLAY.split("static boolean attackWarningAt(int cell) {", 1)[1].split(
            "private static void excludeAttackWarnings", 1)[0]
        mask = PLAY.split("private static void excludeAttackWarnings(boolean[] safe) {", 1)[1].split(
            "private static boolean[] safeCells", 1)[0]
        self.assertIn("synchronized (TargetedCell.cells)", check)
        self.assertIn("warning != null && warning.time >= Actor.now()", check)
        self.assertIn("synchronized (TargetedCell.cells)", mask)
        self.assertIn("float now = Actor.now();", mask)
        self.assertIn("warning.time >= now", mask)
        self.assertIn("safe[warning.pos] = false;", mask)

    def test_warning_mask_after_occupancy_and_preserve_vault_planner(self):
        safe = PLAY.split("boolean phaseEnabled = BlastMovement.enabled();", 1)[1].split(
            "return safe;", 1)[0]
        self.assertLess(safe.index("for (Char character : Actor.chars())"),
                        safe.index("if (!planTimedHazards) excludeAttackWarnings(safe);"))
        self.assertLess(safe.index("excludeAttackWarnings(safe);"),
                        safe.index("safe[hero.pos] = true;"))
        self.assertIn("&& !BlastAutoVault.imminentAttackAt(hero, Dungeon.level)", PLAY)


if __name__ == "__main__":
    unittest.main()
