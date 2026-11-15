#!/usr/bin/env python3
"""Smoke-test the native WndBag refresh integration and its fail-fast anchors."""
from pathlib import Path
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_wnd_bag.py"
GAMESCENE_PATCH = ROOT / "integration/shattered/patches/patch_game_scene.py"
APPLY = ROOT / "integration/shattered/apply.sh"

WND_BAG = (
    "import com.watabou.utils.PointF;\n"
    "\tprivate ItemSelector selector;\n"
    "\t\tsuper();\n\t\t\n\t\tif( INSTANCE != null ){\n"
    "\tprotected void placeTitle( Bag bag, int width ){\n"
    "\t\t\tBitmapText amt = new BitmapText(Integer.toString(Dungeon.gold), PixelScene.pixelFont);\n"
    "\t\t\tBitmapText amt = new BitmapText(Integer.toString(Dungeon.gold), PixelScene.pixelFont);\n"
    "\t\t\tamt = new BitmapText(Integer.toString(Dungeon.energy), PixelScene.pixelFont);\n"
    "\t\tadd(slot);\n"
)


def run_patch(path: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(PATCH), str(path)],
        capture_output=True,
        text=True,
    )


def main() -> None:
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "WndBag.java"
        path.write_text(WND_BAG, encoding="utf-8")

        result = run_patch(path)
        assert result.returncode == 0, result.stderr
        patched = path.read_text(encoding="utf-8")
        assert "public static void refreshOpen()" in patched
        assert "if (parent == null) return;" in patched
        assert "displaySlots.clear();" in patched
        assert "count = col = row = 0;" in patched
        assert "placeItems(displayedBag);" in patched
        assert "displaySlots.add(slot);" in patched
        assert "refreshAmount(goldAmount, Dungeon.gold);" in patched
        assert "refreshAmount(energyAmount, Dungeon.energy);" in patched

        assert run_patch(path).returncode != 0
        assert path.read_text(encoding="utf-8") == patched

        missing = WND_BAG.replace("\t\tadd(slot);", "\t\tadd(other);")
        path.write_text(missing, encoding="utf-8")
        assert run_patch(path).returncode != 0
        assert path.read_text(encoding="utf-8") == missing

    game_scene_patch = GAMESCENE_PATCH.read_text(encoding="utf-8")
    apply = APPLY.read_text(encoding="utf-8")
    assert "WndBag.refreshOpen();" in game_scene_patch
    assert 'patch_wnd_bag.py" "$root/windows/WndBag.java"' in apply
    print("Open WndBag refresh integration checks passed")


if __name__ == "__main__":
    main()
