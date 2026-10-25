#!/usr/bin/env python3
from pathlib import Path
import sys


if len(sys.argv) != 2:
    raise SystemExit("usage: patch_title_scene.py <TitleScene.java>")

path = Path(sys.argv[1])
title = path.read_text(encoding="utf-8")

old = 'version = new BitmapText( "v" + Game.version, pixelFont);'
new = 'version = new BitmapText( com.spd.blast.BlastVersion.display(), pixelFont);'

if new in title:
    raise SystemExit("TitleScene Blast version hook is already present")
if title.count(old) != 1:
    raise SystemExit(f"expected exactly one TitleScene version anchor, found {title.count(old)}")

old_icon = "btnAbout.icon(Icons.get(Icons.SHPX));"
new_icon = "btnAbout.icon(new Image(Assets.Interfaces.BLAST_CREDIT));"
if title.count(old_icon) != 1:
    raise SystemExit(f"expected exactly one TitleScene about icon anchor, found {title.count(old_icon)}")

title = title.replace(old, new, 1).replace(old_icon, new_icon, 1)
path.write_text(title, encoding="utf-8")
print(f"patched {path}: Blast version and About button icon")
