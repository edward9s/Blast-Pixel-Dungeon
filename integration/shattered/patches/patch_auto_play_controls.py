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
    source = replace_once(
        source,
        "\t\t\tif (Dungeon.hero.handle( cell )) {\n\t\t\t\tDungeon.hero.next();\n\t\t\t}",
        "\t\t\tif (com.spd.blast.BlastSettings.testAutoPlay()) {\n\t\t\t\tcom.spd.blast.BlastAutoPlay.requestManualCell(cell);\n\t\t\t} else if (Dungeon.hero.handle( cell )) {\n\t\t\t\tDungeon.hero.next();\n\t\t\t}",
        "directional command ownership",
    )
    source = replace_once(
        source,
        "\t\t\tenabled = Dungeon.hero.ready = true;\n\t\t\tDungeon.observe();\n\t\t\tif (moveFromActions(leftStickAction)) {\n\t\t\t\tDungeon.hero.ready = false;\n\t\t\t}",
        "\t\t\tif (com.spd.blast.BlastSettings.testAutoPlay()) {\n\t\t\t\tmoveFromActions(leftStickAction);\n\t\t\t} else {\n\t\t\t\tenabled = Dungeon.hero.ready = true;\n\t\t\t\tDungeon.observe();\n\t\t\t\tif (moveFromActions(leftStickAction)) {\n\t\t\t\t\tDungeon.hero.ready = false;\n\t\t\t\t}\n\t\t\t}",
        "held stick keeps actor readiness",
    )
    source = replace_once(
        source,
        "\t\t\tenabled = Dungeon.hero.ready = true;\n\t\t\tDungeon.observe();\n\t\t\tif (moveFromActions(heldAction1, heldAction2)) {\n\t\t\t\tDungeon.hero.ready = false;\n\t\t\t}",
        "\t\t\tif (com.spd.blast.BlastSettings.testAutoPlay()) {\n\t\t\t\tmoveFromActions(heldAction1, heldAction2);\n\t\t\t} else {\n\t\t\t\tenabled = Dungeon.hero.ready = true;\n\t\t\t\tDungeon.observe();\n\t\t\t\tif (moveFromActions(heldAction1, heldAction2)) {\n\t\t\t\t\tDungeon.hero.ready = false;\n\t\t\t\t}\n\t\t\t}",
        "held keys keep actor readiness",
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
    # Rest consumes time, so queue it on the actor thread instead of the UI.
    for full_rest, count in (("false", 2), ("true", 3)):
        old = f"Dungeon.hero.rest({full_rest});"
        if source.count(old) != count:
            raise SystemExit(f"Toolbar rest: expected {count} {full_rest} calls")
        source = source.replace(
            old,
            f"if (com.spd.blast.BlastSettings.testAutoPlay()) "
            f"com.spd.blast.BlastAutoPlay.requestManualRest({full_rest}); "
            f"else {old}"
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


def patch_resume_indicator(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace_once(
        source,
        "\t\tif (Dungeon.hero.ready) {\n"
        "\t\t\tDungeon.hero.resume();\n"
        "\t\t}",
        "\t\tif (Dungeon.hero.ready) {\n"
        "\t\t\tif (com.spd.blast.BlastSettings.testAutoPlay()) {\n"
        "\t\t\t\tcom.spd.blast.BlastAutoPlay.beginManualAction(Dungeon.hero);\n"
        "\t\t\t}\n"
        "\t\t\tDungeon.hero.resume();\n"
        "\t\t}",
        "resume indicator manual continuation"
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
    # The old all-true distance map contains exactly the 5x5 neighbourhood.
    # Iterating it in map order preserves every targeting result without
    # allocating two map-sized arrays or overwriting PathFinder.distance.
    source = replace_once(
        source,
        "\t\t//Otherwise pick nearby tiles to try and 'angle' the shot, auto-aim basically.\n"
        "\t\tPathFinder.buildDistanceMap( target.pos, BArray.not( new boolean[Dungeon.level.length()], null ), 2 );\n"
        "\t\tfor (int i = 0; i < PathFinder.distance.length; i++) {\n"
        "\t\t\tif (PathFinder.distance[i] < Integer.MAX_VALUE\n"
        "\t\t\t\t\t&& item.targetingPos(Dungeon.hero, i) == target.pos)\n"
        "\t\t\t\treturn i;\n"
        "\t\t}",
        "\t\t//Try the same nearby aim cells in the same map order, without pathfinding.\n"
        "\t\tint width = Dungeon.level.width();\n"
        "\t\tint height = Dungeon.level.height();\n"
        "\t\tint x = target.pos % width;\n"
        "\t\tint y = target.pos / width;\n"
        "\t\tfor (int yy = Math.max(0, y - 2); yy <= Math.min(height - 1, y + 2); yy++) {\n"
        "\t\t\tfor (int xx = Math.max(0, x - 2); xx <= Math.min(width - 1, x + 2); xx++) {\n"
        "\t\t\t\tint cell = yy * width + xx;\n"
        "\t\t\t\tif (item.targetingPos(Dungeon.hero, cell) == target.pos) return cell;\n"
        "\t\t\t}\n"
        "\t\t}",
        "quickslot nearby auto-aim",
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
    patch_resume_indicator(root / "ui/ResumeIndicator.java")
    patch_quickslot(root / "ui/QuickSlotButton.java")
    patch_item(root / "items/Item.java")
