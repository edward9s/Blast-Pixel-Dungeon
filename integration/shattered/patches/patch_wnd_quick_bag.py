#!/usr/bin/env python3
"""Refresh the already-open SPD quick-use bag window on inventory changes."""
from pathlib import Path
import sys


def replace(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    for anchor in (
        "\t\tresize((int)width, (int)height);\n",
        "\t\toffset(0, (int) (bottom/2 - 30 - height/2));\n",
    ):
        if source.count(anchor) != 1:
            raise SystemExit(f"{path}: quick-use popup layout anchor changed")

    source = replace(
        source,
        "\tprivate static Item bag;\n",
        "\tprivate static Item bag;\n"
        "\tprivate final Bag displayedBag;\n"
        "\tprivate final ArrayList<InventorySlot> displaySlots = new ArrayList<>();\n"
        "\tprivate RenderedTextBlock displayedTitle;\n",
        "quick bag refresh fields",
    )
    source = replace(
        source,
        "\t\tWndQuickBag.bag = bag;\n",
        "\t\tWndQuickBag.bag = bag;\n"
        "\t\tdisplayedBag = bag;\n",
        "quick bag selection",
    )
    source = replace(
        source,
        "\t\tfloat width = 0, height = 0;\n",
        "\t\trefreshContents();\n"
        "\t}\n\n"
        "\t/** Synchronize the visible quick-use items without closing the popup. */\n"
        "\tpublic static void refreshOpen() {\n"
        "\t\tif (WndBag.INSTANCE instanceof WndQuickBag) {\n"
        "\t\t\tWndQuickBag window = (WndQuickBag) WndBag.INSTANCE;\n"
        "\t\t\tif (window.parent != null) window.refreshContents();\n"
        "\t\t}\n"
        "\t}\n\n"
        "\tprivate void refreshContents() {\n"
        "\t\tfor (InventorySlot slot : displaySlots) {\n"
        "\t\t\tremove(slot);\n"
        "\t\t\tslot.destroy();\n"
        "\t\t}\n"
        "\t\tdisplaySlots.clear();\n"
        "\t\tif (displayedTitle != null) {\n"
        "\t\t\tremove(displayedTitle);\n"
        "\t\t\tdisplayedTitle.destroy();\n"
        "\t\t}\n"
        "\t\tBag bag = displayedBag;\n"
        "\t\tfloat width = 0, height = 0;\n",
        "quick bag in-place refresh",
    )
    source = replace(
        source,
        "\t\t\tadd(slot);\n",
        "\t\t\tadd(slot);\n"
        "\t\t\tdisplaySlots.add(slot);\n",
        "quick bag slot tracking",
    )
    source = replace(
        source,
        "\t\tadd( txtTitle );\n",
        "\t\tadd( txtTitle );\n"
        "\t\tdisplayedTitle = txtTitle;\n",
        "quick bag title tracking",
    )
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: refresh open quick-use inventory")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wnd_quick_bag.py <WndQuickBag.java>")
    patch(Path(sys.argv[1]))
