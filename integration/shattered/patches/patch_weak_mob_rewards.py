#!/usr/bin/env python3
"""Apply optional low-level monster loot and non-level experience effects."""
from pathlib import Path
import sys


def patch(root: Path) -> None:
    changes: dict[Path, str] = {}

    def replace_once(relative: str, before: str, after: str) -> None:
        path = root / relative
        source = changes.get(path)
        if source is None:
            source = path.read_text(encoding="utf-8")
        count = source.count(before)
        if count != 1:
            raise SystemExit(f"{path}: expected one anchor, found {count}: {before!r}")
        changes[path] = source.replace(before, after, 1)

    hero = "actors/hero/Hero.java"
    replace_once(
        hero,
        "\tpublic void earnExp( int exp, Class source ) {",
        "\tpublic void earnExp( int exp, Class source ) {\n"
        "\t\tprocessExp(exp, source, true);\n"
        "\t}\n\n"
        "\tpublic void gainNonLevelExp( int exp, Class source ) {\n"
        "\t\tif (exp <= 0) throw new IllegalArgumentException(\"Non-level XP must be positive: \" + exp);\n"
        "\t\tprocessExp(exp, source, false);\n"
        "\t}\n\n"
        "\tprivate void processExp( int exp, Class source, boolean grantHeroExp ) {",
    )
    replace_once(
        hero,
        "\t\tif (source != AscensionChallenge.class) {\n\t\t\tthis.exp += exp;",
        "\t\tif (grantHeroExp && source != AscensionChallenge.class) {\n\t\t\tthis.exp += exp;",
    )
    replace_once(
        hero,
        "\t\twhile (this.exp >= maxExp()) {",
        "\t\twhile (grantHeroExp && this.exp >= maxExp()) {",
    )

    mob = "actors/mobs/Mob.java"
    replace_once(
        mob,
        "\t\t\t\tDungeon.hero.earnExp(exp, getClass());",
        "\t\t\t\tif (exp == 0 && EXP > 0 && maxLvl > 0\n"
        "\t\t\t\t\t\t&& Dungeon.hero.lvl > maxLvl\n"
        "\t\t\t\t\t\t&& com.spd.blast.BlastSettings.weakMobExpEffects()) {\n"
        "\t\t\t\t\tDungeon.hero.gainNonLevelExp(EXP, getClass());\n"
        "\t\t\t\t} else {\n"
        "\t\t\t\t\tDungeon.hero.earnExp(exp, getClass());\n"
        "\t\t\t\t}",
    )

    replace_once(
        "actors/buffs/AllyBuff.java",
        "\t\t\thero.earnExp(exp, enemy.getClass());",
        "\t\t\tif (exp == 0 && enemy.EXP > 0 && enemy.maxLvl > 0\n"
        "\t\t\t\t\t&& hero.lvl > enemy.maxLvl\n"
        "\t\t\t\t\t&& com.spd.blast.BlastSettings.weakMobExpEffects()) {\n"
        "\t\t\t\thero.gainNonLevelExp(enemy.EXP, enemy.getClass());\n"
        "\t\t\t} else {\n"
        "\t\t\t\thero.earnExp(exp, enemy.getClass());\n"
        "\t\t\t}",
    )

    capped = (
        "actors/mobs/Mob.java",
        "actors/mobs/CausticSlime.java",
        "actors/mobs/DM201.java",
        "actors/mobs/SpectralNecromancer.java",
        "actors/mobs/GnollExile.java",
    )
    for relative in capped:
        replace_once(
            relative,
            "\t\tif (Dungeon.hero.lvl > maxLvl + 2) return;",
            "\t\tif (Dungeon.hero.lvl > maxLvl + 2\n"
            "\t\t\t\t&& !com.spd.blast.BlastSettings.weakMobDrops()) return;",
        )

    replace_once(
        "actors/mobs/HermitCrab.java",
        "\t\tif (Dungeon.hero.lvl <= maxLvl + 2){",
        "\t\tif (Dungeon.hero.lvl <= maxLvl + 2\n"
        "\t\t\t\t|| com.spd.blast.BlastSettings.weakMobDrops()){",
    )
    replace_once(
        "items/artifacts/MasterThievesArmband.java",
        "\t\t\t\t\t\t\tif (Dungeon.hero.lvl > ((Mob) ch).maxLvl + 2) {",
        "\t\t\t\t\t\t\tif (Dungeon.hero.lvl > ((Mob) ch).maxLvl + 2\n"
        "\t\t\t\t\t\t\t\t\t&& !com.spd.blast.BlastSettings.weakMobDrops()) {",
    )

    # Refuse to write any source until every upstream anchor is validated.
    for path, content in changes.items():
        path.write_text(content, encoding="utf-8")
    print(f"patched {len(changes)} upstream files: low-level monster rewards")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_weak_mob_rewards.py <SPD Java source root>")
    patch(Path(sys.argv[1]))
