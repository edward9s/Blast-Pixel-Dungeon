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
        "\t\tif (com.spd.blast.BlastBombSafety.protects(this)\n"
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

    # A previously safe cached path may become electrified between hero turns.
    # Clear it before native replanning so the usual long-detour cutoff cannot
    # reject a safe route solely because the old route was shorter.
    s = replace_once(
        s,
        "\t\t\tboolean newPath = false;",
        "\t\t\tif (path != null && !path.isEmpty()"
        " && com.spd.blast.BlastMovement.pylonDanger(path.getFirst())) path = null;\n"
        "\t\t\tboolean newPath = false;",
        "Hero.getCloser replan electrified route"
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
        "\t\t\tif (com.spd.blast.BlastMovement.pylonDanger(step)) {\n"
        "\t\t\t\tpath = null;\n"
        "\t\t\t\treturn false;\n"
        "\t\t\t}\n"
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
        "\t\tboolean blastManualFinished = com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& com.spd.blast.BlastAutoPlay.finishManualAction(this);\n"
        "\t\treturn (blastManualFinished || actResult) && blastActionAfterUnlock == null;\n\t}",
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

    # A chasm prompt suspends the manual move until the user decides.
    s = replace_once(
        s,
        "\t\t\t\tif (!Chasm.jumpConfirmed){\n"
        "\t\t\t\t\tChasm.heroJump(this);",
        "\t\t\t\tif (!Chasm.jumpConfirmed){\n"
        "\t\t\t\t\tcom.spd.blast.BlastAutoPlay.beginManualConfirmation(this);\n"
        "\t\t\t\t\tChasm.heroJump(this);",
        "Hero.getCloser chasm confirmation"
    )

    # Map taps and directional input retain the native multi-turn HeroAction.
    s = replace_once(
        s,
        "\t\treturn true;\n\t}\n\t\n\tpublic void earnExp( int exp, Class source ) {",
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()) {\n"
        "\t\t\tcom.spd.blast.BlastAutoPlay.beginManualAction(this);\n"
        "\t\t}\n"
        "\t\treturn true;\n\t}\n\t\n\tpublic void earnExp( int exp, Class source ) {",
        "Hero.handle manual command"
    )

    # Enemy discovery interrupts native manual movement. Transfer the saved
    # destination to auto-play so tactical combat takes priority.
    s = replace_once(
        s,
        "\t\tcheckVisibleMobs();\n"
        "\t\tif (buff(TalismanOfForesight.Foresight.class) != null){\n"
        "\t\t\tbuff(TalismanOfForesight.Foresight.class).checkAwareness();\n"
        "\t\t}\n"
        "\t\tBuffIndicator.refreshHero();",
        "\t\tcheckVisibleMobs();\n"
        "\t\tif (buff(TalismanOfForesight.Foresight.class) != null){\n"
        "\t\t\tbuff(TalismanOfForesight.Foresight.class).checkAwareness();\n"
        "\t\t}\n"
        "\t\tcom.spd.blast.BlastAutoPlay.interruptManualTravelForCombat(this);\n"
        "\t\tBuffIndicator.refreshHero();",
        "Hero.act interrupt manual route for combat"
    )

    # Use the normal action dispatch for automated turns.
    # If all safe moves are blocked, advance the game by one regular turn.
    s = replace_once(
        s,
        "\t\tboolean actResult;\n\t\tif (curAction == null) {",
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& com.spd.blast.BlastAutoPlay.performManualInput(this)) {\n"
        "\t\t\treturn false;\n"
        "\t\t}\n"
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& com.spd.blast.BlastAutoPlay.pauseForManualInput(this)) {\n"
        "\t\t\treturn false;\n"
        "\t\t}\n"
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& com.spd.blast.BlastAutoPlay.performPausedVaultTravel(this)) {\n"
        "\t\t\treturn false;\n"
        "\t\t}\n"
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& !resting\n"
        "\t\t\t\t&& com.spd.blast.BlastAutoPlay.pauseForUser(this)) {\n"
        "\t\t\tready();\n"
        "\t\t\treturn false;\n"
        "\t\t}\n"
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.manualActionActive(this)\n"
        "\t\t\t\t&& com.spd.blast.BlastAutoPlay.performBombInput(this)) {\n"
        "\t\t\treturn false;\n"
        "\t\t}\n"
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& !resting\n"
        "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.isPaused()\n"
        "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.manualActionActive(this)\n"
        "\t\t\t\t&& com.spd.blast.BlastAutoPlay.chooseAction(this)) {\n"
        "\t\t\treturn false;\n"
        "\t\t}\n"
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& !resting\n"
        "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.isPaused()\n"
        "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.manualActionActive(this)\n"
        "\t\t\t\t&& curAction == null) {\n"
        "\t\t\tspendAndNext(TICK);\n"
        "\t\t\treturn false;\n"
        "\t\t}\n\n"
        "\t\tboolean actResult;\n\t\tif (curAction == null) {",
        "Hero.act automatic actions"
    )

    # A single retry can recover from a stale blocked move; later turns with
    # no legal action are advanced by the regular auto-play idle wait above.
    s = replace_once(
        s,
        "\tprivate void ready() {\n",
        "\tprivate void ready() {\n"
        "\t\tboolean blastRetry = com.spd.blast.BlastSettings.testAutoPlay()\n"
        "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.isPaused()\n"
        "\t\t\t\t&& !com.spd.blast.BlastAutoPlay.manualActionActive(this)\n"
        "\t\t\t\t&& curAction != null\n"
        "\t\t\t\t&& com.spd.blast.BlastAutoPlay.retryBlockedAction(this);\n",
        "Hero.ready bounded auto retry"
    )
    s = replace_once(
        s,
        "\t\tGameScene.ready();\n\t\t//check statistics to see if vault warned?",
        "\t\tGameScene.ready();\n"
        "\t\tif (blastRetry) spendAndNext(TICK);\n"
        "\t\t//check statistics to see if vault warned?",
        "Hero.ready automatic continuation"
    )

    s = replace_once(
        s,
        "\tpublic void damage( int dmg, Object src ) {\n",
        "\tpublic void damage( int dmg, Object src ) {\n"
        "\t\tcom.spd.blast.BlastAutoPlay.onDamage(this, src);\n",
        "Hero.damage attack source"
    )

    # Auto-pickup may need a spare backpack slot and can sell the collected gear.
    s = replace_once(
        s,
        "\t\t\t\tif (item.doPickUp( this )) {\n\t\t\t\t\theap.pickUp();",
        "\t\t\t\tif (com.spd.blast.BlastAutoPickup.pickUp(this, item, pos)) {\n"
        "\t\t\t\t\theap.pickUp();\n"
        "\t\t\t\t\tcom.spd.blast.BlastAutoPickup.onPickupSuccess(this, item, pos);",
        "Hero.actPickUp report native pickup success"
    )

    # Only an actual native pickup failure can suppress its next automatic retry.
    s = replace_once(
        s,
        "\t\t\t\t} else {\n\n\t\t\t\t\tif (waitOrPickup) {",
        "\t\t\t\t} else {\n"
        "\t\t\t\t\tcom.spd.blast.BlastAutoPickup.onPickupFailure(this, item, pos);\n\n"
        "\t\t\t\t\tif (waitOrPickup) {",
        "Hero.actPickUp report native pickup failure"
    )

    # No Ankh, death screen, or save deletion when the test is enabled.
    s = replace_once(
        s,
        "\tpublic void die( Object cause ) {\n\t\t\n\t\tcurAction = null;",
        "\tpublic void die( Object cause ) {\n"
        "\t\tif (com.spd.blast.BlastSettings.testAutoPlay()) {\n"
        "\t\t\tHP = HT;\n"
        "\t\t\treturn;\n"
        "\t\t}\n\n"
        "\t\tcurAction = null;",
        "Hero.die full heal"
    )

    path.write_text(s, encoding="utf-8")
    print(f"patched {path}: bomb damage immunity and phase movement")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_hero.py <Hero.java>")
    patch(Path(sys.argv[1]))
