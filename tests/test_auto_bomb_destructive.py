#!/usr/bin/env python3
"""Keep the auto-play bomb policy independent of item destruction."""
from pathlib import Path
import unittest

SOURCE = (
    Path(__file__).resolve().parents[1]
    / "core/src/main/java/com/spd/blast/BlastAutoBombAI.java"
)


class AutoBombDestructiveTests(unittest.TestCase):
    def test_destructive_bombs_are_not_rejected(self):
        source = SOURCE.read_text(encoding="utf-8")
        action = source.split("public static boolean act(Hero hero) {", 1)[1].split(
            "static boolean blastReachesTarget(", 1
        )[0]
        self.assertIn("Bomb bomb = maniac.selectedBomb();", action)
        self.assertNotIn("itemPreserving(", source)
        self.assertNotIn("explodesDestructively(", source)

    def test_other_automatic_attack_guards_remain(self):
        source = SOURCE.read_text(encoding="utf-8")
        action = source.split("public static boolean act(Hero hero) {", 1)[1].split(
            "static boolean blastReachesTarget(", 1
        )[0]
        for check in (
            "BlastSettings.testAutoPlay()",
            "BlastRemoteBomb.isTimedMode()",
            "hero.buff(BlastRemoteBomb.class)",
            "hero.buff(BlastIndestructible.class)",
            "hero.buff(BlastTestBuffs.Indestructible.class)",
            "maniac.remaining() == 0",
            "if (target == null) return false;",
            "if (containsProtectedMob(blast)) return false;",
        ):
            with self.subTest(check=check):
                self.assertIn(check, action)


if __name__ == "__main__":
    unittest.main()
