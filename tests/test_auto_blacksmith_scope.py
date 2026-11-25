"""Regression guards for manual quest dialogue and native mining combat."""
from pathlib import Path
import unittest

SRC = Path(__file__).resolve().parents[1] / "core/src/main/java/com/spd/blast"


class AutoBlacksmithScopeTest(unittest.TestCase):
    def test_mining_automation_never_opens_the_blacksmith_dialog(self):
        interactions = (SRC / "BlastAutoInteract.java").read_text()
        self.assertNotIn("Blacksmith", interactions)
        mining = (SRC / "BlastAutoBlacksmith.java").read_text()
        self.assertIn("Dungeon.branch == 1 && Dungeon.level instanceof MiningLevel", mining)
        for action in ("mine", "attackBoss", "attackArmoredGeomancer"):
            with self.subTest(action=action):
                method = mining.split("static boolean " + action + "(", 1)[1]
                self.assertIn("!active()", method.split("for (", 1)[0])
        self.assertNotIn("HeroAction.LvlTransition", mining)

    def test_spire_uses_the_pickaxe_without_forcing_global_settings(self):
        mining = (SRC / "BlastAutoBlacksmith.java").read_text()
        self.assertNotIn("setTestFreeMovement", mining)
        self.assertNotIn("setTestDemolition", mining)
        spire = mining.split("static boolean attackBoss(", 1)[1].split(
            "static boolean attackArmoredGeomancer(", 1
        )[0]
        self.assertIn("getItem(Pickaxe.class)", spire)
        self.assertIn("new HeroAction.Interact(mob)", spire)
        self.assertIn("PathFinder.NEIGHBOURS8", spire)
        self.assertIn("mob instanceof CrystalGuardian && mob.isAlive()", spire)

    def test_guardians_have_a_separate_demolition_rule(self):
        mining = (SRC / "BlastAutoBlacksmith.java").read_text()
        scope = mining.split("static boolean guardianDemolition()", 1)[1].split("}", 1)[0]
        self.assertIn("phase()", scope)
        self.assertIn("Blacksmith.Quest.CRYSTAL", scope)
        self.assertIn("!Blacksmith.Quest.bossBeaten()", scope)
        self.assertIn("((CrystalGuardian) mob).recovering()", mining)
        demolition = (SRC / "BlastDemolition.java").read_text()
        self.assertIn("if (!general && !guardians) return null;", demolition)
        self.assertIn("boolean destructive = general && bomb.explodesDestructively();", demolition)
        self.assertIn("if (mob instanceof CrystalGuardian && BlastAutoBlacksmith.active()) return guardians;", demolition)
        self.assertIn("return general && !(mob instanceof CrystalSpire)", demolition)


if __name__ == "__main__":
    unittest.main()
