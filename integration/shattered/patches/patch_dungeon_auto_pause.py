#!/usr/bin/env python3
"""Persist the player's auto-play pause state in the current dungeon save."""
from pathlib import Path
import sys


def replace_once(source: str, before: str, after: str, label: str) -> str:
    count = source.count(before)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(before, after, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")

    source = replace_once(
        source,
        "\tpublic static void init() {\n\n\t\tinitialVersion",
        "\tpublic static void init() {\n\n"
        "\t\tcom.spd.blast.BlastAutoPlay.resetPause();\n"
        "\t\tinitialVersion",
        "new-run pause reset",
    )

    source = replace_once(
        source,
        "\t\t\tbundle.put( HERO, hero );",
        "\t\t\tbundle.put( HERO, hero );\n"
        "\t\t\tbundle.put( \"blast_auto_play_paused\", "
        "com.spd.blast.BlastAutoPlay.isPaused() );",
        "save auto-play pause",
    )

    source = replace_once(
        source,
        "\t\thero = (Hero)bundle.get( HERO );",
        "\t\thero = (Hero)bundle.get( HERO );\n"
        "\t\tcom.spd.blast.BlastAutoPlay.restorePause("
        "hero, bundle.getBoolean(\"blast_auto_play_paused\"));",
        "restore auto-play pause",
    )

    path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_dungeon_auto_pause.py <Dungeon.java>")
    patch(Path(sys.argv[1]))
