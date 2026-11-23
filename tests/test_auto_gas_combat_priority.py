#!/usr/bin/env python3
"""Regression checks for immediate combat before a gas retreat."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "core/src/main/java/com/spd/blast"
PLAY = (SOURCE / "BlastAutoPlay.java").read_text(encoding="utf-8")
COMBAT = (SOURCE / "BlastAutoCombat.java").read_text(encoding="utf-8")
BOMBS = (SOURCE / "BlastAutoBombAI.java").read_text(encoding="utf-8")
MANIAC = (SOURCE / "BlastBombManiac.java").read_text(encoding="utf-8")


class AutoGasCombatPriorityTest(unittest.TestCase):
    def test_gas_combat_precedes_both_retreat_paths(self):
        start = PLAY.index("int toxicRetreat = BlastAutoTrapClearer.gasRetreatStep(hero, safe);")
        gas_combat = PLAY.index('debugDecision = "gas-ranged-bomb";', start)
        trap_retreat = PLAY.index("if (toxicRetreat >= 0) {", gas_combat)
        hazard_retreat = PLAY.index("if (BlastAutoEnvironment.hazardousAt(hero.pos, hazards)", trap_retreat)
        self.assertLess(start, gas_combat)
        self.assertLess(gas_combat, trap_retreat)
        self.assertLess(trap_retreat, hazard_retreat)

    def test_only_persistent_invulnerability_and_gas_are_eligible(self):
        precombat = PLAY.split("int toxicRetreat = BlastAutoTrapClearer.gasRetreatStep(hero, safe);", 1)[1].split(
            "if (toxicRetreat >= 0) {", 1
        )[0]
        self.assertIn("hero.buff(BlastTestBuffs.Indestructible.class) != null", precombat)
        self.assertNotIn("hero.buff(BlastIndestructible.class)", precombat)
        self.assertIn("BlastAutoEnvironment.gasOnlyAt(hero.pos, hazards)", precombat)
        self.assertIn("!BlastAutoEnvironment.hazardousAt(hero.pos, hazards)", precombat)
        self.assertIn("hero.buff(Burning.class) == null", precombat)
        self.assertIn("!specialBarriers[hero.pos]", precombat)

    def test_immediate_attack_does_not_approach_enemy(self):
        self.assertIn("BlastAutoBombAI.actAgainstRanged(hero)", PLAY)
        self.assertIn("BlastAutoBombAI.actAgainstOtherEnemies(hero)", PLAY)
        self.assertIn("BlastAutoCombat.attackIfPossible(hero)", PLAY)
        immediate = COMBAT.split("static Action attackIfPossible(Hero hero) {", 1)[1].split(
            "\n    }", 1
        )[0]
        self.assertIn("return attackFromTargets(hero, enemies(hero));", immediate)
        attacks = COMBAT.split("private static Action attackFromTargets(", 1)[1].split(
            "private static Action chooseAttack(", 1
        )[0]
        self.assertIn("chooseAttack(hero, target, equipment)", attacks)
        self.assertNotIn("HeroAction.Move", attacks)
        self.assertIn("if (hero.rooted) return Action.NONE;", COMBAT)

    def test_sacrifice_rule_and_escape_fallback_remain(self):
        start = PLAY.index('debugDecision = "gas-ranged-bomb";')
        end = PLAY.index("if (toxicRetreat >= 0) {", start)
        branch = PLAY[start:end]
        self.assertIn("BlastAutoSacrifice.knownFireCell(", branch)
        self.assertIn("BlastAutoBombAI.actAgainstSacrifice(hero, fire.cur)", branch)
        self.assertIn("if (sacrificeActive)", branch)
        self.assertIn("} else {", branch)
        self.assertIn("BlastAutoTrapClearer.avoidGasReturn(hero, safe)", PLAY)

    def test_automatic_bomb_turn_costs_are_unchanged(self):
        self.assertIn("hero.spendAndNext(1f);", BOMBS)
        self.assertIn("hero.spendAndNext(autoDetonate ? 1f : timed ? 1f : 0f);", MANIAC)


if __name__ == "__main__":
    unittest.main()
