#!/usr/bin/env python3
"""Keep bomb-generated harmful effects separate from ordinary environmental effects."""
from pathlib import Path
import sys


def replace_once(source: str, before: str, after: str, label: str) -> str:
    count = source.count(before)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(before, after, 1)


def patch(root: Path) -> None:
    files = [
        "actors/blobs/Fire.java",
        "actors/blobs/Freezing.java",
        "items/bombs/Firebomb.java",
        "items/bombs/FrostBomb.java",
        "items/bombs/FlashBangBomb.java",
        "items/bombs/HolyBomb.java",
    ]
    updated = {path: (root / path).read_text(encoding="utf-8") for path in files}

    fire = updated["actors/blobs/Fire.java"]
    fire = replace_once(
        fire,
        "Freezing freeze = (Freezing)Dungeon.level.blobs.get( Freezing.class );",
        "Freezing freeze = (Freezing)Dungeon.level.blobs.get( Freezing.class );\n"
        "\t\tFreezing bombFreeze = (Freezing)Dungeon.level.blobs.get(com.spd.blast.BombFreezing.class);",
        "fire: bomb-freezing lookup",
    )
    fire = replace_once(
        fire,
        "if (freeze != null && freeze.volume > 0 && freeze.cur[cell] > 0){\n"
        "\t\t\t\t\t\tfreeze.clear(cell);",
        "if (hasFreezing(freeze, cell) || hasFreezing(bombFreeze, cell)) {\n"
        "\t\t\t\t\t\tif (hasFreezing(freeze, cell)) freeze.clear(cell);\n"
        "\t\t\t\t\t\tif (hasFreezing(bombFreeze, cell)) bombFreeze.clear(cell);",
        "fire: extinguish both freezing sources",
    )
    fire = replace_once(
        fire,
        "} else if (freeze == null || freeze.volume <= 0 || freeze.cur[cell] <= 0) {",
        "} else if (!hasFreezing(freeze, cell) && !hasFreezing(bombFreeze, cell)) {",
        "fire: spread past freezing",
    )
    if fire.count("burn( cell );") != 2:
        raise SystemExit("fire: expected two burn calls")
    fire = fire.replace("burn( cell );", "burnCell( cell );")
    fire = replace_once(
        fire,
        "\tpublic static void burn( int pos ) {",
        "\tprivate static boolean hasFreezing(Freezing cloud, int cell) {\n"
        "\t\treturn cloud != null && cloud.volume > 0 && cloud.cur[cell] > 0;\n"
        "\t}\n\n"
        "\tprotected boolean shouldBurn(Char target) {\n"
        "\t\treturn true;\n"
        "\t}\n\n"
        "\tprotected void burnCell(int pos) {\n"
        "\t\tburn(pos, shouldBurn(Actor.findChar(pos)));\n"
        "\t}\n\n"
        "\tpublic static void burn( int pos ) {\n"
        "\t\tburn(pos, true);\n"
        "\t}\n\n"
        "\tprivate static void burn(int pos, boolean affectCharacter) {",
        "fire: keep ordinary static burn unchanged",
    )
    fire = replace_once(
        fire,
        "if (ch != null && !ch.isImmune(Fire.class)) {",
        "if (affectCharacter && ch != null && !ch.isImmune(Fire.class)) {",
        "fire: character-only immunity",
    )
    updated["actors/blobs/Fire.java"] = fire

    freezing = updated["actors/blobs/Freezing.java"]
    freezing = replace_once(
        freezing,
        "Fire fire = (Fire)Dungeon.level.blobs.get( Fire.class );",
        "Fire fire = (Fire)Dungeon.level.blobs.get( Fire.class );\n"
        "\t\tFire bombFire = (Fire)Dungeon.level.blobs.get(com.spd.blast.BombFire.class);",
        "freezing: bomb-fire lookup",
    )
    freezing = replace_once(
        freezing,
        "if (fire != null && fire.volume > 0 && fire.cur[cell] > 0){\n"
        "\t\t\t\t\t\tfire.clear(cell);",
        "if (hasFire(fire, cell) || hasFire(bombFire, cell)) {\n"
        "\t\t\t\t\t\tif (hasFire(fire, cell)) fire.clear(cell);\n"
        "\t\t\t\t\t\tif (hasFire(bombFire, cell)) bombFire.clear(cell);",
        "freezing: extinguish both fire sources",
    )
    freezing = replace_once(
        freezing, "Freezing.freeze(cell);", "freezeCell(cell);",
        "freezing: invoke source-aware method",
    )
    freezing = replace_once(
        freezing,
        "\tpublic static void freeze( int cell ){",
        "\tprivate static boolean hasFire(Fire cloud, int cell) {\n"
        "\t\treturn cloud != null && cloud.volume > 0 && cloud.cur[cell] > 0;\n"
        "\t}\n\n"
        "\tprotected boolean shouldFreeze(Char target) {\n"
        "\t\treturn true;\n"
        "\t}\n\n"
        "\tprotected void freezeCell(int cell) {\n"
        "\t\tfreeze(cell, shouldFreeze(Actor.findChar(cell)));\n"
        "\t}\n\n"
        "\tpublic static void freeze( int cell ) {\n"
        "\t\tfreeze(cell, true);\n"
        "\t}\n\n"
        "\tprivate static void freeze(int cell, boolean affectCharacter) {",
        "freezing: keep ordinary static freeze unchanged",
    )
    freezing = replace_once(
        freezing,
        "if (ch != null && !ch.isImmune(Freezing.class)) {",
        "if (affectCharacter && ch != null && !ch.isImmune(Freezing.class)) {",
        "freezing: character-only immunity",
    )
    updated["actors/blobs/Freezing.java"] = freezing

    firebomb = updated["items/bombs/Firebomb.java"]
    firebomb = replace_once(
        firebomb, "Blob.seed(i, 2, Fire.class)",
        "Blob.seed(i, 2, com.spd.blast.BombFire.class)",
        "firebomb: pit fire",
    )
    firebomb = replace_once(
        firebomb, "Blob.seed(i, 10, Fire.class)",
        "Blob.seed(i, 10, com.spd.blast.BombFire.class)",
        "firebomb: normal fire",
    )
    updated["items/bombs/Firebomb.java"] = firebomb

    frostbomb = updated["items/bombs/FrostBomb.java"]
    frostbomb = replace_once(
        frostbomb, "Blob.seed(i, 10, Freezing.class)",
        "Blob.seed(i, 10, com.spd.blast.BombFreezing.class)",
        "frostbomb: freezing source",
    )
    frostbomb = replace_once(
        frostbomb,
        "if (ch != null){\n\t\t\t\t\tBuff.affect(ch, Frost.class, 2f);",
        "if (ch != null && !com.spd.blast.BlastBombSafety.protects(ch)) {\n"
        "\t\t\t\t\tBuff.affect(ch, Frost.class, 2f);",
        "frostbomb: immediate frost",
    )
    updated["items/bombs/FrostBomb.java"] = frostbomb

    flashbang = updated["items/bombs/FlashBangBomb.java"]
    flashbang = replace_once(
        flashbang,
        "for (Char ch : affected){\n\t\t\t//25% bonus damage and 10 turns of stun",
        "for (Char ch : affected){\n"
        "\t\t\tif (com.spd.blast.BlastBombSafety.protects(ch)) continue;\n"
        "\t\t\t//25% bonus damage and 10 turns of stun",
        "flashbang: electric damage and paralysis",
    )
    updated["items/bombs/FlashBangBomb.java"] = flashbang

    holy = updated["items/bombs/HolyBomb.java"]
    holy = replace_once(
        holy,
        "if (ch.properties().contains(Char.Property.UNDEAD) || ch.properties().contains(Char.Property.DEMONIC)){",
        "if ((ch.properties().contains(Char.Property.UNDEAD)\n"
        "\t\t\t\t\t|| ch.properties().contains(Char.Property.DEMONIC))\n"
        "\t\t\t\t\t&& !com.spd.blast.BlastBombSafety.protects(ch)) {",
        "holy bomb: extra unholy damage",
    )
    updated["items/bombs/HolyBomb.java"] = holy

    # All anchors must match before any upstream file changes.
    for path, source in updated.items():
        (root / path).write_text(source, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_bomb_safety.py <spd-java-root>")
    patch(Path(sys.argv[1]))
