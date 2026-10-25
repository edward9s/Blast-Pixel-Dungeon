#!/usr/bin/env python3
"""Show ordinary dungeon floor beneath Blast cave-in rubble."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    old = "\t\tint visual = DungeonTileSheet.directVisuals.get(tile, -1);"
    new = (
        "\t\t// Mining boulders have no base tile in regular dungeon tilesets.\n"
        "\t\t// Keep the native rock feature, but draw ordinary ground beneath it.\n"
        "\t\tif (tile == Terrain.MINE_BOULDER\n"
        "\t\t\t\t&& com.spd.blast.BlastWalls.rubbleAt(Dungeon.level, pos)) {\n"
        "\t\t\treturn DungeonTileSheet.getVisualWithAlts(DungeonTileSheet.FLOOR, pos);\n"
        "\t\t}\n"
        + old
    )
    if source.count(old) != 1:
        raise SystemExit(f"{path}: expected exactly one terrain visual anchor, found {source.count(old)}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_dungeon_terrain_tilemap.py <DungeonTerrainTilemap.java>")
    patch(Path(sys.argv[1]))
