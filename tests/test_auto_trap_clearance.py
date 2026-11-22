#!/usr/bin/env python3
"""Source checks for conservative corridor-trap clearing by automatic play."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CLEAR = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoTrapClearer.java").read_text(encoding="utf-8")
PLAY = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")


class AutoTrapClearanceTest(unittest.TestCase):
    def test_only_after_combat_loot_and_idle_tasks(self):
        self.assertLess(PLAY.index('debugDecision = "pickup";'), PLAY.index('debugDecision = "trap";'))
        self.assertLess(PLAY.index('debugDecision = "trap";'), PLAY.index('debugDecision = "roam";'))
        self.assertIn("BlastAutoTrapClearer.chooseAction(hero, idleSafe, idleDistances)", PLAY)
        self.assertIn("trapAction == BlastAutoTrapClearer.Action.THROW", PLAY)

    def test_hidden_and_revealed_disarming_corridor_traps(self):
        self.assertIn("trap.active", CLEAR)
        self.assertNotIn("trap.visible", CLEAR)
        self.assertIn("trap.disarmedByActivation", CLEAR)
        self.assertIn("terrain == Terrain.TRAP || terrain == Terrain.SECRET_TRAP", CLEAR)
        self.assertIn("level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell]", CLEAR)
        self.assertIn("visited[first] || mapped[first] || visible[first]", CLEAR)
        self.assertIn("visited[second] || mapped[second] || visible[second]", CLEAR)
        self.assertIn("Actor.findChar(cell) != null", CLEAR)
        self.assertIn("separatedSide(", CLEAR)
        self.assertIn("firstReached == secondReached", CLEAR)
        self.assertIn("passable[exit]", CLEAR)
        self.assertIn("pit[exit]", CLEAR)
        self.assertIn("exitKnown && !safe[exit]", CLEAR)
        self.assertIn("distances[stand] == Integer.MAX_VALUE", CLEAR)

    def test_only_safe_known_effects(self):
        allowed = CLEAR.split("static int minimumThrowDistance(", 1)[1].split(
            "static int reachableSide(", 1
        )[0]
        for name in ("CursingTrap", "FlashingTrap", "GrippingTrap", "WeakeningTrap", "OozeTrap"):
            self.assertIn("type == " + name + ".class", allowed)
        for name in ("ExplosiveTrap", "DisintegrationTrap", "BurningTrap", "PoisonDartTrap"):
            self.assertNotIn("type == " + name + ".class", allowed)
        self.assertIn("return -1;", allowed)
        self.assertIn("item.throwPos(hero, chosen.pos) != chosen.pos", CLEAR)
        self.assertIn("Ballistica.PROJECTILE", CLEAR)

    def test_rejection_diagnostic_is_bounded_and_specific(self):
        self.assertIn('status=" + reason', CLEAR)
        self.assertIn('" hidden=" + !trap.visible', CLEAR)
        for reason in ("unsupported", "not-chokepoint", "no-safe-stand",
                       "lost-inventory", "no-throwable-item", "blocked-trajectory"):
            self.assertIn('"' + reason + '"', CLEAR)
        self.assertIn("LOG_REPEAT_INTERVAL = 10", CLEAR)
        self.assertIn("Dungeon.level.distance(hero.pos, trap.pos) > MAX_THROW_DISTANCE", CLEAR)
        self.assertIn("BlastAutoTrapClearer.reset();", PLAY)

    def test_inventory_and_native_throw(self):
        self.assertIn("item instanceof Weapon || item instanceof Armor", CLEAR)
        self.assertIn("item instanceof Food", CLEAR)
        self.assertIn("item.quantity() > largestFood.quantity()", CLEAR)
        self.assertIn("candidate.isEquipped(hero)", CLEAR)
        self.assertIn("item.setCurrent(hero);", CLEAR)
        self.assertIn("item.cast(hero, chosen.pos);", CLEAR)
        self.assertNotIn("trap.activate()", CLEAR)


if __name__ == "__main__":
    unittest.main()
