#!/usr/bin/env python3
"""Patch only vanilla natural respawn pacing and cap; fail on upstream drift."""
from pathlib import Path
import sys

def change(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    replacements = [
        ("Dungeon.level.mobLimit()", "com.spd.blast.BlastSpawn.mobLimit(Dungeon.level.mobLimit())", 1),
        ("Dungeon.level.respawnCooldown()", "com.spd.blast.BlastSpawn.cooldown(Dungeon.level.respawnCooldown())", 3),
    ]
    for old, new, expected in replacements:
        if source.count(old) != expected:
            raise SystemExit(f"{path}: expected {expected} occurrences of {old!r}, got {source.count(old)}")
        source = source.replace(old, new)
    # Keep SPD\'s native 2% rare alternate chance; fail if upstream changes it.
    rare_chance = "1 / 50f * RatSkull.exoticChanceMultiplier()"
    if source.count(rare_chance) != 1:
        raise SystemExit(f"{path}: expected one rare alternate chance")
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: Blast respawn pacing/cap; verified native rare variant chance")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_mob_spawner.py <MobSpawner.java>")
    change(Path(sys.argv[1]))
