#!/usr/bin/env python3
"""Guard combat target priority and melee-only pursuit of ranged enemies."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMBAT = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"
BOMBS = ROOT / "core/src/main/java/com/spd/blast/BlastAutoBombAI.java"


class AutoTargetPriorityTests(unittest.TestCase):
    def test_priority_is_used_before_movement_and_pickup(self):
        source = COMBAT.read_text(encoding="utf-8")
        action = source.split("public static boolean chooseAction(Hero hero)", 1)[1].split(
            "private static boolean useWand(", 1
        )[0]
        self.assertLess(action.index("combatPriority(mob,"),
                        action.index("betterTarget(priority,"))
        self.assertLess(action.index("betterTarget(priority,"),
                        action.index("if (selected != null) {", action.index("if (invulnerable !=")))
        self.assertLess(action.index("if (selected != null) {", action.index("if (invulnerable !=")),
                        action.index("pursueLastSeenTarget(hero"))
        self.assertLess(action.index("pursueLastSeenTarget(hero"),
                        action.index("BlastAutoPickup.chooseAction(hero"))

    def test_ranged_enemies_require_close_combat(self):
        source = COMBAT.read_text(encoding="utf-8")
        self.assertIn("canMeleeTarget(ranged, adjacent, hero.canAttack(mob))", source)
        self.assertIn("return canAttack && (!rangedEnemy || adjacent);", source)
        wand = source.split("private static boolean useWand(Hero hero, Mob target)", 1)[1].split(
            "private static boolean castWand(", 1
        )[0]
        self.assertIn(
            "if (BlastAutoSacrifice.isRangedEnemy(target.getClass())) return false;",
            wand,
        )

    def test_bomb_targeting_uses_actual_landing_footprint(self):
        source = BOMBS.read_text(encoding="utf-8")
        self.assertIn("int center = remoteThrow ? BlastBombTrajectory.throwPos(", source)
        self.assertIn(
            "blastReachesTarget(bomb.blastWarningCells(landing), mob.pos)",
            source,
        )
        self.assertIn("containsRareMob(blast)", source)
        self.assertIn("BlastRemoteBomb.isTimedMode()", source)


if __name__ == "__main__":
    unittest.main()
