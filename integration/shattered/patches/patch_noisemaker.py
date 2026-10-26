#!/usr/bin/env python3
"""Share the native noisemaker alarm with remote fuses; retain timed-fuse behavior."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    alarm = (
        "\t\t\t\t\t\t\t\tCellEmitter.center( heap.pos ).start( Speck.factory( Speck.SCREAM ), 0.3f, 3 );\n"
        "\t\t\t\t\t\t\t\tSample.INSTANCE.play( Assets.Sounds.ALERT );\n\n"
        "\t\t\t\t\t\t\t\tfor (Mob mob : Dungeon.level.mobs.toArray( new Mob[0] )) {\n"
        "\t\t\t\t\t\t\t\t\tmob.beckon( heap.pos );\n"
        "\t\t\t\t\t\t\t\t}\n"
    )
    anchor = "\t\tprivate static final String LEFT = \"left\";"
    if source.count(alarm) != 1 or source.count(anchor) != 1:
        raise SystemExit(f"{path}: expected one noisemaker alarm and bundle anchor")
    source = source.replace(alarm, "\t\t\t\t\t\t\t\tblastAlert(heap.pos);\n", 1)
    method = (
        "\t\t/** Shared alarm effect; fuse implementations own their timing. */\n"
        "\t\tpublic static void blastAlert(int cell) {\n"
        "\t\t\tCellEmitter.center(cell).start(Speck.factory(Speck.SCREAM), 0.3f, 3);\n"
        "\t\t\tSample.INSTANCE.play(Assets.Sounds.ALERT);\n"
        "\t\t\tfor (Mob mob : Dungeon.level.mobs.toArray(new Mob[0])) {\n"
        "\t\t\t\tmob.beckon(cell);\n"
        "\t\t\t}\n"
        "\t\t}\n\n"
    )
    source = source.replace(anchor, method + anchor, 1)
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: shared native noisemaker alarm")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_noisemaker.py <Noisemaker.java>")
    patch(Path(sys.argv[1]))
