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
                      "bow.knockArrow()", "weapon.cast(hero, choice.aim)"):
            self.assertIn(check, source)
        self.assertNotIn("Ballistica.PROJECTILE", source)
        self.assertNotIn("pointBlankAccurate(", source)

    def test_last_durability_does_not_exclude_missiles(self):
        source = SOURCE.read_text(encoding="utf-8")
        candidates = source.split("private static Choice bestMissile(", 1)[1].split(
            "private static Choice bestWand(", 1)[0]
        for restriction in ("durabilityLeft()", "durabilityPerUse()", "extraThrownLeft",
                            "hasGoodEnchant()", "masteryPotionBonus", "enchantHardened"):
            self.assertNotIn(restriction, candidates)
        self.assertIn("item.quantity() <= 0", candidates)
        self.assertIn("float damage = physicalDamage(hero, target, weapon, weapon);", candidates)

    def test_auto_weapon_collateral_safety(self):
        source = SOURCE.read_text(encoding="utf-8")
        self.assertIn("weaponCollateralSafe(hero, target, hero.belongings.attackingWeapon(), false)", source)
        self.assertIn("hero.canAttack(target) && meleeCollateralSafe(hero, target)", source)
        self.assertIn("weaponCollateralSafe(hero, target, arrow, true)", source)
        self.assertIn("weaponCollateralSafe(hero, target, weapon, true)", source)
        self.assertIn("weapon.hasEnchant(Shocking.class, hero)", source)
        self.assertIn("weapon.hasEnchant(Unstable.class, hero)", source)
        self.assertIn("weapon.hasEnchant(Eldritch.class, hero)", source)
        self.assertIn("hero.pointsInTalent(Talent.SHARED_ENCHANTMENT) > 0", source)
        self.assertIn("bow.hasEnchant(Shocking.class, hero)", source)
        self.assertIn("int radius = i == 0 ? 2", source)
        self.assertIn("Dungeon.level.water[from.pos] && !from.flying ? 2 : 1", source)
        self.assertIn("BlastAutoPlay.isAutoAttackExcluded((Mob) ch)", source)
        self.assertIn("mob.fieldOfView[hero.pos] || mob.fieldOfView[target.pos]", source)
        play = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")
        sacrifice = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoSacrifice.java").read_text(
            encoding="utf-8"
        )
        self.assertIn("selectedCanMelee && BlastAutoCombat.meleeCollateralSafe(hero, selected)", play)
        self.assertEqual(play.count("BlastAutoCombat.meleeCollateralSafe(hero, selected)"), 2)
        self.assertIn("attackable != null && BlastAutoCombat.meleeCollateralSafe(hero, attackable)", sacrifice)

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
                      "BlastAutoPlay.isAutoAttackExcluded",
                      "lightningMultiplier(hero, target)",
                      "disintegrationSafe(hero, target, wand)",
                      "corrosionSafe(hero, target)"):
            self.assertIn(check, source)
        self.assertNotIn("wand instanceof WandOfFireblast", source)
        patch = PATCH.read_text(encoding="utf-8")
        self.assertIn("curCharges >= chargesPerCast()", patch)
        self.assertIn("collisionPos == hero.pos", patch)
        self.assertNotIn("collisionPos != target", patch)

if __name__ == "__main__":
    unittest.main()
