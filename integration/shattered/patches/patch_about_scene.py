#!/usr/bin/env python3
"""Add Blast credits above the original SPD credits without replacing any attribution."""
from pathlib import Path
import sys


def replace_once(text: str, old: str, new: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one AboutScene anchor {old!r}, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_about_scene.py <AboutScene.java>")

    path = Path(sys.argv[1])
    source = path.read_text(encoding="utf-8")
    if '"Blast Pixel Dungeon"' in source:
        raise SystemExit("AboutScene already contains Blast credits")

    source = replace_once(
        source,
        '\t\t//*** Shattered Pixel Dungeon Credits ***',
        '\t\t//*** Blast Pixel Dungeon Credits ***\n'
        '\t\tCreditsBlock blast = new CreditsBlock(true, Window.SHPX_COLOR,\n'
        '\t\t\t\t"Blast Pixel Dungeon",\n'
        '\t\t\t\tnew Image(com.shatteredpixel.shatteredpixeldungeon.Assets.Interfaces.BLAST_CREDIT),\n'
        '\t\t\t\t"Developed by: _edward9s_\\nBased on Shattered Pixel Dungeon",\n'
        '\t\t\t\t"github.com/edward9s/Blast-Pixel-Dungeon",\n'
        '\t\t\t\t"https://github.com/edward9s/Blast-Pixel-Dungeon");\n'
        '\t\tif (landscape()){\n'
        '\t\t\tblast.setRect((w - fullWidth)/2f - 6, insets.top + 26, 120, 0);\n'
        '\t\t} else {\n'
        '\t\t\tblast.setRect((w - fullWidth)/2f, insets.top + 6, 120, 0);\n'
        '\t\t}\n'
        '\t\tcontent.add(blast);\n\n'
        '\t\t//*** Shattered Pixel Dungeon Credits ***',
    )

    source = replace_once(
        source,
        'shpx.setRect((w - fullWidth)/2f - 6, insets.top + 26, 120, 0);',
        'shpx.setRect((w - fullWidth)/2f - 6, blast.bottom() + 12, 120, 0);',
    )
    source = replace_once(
        source,
        'shpx.setRect((w - fullWidth)/2f, insets.top + 6, 120, 0);',
        'shpx.setRect((w - fullWidth)/2f, blast.bottom() + 12, 120, 0);',
    )
    source = replace_once(
        source,
        'content.add(shpx);',
        'content.add(shpx);\n\t\taddLine(shpx.top() - 5, content);',
    )
    source = replace_once(
        source,
        'aleks.setPos(shpx.right(), insets.top+6);',
        'aleks.setPos(shpx.right(), shpx.top());',
    )

    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: added Blast credits and preserved original credits")


if __name__ == "__main__":
    main()
