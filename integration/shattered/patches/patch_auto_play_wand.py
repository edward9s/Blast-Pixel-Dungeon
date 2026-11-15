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
        "\t/** Fire a safe targeted wand during a normal automatic hero turn. */\n"
        "\tpublic boolean blastAutoZap(Hero hero, int target) {\n"
        "\t\tif (curCharges < chargesPerCast() || cursed\n"
        "\t\t\t\t|| hero.buff(MagicImmune.class) != null || target == hero.pos) return false;\n"
        "\t\tif (new Ballistica(hero.pos, target, collisionProperties(target)).collisionPos != target) return false;\n"
        "\t\tcurUser = hero;\n"
        "\t\tcurItem = this;\n"
        "\t\tzapper.onSelect(target);\n"
        "\t\treturn true;\n"
        "\t}\n\n"
    )
    path.write_text(source.replace(anchor, addition + anchor, 1), encoding="utf-8")
    print(f"patched {path}: automatic wand targeting")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_auto_play_wand.py <Wand.java>")
    patch(Path(sys.argv[1]))
