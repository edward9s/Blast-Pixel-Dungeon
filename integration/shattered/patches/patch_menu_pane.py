#!/usr/bin/env python3
from pathlib import Path
import sys


if len(sys.argv) != 2:
    raise SystemExit("usage: patch_menu_pane.py <MenuPane.java>")

path = Path(sys.argv[1])
menu = path.read_text(encoding="utf-8")

old = 'version = new BitmapText( "v" + Game.version , PixelScene.pixelFont);'
new = 'version = new BitmapText( com.spd.blast.BlastVersion.display(Game.version), PixelScene.pixelFont);'
if new in menu:
    raise SystemExit("Blast MenuPane version hook is already present")
if menu.count(old) != 1:
    raise SystemExit(f"expected exactly one MenuPane version anchor, found {menu.count(old)}")

layout_anchor = "\t@Override\n\tprotected void layout() {\n\t\tsuper.layout();\n\n\t\tbg.x = x;"
if menu.count(layout_anchor) != 1:
    raise SystemExit("expected exactly one MenuPane layout anchor")
overflow_anchor = "\t\tif (overFlow >= 1){\n"
if menu.count(overflow_anchor) != 1:
    raise SystemExit("expected exactly one MenuPane overflow anchor")

update_hook = """
\t@Override
\tpublic void update() {
\t\tsuper.update();
\t\tString display = com.spd.blast.BlastVersion.display(Game.version);
\t\tif (!display.equals(version.text())) {
\t\t\tversion.text(display);
\t\t\tlayout();
\t\t}
\t}

"""
menu = menu.replace(old, "com.spd.blast.BlastUpdates.checkForUpdate();\n\t\t" + new, 1)
menu = menu.replace(layout_anchor, update_hook + layout_anchor, 1)
menu = menu.replace(overflow_anchor, overflow_anchor + "\t\t\tversionOverflowBG.visible = true;\n", 1)
path.write_text(menu, encoding="utf-8")
print(f"patched {path}: Blast update check and live in-game version label")
