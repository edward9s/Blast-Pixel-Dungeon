#!/usr/bin/env python3
"""Apply the same shortened cooldown to the first natural respawn."""
from pathlib import Path
import sys

def change(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    replacements = [
        (
            "Actor.addDelayed(respawner, respawnCooldown());",
            "Actor.addDelayed(respawner, com.spd.blast.BlastSpawn.cooldown(respawnCooldown()));",
        ),
        (
            "if (respawner.cooldown() > respawnCooldown()){",
            "if (respawner.cooldown() > com.spd.blast.BlastSpawn.cooldown(respawnCooldown())){",
        ),
    ]
    replacements.extend([
        (
            "\t\t}\n\n\t}\n\n\t//updates open space both on the cell itself and adjacent cells",
            "\t\t}\n\t\tcom.spd.blast.BlastBombCollision.blockAll(this);\n\n\t}\n\n"
            "\t//updates open space both on the cell itself and adjacent cells",
        ),
        (
            "\t\tupdateOpenSpace(cell);\n\t}\n\t\n\tpublic Heap drop(",
            "\t\tupdateOpenSpace(cell);\n"
            "\t\tcom.spd.blast.BlastBombCollision.block(this, cell);\n\t}\n\t\n\tpublic Heap drop(",
        ),
        (
            "\t\treturn heap;\n\t}\n\t\n\tpublic Plant plant(",
            "\t\tcom.spd.blast.BlastBombCollision.refresh(this, cell);\n"
            "\t\treturn heap;\n\t}\n\t\n\tpublic Plant plant(",
        ),
    ])
    # Prevent charged Bomb Maniac bombs from entering the next floor's loot.
    replacements.append((
        "\t\t\t\tDungeon.dropToChasm( item );",
        "\t\t\t\tif (!com.spd.blast.BlastBombManiac.discardInChasm(item)) {\n"
        "\t\t\t\t\tDungeon.dropToChasm( item );\n"
        "\t\t\t\t}",
    ))
    # Seeded destructible walls are owned by each Level and saved with it.
    replacements.extend([
        (
            "\tpublic void create() {",
            "\tpublic int[] blastWallStrength;\n\tpublic boolean[] blastRubble;\n\n\tpublic void create() {",
        ),
        (
            "\t\tcreateMobs();\n\t\tcreateItems();",
            "\t\tcreateMobs();\n\t\tcreateItems();\n"
            "\t\tcom.spd.blast.BlastWalls.initialize(this);",
        ),
        (
            "\t\tbuildFlagMaps();\n\t\tcleanWalls();\n\n\t}\n\t\n\t@Override\n\tpublic void storeInBundle",
            "\t\tcom.spd.blast.BlastWalls.restore(this, bundle);\n"
            "\t\tbuildFlagMaps();\n\t\tcleanWalls();\n\n\t}\n\t\n\t@Override\n\tpublic void storeInBundle",
        ),
        (
            "\t\tbundle.put( MAP, map );",
            "\t\tbundle.put( MAP, map );\n"
            "\t\tcom.spd.blast.BlastWalls.store(this, bundle);",
        ),
        (
            "\t\tcom.spd.blast.BlastBombCollision.block(this, cell);\n\t}\n\t\n\tpublic Heap drop(",
            "\t\tcom.spd.blast.BlastBombCollision.block(this, cell);\n"
            "\t\tcom.spd.blast.BlastWalls.terrainChanged(this, cell);\n\t}\n\t\n\tpublic Heap drop(",
        ),
    ])
    for old, new in replacements:
        if source.count(old) != 1:
            raise SystemExit(f"{path}: expected exactly one {old!r}, got {source.count(old)}")
        source = source.replace(old, new, 1)
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: Blast respawn and lit bomb collision")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_level.py <Level.java>")
    change(Path(sys.argv[1]))
