#!/usr/bin/env python3
"""Smoke-test regrowth bomb rooting, herbal healing, and fail-fast anchors."""
from pathlib import Path
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "integration/shattered/patches/patch_regrowth_bomb.py"
JAVA = ROOT / "core/src/main/java/com/spd/blast/BombRegrowth.java"

REGROWTH = (
    "public class Regrowth extends Blob {\n"
    "\t@Override\n"
    "\tprotected void evolve() {\n"
    "\t\tif (ch != null\n"
    "\t\t\t&& !ch.isImmune(this.getClass())\n"
    "\t\t\t&& off[cell] > 1) {\n"
    "\t\t}\n"
    "\t}\n"
    "}\n"
)
BOMB = (
    "import com.shatteredpixel.shatteredpixeldungeon.Dungeon;\n"
    "import com.shatteredpixel.shatteredpixeldungeon.actors.Char;\n"
    "import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Regrowth;\n"
    "import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;\n"
    "import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;\n"
    "\t\t\t\t\t\t//same as a healing potion\n"
    "\t\t\t\t\t\tPotionOfHealing.cure(ch);\n"
    "\t\t\t\t\t\tPotionOfHealing.heal(ch);\n"
    "GameScene.add( Blob.seed( i, 10, Regrowth.class ) );\n"
)


def main() -> None:
    java = JAVA.read_text(encoding="utf-8")
    assert "target != Dungeon.hero && target.alignment != Char.Alignment.ALLY" in java
    assert "protected boolean shouldRoot(Char target)" in java

    with tempfile.TemporaryDirectory() as temporary:
        regrowth = Path(temporary) / "Regrowth.java"
        bomb = Path(temporary) / "RegrowthBomb.java"
        regrowth.write_text(REGROWTH, encoding="utf-8")
        bomb.write_text(BOMB, encoding="utf-8")
        command = [sys.executable, str(SCRIPT), str(regrowth), str(bomb)]
        subprocess.run(command, check=True, capture_output=True, text=True)

        shared = regrowth.read_text(encoding="utf-8")
        special = bomb.read_text(encoding="utf-8")
        assert "return true;" in shared
        assert "&& !ch.isImmune(this.getClass())" in shared
        assert "&& shouldRoot(ch)" in shared
        assert "Blob.seed( i, 10, BombRegrowth.class )" in special
        assert "import com.spd.blast.BombRegrowth;" in special
        assert "PotionOfHealing.cure(ch);" in special
        assert "Buff.affect(ch, Sungrass.Health.class).boost((int) (0.8f * ch.HT + 14));" in special
        assert "if (ch == Dungeon.hero && Dungeon.isChallenged(Challenges.NO_HEALING))" in special
        assert special.count("PotionOfHealing.heal(ch);") == 1
        assert "//same as a healing potion" not in special

        # Reapplying to patched upstream must fail rather than silently pass.
        assert subprocess.run(command, capture_output=True).returncode != 0
        # An upstream change must fail without partially patching either file.
        regrowth.write_text(REGROWTH, encoding="utf-8")
        changed = BOMB.replace("PotionOfHealing.heal(ch);", "PotionOfHealing.otherHeal(ch);")
        bomb.write_text(changed, encoding="utf-8")
        assert subprocess.run(command, capture_output=True).returncode != 0
        assert regrowth.read_text(encoding="utf-8") == REGROWTH
        assert bomb.read_text(encoding="utf-8") == changed
    print("Regrowth bomb integration checks passed")


if __name__ == "__main__":
    main()
