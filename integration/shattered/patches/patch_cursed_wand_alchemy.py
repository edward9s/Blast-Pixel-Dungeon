#!/usr/bin/env python3
"""Allow wands with a known curse status to be brewed into arcane resin."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(recipe_path: Path, arcane_resin_path: Path) -> None:
    recipe = recipe_path.read_text(encoding="utf-8")
    arcane_resin = arcane_resin_path.read_text(encoding="utf-8")

    recipe = replace_once(
        recipe,
        "\t\t} else if (item instanceof Wand) {\n"
        "\t\t\treturn item.cursedKnown && !item.cursed;",
        "\t\t} else if (item instanceof Wand) {\n"
        "\t\t\treturn item.cursedKnown;",
        "Recipe.java: wand alchemy ingredient eligibility",
    )
    arcane_resin = replace_once(
        arcane_resin,
        "\t\t\t\t\t&& ingredients.get(0).cursedKnown\n"
        "\t\t\t\t\t&& !ingredients.get(0).cursed;",
        "\t\t\t\t\t&& ingredients.get(0).cursedKnown;",
        "ArcaneResin.java: wand melting eligibility",
    )

    recipe_path.write_text(recipe, encoding="utf-8")
    arcane_resin_path.write_text(arcane_resin, encoding="utf-8")
    print("patched Recipe.java and ArcaneResin.java: cursed wands can be recycled")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_cursed_wand_alchemy.py <Recipe.java> <ArcaneResin.java>")
    patch(Path(sys.argv[1]), Path(sys.argv[2]))
