#!/usr/bin/env python3
"""Start one replace-on-entry debug file per game save, never per floor."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, name: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{name}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "\t\t\tDungeon.switchLevel( level, -1 );\n\t\t} else {",
        "\t\t\tDungeon.switchLevel( level, -1 );\n"
        "\t\t\tcom.spd.blast.BlastLog.beginGame(GamesInProgress.curSlot);\n"
        "\t\t} else {",
        "new-game debug session",
    )
    source = replace_once(
        source,
        "\t\t\tDungeon.switchLevel( level, Dungeon.hero.pos );\n\t\t}\n\t}",
        "\t\t\tDungeon.switchLevel( level, Dungeon.hero.pos );\n\t\t}\n"
        "\t\tcom.spd.blast.BlastLog.beginGame(GamesInProgress.curSlot);\n\t}",
        "continued-game debug session",
    )
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: per-save debug file session")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_interlevel_debug.py <InterlevelScene.java>")
    patch(Path(sys.argv[1]))
