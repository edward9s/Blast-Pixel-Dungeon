#!/usr/bin/env python3
"""Allow Ethereal Chains to start from the hero's blocked bomb cell."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    before = (
        "\t\t\t\tPathFinder.buildDistanceMap(target, BArray.or(Dungeon.level.passable, Dungeon.level.avoid, null));\n"
        "\t\t\t\tif (!(Dungeon.level instanceof MiningLevel) && PathFinder.distance[curUser.pos] == Integer.MAX_VALUE){"
    )
    after = (
        "\t\t\t\t// The hero can leave a cell blocked by a lit bomb.\n"
        "\t\t\t\tif (!(Dungeon.level instanceof MiningLevel)\n"
        "\t\t\t\t\t\t&& curUser.pos != target\n"
        "\t\t\t\t\t\t&& PathFinder.find(curUser.pos, target,\n"
        "\t\t\t\t\t\t\t\tBArray.or(Dungeon.level.passable, Dungeon.level.avoid, null)) == null){"
    )
    if source.count(before) != 1:
        raise SystemExit(f"{path}: expected exactly one Ethereal Chains reachability check")
    path.write_text(source.replace(before, after, 1), encoding="utf-8")
    print(f"patched {path}: Ethereal Chains reachability from blocked hero cell")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_ethereal_chains.py <EtherealChains.java>")
    patch(Path(sys.argv[1]))
