#!/usr/bin/env python3
"""Reuse native buff icons for Blast floor pickups."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "\t\tview(item.image(), item.glowing());",
        """\t\tview(item.image(), item.glowing());
\t\tif (item instanceof com.spd.blast.BlastRadiusPickup) {
\t\t\tcom.spd.blast.BlastRadiusPickup.renderBurning(this);
\t\t} else if (item instanceof com.spd.blast.BlastHastePickup) {
\t\t\tcom.spd.blast.BlastHastePickup.renderHaste(this);
\t\t}""",
        "Blast pickup buff icons",
    )
    source = replace_once(
        source,
        "\tpublic void frame( int image ){\n\t\tframe( ItemSpriteSheet.film.get( image ));",
        """\tpublic void frame( int image ){
\t\t// Blast pickup icons use the buff atlas. Recycled sprites must return
\t\t// to the ordinary item atlas and reset their previous tint.
\t\tif (texture != TextureCache.get(Assets.Sprites.ITEMS)) {
\t\t\ttexture(Assets.Sprites.ITEMS);
\t\t\tresetColor();
\t\t}
\t\tframe( ItemSpriteSheet.film.get( image ));""",
        "item sprite atlas reset",
    )
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: Blast pickups render native buff icons")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_item_sprite.py <ItemSprite.java>")
    patch(Path(sys.argv[1]))
