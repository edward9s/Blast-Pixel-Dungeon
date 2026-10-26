#!/usr/bin/env python3
"""Let demolition own bomb-driven Tengu transitions, avoiding queued native jumps."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    anchor = "\tpublic void damage(int dmg, Object src) {\n"
    if source.count(anchor) != 1:
        raise SystemExit("Tengu.damage: expected exactly one damage method")
    source = source.replace(anchor, anchor
        + "\t\tif (com.spd.blast.BlastSettings.testDemolition()\n"
        + "\t\t\t\t&& src instanceof com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb) return;\n",
        1)
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: demolition-controlled bomb transitions")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_tengu.py <Tengu.java>")
    patch(Path(sys.argv[1]))
