#!/usr/bin/env python3
"""Smoke-test the two SPD v4.0.2 version-label integration patches."""
from pathlib import Path
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
TITLE = """
btnAbout.icon(Icons.get(Icons.SHPX));
version = new BitmapText( "v" + Game.version, pixelFont);
\tprivate float uiAlpha;
"""
MENU = """
\t\tversion = new BitmapText( "v" + Game.version , PixelScene.pixelFont);
\t@Override
\tprotected void layout() {
\t\tsuper.layout();

\t\tbg.x = x;
\t\tif (overFlow >= 1){
"""


def patch(script: str, filename: str, original: str) -> str:
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp) / filename
        path.write_text(original, encoding="utf-8")
        subprocess.run(
            [sys.executable, str(ROOT / "integration/shattered/patches" / script), str(path)],
            check=True,
            capture_output=True,
            text=True,
        )
        return path.read_text(encoding="utf-8")


def main() -> None:
    title = patch("patch_title_scene.py", "TitleScene.java", TITLE)
    menu = patch("patch_menu_pane.py", "MenuPane.java", MENU)
    for source in (title, menu):
        assert "com.spd.blast.BlastUpdates.checkForUpdate();" in source
        assert "com.spd.blast.BlastVersion.display()" in source
        assert "public void update()" in source
        assert "version.text(display);" in source
    assert "float right = version.x + version.width();" in title
    assert "version.x = right - version.width();" in title
    assert "versionOverflowBG.visible = true;" in menu
    assert "layout();" in menu
    print("Blast update UI patch checks passed")


if __name__ == "__main__":
    main()
