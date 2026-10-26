#!/usr/bin/env python3
"""Integrate remote fuses and track generated bombs without touching vanilla throws."""
from pathlib import Path
import sys


def replace(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(old, new, 1)


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    # The backpack bomb's primary action imbues one item into Bomb Maniac.
    # Ordinary (unlit) throws remain available, but only Bomb Maniac arms bombs.
    source = replace(source,
        "\t\tdefaultAction = AC_LIGHTTHROW;\n\t\tusesTargeting = true;",
        "\t\tdefaultAction = AC_IMBUE;\n\t\tusesTargeting = false;",
        "bomb default imbue action")
    source = replace(source,
        'private static final String AC_LIGHTTHROW = "LIGHTTHROW";',
        'private static final String AC_IMBUE = "BLAST_IMBUE";',
        "bomb imbue action constant")
    source = replace(source,
        "\t\tactions.add ( AC_LIGHTTHROW );",
        "\t\tactions.add ( AC_IMBUE );",
        "replace lit-throw action with imbue")
    source = replace(source,
        "\t\tif (action.equals(AC_LIGHTTHROW)) {\n"
        "\t\t\tlightingFuse = true;\n"
        "\t\t\taction = AC_THROW;\n"
        "\t\t} else\n"
        "\t\t\tlightingFuse = false;\n"
        "\n"
        "\t\tsuper.execute(hero, action);",
        "\t\tlightingFuse = false;\n"
        "\t\tif (action.equals(AC_IMBUE)) {\n"
        "\t\t\tGameScene.cancel();\n"
        "\t\t\tcom.spd.blast.BlastBombManiac.current().imbue(this);\n"
        "\t\t\treturn;\n"
        "\t\t}\n"
        "\t\tsuper.execute(hero, action);",
        "delegate bomb imbue to Bomb Maniac")
    source = replace(source,
        "\tprotected Fuse createFuse(){",
        "\t@Override\n"
        "\tpublic String actionName(String action, Hero hero) {\n"
        "\t\tif (action.equals(AC_IMBUE)) {\n"
        '\t\t\treturn com.spd.blast.BlastMessages.get("bomb_maniac.imbue");\n'
        "\t\t}\n"
        "\t\treturn super.actionName(action, hero);\n"
        "\t}\n\n"
        "\tprotected Fuse createFuse(){",
        "localize bomb imbue action")

    source = replace(source,
        "\t\tif (fuse != null) {\n"
        '\t\t\tGLog.w( Messages.get(this, "snuff_fuse") );\n'
        "\t\t\tfuse.snuff();\n"
        "\t\t\tfuse = null;\n"
        "\t\t}\n"
        "\t\treturn super.doPickUp(hero, pos);",
        "\t\tif (fuse != null) {\n"
        '\t\t\tGLog.w(com.spd.blast.BlastMessages.get("bomb.lit_pickup"));\n'
        "\t\t\treturn false;\n"
        "\t\t}\n"
        "\t\treturn super.doPickUp(hero, pos);",
        "prevent picking up lit bombs")

    source = replace(source,
        "\t\t\tActor.addDelayed(fuse = createFuse().ignite(this), 2);",
        """\t\t\tcom.spd.blast.BlastRemoteBomb remote = Dungeon.hero == null
\t\t\t\t\t? null : Dungeon.hero.buff(com.spd.blast.BlastRemoteBomb.class);
\t\t\tif (remote == null || remote.timedMode()) {
\t\t\t\tActor.addDelayed(fuse = createFuse().ignite(this), 2);
\t\t\t} else {
\t\t\t\tActor.add(fuse = new com.spd.blast.BlastRemoteBomb.RemoteFuse(
\t\t\t\t\t\tremote.allocateSequence()).ignite(this));
\t\t\t}""",
        "lit fuse")

    source = replace(source,
        "\t@Override\n\tpublic boolean isSimilar(Item item) {\n"
        "\t\treturn super.isSimilar(item) && this.fuse == ((Bomb) item).fuse;\n\t}",
        """\t@Override
\tpublic boolean isSimilar(Item item) {
\t\treturn super.isSimilar(item) && this.fuse == ((Bomb) item).fuse
\t\t\t\t&& blastManiacCharge == ((Bomb) item).blastManiacCharge;
\t}

\tprivate boolean blastManiacCharge;
\tprivate boolean blastManiacExplosion;

\t/** True for damage from a Bomb Maniac bomb, even after its slot is refunded. */
\tpublic boolean blastManiacExplosion() {
\t\treturn blastManiacExplosion;
\t}

\tpublic boolean blastManiacCharge() {
\t\treturn blastManiacCharge;
\t}

\tpublic void blastSetManiacCharge(boolean value) {
\t\tif (value && fuse != null) throw new IllegalStateException("Cannot mark an ignited bomb");
\t\tblastManiacCharge = value;
\t}

\t/** Only phase-enabled bombs ignore terrain and actors in flight. */
\t@Override
\tpublic int throwPos(Hero user, int dst) {
\t\treturn com.spd.blast.BlastBombTrajectory.throwPos(user.pos, dst);
\t}

\t/** Lit throw for the virtual bomb supply: no inventory slot is needed. */
\tpublic void blastThrowLit(int cell) {
\t\tboolean previous = lightingFuse;
\t\tlightingFuse = true;
\t\ttry {
\t\t\tonThrow(cell);
\t\t} finally {
\t\t\tlightingFuse = previous;
\t\t}
\t}""",
        "generated bomb metadata and throw")

    source = replace(source,
        "\t\tbundle.put( FUSE, fuse );",
        "\t\tbundle.put( FUSE, fuse );\n"
        '\t\tbundle.put("blast_maniac_charge", blastManiacCharge);',
        "save generated bomb flag")

    source = replace(source,
        "\t\tif (bundle.contains( FUSE ))",
        '\t\tblastManiacCharge = bundle.getBoolean("blast_maniac_charge");\n'
        "\t\tif (bundle.contains( FUSE ))",
        "restore generated bomb flag")

    source = replace(source,
        "\tpublic void explode(int cell){\n",
        """\tpublic void explode(int cell){
\t\tblastManiacExplosion = blastManiacCharge();
\t\tcom.spd.blast.BlastBombManiac.onConsumed(this);
""",
        "refund a bomb on explosion")

    source = replace(source,
        "\tprotected int explosionRange(){\n\t\treturn 1;\n\t}",
        """\tprotected int explosionRange(){
\t\treturn 1;
\t}

\t/** Cells reached by this bomb's initial explosion/effect. */
\tpublic boolean[] blastWarningCells(int cell) {
\t\tboolean[] explodable = com.watabou.utils.BArray.not(Dungeon.level.solid, null);
\t\tif (explodesDestructively()) {
\t\t\tcom.watabou.utils.BArray.or(Dungeon.level.flamable, explodable, explodable);
\t\t}
\t\tcom.spd.blast.BlastDemolition.includeDoors(explodable);
\t\treturn com.spd.blast.BlastBombWarnings.reachable(
\t\t\t\tDungeon.level, cell, explodable, explosionRange());
\t}""", "bomb blast warning footprint")

    source = replace(source,
        "\t\t\tBomb bomb = new Bomb();",
        """\t\t\tif (fuse != null) {
\t\t\t\tGLog.w(com.spd.blast.BlastMessages.get("bomb.lit_pickup"));
\t\t\t\treturn false;
\t\t\t}
\t\t\tif (blastManiacCharge()) {
\t\t\t\tBomb charged = new Bomb();
\t\t\t\tcharged.blastSetManiacCharge(true);
\t\t\t\tif (charged.doPickUp(hero, pos)) {
\t\t\t\t\tnew Bomb().collect(hero.belongings.backpack);
\t\t\t\t\treturn true;
\t\t\t\t}
\t\t\t\treturn false;
\t\t\t}
\t\t\tBomb bomb = new Bomb();""",
        "double-bomb pickup preserves exactly one charged bomb")

    source = replace(source,
        "\t@Override\n\tpublic boolean doPickUp(Hero hero, int pos) {",
        """\t/** Every kicked bomb gets a new timed fuse based on its effective blast radius. */
\tpublic void blastResetFuseAfterKick() {
\t\tif (fuse == null) throw new IllegalStateException("Cannot reset an unlit bomb");
\t\tfuse.snuff();
\t\tint duration = Math.max(2, blastExplosionRange() + 1);
\t\tActor.addDelayed(fuse = createFuse().ignite(this), duration);
\t}

\t@Override
\tpublic boolean doPickUp(Hero hero, int pos) {""",
        "remote fuse release")

    source = replace(source,
        "\t/** Cells reached by this bomb's initial explosion/effect. */",
        "\tpublic int blastExplosionRange() {\n"
        "\t\treturn com.spd.blast.BlastRadius.apply(explosionRange());\n"
        "\t}\n\n"
        "\t/** Cells reached by this bomb's initial explosion/effect. */",
        "blast radius public accessor")
    source = replace(source,
        "PathFinder.buildDistanceMap( cell, explodable, explosionRange() );",
        "PathFinder.buildDistanceMap( cell, explodable, blastExplosionRange() );",
        "actual bomb explosion radius")
    source = replace(source,
        "Dungeon.level, cell, explodable, explosionRange());",
        "Dungeon.level, cell, explodable, blastExplosionRange());",
        "bomb warning radius")
    source = replace(source,
        "PathFinder.buildDistanceMap( cell, explodable, blastExplosionRange() );",
        "com.spd.blast.BlastBombWarnings.buildEffectDistanceMap( cell, explodable, blastExplosionRange() );",
        "actual enhanced blast footprint")

    source = replace(source,
        "\t\tif (explodesDestructively()) {\n\n\t\t\tArrayList<Integer> affectedCells",
        "\t\tcom.spd.blast.BlastDemolition demolition =\n"
        "\t\t\t\tcom.spd.blast.BlastDemolition.prepare(this, cell);\n\n"
        "\t\tif (explodesDestructively()) {\n\n\t\t\tArrayList<Integer> affectedCells",
        "snapshot demolition before bomb terrain changes")
    source = replace(source,
        "\t\t\t\tif (heap != null) {\n\t\t\t\t\theap.explode();",
        "\t\t\t\tif (heap != null && !com.spd.blast.BlastDemolition.protect(heap)) {\n"
        "\t\t\t\t\theap.explode();",
        "preserve chest contents throughout linked explosions")
    source = replace(source,
        "\t\t\tif (terrainAffected) {\n\t\t\t\tDungeon.observe();\n"
        "\t\t\t}\n\t\t}\n\t}\n",
        "\t\t\tif (terrainAffected) {\n\t\t\t\tDungeon.observe();\n"
        "\t\t\t}\n\t\t}\n"
        "\t\tif (demolition != null) demolition.finish();\n\t}\n",
        "finish demolition for every bomb type")

    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: remote fuse, generated bomb throw and identity")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_bomb.py <Bomb.java>")
    patch(Path(sys.argv[1]))
