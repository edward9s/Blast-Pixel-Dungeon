#!/usr/bin/env python3
"""Regression checks for the compact Blast settings and enchantment-stone toggle."""
from pathlib import Path
import runpy
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SETTINGS = ROOT / "core/src/main/java/com/spd/blast/BlastSettings.java"
TAB = ROOT / "core/src/main/java/com/spd/blast/BlastSettingsTab.java"
OPTION_SLIDER_PATCH = ROOT / "integration/shattered/patches/patch_option_slider.py"
APPLY_SCRIPT = ROOT / "integration/shattered/apply.sh"
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
        self.assertIn("demolition, autoPlay, autoFarming, enchantmentReverseAlchemy", source)
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

    def test_spawn_and_explosion_radius_use_full_width_sliders(self):
        tab = TAB.read_text(encoding="utf-8")
        settings = SETTINGS.read_text(encoding="utf-8")

        self.assertIn("MIN_SPAWN_MULTIPLIER = 1", settings)
        self.assertIn("MAX_SPAWN_MULTIPLIER = 10", settings)
        self.assertIn("RADIUS_BOOST_VALUES = {0, 1, 2, 3, 5, 10, 20, 40, 70, 100}", tab)
        self.assertIn('new OptionSlider("", "1x", "10x",', tab)
        self.assertIn('new OptionSlider("", "0", "100", 0, RADIUS_BOOST_VALUES.length - 1)', tab)

        for name in ("spawn", "radius"):
            self.assertNotIn(f"{name}Decrease", tab)
            self.assertNotIn(f"{name}Increase", tab)
            self.assertIn(f"{name}Slider.setSelectedValue(", tab)
        self.assertIn("spawnSlider.setRect(0, spawnTop, width, SLIDER_HEIGHT)", tab)
        self.assertIn("radiusSlider.setRect(0, controlsTop, width, SLIDER_HEIGHT)", tab)

        self.assertIn("BlastSettings.setSpawnMultiplier(getSelectedValue())", tab)
        self.assertIn("BlastRadius.setStacks(hero, RADIUS_BOOST_VALUES[getSelectedValue()])", tab)
        self.assertIn('spawnValue.text(multiplier + "x")', tab)
        self.assertIn('radiusValue.text("+" + count)', tab)
        self.assertIn("radiusSlider.setSelectedValue(radiusBoostIndex(count))", tab)
        self.assertIn("private static int radiusBoostIndex(int current)", tab)
        self.assertIn("if (current < 0) throw new IllegalArgumentException", tab)
        self.assertNotIn("BlastRadius.setStacks(hero, count)", tab)
        self.assertIn("radiusSlider.visible = radiusVisible", tab)

    def test_option_slider_drag_preview_only_commits_on_release(self):
        tab = TAB.read_text(encoding="utf-8")
        apply_script = APPLY_SCRIPT.read_text(encoding="utf-8")
        self.assertIn('patch_option_slider.py" "$root/ui/OptionSlider.java"', apply_script)
        self.assertEqual(tab.count("protected void onDragPreview(int value)"), 2)
        self.assertIn('spawnValue.text(value + "x")', tab)
        self.assertIn('radiusValue.text("+" + RADIUS_BOOST_VALUES[value])', tab)

        # Small upstream-style fixture exercises every fail-fast patch anchor.
        fixture = (
            "\tprivate int selectedVal;\n"
            "\tprotected abstract void onChange();\n"
            "\t\tthis.selectedVal = val;\n"
            "\t\t\t\tsliderNode.brightness(1.5f);\n"
            "\t\t\t\t\tselectedVal = minVal + Math.round((sliderNode.x - x) / tickDist);\n"
            "\t\t\tprotected void onDrag( PointerEvent event ) {\n"
            "\t\t\t\tif (pressed) {\n"
            "\t\t\t\t\tPointF p = camera().screenToCamera((int) event.current.x, (int) event.current.y);\n"
            "\t\t\t\t\tsliderNode.x = GameMath.gate(sliderBG.x - 2, p.x - sliderNode.width()/2, sliderBG.x + sliderBG.width() - 2);\n"
        )
        patch = runpy.run_path(str(OPTION_SLIDER_PATCH))["patch"]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "OptionSlider.java"
            path.write_text(fixture, encoding="utf-8")
            patch(path)
            patched = path.read_text(encoding="utf-8")
            with self.assertRaises(SystemExit):
                patch(path)

        self.assertEqual(patched.count("previewDragValue();"), 2)
        self.assertIn("protected void onDragPreview(int value) {}", patched)
        self.assertIn("if (value != previewVal)", patched)
        self.assertIn("previewVal = val;", patched)
        self.assertIn("previewVal = selectedVal;", patched)
        self.assertIn("onDragPreview(value);", patched)
        self.assertNotIn("onChange();\n\t\t\t\t\tpreviewDragValue();", patched)

    def test_explosion_boost_has_ten_non_linear_steps(self):
        tab = TAB.read_text(encoding="utf-8")
        self.assertIn("RADIUS_BOOST_VALUES.length - 1", tab)
        self.assertIn("Math.abs((long) RADIUS_BOOST_VALUES[i] - current)", tab)
        self.assertIn("Math.abs((long) RADIUS_BOOST_VALUES[closest] - current)", tab)
        self.assertIn("return closest;", tab)
        self.assertIn("radiusValue.text(\"+\" + count)", tab)
        # Loading an existing save must not rewrite a non-step boost by opening settings.
        self.assertEqual(
            tab.count("BlastRadius.setStacks(hero,"),
            1,
        )

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
        self.assertIn("blast.settings.enchantment_reverse=升級合成", traditional)
        japanese = (ROOT / "messages/misc_ja.properties").read_text(encoding="utf-8")
        korean = (ROOT / "messages/misc_ko.properties").read_text(encoding="utf-8")
        self.assertIn("blast.settings.enchantment_reverse=強化合成", japanese)
        self.assertIn("blast.settings.enchantment_reverse=강화 합성", korean)
        self.assertNotIn("enchantment_reverse=Enchantment stone reversal", japanese)
        self.assertNotIn("enchantment_reverse=Enchantment stone reversal", korean)


if __name__ == "__main__":
    unittest.main()
