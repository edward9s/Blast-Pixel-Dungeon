#!/usr/bin/env python3
"""Expose SPD's native class-armor stat transfer to automatic equipment handling.

Extract the existing manual transfer statements into one shared method, so
manual and automatic transfers cannot diverge.
"""
from pathlib import Path
import sys

SELECTOR_START = "\t" * 8 + "Armor armor = (Armor)item;\n"
EFFECTS_START = "\t" * 8 + 'GLog.p( Messages.get(ClassArmor.class, "transfer_complete") );'
METHOD_ANCHOR = "\t@Override\n\tpublic ArrayList<String> actions( Hero hero ) {"


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    for anchor in (SELECTOR_START, EFFECTS_START, METHOD_ANCHOR):
        if source.count(anchor) != 1:
            raise SystemExit(f"ClassArmor transfer anchor changed: {anchor!r}")

    start = source.index(SELECTOR_START)
    end = source.index(EFFECTS_START)
    if end <= start:
        raise SystemExit("ClassArmor manual transfer block is missing")
    body = source[start + len(SELECTOR_START):end].strip("\n")
    if "level(armor.trueLevel());" not in body or "hero.belongings.armor = null;" not in body:
        raise SystemExit("ClassArmor native transfer semantics have changed")

    # The original selector lives eight tabs deep. The shared method body uses two.
    statements = []
    for line in body.splitlines():
        if line:
            if not line.startswith("\t" * 8):
                raise SystemExit("Unexpected ClassArmor transfer indentation")
            statements.append("\t\t" + line[8:].replace("ClassArmor.this", "this"))
        else:
            statements.append("")
    method = (
        "\t/** Transfer armor properties while retaining this hero-ability armor and its charge. */\n"
        "\tpublic void transferStatsFrom(Hero hero, Armor armor) {\n"
        "\t\tif (armor == this || !hero.belongings.contains(armor)) {\n"
        '\t\t\tthrow new IllegalArgumentException("Invalid class armor transfer source");\n'
        "\t\t}\n"
        + "\n".join(statements)
        + "\n\t}\n\n"
    )

    source = source[:start] + (
        "\t" * 8 + "ClassArmor.this.transferStatsFrom(hero, (Armor)item);\n\n"
    ) + source[end:]
    source = source.replace(METHOD_ANCHOR, method + METHOD_ANCHOR, 1)
    path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_class_armor.py <ClassArmor.java>")
    patch(Path(sys.argv[1]))
