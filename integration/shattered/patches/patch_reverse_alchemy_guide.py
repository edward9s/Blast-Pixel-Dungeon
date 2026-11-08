#!/usr/bin/env python3
"""Add a reverse-runestone page to SPD's existing alchemy guide."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(journal_path: Path, recipe_path: Path, document_page_path: Path) -> None:
    journal = journal_path.read_text(encoding="utf-8")
    recipe = recipe_path.read_text(encoding="utf-8")
    document_page = document_page_path.read_text(encoding="utf-8")

    journal = replace_once(
        journal,
        "\t\tprivate static final int NUM_BUTTONS = 9;",
        "\t\tprivate static final int NUM_BUTTONS = 10;\n"
        "\t\tpublic static final int REVERSE_STONES_IDX = 2;",
        "alchemy guide button count",
    )
    journal = replace_once(
        journal,
        "\t\t\t\tItemSpriteSheet.STONE_HOLDER,\n\t\t\t\tItemSpriteSheet.FOOD_HOLDER,",
        "\t\t\t\tItemSpriteSheet.STONE_HOLDER,\n"
        "\t\t\t\tItemSpriteSheet.SCROLL_HOLDER,\n"
        "\t\t\t\tItemSpriteSheet.FOOD_HOLDER,",
        "reverse runestone button icon",
    )
    journal = replace_once(
        journal,
        "\t\t\t\tif (Document.ALCHEMY_GUIDE.isPageFound(i)) {",
        "\t\t\t\tint originalIndex = i > REVERSE_STONES_IDX ? i - 1 : i;\n"
        "\t\t\t\tif (Document.ALCHEMY_GUIDE.isPageFound(\n"
        "\t\t\t\t\t\ti == REVERSE_STONES_IDX ? 1 : originalIndex)) {",
        "reverse runestone page unlock",
    )
    journal = replace_once(
        journal,
        "\t\t\t\t\tbuttonWidth = width()/4;",
        "\t\t\t\t\tbuttonWidth = width()/5;",
        "five buttons in the guide's second portrait row",
    )
    journal = replace_once(
        journal,
        "\t\t\tif (currentPageIdx != -1 && !Document.ALCHEMY_GUIDE.isPageFound(currentPageIdx)){",
        "\t\t\tint originalIndex = currentPageIdx > REVERSE_STONES_IDX\n"
        "\t\t\t\t\t? currentPageIdx - 1 : currentPageIdx;\n"
        "\t\t\tif (currentPageIdx == REVERSE_STONES_IDX) originalIndex = 1;\n"
        "\t\t\tif (currentPageIdx != -1 && !Document.ALCHEMY_GUIDE.isPageFound(originalIndex)){",
        "existing alchemy page discovery mapping",
    )
    journal = replace_once(
        journal,
        "\t\t\ttitle.label(Document.ALCHEMY_GUIDE.pageTitle(currentPageIdx));",
        "\t\t\ttitle.label(currentPageIdx == REVERSE_STONES_IDX\n"
        "\t\t\t\t\t? com.spd.blast.BlastMessages.get(\"alchemy.reverse_stones.title\")\n"
        "\t\t\t\t\t: Document.ALCHEMY_GUIDE.pageTitle(originalIndex));",
        "reverse runestone guide title",
    )
    journal = replace_once(
        journal,
        "\t\t\tbody.text(Document.ALCHEMY_GUIDE.pageBody(currentPageIdx));",
        "\t\t\tif (currentPageIdx == REVERSE_STONES_IDX) {\n"
        "\t\t\t\tString description = com.spd.blast.BlastMessages.get(\"alchemy.reverse_stones.body\");\n"
        "\t\t\t\tif (!com.spd.blast.BlastSettings.ENABLE_ENCHANTMENT_STONE_REVERSE_ALCHEMY) {\n"
        "\t\t\t\t\tdescription += \"\\n\" + com.spd.blast.BlastMessages.get(\"alchemy.reverse_stones.enchantment_disabled\");\n"
        "\t\t\t\t}\n"
        "\t\t\t\tbody.text(description);\n"
        "\t\t\t} else {\n"
        "\t\t\t\tbody.text(Document.ALCHEMY_GUIDE.pageBody(originalIndex));\n"
        "\t\t\t}",
        "reverse runestone guide description",
    )
    journal = replace_once(
        journal,
        "\t\t\tDocument.ALCHEMY_GUIDE.readPage(currentPageIdx);\n\t\t\t\n"
        "\t\t\tArrayList<QuickRecipe> toAdd = QuickRecipe.getRecipes(currentPageIdx);",
        "\t\t\tif (currentPageIdx != REVERSE_STONES_IDX) {\n"
        "\t\t\t\tDocument.ALCHEMY_GUIDE.readPage(originalIndex);\n"
        "\t\t\t}\n\t\t\t\n"
        "\t\t\tArrayList<QuickRecipe> toAdd = QuickRecipe.getRecipes(\n"
        "\t\t\t\t\tcurrentPageIdx == REVERSE_STONES_IDX ? 9 : originalIndex);",
        "reverse runestone recipe rows",
    )

    recipe = replace_once(
        recipe,
        "\t\t\tcase 8:\n",
        "\t\t\tcase 9:\n"
        "\t\t\t\tr = new Scroll.StoneToScroll();\n"
        "\t\t\t\tfor (Class<?> cls : Generator.Category.STONE.classes) {\n"
        "\t\t\t\t\tRunestone first = (Runestone) Reflection.newInstance(cls);\n"
        "\t\t\t\t\tRunestone second = (Runestone) Reflection.newInstance(cls);\n"
        "\t\t\t\t\tArrayList<Item> in = new ArrayList<>(Arrays.asList(first, second));\n"
        "\t\t\t\t\tif (r.testIngredients(in)) {\n"
        "\t\t\t\t\t\tresult.add(new QuickRecipe(r, in, r.sampleOutput(in)));\n"
        "\t\t\t\t\t}\n"
        "\t\t\t\t}\n"
        "\t\t\t\treturn result;\n"
        "\t\t\tcase 8:\n",
        "reverse runestone quick recipes",
    )

    document_page = replace_once(
        document_page,
        "\t\t\tWndJournal.AlchemyTab.currentPageIdx = document().pageIdx(page());",
        "\t\t\tint originalIndex = document().pageIdx(page());\n"
        "\t\t\tWndJournal.AlchemyTab.currentPageIdx = originalIndex >= WndJournal.AlchemyTab.REVERSE_STONES_IDX\n"
        "\t\t\t\t\t? originalIndex + 1 : originalIndex;",
        "existing alchemy page pickup index",
    )

    journal_path.write_text(journal, encoding="utf-8")
    recipe_path.write_text(recipe, encoding="utf-8")
    document_page_path.write_text(document_page, encoding="utf-8")
    print("patched reverse-runestone guide tab, quick recipes, and page pickup indices")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: patch_reverse_alchemy_guide.py <WndJournal.java> <QuickRecipe.java> <DocumentPage.java>")
    patch(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
