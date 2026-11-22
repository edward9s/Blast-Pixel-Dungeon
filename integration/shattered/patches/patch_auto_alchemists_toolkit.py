#!/usr/bin/env python3
"""Expose native Alchemist's Toolkit readiness to automatic alchemy."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    anchor = (
        "\tpublic int availableEnergy(){\n"
        "\t\treturn charge;\n"
        "\t}\n"
    )
    replacement = (
        "\t/** Same readiness requirements as the native BREW action. */\n"
        "\tpublic boolean canBrewNow(Hero hero) {\n"
        "\t\treturn isEquipped(hero) && !cursed && hero.buff(MagicImmune.class) == null\n"
        "\t\t\t\t&& warmUpDelay <= 0;\n"
        "\t}\n"
        "\n"
        + anchor
    )
    if "public boolean canBrewNow(Hero hero)" in source:
        raise SystemExit(f"{path}: native AlchemistsToolkit is already patched")
    if source.count(anchor) != 1:
        raise SystemExit(f"{path}: expected one native AlchemistsToolkit.availableEnergy anchor")
    path.write_text(source.replace(anchor, replacement, 1), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_auto_alchemists_toolkit.py <AlchemistsToolkit.java>")
    patch(Path(sys.argv[1]))
