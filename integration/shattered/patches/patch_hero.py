#!/usr/bin/env python3
"""Install the two experimental hero-only abilities in SPD's existing code paths."""
from pathlib import Path
import sys

def replace_once(source: str, before: str, after: str, location: str) -> str:
    count = source.count(before)
    if count != 1:
        raise SystemExit(f"{location}: expected 1 occurrence, found {count}: {before!r}")
    return source.replace(before, after, 1)

def patch(path: Path) -> None:
    s = path.read_text(encoding="utf-8")

    # Maintain both permanent bomb abilities on the actor thread.
    s = replace_once(
        s,
        "\tpublic boolean act() {\n\t\t\n\t\t//calls to dungeon.observe",
        "\tpublic boolean act() {\n\t\t\n"
        "\t\tcom.spd.blast.BlastRemoteBomb.ensureHero();\n"
        "\t\tcom.spd.blast.BlastBombManiac.ensureHero();\n"
        "\t\tcom.spd.blast.BlastTestBuffs.syncHero();\n"
        "\t\tcom.spd.blast.BlastBombWarnings.refresh(Dungeon.level);\n"
        "\t\t//calls to dungeon.observe",
        "Hero.act remote buff maintenance"
    )

    # Unblessed Ankh removes the blast-radius upgrade but preserves bomb capacity.
    s = replace_once(
        s,
        "\tpublic void resurrect() {\n\t\tHP = HT;",
        "\tpublic void resurrect() {\n"
        "\t\tcom.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff.detach("
        "this, com.spd.blast.BlastRadius.class);\n"
        "\t\tHP = HT;",
        "Hero.resurrect radius reset"
    )

    # Source attribution is a Bomb (including its subclasses) only for the actual
    # explosive damage. Other environmental damage continues to work normally.
    s = replace_once(
        s,
        "\tpublic void damage( int dmg, Object src ) {\n"
        "\t\tif (buff(TimekeepersHourglass.timeStasis.class) != null",
        "\tpublic void damage( int dmg, Object src ) {\n"
        "\t\tif (com.spd.blast.BlastSettings.testIndestructible()\n"
        "\t\t\t\t&& src instanceof com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb) {\n"
        "\t\t\treturn;\n"
        "\t\t}\n"
        "\t\tif (buff(TimekeepersHourglass.timeStasis.class) != null",
        "Hero.damage"
    )

    s = replace_once(
        s,
        "\t\t\t\tif (Dungeon.level.passable[target] || Dungeon.level.avoid[target]) {",
        "\t\t\t\tif (Dungeon.level.passable[target] || Dungeon.level.avoid[target]"
        " || com.spd.blast.BlastMovement.canPhase(target)) {",
        "Hero.getCloser adjacent movement"
    )
    s = replace_once(
        s,
        "\t\t\t\tif (!Dungeon.level.passable[path.get(0)] || Actor.findChar(path.get(0)) != null) {",
        "\t\t\t\tif ((!Dungeon.level.passable[path.get(0)]"
        " && !com.spd.blast.BlastMovement.canPhase(path.get(0)))"
        " || Actor.findChar(path.get(0)) != null) {",
        "Hero.getCloser retained path"
    )
    s = replace_once(
        s,
        "\t\t\t\t\tpassable[i] = p[i] && (v[i] || m[i]);",
        "\t\t\t\t\tpassable[i] = com.spd.blast.BlastMovement.pathPassable("
        "i, p[i] && (v[i] || m[i]), v[i] || m[i] || fieldOfView[i]);",
        "Hero.getCloser path generation"
    )


    # Free Movement can cross magical sheep while keeping every other actor solid.
    s = replace_once(
        s,
        "\t\t\tif (Actor.findChar( target ) == null) {",
        "\t\t\tif (Actor.findChar( target ) == null"
        " || com.spd.blast.BlastMovement.canPassSheep(target)) {",
        "Hero.getCloser adjacent sheep"
    )
    s = replace_once(
        s,
        " || Actor.findChar(path.get(0)) != null) {",
        " || (Actor.findChar(path.get(0)) != null"
        " && !com.spd.blast.BlastMovement.canPassSheep(path.get(0)))) {",
        "Hero.getCloser retained path sheep"
    )
    s = replace_once(
        s,
        "\t\t\t\tPathFinder.Path newpath = Dungeon.findPath(this, target, passable, fieldOfView, true);",
        "\t\t\t\tPathFinder.Path newpath = com.spd.blast.BlastMovement.findPath("
        "target, passable, fieldOfView);",
        "Hero.getCloser pathfinding around sheep"
    )
    s = replace_once(
        s,
        "\t\t} else if (ch instanceof Mob && (fieldOfView[cell] || Char.hasProp(ch, Property.OBJECT))) {",
        "\t\t} else if (com.spd.blast.BlastMovement.canPassSheep(cell)) {\n"
        "\t\t\tcurAction = new HeroAction.Move(cell);\n"
        "\t\t\tlastAction = null;\n"
        "\t\t} else if (ch instanceof Mob && (fieldOfView[cell] || Char.hasProp(ch, Property.OBJECT))) {",
        "Hero.handle tap on sheep"
    )

    # For doors with a matching key, delegate to SPD's normal Unlock action.
    # Preserve the movement destination until the unlock animation completes.
    s = replace_once(
        s,
        "\tprivate boolean walkingToVisibleTrapInFog = false;",
        "\tprivate boolean walkingToVisibleTrapInFog = false;\n"
        "\tprivate HeroAction blastActionAfterUnlock;",
        "Hero unlock continuation field"
    )
    s = replace_once(
        s,
        "\t\tif (step != -1) {\n\n\t\t\tfloat delay = 1;",
        "\t\tif (step != -1) {\n"
        "\t\t\tif (com.spd.blast.BlastMovement.shouldUnlock(step)) {\n"
        "\t\t\t\tblastActionAfterUnlock = curAction;\n"
        "\t\t\t\tcurAction = new HeroAction.Unlock(step);\n"
        "\t\t\t\tactUnlock((HeroAction.Unlock) curAction);\n"
        "\t\t\t\treturn true;\n"
        "\t\t\t}\n\n"
        "\t\t\tfloat delay = 1;",
        "Hero.getCloser unlock matching-key doors"
    )
    # A key-unlock operation waits for its animation callback before advancing
    # the actor. getCloser() reported success to avoid its callers clearing the
    # pending Unlock action, so Hero.act() must return false for that case.
    s = replace_once(
        s,
        "\t\treturn actResult;\n\t}",
        "\t\treturn actResult && blastActionAfterUnlock == null;\n\t}",
        "Hero.act wait for door unlock animation"
    )
    s = replace_once(
        s,
        "\t\tcurAction = null;\n\n"
        "\t\tif (!ready) {\n\t\t\tsuper.onOperateComplete();\n\t\t}",
        "\t\tHeroAction resume = null;\n"
        "\t\tif (blastActionAfterUnlock != null && curAction instanceof HeroAction.Unlock) {\n"
        "\t\t\tint doorCell = ((HeroAction.Unlock) curAction).dst;\n"
        "\t\t\tif (Dungeon.level.map[doorCell] != Terrain.LOCKED_DOOR\n"
        "\t\t\t\t\t&& Dungeon.level.map[doorCell] != Terrain.CRYSTAL_DOOR) {\n"
        "\t\t\t\tresume = blastActionAfterUnlock;\n"
        "\t\t\t}\n"
        "\t\t}\n"
        "\t\tblastActionAfterUnlock = null;\n"
        "\t\tcurAction = resume;\n\n"
        "\t\tif (!ready) {\n\t\t\tsuper.onOperateComplete();\n\t\t}",
        "Hero.onOperateComplete resume after unlocking"
    )

    # With the setting on, touching a locked door is a move, not an unlock.
    # Locked exits are kept as progression gates.
    s = replace_once(
        s,
        "\t\t} else if (Dungeon.level.map[cell] == Terrain.LOCKED_DOOR\n"
        "\t\t\t\t|| Dungeon.level.map[cell] == Terrain.HERO_LKD_DR\n"
        "\t\t\t\t|| Dungeon.level.map[cell] == Terrain.CRYSTAL_DOOR\n"
        "\t\t\t\t|| Dungeon.level.map[cell] == Terrain.LOCKED_EXIT) {",
        "\t\t} else if (!com.spd.blast.BlastMovement.canPhase(cell)\n"
        "\t\t\t\t&& (Dungeon.level.map[cell] == Terrain.LOCKED_DOOR\n"
        "\t\t\t\t|| Dungeon.level.map[cell] == Terrain.HERO_LKD_DR\n"
        "\t\t\t\t|| Dungeon.level.map[cell] == Terrain.CRYSTAL_DOOR\n"
        "\t\t\t\t|| Dungeon.level.map[cell] == Terrain.LOCKED_EXIT)) {",
        "Hero.handle locked doors"
    )

    # Free Movement keeps ordinary mine walls phaseable. Only mine deposits
    # which cannot be phased through use the native Pickaxe action.
    s = replace_once(
        s,
        "\t\t} else if (Dungeon.level instanceof MiningLevel &&\n"
        "\t\t\t\t\tbelongings.getItem(Pickaxe.class) != null &&",
        "\t\t} else if (Dungeon.level instanceof MiningLevel &&\n"
        "\t\t\t\t\tbelongings.getItem(Pickaxe.class) != null &&\n"
        "\t\t\t\t\t!com.spd.blast.BlastMovement.canPhase(cell) &&",
        "Hero.handle mining versus phasing"
    )

    s = replace_once(
        s,
        "com.spd.blast.BlastSettings.testIndestructible()\n"
        "\t\t\t\t&& src",
        "(com.spd.blast.BlastSettings.testIndestructible()\n"
        "\t\t\t\t|| buff(com.spd.blast.BlastIndestructible.class) != null)\n"
        "\t\t\t\t&& src",
        "temporary bomb immunity"
    )
    path.write_text(s, encoding="utf-8")
    print(f"patched {path}: bomb damage immunity and phase movement")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_hero.py <Hero.java>")
    patch(Path(sys.argv[1]))
