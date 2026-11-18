#!/usr/bin/env python3
"""Auto-play opens discovered containers and uses proper journal keys."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
AUTO = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"
INTERACT = ROOT / "core/src/main/java/com/spd/blast/BlastAutoInteract.java"


class AutoInteractionTests(unittest.TestCase):
    def test_uses_native_actions_after_combat_and_pickup(self):
        source = AUTO.read_text(encoding="utf-8")
        self.assertLess(source.index("pursueLastSeenTarget(hero"),
                        source.index("BlastAutoPickup.chooseAction(hero"))
        self.assertLess(source.index("BlastAutoPickup.chooseAction(hero"),
                        source.index("BlastAutoInteract.chooseAction(hero"))

    def test_container_and_key_eligibility(self):
        source = INTERACT.read_text(encoding="utf-8")
        self.assertIn("new HeroAction.OpenChest(target)", source)
        self.assertIn("new HeroAction.Unlock(target)", source)
        self.assertIn("if (!heap.seen", source)
        self.assertIn("Dungeon.branch == 0", source)
        self.assertIn("Notes.keyCount(new GoldenKey(Dungeon.depth))", source)
        self.assertIn("Notes.keyCount(new CrystalKey(Dungeon.depth))", source)
        self.assertIn("Notes.keyCount(new IronKey(Dungeon.depth))", source)
        self.assertIn("Notes.keyCount(new WornKey(Dungeon.depth))", source)

    def test_wands_remain_available_for_evasive_enemies(self):
        source = AUTO.read_text(encoding="utf-8")
        tactics = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoCombat.java").read_text(
            encoding="utf-8")
        self.assertIn("BlastAutoCombat.chooseAction(hero, safe, distances)", source)
        self.assertIn("BlastAutoPlay.highEvasion(hero, target) && wand != null", tactics)
        self.assertIn("if (avoidEvasiveNeutral(hero, mob)) continue;", source)
        self.assertNotIn("useWand(", source)


if __name__ == "__main__":
    unittest.main()
