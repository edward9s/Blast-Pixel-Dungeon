#!/usr/bin/env python3
"""Roll Blast loot only after a mob's native drops, including subclass overrides."""
from pathlib import Path
import sys

def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    old = "\t\t\trollToDropLoot();"
    if source.count(old) != 1:
        raise SystemExit(f"{path}: expected one Mob.die loot roll, got {source.count(old)}")
    source = source.replace(old,
        "\t\t\tint blastPriorLoot = com.spd.blast.BlastLoot.groundQuantity(pos);\n"
        "\t\t\trollToDropLoot();\n"
        "\t\t\tcom.spd.blast.BlastLoot.dropIfNoOtherLoot(this, blastPriorLoot);\n"
        "\t\t\tcom.spd.blast.BlastDeathBomb.onMobDeath(this, cause, blastPriorLoot);", 1)
    anchor = "\t\tAiState curState = state;"
    if source.count(anchor) != 1:
        raise SystemExit(f"{path}: expected one Mob.act AI action seam")
    source = source.replace(anchor,
        "\t\tif (com.spd.blast.BlastBombKick.tryKick(this)) {\n"
        "\t\t\tspend(TICK);\n"
        "\t\t\treturn true;\n"
        "\t\t}\n"
        "\t\tint blastDodgeStep = com.spd.blast.BlastBombDodge.dodgeStep(this);\n"
        "\t\tif (blastDodgeStep != -1) {\n"
        "\t\t\tint oldPos = pos;\n"
        "\t\t\tmove(blastDodgeStep);\n"
        "\t\t\tspend(1 / speed());\n"
        "\t\t\treturn moveSprite(oldPos, pos);\n"
        "\t\t}\n\n" + anchor, 1)
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: conditional Blast mob loot")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_mob.py <Mob.java>")
    patch(Path(sys.argv[1]))
