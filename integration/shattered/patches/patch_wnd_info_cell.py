#!/usr/bin/env python3
"""Give Blast cave-in boulders their own magnifier name, description and native rock image."""
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
        "\tpublic static Image cellImage( int cell ){\n",
        "\tpublic static Image cellImage( int cell ){\n"
        "\t\tif (com.spd.blast.BlastWalls.rubbleAt(Dungeon.level, cell)) {\n"
        "\t\t\treturn com.shatteredpixel.shatteredpixeldungeon.tiles.TerrainFeaturesTilemap.tile(cell, Terrain.MINE_BOULDER);\n"
        "\t\t}\n",
        "Blast rubble info image")
    source = replace(source,
        "\tpublic static String cellName( int cell ){\n",
        "\tpublic static String cellName( int cell ){\n"
        "\t\tif (com.spd.blast.BlastWalls.rubbleAt(Dungeon.level, cell)) {\n"
        "\t\t\treturn com.spd.blast.BlastMessages.get(\"rubble.name\");\n"
        "\t\t}\n",
        "Blast rubble name")
    source = replace(source,
        "\t\ttitlebar.setRect(0, 0, WIDTH, 0);",
        "\t\tif (com.spd.blast.BlastWalls.rubbleAt(Dungeon.level, cell)) {\n"
        "\t\t\tdesc = com.spd.blast.BlastMessages.get(\"rubble.desc\");\n"
        "\t\t}\n"
        "\t\ttitlebar.setRect(0, 0, WIDTH, 0);",
        "Blast rubble description")

    path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wnd_info_cell.py <WndInfoCell.java>")
    patch(Path(sys.argv[1]))
