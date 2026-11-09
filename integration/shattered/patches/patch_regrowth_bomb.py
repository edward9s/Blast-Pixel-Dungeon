#!/usr/bin/env python3
"""Customize regrowth bomb rooting and healing while preserving other sources."""
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

    bomb = replace_once(
        bomb,
        "import com.shatteredpixel.shatteredpixeldungeon.Dungeon;\n",
        "import com.shatteredpixel.shatteredpixeldungeon.Challenges;\n"
        "import com.shatteredpixel.shatteredpixeldungeon.Dungeon;\n",
        "challenge import",
    )
    bomb = replace_once(
        bomb,
        "import com.shatteredpixel.shatteredpixeldungeon.actors.Char;\n",
        "import com.shatteredpixel.shatteredpixeldungeon.actors.Char;\n"
        "import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;\n",
        "buff import",
    )
    bomb = replace_once(
        bomb,
        "import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;\n",
        "import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;\n"
        "import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;\n",
        "sungrass import",
    )
    bomb = replace_once(
        bomb,
        "\t\t\t\t\t\t//same as a healing potion\n"
        "\t\t\t\t\t\tPotionOfHealing.cure(ch);\n"
        "\t\t\t\t\t\tPotionOfHealing.heal(ch);",
        "\t\t\t\t\t\tPotionOfHealing.cure(ch);\n"
        "\t\t\t\t\t\tif (ch == Dungeon.hero && Dungeon.isChallenged(Challenges.NO_HEALING)) {\n"
        "\t\t\t\t\t\t\tPotionOfHealing.heal(ch);\n"
        "\t\t\t\t\t\t} else {\n"
        "\t\t\t\t\t\t\tBuff.affect(ch, Sungrass.Health.class).boost((int) (0.8f * ch.HT + 14));\n"
        "\t\t\t\t\t\t}",
        "regrowth bomb healing",
    )

    # Verify both files before changing either; unexpected upstream changes fail.
    regrowth_path.write_text(regrowth, encoding="utf-8")
    bomb_path.write_text(bomb, encoding="utf-8")
    print("patched regrowth bomb: ally-safe rooting and stationary herbal healing")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_regrowth_bomb.py <Regrowth.java> <RegrowthBomb.java>")
    patch(Path(sys.argv[1]), Path(sys.argv[2]))
