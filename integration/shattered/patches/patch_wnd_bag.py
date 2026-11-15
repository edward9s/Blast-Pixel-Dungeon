#!/usr/bin/env python3
"""Keep the already-open SPD WndBag synchronized with native item display updates."""
from pathlib import Path
import sys


def replace(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace(
        source,
        "import com.watabou.utils.PointF;\n",
        "import com.watabou.utils.PointF;\n\nimport java.util.ArrayList;\n",
        "inventory slot list import",
    )
    source = replace(
        source,
        "\tprivate ItemSelector selector;\n",
        "\tprivate ItemSelector selector;\n"
        "\tprivate final Bag displayedBag;\n"
        "\tprivate final ArrayList<InventorySlot> displaySlots = new ArrayList<>();\n"
        "\tprivate BitmapText goldAmount;\n"
        "\tprivate BitmapText energyAmount;\n",
        "inventory refresh state",
    )
    source = replace(
        source,
        "\t\tsuper();\n\t\t\n\t\tif( INSTANCE != null ){",
        "\t\tsuper();\n\t\tdisplayedBag = bag;\n\t\t\n\t\tif( INSTANCE != null ){",
        "displayed bag assignment",
    )
    source = replace(
        source,
        "\tprotected void placeTitle( Bag bag, int width ){\n",
        "\t/** Update existing slots in place without closing the window or changing tabs. */\n"
        "\tpublic static void refreshOpen() {\n"
        "\t\tif (INSTANCE instanceof WndBag) ((WndBag) INSTANCE).refreshContents();\n"
        "\t}\n\n"
        "\tprivate void refreshContents() {\n"
        "\t\tif (parent == null) return;\n"
        "\t\tfor (InventorySlot slot : displaySlots) {\n"
        "\t\t\tremove(slot);\n"
        "\t\t\tslot.destroy();\n"
        "\t\t}\n"
        "\t\tdisplaySlots.clear();\n"
        "\t\tcount = col = row = 0;\n"
        "\t\tplaceItems(displayedBag);\n"
        "\t\trefreshAmount(goldAmount, Dungeon.gold);\n"
        "\t\trefreshAmount(energyAmount, Dungeon.energy);\n"
        "\t}\n\n"
        "\tprivate static void refreshAmount(BitmapText amount, int value) {\n"
        "\t\tif (amount == null) return;\n"
        "\t\tfloat right = amount.x + amount.width();\n"
        "\t\tamount.text(Integer.toString(value));\n"
        "\t\tamount.measure();\n"
        "\t\tamount.x = right - amount.width();\n"
        "\t\tPixelScene.align(amount);\n"
        "\t}\n\n"
        "\tprotected void placeTitle( Bag bag, int width ){\n",
        "in-place inventory refresh",
    )
    gold = "\t\t\tBitmapText amt = new BitmapText(Integer.toString(Dungeon.gold), PixelScene.pixelFont);\n"
    if source.count(gold) != 2:
        raise SystemExit(f"{path}: expected two gold display anchors")
    source = source.replace(gold, gold + "\t\t\tgoldAmount = amt;\n")
    source = replace(
        source,
        "\t\t\tamt = new BitmapText(Integer.toString(Dungeon.energy), PixelScene.pixelFont);\n",
        "\t\t\tamt = new BitmapText(Integer.toString(Dungeon.energy), PixelScene.pixelFont);\n"
        "\t\t\tenergyAmount = amt;\n",
        "energy display reference",
    )
    source = replace(
        source,
        "\t\tadd(slot);\n",
        "\t\tadd(slot);\n"
        "\t\tdisplaySlots.add(slot);\n",
        "register displayed slot",
    )
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: refresh open inventory on item display changes")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wnd_bag.py <WndBag.java>")
    patch(Path(sys.argv[1]))
