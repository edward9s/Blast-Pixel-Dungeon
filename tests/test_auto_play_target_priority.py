#!/usr/bin/env python3
"""Guard unified hostile combat and bomb targeting."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
COMBAT = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"
BOMBS = ROOT / "core/src/main/java/com/spd/blast/BlastAutoBombAI.java"
TACTICS = ROOT / "core/src/main/java/com/spd/blast/BlastAutoCombat.java"


class AutoTargetPriorityTests(unittest.TestCase):
    def test_priority_is_used_before_movement_and_pickup(self):
        source = COMBAT.read_text(encoding="utf-8")
        tactics = TACTICS.read_text(encoding="utf-8")
        self.assertLess(source.index("BlastAutoCombat.chooseAction(hero"),
                        source.index("pursueLastSeenTarget(hero"))
        self.assertLess(source.index("pursueLastSeenTarget(hero"),
                        source.index("BlastAutoPickup.chooseAction(hero"))
        self.assertIn("targets.sort(Comparator.comparingInt", tactics)
        self.assertIn("BlastAutoPlay.combatPriority(mob,", tactics)
        self.assertLess(tactics.index("chooseAttack(hero, target, bow, arrow)"),
                        tactics.index("approachBombRange(hero, target, safe, distances)"))

    def test_ranged_and_melee_enemies_share_attack_selection(self):
        source = COMBAT.read_text(encoding="utf-8")
        tactics = TACTICS.read_text(encoding="utf-8")
        self.assertNotIn("canMeleeTarget(", source)
        self.assertNotIn("useWand(", source)
        self.assertIn("if (mob.alignment == Char.Alignment.ENEMY) continue;", source)
        self.assertIn("boolean canMelee = hero.canAttack(target);", tactics)
        self.assertIn("preferRemote(remote.damage, melee)", tactics)
        self.assertNotIn("pointBlankAccurate(", tactics)

    def test_npcs_and_demon_spawners_are_excluded_from_combat(self):
        combat = COMBAT.read_text(encoding="utf-8")
        bombs = BOMBS.read_text(encoding="utf-8")
        sacrifice = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoSacrifice.java").read_text(
            encoding="utf-8"
        )

        self.assertIn("return mob instanceof NPC || mob instanceof DemonSpawner;", combat)
        self.assertIn("&& isAwake(mob) && !isAutoAttackExcluded(mob)) return true;", combat)
        self.assertIn("|| isAutoAttackExcluded(mob)", combat)
        self.assertIn("|| isAutoAttackExcluded(combatTarget)", combat)
        self.assertIn("|| attacker instanceof NPC", combat)
        self.assertIn("&& !(lastAttacker instanceof NPC)", combat)
        tactics = TACTICS.read_text(encoding="utf-8")
        self.assertIn("|| BlastAutoPlay.isAutoAttackExcluded(mob)", tactics)

        self.assertIn("containsExcludedMob(blast)", bombs)
        self.assertIn("|| !blast[mob.pos] || ignoresBombCollateral(mob)", bombs)
        self.assertIn("&& !ignoresBombCollateral(mob)) return true;", bombs)
        self.assertIn("|| BlastAutoPlay.isAutoAttackExcluded(mob)", bombs)
        self.assertIn("!BlastAutoPlay.isAutoAttackExcluded(mob)", sacrifice)

    def test_bomb_targeting_uses_actual_landing_footprint(self):
        source = BOMBS.read_text(encoding="utf-8")
        self.assertIn("int center = remoteThrow\n                    ? BlastBombTrajectory.throwPos(hero.pos, mob.pos) : hero.pos;", source)
        self.assertIn(
            "!blastReachesTarget(blast, mob.pos)",
            source,
        )
        self.assertIn("containsExcludedMob(blast)", source)
        self.assertIn("BlastRemoteBomb.isTimedMode()", source)


if __name__ == "__main__":
    unittest.main()
