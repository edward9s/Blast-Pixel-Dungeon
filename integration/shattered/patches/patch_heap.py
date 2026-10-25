#!/usr/bin/env python3
"""Refresh bomb collision on heap mutations, including freezing and explosions."""
from pathlib import Path
import sys


def wrap_method(source: str, signature: str) -> str:
    if source.count(signature) != 1:
        raise SystemExit(f"Heap.{signature}: expected exactly one method")
    opening = source.index("{", source.index(signature) + len(signature))
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return (source[:opening + 1] + "\n\t\ttry {"
                        + source[opening + 1:index]
                        + "\n\t\t} finally {\n"
                        + "\t\t\tcom.spd.blast.BlastBombCollision.refresh(Dungeon.level, pos);\n"
                        + "\t\t}\n\t" + source[index:])
    raise SystemExit(f"Heap.{signature}: unmatched method braces")


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    burn = "for (Item item : items.toArray( new Item[0] )) {\n\t\t\tif (item instanceof Scroll"
    if source.count(burn) != 1:
        raise SystemExit("Heap.burn: expected exactly one pickup seam")
    source = source.replace(burn,
        "for (Item item : items.toArray( new Item[0] )) {\n"
        "\t\t\tif (item instanceof com.spd.blast.BlastPickup) {\n"
        "\t\t\t\titems.remove(item);\n"
        "\t\t\t\tcom.spd.blast.BlastLoot.onDestroyed((com.spd.blast.BlastPickup)item, pos);\n"
        "\t\t\t\tburnt = true;\n"
        "\t\t\t} else if (item instanceof Scroll", 1)

    explode = "//unique items and equipment aren't affect by explosions"
    if source.count(explode) != 1:
        raise SystemExit("Heap.explode: expected exactly one pickup seam")
    source = source.replace(explode,
        "if (item instanceof com.spd.blast.BlastPickup) {\n"
        "\t\t\t\t\titems.remove(item);\n"
        "\t\t\t\t\tcom.spd.blast.BlastLoot.onDestroyed((com.spd.blast.BlastPickup)item, pos);\n"
        "\t\t\t\t\tcontinue;\n"
        "\t\t\t\t}\n\n\t\t\t\t"
        + explode, 1)

    for signature in (
        "public Item pickUp()",
        "public void drop( Item item )",
        "public void replace( Item a, Item b )",
        "public void remove( Item a )",
        "public void burn()",
        "public void explode()",
        "public void freeze()",
        "public void destroy()",
    ):
        source = wrap_method(source, signature)
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: lit bomb collision after heap mutations")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_heap.py <Heap.java>")
    patch(Path(sys.argv[1]))

