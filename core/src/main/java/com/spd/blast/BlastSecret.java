package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;

/** Secret gesture requires empty equipment slots and no Waterskin or Velvet Pouch in inventory. */
public final class BlastSecret {
    private BlastSecret() {}

    public static boolean canToggleTests() {
        Hero hero = Dungeon.hero;
        if (hero == null || !hero.isAlive() || Dungeon.level == null) return false;
        if (hero.belongings.weapon != null || hero.belongings.secondWep != null
                || hero.belongings.armor != null || hero.belongings.artifact != null
                || hero.belongings.misc != null || hero.belongings.ring != null) return false;
        return hero.belongings.getItem(Waterskin.class) == null
                && hero.belongings.getItem(VelvetPouch.class) == null;
    }
}
