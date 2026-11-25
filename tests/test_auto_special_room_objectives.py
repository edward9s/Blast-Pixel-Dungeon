"""Regression guards for special-room entrance ownership after task completion."""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "core/src/main/java/com/spd/blast"


def source(name):
    return (SRC / name).read_text(encoding="utf-8")


class SpecialRoomObjectivesTest(unittest.TestCase):
    def test_common_entrance_requires_objective(self):
        code = source("BlastAutoSpecialRooms.java")
        self.assertIn("static boolean canApproachEntrance(", code)
        self.assertIn("return objective >= 0 && position != entrance && !rooted", code)
        self.assertIn("if (!canApproachEntrance(objective, entrance, hero.pos, hero.rooted,", code)
        self.assertIn("return Action.NONE;", code)

    def test_summoning_room_does_not_claim_entrance_without_active_trap(self):
        code = source("BlastAutoSummoningRoom.java")
        action = code.split("static BlastAutoSpecialRooms.Action chooseAction(", 1)[1]
        self.assertLess(action.index("int best = -1"), action.index("if (best < 0 || hero.rooted)"))
        self.assertLess(action.index("if (best < 0 || hero.rooted)"),
                        action.index("BlastAutoSpecialRooms.approachEntrance("))
        self.assertIn("int origin = inside ? hero.pos : door;", action)
        self.assertIn("!isActiveSummoning(level, cell)", action)
        self.assertIn("crossing[cell] == Integer.MAX_VALUE", action)
        self.assertNotIn("if (!inside && hero.pos != door) {", action)

    def test_chasm_room_does_not_drink_for_entrance_only(self):
        code = source("BlastAutoChasmRoom.java")
        action = code.split("static BlastAutoSpecialRooms.Action chooseAction(", 1)[1]
        self.assertIn("if (goal < 0) return BlastAutoSpecialRooms.Action.NONE;", action)
        self.assertIn("BlastAutoSpecialRooms.approachEntrance(", action)
        self.assertNotIn("goal = entrance;", action)
        self.assertIn("potion.execute(hero, Potion.AC_DRINK)", action)
        self.assertIn("static BlastAutoSpecialRooms.Action continueFlight(", code)

    def test_other_entrance_tasks_use_the_same_guard(self):
        sentry = source("BlastAutoSentryRoom.java")
        special = source("BlastAutoSpecialRooms.java")
        self.assertIn("if (treasure == null) return BlastAutoSpecialRooms.Action.NONE;", sentry)
        self.assertIn("hero, treasure.pos, exit, safe, distances", sentry)
        self.assertIn("return approachEntrance(hero, chest.pos, exit, safe, distances);", special)
        self.assertIn("if (chest == null) return Action.NONE;", special)

    def test_opening_containers_does_not_filter_by_pickup_eligibility(self):
        for name in ("BlastAutoInteract.java", "BlastAutoSentryRoom.java",
                     "BlastAutoSpecialRooms.java", "BlastAutoChasmRoom.java",
                     "BlastAutoToxicRoom.java", "BlastAutoVault.java"):
            self.assertNotIn("hasCollectibleLoot(", source(name), name)

    def test_active_crossing_precedes_optional_tasks(self):
        code = source("BlastAutoPlay.java")
        escape = code.index('debugDecision = "trap-retreat"')
        haste = code.index("BlastAutoSentryRoom.continueRun(")
        flight = code.index("BlastAutoSpecialRooms.continueFlight(")
        self.assertLess(escape, haste)
        self.assertLess(escape, flight)
        for task in ("BlastAutoCrystalRooms.collect(", "resumeInterruptedTravel(hero,",
                     "BlastAutoEquipment.manage(", "BlastAutoAlchemy.brewIfPossible(",
                     "BlastAutoPickup.chooseAction("):
            self.assertLess(haste, code.index(task, haste))
            self.assertLess(flight, code.index(task, haste))

    def test_live_hazards_keep_their_separate_retreat_rules(self):
        self.assertIn("hero.pos != exit && travel[exit]", source("BlastAutoSentryRoom.java"))
        chasm = source("BlastAutoChasmRoom.java")
        self.assertIn("if (reach[exit] != Integer.MAX_VALUE) {\n            landing = exit;", chasm)
        self.assertIn("if (!flight[cell] || level.pit[cell]", chasm)


if __name__ == "__main__":
    unittest.main()
