#!/usr/bin/env python3
"""Render Blast pickup icons through ItemSprite's item-aware path in the journal."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    anchor = "\t\t\t\tsprite = new ItemSprite(item.image, seen ? item.glowing() : null);"
    if source.count(anchor) != 1:
        raise SystemExit(f"{path}: expected exactly one journal item sprite anchor")
    source = source.replace(
        anchor,
        """\t\t\t\tsprite = item instanceof com.spd.blast.BlastPickup
\t\t\t\t\t\t? new ItemSprite(item)
\t\t\t\t\t\t: new ItemSprite(item.image, seen ? item.glowing() : null);""",
        1,
    )
    icon_anchor = "\t\t\t\t\t\tsecondIcon.frame(ItemSpriteSheet.Icons.film.get(item.icon));"
    if source.count(icon_anchor) != 1:
        raise SystemExit(f"{path}: expected exactly one journal icon anchor")
    source = source.replace(icon_anchor, icon_anchor +
        "\n\t\t\t\t\t\tif (item instanceof com.spd.blast.BlastPhasePotion) {" +
        "\n\t\t\t\t\t\t\tcom.spd.blast.BlastPhasePotion.tintRunningIcon(secondIcon);" +
        "\n\t\t\t\t\t\t}", 1)
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: Blast pickup and sky-blue phase icons in journal")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wndjournal.py <WndJournal.java>")
    patch(Path(sys.argv[1]))
