#!/usr/bin/env python3
"""Register Blast's two native tags and keep their edge ordering deterministic."""
from pathlib import Path
import sys


def replace(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    source = replace(source,
        "\tprivate ResumeIndicator resume;\n",
        """\tprivate ResumeIndicator resume;
\tprivate com.spd.blast.BlastRemoteBombTag blastRemote;
\tprivate com.spd.blast.BlastBombManiacTag blastManiac;
""", "blast tag fields")

    # Activate permanent bomb buffs at the same event that unlocks the tutorial UI.
    source = replace(source,
        "\t\t\tSPDSettings.intro(false);",
        "\t\t\tSPDSettings.intro(false);\n"
        "\t\t\tcom.spd.blast.BlastRemoteBomb.ensureHero();\n"
        "\t\t\tcom.spd.blast.BlastBombManiac.ensureHero();",
        "bomb buffs after introduction")

    source = replace(source,
        "\t\tDungeon.level.playLevelMusic();",
        """\t\tcom.spd.blast.BlastRemoteBomb.ensureHero();
\t\tcom.spd.blast.BlastBombManiac.ensureHero();
		com.spd.blast.BlastTestBuffs.syncHero();
\t\tDungeon.level.playLevelMusic();""", "buff maintenance")

    source = replace(source,
        "\t\tattack.camera = uiCamera;\n\t\tadd( attack );",
        """\t\tattack.camera = uiCamera;
\t\tadd( attack );

\t\tblastRemote = new com.spd.blast.BlastRemoteBombTag();
\t\tblastRemote.camera = uiCamera;
\t\tblastRemote.visible = blastRemote.active =
\t\t\t\tDungeon.hero.buff(com.spd.blast.BlastRemoteBomb.class) != null;
\t\tadd(blastRemote);

\t\tblastManiac = new com.spd.blast.BlastBombManiacTag();
\t\tblastManiac.camera = uiCamera;
\t\tblastManiac.visible = blastManiac.active =
\t\t\t\tDungeon.hero.buff(com.spd.blast.BlastBombManiac.class) != null;
\t\tadd(blastManiac);""", "blast tag construction")

    source = replace(source,
        "\t\ttargetedCells = new Group();\n\t\tadd(targetedCells);",
        "\t\ttargetedCells = new Group();\n\t\tadd(targetedCells);\n"
        "\t\tadd(new com.spd.blast.BlastBombVisuals());\n"
        "\t\tadd(new com.spd.blast.BlastBombWarnings());\n"
        "\t\tadd(new com.spd.blast.BlastOutOfSightItems());",
        "persistent blast danger and out-of-sight items")

    source = replace(source,
        "\t\tsuper.update();\n\n\t\tif (notifyDelay > 0)",
        """\t\tsuper.update();
\t\tcom.spd.blast.BlastAutoPlay.resumeIfInputFinished();

\t\tblastRemote.visible = blastRemote.active =
\t\t\t\tDungeon.hero.buff(com.spd.blast.BlastRemoteBomb.class) != null;
\t\tblastManiac.visible = blastManiac.active =
\t\t\t\tDungeon.hero.buff(com.spd.blast.BlastBombManiac.class) != null;

\t\tif (notifyDelay > 0)""", "blast tag visibility")

    source = replace(source,
        "\t\t\ttagResume = resume.visible;\n\n\t\t\tlayoutTags();",
        """\t\t\ttagResume = resume.visible;
\t\t\ttagBlastRemote = blastRemote.visible;
\t\t\ttagBlastManiac = blastManiac.visible;

\t\t\tlayoutTags();""", "forced tag layout")

    source = replace(source,
        "\t\t\t\ttagResume != resume.visible) {",
        """\t\t\t\ttagResume != resume.visible ||
\t\t\t\ttagBlastRemote != blastRemote.visible ||
\t\t\t\ttagBlastManiac != blastManiac.visible) {""", "tag changes")

    source = replace(source,
        "\t\t\t\t\t\t\t\t\t(resume.visible && !tagResume);",
        """\t\t\t\t\t\t\t\t\t(resume.visible && !tagResume) ||
\t\t\t\t\t\t\t\t\t(blastRemote.visible && !tagBlastRemote) ||
\t\t\t\t\t\t\t\t\t(blastManiac.visible && !tagBlastManiac);""", "tag appearance")

    source = replace(source,
        "\t\t\ttagResume = resume.visible;\n\n\t\t\t//if a new tag appears",
        """\t\t\ttagResume = resume.visible;
\t\t\ttagBlastRemote = blastRemote.visible;
\t\t\ttagBlastManiac = blastManiac.visible;

\t\t\t//if a new tag appears""", "tag state")

    source = replace(source,
        "\tprivate boolean tagResume    = false;",
        """\tprivate boolean tagResume    = false;
\tprivate boolean tagBlastRemote = false;
\tprivate boolean tagBlastManiac = false;""", "tag flags")

    source = replace(source,
        """\t\tif (scene.tagResume) {
\t\t\tscene.resume.setRect( tagLeft, pos - Tag.SIZE, tagWidth, Tag.SIZE );
\t\t\tscene.resume.flip(tagsOnLeft);
\t\t}""",
        """\t\tif (scene.tagResume) {
\t\t\tscene.resume.setRect( tagLeft, pos - Tag.SIZE, tagWidth, Tag.SIZE );
\t\t\tscene.resume.flip(tagsOnLeft);
\t\t\tpos = scene.resume.top();
\t\t}

\t\tif (scene.tagBlastRemote) {
\t\t\tscene.blastRemote.setRect(tagLeft, pos - Tag.SIZE, tagWidth, Tag.SIZE);
\t\t\tscene.blastRemote.flip(tagsOnLeft);
\t\t\tpos = scene.blastRemote.top();
\t\t}

\t\tif (scene.tagBlastManiac) {
\t\t\tscene.blastManiac.setRect(tagLeft, pos - Tag.SIZE, tagWidth, Tag.SIZE);
\t\t\tscene.blastManiac.flip(tagsOnLeft);
\t\t}""", "blast tag placement")

    source = replace(source,
        "\t\tscene = null;\n\t\tBadges.saveGlobal();",
        "\t\tcom.spd.blast.BlastBombVisuals.reset();\n"
        "\t\tcom.spd.blast.BlastBombWarnings.resetSnapshot();\n"
        "\t\tcom.spd.blast.BlastOutOfSightItems.resetSnapshot();\n"
        "\t\tscene = null;\n\t\tBadges.saveGlobal();",
        "warning cleanup on scene close")

    # Opening an informational window does not cancel the auto-play action.
    source = replace(source,
        "\t\t\tcancel();\n\n\t\t\t//If a window is already present",
        "\t\t\tif (!com.spd.blast.BlastSettings.testAutoPlay()) cancel();\n\n"
        "\t\t\t//If a window is already present",
        "windows keep auto-play running")

    source = replace(source,
        "\tpublic static boolean cancel() {\n\t\tcellSelector.resetKeyHold();",
        "\tpublic static boolean cancel() {\n\t\tcellSelector.resetKeyHold();\n"
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()) return cancelCellSelector();",
        "autoplay ignores queued move during cancellation")

    source = replace(source,
        "\tpublic static boolean interfaceBlockingHero(){",
        "\tpublic static boolean manualInputPending(){\n"
        "\t\tif (scene == null) return false;\n"
        "\t\tif (cellSelector.listener != null && cellSelector.listener != defaultCellListener) return true;\n"
        "\t\tif (scene.inventory != null && scene.inventory.isSelecting()) return true;\n"
        "\t\treturn WndBag.INSTANCE instanceof WndBag\n"
        "\t\t\t\t&& ((WndBag) WndBag.INSTANCE).getSelector() != null;\n"
        "\t}\n\n"
        "\tpublic static boolean interfaceBlockingHero(){",
        "manual targeting while auto-play")

    source = replace(source,
        "\t\tcellSelector.enable(Dungeon.hero.ready);",
        "\t\tcellSelector.enable(Dungeon.hero.ready || com.spd.blast.BlastSettings.testAutoPlay());",
        "manual map selection enabled")

    source = replace(source,
        "\t\t\tInventoryPane.refresh();",
        "\t\t\tInventoryPane.refresh();\n"
        "\t\t\tWndBag.refreshOpen();\n"
        "\t\t\tWndQuickBag.refreshOpen();",
        "refresh already-open bag window")

    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: remote bomb and Bomb Maniac edge tags")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_game_scene.py <GameScene.java>")
    patch(Path(sys.argv[1]))
