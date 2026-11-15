#!/usr/bin/env python3
"""Smoke-test the native WndBag refresh integration and its fail-fast anchors."""
from pathlib import Path
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_wnd_bag.py"
GAMESCENE_PATCH = ROOT / "integration/shattered/patches/patch_game_scene.py"
QUICK_BAG_PATCH = ROOT / "integration/shattered/patches/patch_wnd_quick_bag.py"
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

QUICK_BAG = (
    "\tprivate static Item bag;\n"
    "\t\tWndQuickBag.bag = bag;\n"
    "\t\tfloat width = 0, height = 0;\n"
    "\t\t\tadd(slot);\n"
    "\t\tadd( txtTitle );\n"
    "\t\tresize((int)width, (int)height);\n"
    "\t\toffset(0, (int) (bottom/2 - 30 - height/2));\n"
)


def run_quick_patch(path: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(QUICK_BAG_PATCH), str(path)],
        capture_output=True,
        text=True,
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

    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "WndQuickBag.java"
        path.write_text(QUICK_BAG, encoding="utf-8")
        result = run_quick_patch(path)
        assert result.returncode == 0, result.stderr
        patched = path.read_text(encoding="utf-8")
        assert "public static void refreshOpen()" in patched
        assert "WndBag.INSTANCE instanceof WndQuickBag" in patched
        assert "if (window.parent != null) window.refreshContents();" in patched
        assert "Bag bag = displayedBag;" in patched
        assert "displaySlots.add(slot);" in patched
        assert "displaySlots.clear();" in patched
        assert "displayedTitle.destroy();" in patched
        assert "displayedTitle = txtTitle;" in patched
        assert "resize((int)width, (int)height);" in patched
        assert "offset(0, (int) (bottom/2 - 30 - height/2));" in patched
        assert run_quick_patch(path).returncode != 0
        assert path.read_text(encoding="utf-8") == patched

        changed = QUICK_BAG.replace("\t\t\tadd(slot);", "\t\t\tadd(other);")
        path.write_text(changed, encoding="utf-8")
        assert run_quick_patch(path).returncode != 0
        assert path.read_text(encoding="utf-8") == changed

    game_scene_patch = GAMESCENE_PATCH.read_text(encoding="utf-8")
    apply = APPLY.read_text(encoding="utf-8")
    assert "WndBag.refreshOpen();" in game_scene_patch
    assert "WndQuickBag.refreshOpen();" in game_scene_patch
    assert "import com.shatteredpixel.shatteredpixeldungeon.windows.WndQuickBag;" in game_scene_patch
    assert 'patch_wnd_bag.py" "$root/windows/WndBag.java"' in apply
    assert 'patch_wnd_quick_bag.py" "$root/windows/WndQuickBag.java"' in apply
    print("Open bag and quick-use bag refresh integration checks passed")


if __name__ == "__main__":
    main()
