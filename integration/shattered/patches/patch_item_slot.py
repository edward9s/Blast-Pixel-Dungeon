#!/usr/bin/env python3
"""Recolor only the phase potion's upper-right running icon sky blue."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    old = "\t\t\titemIcon.frame(ItemSpriteSheet.Icons.film.get(item.icon));\n"
    if source.count(old) != 1:
        raise SystemExit(f"{path}: expected one item icon rendering anchor")
    source = source.replace(
        old,
        old +
        "\t\t\tif (item instanceof com.spd.blast.BlastPhasePotion) {\n"
        "\t\t\t\tcom.spd.blast.BlastPhasePotion.tintRunningIcon(itemIcon);\n"
        "\t\t\t}\n",
        1,
    )
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: sky-blue running icon for phase potion")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_item_slot.py <ItemSlot.java>")
    patch(Path(sys.argv[1]))
