#!/usr/bin/env python3
"""Place Blast cover after room painting, before level flags and spawns are built."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    old = "\t\treturn painter().paint(this, rooms);"
    new = (
        "\t\tboolean painted = painter().paint(this, rooms);\n"
        "\t\tif (painted) com.spd.blast.BlastRoomCover.place(this, rooms);\n"
        "\t\treturn painted;"
    )
    if source.count(old) != 1:
        raise SystemExit(f"{path}: expected exactly one room-painter anchor, found {source.count(old)}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_regular_level.py <RegularLevel.java>")
    patch(Path(sys.argv[1]))
