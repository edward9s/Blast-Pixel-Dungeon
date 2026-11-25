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
        self.assertNotIn("CrystalGuardian", mining)
        spire = mining.split("static boolean attackBoss(", 1)[1].split(
            "static boolean attackArmoredGeomancer(", 1
        )[0]
        self.assertIn("getItem(Pickaxe.class)", spire)
        self.assertIn("new HeroAction.Interact(mob)", spire)


if __name__ == "__main__":
    unittest.main()
