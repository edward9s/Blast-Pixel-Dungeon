"""Regression checks for keeping SPD class armor and hero abilities in auto-play."""
from pathlib import Path
import runpy
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_class_armor.py"
SOURCE = ROOT / "core/src/main/java/com/spd/blast/BlastAutoEquipment.java"
APPLY = ROOT / "integration/shattered/apply.sh"

NATIVE_TRANSFER = (
    "\t" * 8 + "Armor armor = (Armor)item;\n"
    + "\t" * 8 + "armor.detach(hero.belongings.backpack);\n"
    + "\t" * 8 + "if (hero.belongings.armor == armor){\n"
    + "\t" * 9 + "hero.belongings.armor = null;\n"
    + "\t" * 8 + "}\n"
    + "\t" * 8 + "level(armor.trueLevel());\n"
    + "\t" * 8 + "tier = armor.tier;\n"
    + "\n"
)

FIXTURE = (
    "class ClassArmor {\n"
    + "\t@Override\n\tpublic ArrayList<String> actions( Hero hero ) { return null; }\n"
    + "\t" * 8 + "public void onSelect(Item item) {\n"
    + "\t" * 8 + "if (item == null || item == ClassArmor.this) return;\n\n"
    + NATIVE_TRANSFER
    + "\t" * 8 + 'GLog.p( Messages.get(ClassArmor.class, "transfer_complete") );\n'
    + "\t" * 8 + "hero.sprite.operate(hero.pos);\n"
    + "\t" * 8 + "}\n"
    + "}\n"
)


class AutoClassArmorTests(unittest.TestCase):

    @staticmethod
    def apply_patch(content):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "ClassArmor.java"
            path.write_text(content, encoding="utf-8")
            runpy.run_path(str(PATCH))["patch"](path)
            return path.read_text(encoding="utf-8")

    def test_transfer_reuses_native_implementation(self):
        result = self.apply_patch(FIXTURE)
        self.assertIn("public void transferStatsFrom(Hero hero, Armor armor)", result)
        self.assertIn("ClassArmor.this.transferStatsFrom(hero, (Armor)item);", result)
        self.assertEqual(result.count("armor.detach(hero.belongings.backpack);"), 1)
        self.assertEqual(result.count("level(armor.trueLevel());"), 1)
        self.assertIn("tier = armor.tier;", result)
        self.assertIn("hero.sprite.operate(hero.pos);", result)
        self.assertLess(result.index("transferStatsFrom(hero, (Armor)item);"),
                        result.index("GLog.p("))

    def test_patch_rejects_upstream_drift(self):
        with self.assertRaises(SystemExit):
            self.apply_patch(FIXTURE.replace(
                "Armor armor = (Armor)item;", "Armor chosen = (Armor)item;"))

    def test_auto_selection_keeps_class_armor_equipped(self):
        equipment = SOURCE.read_text(encoding="utf-8")
        script = APPLY.read_text(encoding="utf-8")
        self.assertIn('patch_class_armor.py" "$root/items/armor/ClassArmor.java"', script)
        self.assertIn("if (current instanceof ClassArmor)", equipment)
        self.assertIn("classArmor.transferStatsFrom(hero, best);", equipment)
        self.assertIn("ClassArmor classArmor = hero.belongings.getItem(ClassArmor.class);",
                      equipment)
        self.assertIn("wouldLoseProtectedClassGlyph(classArmor, best)", equipment)
        self.assertIn("classArmor.charge != charge", equipment)


if __name__ == "__main__":
    unittest.main()
