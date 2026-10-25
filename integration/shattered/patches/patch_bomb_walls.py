#!/usr/bin/env python3
"""Apply seeded wall damage only to destructive bombs."""
from pathlib import Path
import sys


def replace(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace(
        source,
        "\t\t\tcom.spd.blast.BlastBombVisuals.blast(this, cell, affectedCells);\n",
        "\t\t\tcom.spd.blast.BlastBombVisuals.blast(this, cell, affectedCells);\n"
        "\t\t\tif (com.spd.blast.BlastWalls.damage(this, cell)) terrainAffected = true;\n",
        "destructive bomb wall damage",
    )
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: destructive bomb wall damage and wall-break particles")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_bomb_walls.py <Bomb.java>")
    patch(Path(sys.argv[1]))
