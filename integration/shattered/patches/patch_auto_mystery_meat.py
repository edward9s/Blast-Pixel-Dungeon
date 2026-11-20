#!/usr/bin/env python3
"""Keep raw-meat side effects for manual eating, but suppress them during automatic eating."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    anchor = (
        "\t@Override\n"
        "\tprotected void satisfy(Hero hero) {\n"
        "\t\tsuper.satisfy(hero);\n"
        "\t\teffect(hero);\n"
        "\t}"
    )
    replacement = (
        "\t@Override\n"
        "\tprotected void satisfy(Hero hero) {\n"
        "\t\tsuper.satisfy(hero);\n"
        "\t\tif (!com.spd.blast.BlastAutoFood.isEatingAutomatically()) {\n"
        "\t\t\teffect(hero);\n"
        "\t\t}\n"
        "\t}"
    )
    if source.count(anchor) != 1:
        raise SystemExit(f"{path}: expected one native MysteryMeat.satisfy anchor")
    path.write_text(source.replace(anchor, replacement, 1), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_auto_mystery_meat.py <MysteryMeat.java>")
    patch(Path(sys.argv[1]))
