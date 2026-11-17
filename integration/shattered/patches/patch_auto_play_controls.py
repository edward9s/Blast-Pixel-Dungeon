#!/usr/bin/env python3
"""Allow player inputs while test auto-play is active; retain vanilla behavior otherwise."""
from pathlib import Path
import sys


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch_cell_selector(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "if (enabled && Dungeon.hero.ready && !GameScene.interfaceBlockingHero()",
        "if ((enabled || com.spd.blast.BlastSettings.testAutoPlay())"
        " && (Dungeon.hero.ready || com.spd.blast.BlastSettings.testAutoPlay())"
        " && !GameScene.interfaceBlockingHero()",
        "map targeting"
    )
    source = replace_once(
        source,
        "} else if (Dungeon.hero.ready){",
        "} else if (Dungeon.hero.ready || com.spd.blast.BlastSettings.testAutoPlay()){",
        "held movement"
    )
    source = replace_once(
        source,
        "if (Dungeon.hero == null || !Dungeon.hero.ready){",
        "if (Dungeon.hero == null || (!Dungeon.hero.ready"
        " && !com.spd.blast.BlastSettings.testAutoPlay())){",
        "directional movement"
    )
    path.write_text(source, encoding="utf-8")


def patch_toolbar(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    ready = "Dungeon.hero.ready"
    count = source.count(ready)
    if count != 11:
        raise SystemExit(f"Toolbar readiness: expected 11 occurrences, found {count}")
    source = source.replace(
        ready, "(Dungeon.hero.ready || com.spd.blast.BlastSettings.testAutoPlay())"
    )
    source = replace_once(
        source,
        "\t\t\t\t\t\tinformer.onSelect(null);\n"
        "\t\t\t\t\t\tDungeon.hero.search(true);",
        "\t\t\t\t\t\tif (com.spd.blast.BlastSettings.testAutoPlay()) {\n"
        "\t\t\t\t\t\t\tGameScene.cancelCellSelector();\n"
        "\t\t\t\t\t\t} else {\n"
        "\t\t\t\t\t\t\tinformer.onSelect(null);\n"
        "\t\t\t\t\t\t}\n"
        "\t\t\t\t\t\tDungeon.hero.search(true);",
        "examine selection cleanup"
    )
    path.write_text(source, encoding="utf-8")


def patch_quickslot(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source, "!Dungeon.hero.ready",
        "!(Dungeon.hero.ready || com.spd.blast.BlastSettings.testAutoPlay())",
        "quickslot activation"
    )
    source = replace_once(
        source,
        "if (Dungeon.hero.ready && !GameScene.cancel())",
        "if ((Dungeon.hero.ready || com.spd.blast.BlastSettings.testAutoPlay())"
        " && !GameScene.cancel())",
        "quickslot selection"
    )
    path.write_text(source, encoding="utf-8")


def patch_item(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "\tpublic void execute( Hero hero, String action ) {\n\n\t\tGameScene.cancel();",
        "\tpublic void execute( Hero hero, String action ) {\n\n"
        "\t\tcom.spd.blast.BlastAutoPlay.beginItemAction(hero);\n"
        "\t\tGameScene.cancel();",
        "manual item use"
    )
    path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_auto_play_controls.py <core Java root>")
    root = Path(sys.argv[1])
    patch_cell_selector(root / "scenes/CellSelector.java")
    patch_toolbar(root / "ui/Toolbar.java")
    patch_quickslot(root / "ui/QuickSlotButton.java")
    patch_item(root / "items/Item.java")
