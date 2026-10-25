#!/usr/bin/env python3
"""Route shrapnel damage and warning ranges through the shared blast rays."""
from pathlib import Path
import sys


def replace(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace(source,
        "import com.shatteredpixel.shatteredpixeldungeon.mechanics.ShadowCaster;\n",
        "", "obsolete shadowcast import")
    source = replace(source,
        "import com.watabou.utils.Point;\n",
        "", "obsolete point import")

    signature = "\t@Override\n\tpublic void explode(int cell) {"
    method = """\t@Override
\tpublic boolean[] blastWarningCells(int cell) {
\t\treturn com.spd.blast.BlastBombGeometry.reached(
\t\t\t\tDungeon.level, cell, blastExplosionRange());
\t}

"""
    source = replace(source, signature, method + signature, "shrapnel warning")

    source = replace(source,
        "\t\tboolean[] FOV = new boolean[Dungeon.level.length()];\n"
        "\t\tPoint c = Dungeon.level.cellToPoint(cell);\n"
        "\t\tShadowCaster.castShadow(c.x, c.y, Dungeon.level.width(), FOV, Dungeon.level.losBlocking, explosionRange());",
        "\t\tboolean[] FOV = com.spd.blast.BlastBombGeometry.reached(\n"
        "\t\t\t\tDungeon.level, cell, blastExplosionRange());",
        "shrapnel damage area")
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: shared ray-traced shrapnel damage and warnings")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_shrapnel_bomb.py <ShrapnelBomb.java>")
    patch(Path(sys.argv[1]))
