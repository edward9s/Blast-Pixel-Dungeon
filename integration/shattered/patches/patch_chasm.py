#!/usr/bin/env python3
"""Complete interrupted manual actions when the chasm dialog is answered."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")

    source = replace_once(
        source,
        "\t\t\t\t\t\tprotected void onSelect( int index ) {\n"
        "\t\t\t\t\t\t\t\tif (index == 0 && elapsed > 0.2f) {",
        "\t\t\t\t\t\tprotected void onSelect( int index ) {\n"
        "\t\t\t\t\t\t\t\tif (index == 1 && elapsed > 0.2f) {\n"
        "\t\t\t\t\t\t\t\t\tcom.spd.blast.BlastAutoPlay.cancelManualConfirmation(hero);\n"
        "\t\t\t\t\t\t\t\t}\n"
        "\t\t\t\t\t\t\t\tif (index == 0 && elapsed > 0.2f) {",
        "chasm declined"
    )

    source = replace_once(
        source,
        "\t\t\t\t\t\t\t\t\t\tjumpConfirmed = true;\n"
        "\t\t\t\t\t\t\t\t\t\thero.resume();",
        "\t\t\t\t\t\t\t\t\t\tjumpConfirmed = true;\n"
        "\t\t\t\t\t\t\t\t\t\tcom.spd.blast.BlastAutoPlay.acceptManualConfirmation(hero);\n"
        "\t\t\t\t\t\t\t\t\t\thero.resume();",
        "chasm accepted"
    )

    source = replace_once(
        source,
        "\t\t\t\t\t\t}\n"
        "\t\t\t\t\t}\n"
        "\t\t\t\t);",
        "\t\t\t\t\t\t}\n"
        "\n"
        "\t\t\t\t\t\t@Override\n"
        "\t\t\t\t\t\tpublic void onBackPressed() {\n"
        "\t\t\t\t\t\t\tif (elapsed <= 0.2f) return;\n"
        "\t\t\t\t\t\t\tsuper.onBackPressed();\n"
        "\t\t\t\t\t\t\tcom.spd.blast.BlastAutoPlay.cancelManualConfirmation(hero);\n"
        "\t\t\t\t\t\t}\n"
        "\t\t\t\t\t}\n"
        "\t\t\t\t);",
        "chasm dialog dismissed"
    )

    path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_chasm.py <Chasm.java>")
    patch(Path(sys.argv[1]))
