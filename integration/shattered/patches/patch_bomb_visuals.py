#!/usr/bin/env python3
"""Move bomb explosion audiovisual effects to the render loop, without delaying damage."""
from pathlib import Path
import sys


def replace(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch_bomb(source: str) -> str:
    source = replace(source,
        "\t\tSample.INSTANCE.play( Assets.Sounds.BLAST );\n", "",
        "base blast sound")
    source = replace(source,
        "\t\t\tif (Dungeon.level.heroFOV[cell]) {\n"
        "\t\t\t\tCellEmitter.center(cell).burst(BlastParticle.FACTORY, 30);\n"
        "\t\t\t}\n", "", "base blast particles")
    source = replace(source,
        "\t\t\tfor (int i : affectedCells){\n"
        "\t\t\t\tif (Dungeon.level.heroFOV[i]) {\n"
        "\t\t\t\t\tCellEmitter.get(i).burst(SmokeParticle.FACTORY, 4);\n"
        "\t\t\t\t}\n",
        "\t\t\tcom.spd.blast.BlastBombVisuals.blast(this, cell, affectedCells);\n"
        "\t\t\tfor (int i : affectedCells){\n",
        "base blast smoke and order")
    source = replace(source,
        "\t\t\tif (terrainAffected) {\n\t\t\t\tDungeon.observe();\n"
        "\t\t\t}\n\t\t}\n"
        "\t\tif (demolition != null) demolition.finish();\n\t}\n",
        "\t\t\tif (terrainAffected) {\n\t\t\t\tDungeon.observe();\n"
        "\t\t\t}\n\t\t} else {\n"
        "\t\t\tcom.spd.blast.BlastBombVisuals.blast(this, cell, null);\n"
        "\t\t}\n"
        "\t\tif (demolition != null) demolition.finish();\n\t}\n", "non-destructive blast sound")
    # A Bomb.explode() call may recursively explode other bombs through Heap.
    # Finish gathering the whole chain before the render thread gets its events.
    signature = "\tpublic void explode(int cell){"
    if source.count(signature) != 1:
        raise SystemExit("Bomb.explode: expected exactly one method")
    opening = source.index("{", source.index(signature))
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return (source[:opening + 1]
                        + "\n\t\tcom.spd.blast.BlastBombVisuals.begin(cell);\n\t\ttry {"
                        + source[opening + 1:index]
                        + "\n\t\t} finally {\n\t\t\tcom.spd.blast.BlastBombVisuals.end();\n\t\t}\n\t"
                        + source[index:])
    raise SystemExit("Bomb.explode: unmatched braces")


def patch_fire(source: str) -> str:
    source = replace(source,
        "\t\tPathFinder.buildDistanceMap( cell,",
        "\t\tjava.util.ArrayList<Integer> flameCells = new java.util.ArrayList<>();\n"
        "\t\tPathFinder.buildDistanceMap( cell,", "fire bomb effect cells")
    source = replace(source,
        "\t\t\t\tCellEmitter.get(i).burst(FlameParticle.FACTORY, 5);",
        "\t\t\t\tflameCells.add(i);", "fire bomb particles")
    return replace(source,
        "\t\tSample.INSTANCE.play(Assets.Sounds.BURNING);",
        "\t\tcom.spd.blast.BlastBombVisuals.effect(this, () -> {\n"
        "\t\t\tfor (int affected : flameCells) CellEmitter.get(affected).burst(FlameParticle.FACTORY, 5);\n"
        "\t\t\tSample.INSTANCE.play(Assets.Sounds.BURNING);\n"
        "\t\t});", "fire bomb sound")


def patch_holy(source: str) -> str:
    source = replace(source,
        "\t\tif (Dungeon.level.heroFOV[cell]) {\n"
        "\t\t\tnew Flare(10, 64).show(Dungeon.hero.sprite.parent, DungeonTilemap.tileCenterToWorld(cell), 2f);\n"
        "\t\t}",
        "\t\tboolean showFlare = Dungeon.level.heroFOV[cell];\n"
        "\t\tArrayList<Char> shadowTargets = new ArrayList<>();",
        "holy bomb flare")
    source = replace(source,
        "\t\t\t\tch.sprite.emitter().start( ShadowParticle.UP, 0.05f, 10 );",
        "\t\t\t\tshadowTargets.add(ch);", "holy bomb particles")
    return replace(source,
        "\t\tSample.INSTANCE.play( Assets.Sounds.READ );",
        "\t\tcom.spd.blast.BlastBombVisuals.effect(this, () -> {\n"
        "\t\t\tif (showFlare && Dungeon.hero != null && Dungeon.hero.sprite != null) {\n"
        "\t\t\t\tnew Flare(10, 64).show(Dungeon.hero.sprite.parent, DungeonTilemap.tileCenterToWorld(cell), 2f);\n"
        "\t\t\t}\n"
        "\t\t\tfor (Char target : shadowTargets) {\n"
        "\t\t\t\tif (target.sprite != null) target.sprite.emitter().start(ShadowParticle.UP, 0.05f, 10);\n"
        "\t\t\t}\n"
        "\t\t\tSample.INSTANCE.play( Assets.Sounds.READ );\n"
        "\t\t});", "holy bomb sound")


def patch_woolly(source: str) -> str:
    source = replace(source,
        "\t\tfor (int i : spawnPoints){",
        "\t\tArrayList<Integer> woolCells = new ArrayList<>();\n"
        "\t\tfor (int i : spawnPoints){", "woolly bomb particles list")
    source = replace(source,
        "\t\t\t\tCellEmitter.get(i).burst(Speck.factory(Speck.WOOL), 4);",
        "\t\t\t\twoolCells.add(i);", "woolly bomb particles")
    return replace(source,
        "\t\tSample.INSTANCE.play(Assets.Sounds.PUFF);\n"
        "\t\tSample.INSTANCE.play(Assets.Sounds.SHEEP);",
        "\t\tcom.spd.blast.BlastBombVisuals.effect(this, () -> {\n"
        "\t\t\tfor (int affected : woolCells) CellEmitter.get(affected).burst(Speck.factory(Speck.WOOL), 4);\n"
        "\t\t\tSample.INSTANCE.play(Assets.Sounds.PUFF);\n"
        "\t\t\tSample.INSTANCE.play(Assets.Sounds.SHEEP);\n"
        "\t\t});", "woolly bomb sounds")


def patch_arcane(source: str) -> str:
    source = replace(source,
        "\t\tArrayList<Char> affected = new ArrayList<>();",
        "\t\tArrayList<Char> affected = new ArrayList<>();\n"
        "\t\tArrayList<Integer> arcaneCells = new ArrayList<>();",
        "arcane bomb particle cells")
    source = replace(source,
        "\t\t\t\tCellEmitter.get(i).burst(ElmoParticle.FACTORY, 10);",
        "\t\t\t\tarcaneCells.add(i);", "arcane bomb particles")
    return replace(source,
        "\t\tfor (Char ch : affected){",
        "\t\tcom.spd.blast.BlastBombVisuals.effect(this, () -> {\n"
        "\t\t\tfor (int affectedCell : arcaneCells) CellEmitter.get(affectedCell).burst(ElmoParticle.FACTORY, 10);\n"
        "\t\t});\n"
        "\t\tfor (Char ch : affected){", "arcane bomb visual scheduling")


def patch_flash(source: str) -> str:
    source = replace(source,
        "\t\tArrayList<Lightning.Arc> arcs = new ArrayList<>();",
        "\t\tArrayList<Lightning.Arc> arcs = new ArrayList<>();\n"
        "\t\tboolean flashHero = false;", "flashbang visual flag")
    source = replace(source,
        "\t\t\tif (ch == Dungeon.hero){\n"
        "\t\t\t\tGameScene.flash(0x80FFFFFF);\n"
        "\t\t\t}",
        "\t\t\tif (ch == Dungeon.hero){\n"
        "\t\t\t\tflashHero = true;\n"
        "\t\t\t}", "flashbang flash timing")
    return replace(source,
        "\t\tCellEmitter.center(cell).burst(SparkParticle.FACTORY, 20);\n"
        "\t\tDungeon.hero.sprite.parent.addToFront(new Lightning(arcs, null));\n"
        "\t\tSample.INSTANCE.play( Assets.Sounds.LIGHTNING );",
        "\t\tfinal boolean showFlash = flashHero;\n"
        "\t\tcom.spd.blast.BlastBombVisuals.effect(this, () -> {\n"
        "\t\t\tif (showFlash) GameScene.flash(0x80FFFFFF);\n"
        "\t\t\tCellEmitter.center(cell).burst(SparkParticle.FACTORY, 20);\n"
        "\t\t\tif (Dungeon.hero != null && Dungeon.hero.sprite != null) {\n"
        "\t\t\t\tDungeon.hero.sprite.parent.addToFront(new Lightning(arcs, null));\n"
        "\t\t\t}\n"
        "\t\t\tSample.INSTANCE.play( Assets.Sounds.LIGHTNING );\n"
        "\t\t});", "flashbang particles and sound")


def patch_shrapnel(source: str) -> str:
    source = replace(source,
        "\t\tArrayList<Char> affected = new ArrayList<>();",
        "\t\tArrayList<Char> affected = new ArrayList<>();\n"
        "\t\tArrayList<Integer> particleCells = new ArrayList<>();",
        "shrapnel particle cells")
    source = replace(source,
        "\t\t\t\tif (Dungeon.level.heroFOV[i] && !Dungeon.level.solid[i]) {\n"
        "\t\t\t\t\tCellEmitter.center( i ).burst( BlastParticle.FACTORY, 5 );\n"
        "\t\t\t\t}",
        "\t\t\t\tif (Dungeon.level.heroFOV[i] && !Dungeon.level.solid[i]) {\n"
        "\t\t\t\t\tparticleCells.add(i);\n"
        "\t\t\t\t}", "shrapnel particles")
    return replace(source,
        "\t\tfor (Char ch : affected){",
        "\t\tcom.spd.blast.BlastBombVisuals.effect(this, () -> {\n"
        "\t\t\tfor (int particleCell : particleCells) CellEmitter.center(particleCell).burst(BlastParticle.FACTORY, 5);\n"
        "\t\t});\n"
        "\t\tfor (Char ch : affected){", "shrapnel effect scheduling")


# Keep the visual/gameplay chain open until all subtype effects have completed.
# Bomb.explode is wrapped separately by patch_bomb; no subclass is wrapped twice.
EXPLODING_SUBTYPES = (
    "ArcaneBomb.java", "Firebomb.java", "FlashBangBomb.java",
    "FrostBomb.java", "HolyBomb.java", "RegrowthBomb.java",
    "ShrapnelBomb.java", "SmokeBomb.java", "WoollyBomb.java",
)


def wrap_subtype(source: str, name: str) -> str:
    signature = "\tpublic void explode(int cell) {"
    if source.count(signature) != 1:
        raise SystemExit(f"{name}: expected exactly one explode method")
    opening = source.index("{", source.index(signature))
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return (source[:opening + 1]
                        + "\n\t\tcom.spd.blast.BlastBombVisuals.begin(cell);\n\t\ttry {"
                        + source[opening + 1:index]
                        + "\n\t\t} finally {\n\t\t\tcom.spd.blast.BlastBombVisuals.end();\n\t\t}\n\t"
                        + source[index:])
    raise SystemExit(f"{name}: unmatched explode braces")


PATCHES = {
    "Bomb.java": patch_bomb,
    "Firebomb.java": patch_fire,
    "HolyBomb.java": patch_holy,
    "WoollyBomb.java": patch_woolly,
    "ArcaneBomb.java": patch_arcane,
    "FlashBangBomb.java": patch_flash,
    "ShrapnelBomb.java": patch_shrapnel,
}

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_bomb_visuals.py <bombs-directory>")
    directory = Path(sys.argv[1])
    for name in dict.fromkeys((*PATCHES, *EXPLODING_SUBTYPES)):
        path = directory / name
        source = path.read_text(encoding="utf-8")
        if name in PATCHES:
            source = PATCHES[name](source)
        if name in EXPLODING_SUBTYPES:
            source = wrap_subtype(source, name)
        path.write_text(source, encoding="utf-8")
        print(f"patched {path}: deferred audiovisual effects and full-chain terrain")
