#!/usr/bin/env python3
"""Regression checks for automatic pickup candidate visibility and native retries."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
PICKUP = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPickup.java").read_text(encoding="utf-8")
PATCH = (ROOT / "integration/shattered/patches/patch_hero.py").read_text(encoding="utf-8")


class NativeAutoPickupTests(unittest.TestCase):
    def test_current_visibility_can_reveal_eligible_floor_loot(self):
        self.assertIn("knownLoot(heap.seen, hero.fieldOfView[cell])", PICKUP)
        self.assertIn("return seen || visible;", PICKUP)
        self.assertLess(PICKUP.index("knownLoot(heap.seen"), PICKUP.index("canCollect(hero, item"))

    def test_cooldown_only_starts_after_native_failure(self):
        action = PICKUP.split("public static boolean chooseAction(", 1)[1].split(
            "static boolean isPickupType(", 1
        )[0]
        self.assertNotIn("retryTurns = RETRY_TURNS;", action)
        callback = PICKUP.split("public static void onPickupFailure(", 1)[1].split(
            "static boolean knownLoot(", 1
        )[0]
        self.assertIn("item == attemptedItem && cell == attemptedCell", callback)
        self.assertIn("hero == currentHero && Dungeon.level == currentLevel", callback)
        self.assertIn("retryTurns = RETRY_TURNS;", callback)

    def test_buried_loot_uses_native_pickup_after_reordering(self):
        action = PICKUP.split("public static boolean chooseAction(", 1)[1].split(
            "static String nearbyDiagnostic(", 1
        )[0]
        self.assertIn("firstEligibleItem(heap, candidate ->", action)
        self.assertIn("exposeForPickup(nearestHeap, nearestItem);", action)
        self.assertIn("hero.curAction = new HeroAction.PickUp(nearest);", action)
        self.assertLess(action.index("if (nearest == hero.pos)"),
                        action.index("exposeForPickup(nearestHeap, nearestItem);"))
        self.assertIn("for (Item item : heap.items)", PICKUP)
        self.assertIn("heap.items.addFirst(item);", PICKUP)

    def test_native_hero_reports_only_failed_pickup(self):
        self.assertIn("Hero.actPickUp report native pickup failure", PATCH)
        self.assertIn("BlastAutoPickup.onPickupFailure(this, item, pos);", PATCH)
        callback = PATCH.index("BlastAutoPickup.onPickupFailure(this, item, pos);")
        self.assertLess(callback, PATCH.index("if (waitOrPickup)", callback))


if __name__ == "__main__":
    unittest.main()
