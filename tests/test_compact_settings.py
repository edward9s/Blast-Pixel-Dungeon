#!/usr/bin/env python3
"""Regression checks for the compact Blast settings and enchantment-stone toggle."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "core/src/main/java/com/spd/blast/BlastSettings.java"
TAB = ROOT / "core/src/main/java/com/spd/blast/BlastSettingsTab.java"
STONE_PATCH = ROOT / "integration/shattered/patches/patch_stone_to_scroll.py"
GUIDE_PATCH = ROOT / "integration/shattered/patches/patch_reverse_alchemy_guide.py"

TEST_LABELS = (
    "indestructible",
    "free_movement",
    "remote_throw",
    "demolition",
    "auto_play",
    "radius",
    "infinite_bomb",
)


class CompactSettingsTests(unittest.TestCase):
    def test_toggle_is_persistent_and_disabled_by_default(self):
        source = SETTINGS.read_text(encoding="utf-8")
        self.assertIn("getBoolean(ENCHANTMENT_STONE_REVERSE_ALCHEMY, false)", source)
        self.assertIn("setEnchantmentStoneReverseAlchemy(boolean value)", source)
        self.assertIn("GameSettings.put(ENCHANTMENT_STONE_REVERSE_ALCHEMY, value)", source)
        self.assertNotIn("ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY", source)

    def test_recipe_and_guide_check_the_live_setting(self):
        for path in (STONE_PATCH, GUIDE_PATCH):
            source = path.read_text(encoding="utf-8")
            self.assertIn("BlastSettings.enchantmentStoneReverseAlchemy()", source)
            self.assertNotIn("BlastSettings.ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY", source)

    def test_test_switches_use_a_compact_two_column_layout(self):
        source = TAB.read_text(encoding="utf-8")
        self.assertIn("testOptions = new CheckBox[]{", source)
        self.assertIn("demolition, autoPlay, enchantmentReverseAlchemy", source)
        self.assertNotIn("Messages.lang()", source)
        self.assertNotIn("Languages.CHI_TRAD", source)
        self.assertIn("option.multiline = true;", source)
        self.assertIn("i += 2", source)
        self.assertIn("left.reqHeight()", source)
        self.assertIn("right.reqHeight()", source)
        self.assertIn("if (BlastSettings.showTests())", source)
        self.assertIn("if (radiusLabel.visible)", source)
        self.assertIn("if (heightChanged != null) heightChanged.run()", source)
        self.assertIn("BlastSettings.setEnchantmentStoneReverseAlchemy(checked())", source)

    def test_settings_window_resizes_after_toggle(self):
        patch = (ROOT / "integration/shattered/patches/patch_wndsettings.py").read_text(encoding="utf-8")
        self.assertIn("final float standardHeight = height;", patch)
        self.assertIn("blast.setHeightChanged(() -> resize(width,", patch)

    def test_all_labels_have_no_test_prefix(self):
        locales = sorted((ROOT / "messages").glob("misc*.properties"))
        self.assertEqual(23, len(locales))
        for locale in locales:
            messages = dict(
                line.split("=", 1)
                for line in locale.read_text(encoding="utf-8").splitlines()
                if line.startswith("blast.") and "=" in line
            )
            self.assertTrue(messages["blast.settings.enchantment_reverse"], locale.name)
            for key in TEST_LABELS:
                label = messages[f"blast.settings.{key}"]
                self.assertNotIn(":", label, (locale.name, key))
                self.assertNotIn("：", label, (locale.name, key))
        traditional = (ROOT / "messages/misc_zh-hant.properties").read_text(
            encoding="utf-8"
        )
        self.assertIn("blast.settings.enchantment_reverse=附魔逆合成", traditional)
        japanese = (ROOT / "messages/misc_ja.properties").read_text(encoding="utf-8")
        korean = (ROOT / "messages/misc_ko.properties").read_text(encoding="utf-8")
        self.assertIn("blast.settings.enchantment_reverse=付呪逆錬金", japanese)
        self.assertIn("blast.settings.enchantment_reverse=부여석 역연금", korean)
        self.assertNotIn("enchantment_reverse=Enchantment stone reversal", japanese)
        self.assertNotIn("enchantment_reverse=Enchantment stone reversal", korean)


if __name__ == "__main__":
    unittest.main()
