#!/usr/bin/env python3
"""Allow curse-identified thrown weapons to be brewed into liquid metal."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(recipe_path: Path, liquid_metal_path: Path) -> None:
    recipe = recipe_path.read_text(encoding="utf-8")
    liquid_metal = liquid_metal_path.read_text(encoding="utf-8")

    recipe = replace_once(
        recipe,
        "\t\t\treturn item.cursedKnown && !item.cursed &&\n"
        "\t\t\t\t\titem instanceof MissileWeapon && item.isUpgradable();",
        "\t\t\treturn item.cursedKnown &&\n"
        "\t\t\t\t\titem instanceof MissileWeapon && item.isUpgradable();",
        "Recipe.java: alchemy ingredient eligibility",
    )
    liquid_metal = replace_once(
        liquid_metal,
        "\t\t\t\t\t&& ingredients.get(0).cursedKnown\n"
        "\t\t\t\t\t&& !ingredients.get(0).cursed;",
        "\t\t\t\t\t&& ingredients.get(0).cursedKnown;",
        "LiquidMetal.java: melting eligibility",
    )

    recipe_path.write_text(recipe, encoding="utf-8")
    liquid_metal_path.write_text(liquid_metal, encoding="utf-8")
    print("patched Recipe.java and LiquidMetal.java: cursed thrown weapons can be melted")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_cursed_missile_alchemy.py <Recipe.java> <LiquidMetal.java>")
    patch(Path(sys.argv[1]), Path(sys.argv[2]))
