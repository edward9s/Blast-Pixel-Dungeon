#!/usr/bin/env python3
"""Verify native auto-aim's optimized candidate order matches SPD v4.0.2."""
from collections import deque
from pathlib import Path
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "integration/shattered/patches"))
from patch_auto_play_controls import patch_quickslot


ORIGINAL_FALLBACK = (
    "\t\t//Otherwise pick nearby tiles to try and 'angle' the shot, auto-aim basically.\n"
    "\t\tPathFinder.buildDistanceMap( target.pos, BArray.not( new boolean[Dungeon.level.length()], null ), 2 );\n"
    "\t\tfor (int i = 0; i < PathFinder.distance.length; i++) {\n"
    "\t\t\tif (PathFinder.distance[i] < Integer.MAX_VALUE\n"
    "\t\t\t\t\t&& item.targetingPos(Dungeon.hero, i) == target.pos)\n"
    "\t\t\t\treturn i;\n"
    "\t\t}"
)
QUICKSLOT_FIXTURE = (
    "if (!Dungeon.hero.ready) return;\n"
    "if (Dungeon.hero.ready && !GameScene.cancel()) {}\n"
    + ORIGINAL_FALLBACK
    + "\n"
)


def original_candidates(width: int, height: int, target: int) -> list[int]:
    """SPD's 8-neighbour BFS with an all-true mask and a two-step limit."""
    distance = [-1] * (width * height)
    distance[target] = 0
    queue = deque([target])
    while queue:
        cell = queue.popleft()
        if distance[cell] == 2:
            continue
        x, y = cell % width, cell // width
        for yy in range(max(0, y - 1), min(height - 1, y + 1) + 1):
            for xx in range(max(0, x - 1), min(width - 1, x + 1) + 1):
                neighbour = yy * width + xx
                if distance[neighbour] == -1:
                    distance[neighbour] = distance[cell] + 1
                    queue.append(neighbour)
    return [cell for cell, steps in enumerate(distance) if steps != -1]


def optimized_candidates(width: int, height: int, target: int) -> list[int]:
    """Row-major local square used by the patched QuickSlotButton."""
    x, y = target % width, target // width
    return [
        yy * width + xx
        for yy in range(max(0, y - 2), min(height - 1, y + 2) + 1)
        for xx in range(max(0, x - 2), min(width - 1, x + 2) + 1)
    ]


class AutoAimPatchTests(unittest.TestCase):
    def test_two_step_candidates_match_across_map_sizes_and_boundaries(self):
        for width in range(1, 17):
            for height in range(1, 13):
                for target in range(width * height):
                    self.assertEqual(
                        original_candidates(width, height, target),
                        optimized_candidates(width, height, target),
                    )

    def test_quickslot_patch_preserves_targeting_and_removes_allocations(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "QuickSlotButton.java"
            path.write_text(QUICKSLOT_FIXTURE, encoding="utf-8")
            patch_quickslot(path)
            patched = path.read_text(encoding="utf-8")
        self.assertNotIn("BArray.not( new boolean[", patched)
        self.assertNotIn("PathFinder.buildDistanceMap(", patched)
        self.assertIn("item.targetingPos(Dungeon.hero, cell) == target.pos", patched)
        self.assertIn("for (int yy = Math.max(0, y - 2);", patched)
        self.assertIn("for (int xx = Math.max(0, x - 2);", patched)
        self.assertIn("com.spd.blast.BlastSettings.testAutoPlay()", patched)

    def test_quickslot_patch_rejects_upstream_aim_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "QuickSlotButton.java"
            path.write_text(
                QUICKSLOT_FIXTURE.replace("BArray.not(", "BArray.and("),
                encoding="utf-8",
            )
            with self.assertRaises(SystemExit):
                patch_quickslot(path)


if __name__ == "__main__":
    unittest.main()
