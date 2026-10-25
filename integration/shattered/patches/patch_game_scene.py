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

    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: remote bomb and Bomb Maniac edge tags")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_game_scene.py <GameScene.java>")
    patch(Path(sys.argv[1]))
