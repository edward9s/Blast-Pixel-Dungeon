#!/usr/bin/env python3
"""Expose the native wand zap flow to Blast's actor-driven auto-play test."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    anchor = "\tpublic boolean tryToZap( Hero owner, int target ){"
    count = source.count(anchor)
    if count != 1:
        raise SystemExit(f"{path}: expected one Wand.tryToZap anchor, found {count}")

    addition = (
        "\t/** Side-effect-free eligibility for automatic wand selection. */\n"
        "\tpublic boolean blastAutoCanZap(Hero hero) {\n"
        "\t\treturn curCharges >= chargesPerCast() && !cursed\n"
        "\t\t\t\t&& hero.buff(MagicImmune.class) == null;\n"
        "\t}\n\n"
        "\t/** Fire an aim cell already validated by Blast's combat policy. */\n"
        "\tpublic boolean blastAutoZap(Hero hero, int target) {\n"
        "\t\tif (!blastAutoCanZap(hero) || target == hero.pos) return false;\n"
        "\t\tif (new Ballistica(hero.pos, target, collisionProperties(target)).collisionPos == hero.pos) return false;\n"
        "\t\tcurUser = hero;\n"
        "\t\tcurItem = this;\n"
        "\t\tzapper.onSelect(target);\n"
        "\t\treturn true;\n"
        "\t}\n\n"
    )
    path.write_text(source.replace(anchor, addition + anchor, 1), encoding="utf-8")
    print(f"patched {path}: automatic wand targeting")


def patch_staff(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    anchor = "\tpublic MagesStaff() {"
    count = source.count(anchor)
    if count != 1:
        raise SystemExit(f"{path}: expected one MagesStaff constructor anchor, found {count}")
    addition = (
        "\t/** Expose the imbued wand while preserving the staff's curse restriction. */\n"
        "\tpublic Wand blastAutoPlayWand() {\n"
        "\t\treturn cursed || hasCurseEnchant() ? null : wand;\n"
        "\t}\n\n"
    )
    path.write_text(source.replace(anchor, addition + anchor, 1), encoding="utf-8")
    print(f"patched {path}: imbued wand access for auto-play")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_auto_play_wand.py <Wand.java> <MagesStaff.java>")
    patch(Path(sys.argv[1]))
    patch_staff(Path(sys.argv[2]))
