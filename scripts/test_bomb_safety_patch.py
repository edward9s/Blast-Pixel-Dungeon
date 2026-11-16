#!/usr/bin/env python3
"""Regression checks for bomb-only harmful effects and fail-fast patching."""
from pathlib import Path
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_bomb_safety.py"
SAFETY = ROOT / "core/src/main/java/com/spd/blast/BlastBombSafety.java"
BOMB_FIRE = ROOT / "core/src/main/java/com/spd/blast/BombFire.java"
BOMB_FREEZING = ROOT / "core/src/main/java/com/spd/blast/BombFreezing.java"
BOMB_REGROWTH = ROOT / "core/src/main/java/com/spd/blast/BombRegrowth.java"

FIXTURES = {
    "actors/blobs/Fire.java": (
        "Freezing freeze = (Freezing)Dungeon.level.blobs.get( Freezing.class );\n"
        "if (freeze != null && freeze.volume > 0 && freeze.cur[cell] > 0){\n"
        "\t\t\t\t\t\tfreeze.clear(cell);\n"
        "burn( cell );\n"
        "} else if (freeze == null || freeze.volume <= 0 || freeze.cur[cell] <= 0) {\n"
        "burn( cell );\n"
        "\tpublic static void burn( int pos ) {\n"
        "if (ch != null && !ch.isImmune(Fire.class)) {\n"
    ),
    "actors/blobs/Freezing.java": (
        "Fire fire = (Fire)Dungeon.level.blobs.get( Fire.class );\n"
        "if (fire != null && fire.volume > 0 && fire.cur[cell] > 0){\n"
        "\t\t\t\t\t\tfire.clear(cell);\n"
        "Freezing.freeze(cell);\n"
        "\tpublic static void freeze( int cell ){\n"
        "if (ch != null && !ch.isImmune(Freezing.class)) {\n"
    ),
    "items/bombs/Firebomb.java": (
        "Blob.seed(i, 2, Fire.class)\n"
        "Blob.seed(i, 10, Fire.class)\n"
    ),
    "items/bombs/FrostBomb.java": (
        "Blob.seed(i, 10, Freezing.class)\n"
        "if (ch != null){\n"
        "\t\t\t\t\tBuff.affect(ch, Frost.class, 2f);\n"
    ),
    "items/bombs/FlashBangBomb.java": (
        "for (Char ch : affected){\n"
        "\t\t\t//25% bonus damage and 10 turns of stun\n"
    ),
    "items/bombs/HolyBomb.java": (
        "if (ch.properties().contains(Char.Property.UNDEAD) || ch.properties().contains(Char.Property.DEMONIC)){\n"
    ),
}


def run_patch(root: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(PATCH), str(root)],
        capture_output=True, text=True,
    )


def write_fixtures(root: Path) -> None:
    for name, content in FIXTURES.items():
        path = root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")


def main() -> None:
    safety = SAFETY.read_text(encoding="utf-8")
    assert "target == Dungeon.hero" in safety
    assert "target.buff(BlastIndestructible.class) != null" in safety
    assert "target.buff(BlastTestBuffs.Indestructible.class) != null" in safety
    assert "!BlastBombSafety.protects(target)" in BOMB_REGROWTH.read_text(encoding="utf-8")
    assert "!BlastBombSafety.protects(target)" in BOMB_FIRE.read_text(encoding="utf-8")
    assert "!BlastBombSafety.protects(target)" in BOMB_FREEZING.read_text(encoding="utf-8")

    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        write_fixtures(root)
        result = run_patch(root)
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)

        fire = (root / "actors/blobs/Fire.java").read_text(encoding="utf-8")
        freezing = (root / "actors/blobs/Freezing.java").read_text(encoding="utf-8")
        assert "burnCell( cell );" in fire
        assert "burn(pos, true);" in fire
        assert "if (affectCharacter && ch != null" in fire
        assert "freezeCell(cell);" in freezing
        assert "freeze(cell, true);" in freezing
        assert "if (affectCharacter && ch != null" in freezing
        assert "BombFreezing.class" in fire
        assert "BombFire.class" in freezing
        assert "com.spd.blast.BombFire.class" in (
            root / "items/bombs/Firebomb.java").read_text(encoding="utf-8")
        frostbomb = (root / "items/bombs/FrostBomb.java").read_text(encoding="utf-8")
        assert "com.spd.blast.BombFreezing.class" in frostbomb
        assert "!com.spd.blast.BlastBombSafety.protects(ch)" in frostbomb
        assert "if (com.spd.blast.BlastBombSafety.protects(ch)) continue;" in (
            root / "items/bombs/FlashBangBomb.java").read_text(encoding="utf-8")
        assert "&& !com.spd.blast.BlastBombSafety.protects(ch)" in (
            root / "items/bombs/HolyBomb.java").read_text(encoding="utf-8")

        snapshot = {name: (root / name).read_text(encoding="utf-8")
                    for name in FIXTURES}
        assert run_patch(root).returncode != 0
        assert snapshot == {name: (root / name).read_text(encoding="utf-8")
                            for name in FIXTURES}

        write_fixtures(root)
        broken = root / "items/bombs/FlashBangBomb.java"
        broken.write_text("upstream removed flashbang damage\n", encoding="utf-8")
        assert run_patch(root).returncode != 0
        for name, initial in FIXTURES.items():
            expected = "upstream removed flashbang damage\n" if name.endswith(
                "FlashBangBomb.java") else initial
            assert (root / name).read_text(encoding="utf-8") == expected

    print("Bombproof bomb-effects patch checks passed")


if __name__ == "__main__":
    main()
