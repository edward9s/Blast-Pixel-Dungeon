#!/usr/bin/env python3
"""Smoke-test the low-level rewards patch and its fail-fast anchors."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_weak_mob_rewards.py"
FILES = {
    "actors/hero/Hero.java": (
        "\tpublic void earnExp( int exp, Class source ) {\n"
        "\t\tif (source != AscensionChallenge.class) {\n"
        "\t\t\tthis.exp += exp;\n"
        "\t\twhile (this.exp >= maxExp()) {\n"
    ),
    "actors/mobs/Mob.java": (
        "\t\t\t\tDungeon.hero.earnExp(exp, getClass());\n"
        "\tpublic void rollToDropLoot(){\n"
        "\t\tif (Dungeon.hero.lvl > maxLvl + 2) return;\n"
    ),
    "actors/buffs/AllyBuff.java": "\t\t\thero.earnExp(exp, enemy.getClass());\n",
    "actors/mobs/CausticSlime.java": "\t\tif (Dungeon.hero.lvl > maxLvl + 2) return;\n",
    "actors/mobs/DM201.java": "\t\tif (Dungeon.hero.lvl > maxLvl + 2) return;\n",
    "actors/mobs/SpectralNecromancer.java": "\t\tif (Dungeon.hero.lvl > maxLvl + 2) return;\n",
    "actors/mobs/GnollExile.java": "\t\tif (Dungeon.hero.lvl > maxLvl + 2) return;\n",
    "actors/mobs/HermitCrab.java": "\t\tif (Dungeon.hero.lvl <= maxLvl + 2){\n",
    "items/artifacts/MasterThievesArmband.java": (
        "\t\t\t\t\t\t\tif (Dungeon.hero.lvl > ((Mob) ch).maxLvl + 2) {\n"
    ),
}


def write_fixture(root: Path) -> None:
    for relative, contents in FILES.items():
        file = root / relative
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(contents, encoding="utf-8")


class WeakMobRewardsPatchTest(unittest.TestCase):
    def test_patched_effects_and_loot_paths(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_fixture(root)
            subprocess.run([sys.executable, str(PATCH), str(root)], check=True,
                           capture_output=True, text=True)
            hero = (root / "actors/hero/Hero.java").read_text(encoding="utf-8")
            self.assertIn("gainNonLevelExp", hero)
            self.assertIn("grantHeroExp && source != AscensionChallenge.class", hero)
            self.assertIn("while (grantHeroExp && this.exp >= maxExp())", hero)
            mob = (root / "actors/mobs/Mob.java").read_text(encoding="utf-8")
            self.assertIn("EXP > 0 && maxLvl > 0", mob)
            self.assertIn("weakMobExpEffects()", mob)
            self.assertIn("weakMobDrops()", mob)
            self.assertIn("gainNonLevelExp(enemy.EXP", (root / "actors/buffs/AllyBuff.java")
                          .read_text(encoding="utf-8"))
            for relative in FILES:
                if relative in ("actors/hero/Hero.java", "actors/buffs/AllyBuff.java"):
                    continue
                self.assertIn("weakMobDrops()", (root / relative).read_text(encoding="utf-8"),
                              relative)
            rerun = subprocess.run([sys.executable, str(PATCH), str(root)],
                                   capture_output=True, text=True)
            self.assertNotEqual(rerun.returncode, 0)

    def test_missing_anchor_does_not_partially_write(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_fixture(root)
            bad = root / "items/artifacts/MasterThievesArmband.java"
            bad.write_text("unexpected upstream", encoding="utf-8")
            result = subprocess.run([sys.executable, str(PATCH), str(root)],
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            for relative, original in FILES.items():
                if relative == "items/artifacts/MasterThievesArmband.java":
                    continue
                self.assertEqual((root / relative).read_text(encoding="utf-8"), original)


if __name__ == "__main__":
    unittest.main()
