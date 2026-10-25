#!/usr/bin/env python3
"""Expand each bomb subtype's real effect radius through the shared Blast hook."""
from pathlib import Path
import re
import sys

def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    pattern = r"(?<!int )\bexplosionRange\(\)"
    updated, count = re.subn(pattern, "blastExplosionRange()", source)
    if count < 1 or source.count("protected int explosionRange()") != 1:
        raise SystemExit(f"{path}: unexpected bomb radius uses: {count}")
    distance_calls = updated.count("PathFinder.buildDistanceMap(")
    if distance_calls < 1:
        raise SystemExit(f"{path}: missing blast distance map")
    updated = updated.replace("PathFinder.buildDistanceMap(",
                              "com.spd.blast.BlastBombWarnings.buildEffectDistanceMap(")
    path.write_text(updated, encoding="utf-8")
    print(f"patched {path}: {count} bomb effect ranges")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_bomb_ranges.py <subclass Bomb.java>")
    patch(Path(sys.argv[1]))
