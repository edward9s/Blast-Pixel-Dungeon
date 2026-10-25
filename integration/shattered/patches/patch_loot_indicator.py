#!/usr/bin/env python3
"""Hide the Loot Tag if its target cannot be picked up."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    old = ("\t\t\tHeap heap = Dungeon.level.heaps.get( Dungeon.hero.pos );\n"
           "\t\t\tif (heap != null) {")
    if source.count(old) != 1:
        raise SystemExit(f"{path}: expected exactly one LootIndicator anchor")
    source = source.replace(old,
        "\t\t\tHeap heap = Dungeon.level.heaps.get( Dungeon.hero.pos );\n"
        "\t\t\tif (com.spd.blast.BlastLoot.showLootTag(heap)) {", 1)
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: skip non-pickable bombs")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_loot_indicator.py <LootIndicator.java>")
    patch(Path(sys.argv[1]))
