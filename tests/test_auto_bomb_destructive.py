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
        action = source.split("private static boolean act(Hero hero, boolean rangedTargets) {", 1)[1].split(
            "static boolean blastReachesTarget(", 1
        )[0]
        self.assertIn("Bomb bomb = maniac.selectedBomb();", action)
        self.assertNotIn("itemPreserving(", source)
        self.assertNotIn("explodesDestructively(", source)

    def test_other_automatic_attack_guards_remain(self):
        source = SOURCE.read_text(encoding="utf-8")
        action = source.split("private static boolean act(Hero hero, boolean rangedTargets) {", 1)[1].split(
            "static boolean blastReachesTarget(", 1
        )[0]
        for check in (
            "BlastSettings.testAutoPlay()",
            "BlastRemoteBomb.isTimedMode()",
            "hero.buff(BlastRemoteBomb.class)",
            "hero.buff(BlastIndestructible.class)",
            "hero.buff(BlastTestBuffs.Indestructible.class)",
            "maniac.remaining() == 0",
            "return target != null && detonate(hero, maniac, remoteThrow, target);",
        ):
            with self.subTest(check=check):
                self.assertIn(check, action)


    def test_rare_enemies_are_eligible_only_with_existing_bomb_immunity_gate(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertNotIn("isRare(", source)
        self.assertNotIn("RARE_ALTS", source)
        play = (SOURCE.parent / "BlastAutoPlay.java").read_text(encoding="utf-8")
        self.assertIn("return mob instanceof NPC || mob instanceof DemonSpawner;", play)
        self.assertEqual(source.count("if (!autoBombAvailable(hero))"), 2)
        self.assertIn("if (!autoBombAvailable(hero) || BlastSettings.testRemoteThrow()", source)
        self.assertEqual(source.count("containsExcludedMob(blast)"), 3)
        self.assertIn("&& !ignoresBombCollateral(mob)) return true;", source)
        self.assertIn("|| BlastAutoPlay.isAutoAttackExcluded(target)) return -1;", source)
        self.assertIn("|| containsUnreadySacrificeMob(blast, flames)", source)
        self.assertIn("|| !blast[mob.pos] || ignoresBombCollateral(mob)", source)
        self.assertIn("mob instanceof NPC && mob.alignment == Char.Alignment.NEUTRAL", source)
        self.assertIn("&& !(mob instanceof Shopkeeper)", source)


if __name__ == "__main__":
    unittest.main()
