#!/usr/bin/env python3
"""Give regrowth bombs their own rooting rule while preserving other sources."""
from pathlib import Path
import sys


def replace_once(source: str, before: str, after: str, label: str) -> str:
    count = source.count(before)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(before, after, 1)


def patch(regrowth_path: Path, bomb_path: Path) -> None:
    regrowth = regrowth_path.read_text(encoding="utf-8")
    regrowth = replace_once(
        regrowth,
        "\t@Override\n\tprotected void evolve() {",
        "\tprotected boolean shouldRoot(Char target) {\n"
        "\t\treturn true;\n"
        "\t}\n\n"
        "\t@Override\n\tprotected void evolve() {",
        "regrowth rooting hook",
    )

    lines = regrowth.splitlines(keepends=True)
    marker = "&& !ch.isImmune(this.getClass())"
    matches = [index for index, line in enumerate(lines) if line.strip() == marker]
    if len(matches) != 1:
        raise SystemExit(f"regrowth rooting condition: expected one anchor, found {len(matches)}")
    index = matches[0]
    indentation = lines[index].split(marker, 1)[0]
    lines.insert(index + 1, indentation + "&& shouldRoot(ch)\n")
    regrowth = "".join(lines)

    bomb = bomb_path.read_text(encoding="utf-8")
    bomb = replace_once(
        bomb,
        "import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Regrowth;\n",
        "import com.spd.blast.BombRegrowth;\n",
        "regrowth bomb import",
    )
    bomb = replace_once(
        bomb,
        "Blob.seed( i, 10, Regrowth.class )",
        "Blob.seed( i, 10, BombRegrowth.class )",
        "regrowth bomb cloud",
    )

    # Verify both files before changing either; unexpected upstream changes fail.
    regrowth_path.write_text(regrowth, encoding="utf-8")
    bomb_path.write_text(bomb, encoding="utf-8")
    print("patched regrowth bomb rooting: allies excluded, enemies and neutrals included")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_regrowth_bomb.py <Regrowth.java> <RegrowthBomb.java>")
    patch(Path(sys.argv[1]), Path(sys.argv[2]))
