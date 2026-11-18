#!/usr/bin/env python3
"""Guard native aiming and per-wand safety in unified auto combat."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "core/src/main/java/com/spd/blast/BlastAutoCombat.java"
PATCH = ROOT / "integration/shattered/patches/patch_auto_play_wand.py"

class AutoCombatWeaponTests(unittest.TestCase):
    def test_native_aim_is_used_for_ordinary_and_spirit_missiles(self):
        source = SOURCE.read_text(encoding="utf-8")
        for check in ("QuickSlotButton.autoAim(target, weapon)",
                      "QuickSlotButton.autoAim(target, arrow)",
                      "bow.knockArrow()", "weapon.cast(hero, choice.aim)",
                      "weapon.durabilityLeft() <= weapon.durabilityPerUse()"):
            self.assertIn(check, source)
        self.assertNotIn("Ballistica.PROJECTILE", source)
        self.assertNotIn("pointBlankAccurate(", source)

    def test_wand_safety_and_aim(self):
        source = SOURCE.read_text(encoding="utf-8")
        for check in ("wand.blastAutoCanZap(hero)",
                      "QuickSlotButton.autoAim(target, wand)",
                      "choice.wand.blastAutoZap(hero, choice.aim)",
                      "wand instanceof WandOfLightning",
                      "wand instanceof WandOfDisintegration",
                      "wand instanceof WandOfCorrosion",
                      "lightningMultiplier(hero, target)",
                      "disintegrationSafe(hero, target, wand)",
                      "corrosionSafe(hero, target)",
                      "Math.min(beam.dist, 6 + 2 * wand.buffedLvl())",
                      "Blob.volumeAt(target.pos, CorrosiveGas.class)",
                      "BlastAutoPlay.isProtectedNpc"):
            self.assertIn(check, source)
        self.assertNotIn("wand instanceof WandOfFireblast", source)
        patch = PATCH.read_text(encoding="utf-8")
        self.assertIn("curCharges >= chargesPerCast()", patch)
        self.assertIn("collisionPos == hero.pos", patch)
        self.assertNotIn("collisionPos != target", patch)

if __name__ == "__main__":
    unittest.main()
