#!/usr/bin/env python3
"""Smoke tests for the reverse-runestone alchemy guide integration patch."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_reverse_alchemy_guide.py"

JOURNAL = """public class WndJournal {
\t\tprivate static final int NUM_BUTTONS = 9;
\t\tprivate static final int[] sprites = {
\t\t\t\tItemSpriteSheet.STONE_HOLDER,
\t\t\t\tItemSpriteSheet.FOOD_HOLDER,
\t\t};
\t\t\t\tif (Document.ALCHEMY_GUIDE.isPageFound(i)) {
\t\t\t\t\tbuttonWidth = width()/4;
\t\t\tif (currentPageIdx != -1 && !Document.ALCHEMY_GUIDE.isPageFound(currentPageIdx)){
\t\t\ttitle.label(Document.ALCHEMY_GUIDE.pageTitle(currentPageIdx));
\t\t\tbody.text(Document.ALCHEMY_GUIDE.pageBody(currentPageIdx));
\t\t\tDocument.ALCHEMY_GUIDE.readPage(currentPageIdx);
\t\t\t
\t\t\tArrayList<QuickRecipe> toAdd = QuickRecipe.getRecipes(currentPageIdx);
}
"""
RECIPE = """public class QuickRecipe {
\t\t\tcase 8:
\t\t\t\treturn result;
}
"""
PAGE = """public class DocumentPage {
\t\t\tWndJournal.AlchemyTab.currentPageIdx = document().pageIdx(page());
}
"""


class ReverseAlchemyGuideTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("patch_reverse_alchemy_guide", PATCH)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def patch(self, journal_source=JOURNAL, recipe_source=RECIPE, page_source=PAGE):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            journal, recipe, page = [root / filename for filename in
                                     ("WndJournal.java", "QuickRecipe.java", "DocumentPage.java")]
            for path, source in ((journal, journal_source),
                                 (recipe, recipe_source), (page, page_source)):
                path.write_text(source, encoding="utf-8")
            self.module.patch(journal, recipe, page)
            return tuple(path.read_text(encoding="utf-8")
                         for path in (journal, recipe, page))

    def test_inserts_virtual_page_after_stones(self):
        journal, recipe, page = self.patch()
        self.assertIn("NUM_BUTTONS = 10;", journal)
        self.assertIn("REVERSE_STONES_IDX = 2;", journal)
        self.assertIn("buttonWidth = width()/5;", journal)
        self.assertIn("i == REVERSE_STONES_IDX ? 1 : originalIndex", journal)
        self.assertIn("currentPageIdx - 1 : currentPageIdx", journal)
        self.assertIn("currentPageIdx == REVERSE_STONES_IDX ? 9 : originalIndex", journal)
        self.assertIn("ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY", journal)
        self.assertIn("case 9:", recipe)
        self.assertIn("new Scroll.StoneToScroll()", recipe)
        self.assertIn("r.testIngredients(in)", recipe)
        self.assertIn("originalIndex + 1 : originalIndex", page)

    def test_patch_fails_on_missing_or_changed_upstream(self):
        for index in range(3):
            sources = [JOURNAL, RECIPE, PAGE]
            sources[index] = "unexpected upstream content"
            with self.assertRaises(SystemExit):
                self.patch(*sources)

    def test_patch_fails_if_applied_twice(self):
        with self.assertRaises(SystemExit):
            self.patch(*self.patch())

    def test_messages_present_in_every_locale(self):
        keys = {
            "blast.alchemy.reverse_stones.title",
            "blast.alchemy.reverse_stones.body",
            "blast.alchemy.reverse_stones.enchantment_disabled",
        }
        locales = sorted((ROOT / "messages").glob("misc*.properties"))
        self.assertEqual(23, len(locales))
        for locale in locales:
            found = {line.split("=", 1)[0] for line in locale.read_text(
                encoding="utf-8").splitlines() if "=" in line}
            self.assertTrue(keys.issubset(found), locale.name)


if __name__ == "__main__":
    unittest.main()
