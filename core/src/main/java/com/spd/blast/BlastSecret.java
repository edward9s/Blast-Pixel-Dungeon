package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Waterskin;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.VelvetPouch;

/** Exact debug-switch easter egg; both bags must have been placed on this floor. */
public final class BlastSecret {
    private BlastSecret() {}

    public static boolean canToggleTests() {
        Hero hero = Dungeon.hero;
        if (hero == null || !hero.isAlive() || Dungeon.level == null) return false;
        if (hero.belongings.weapon != null || hero.belongings.secondWep != null
                || hero.belongings.armor != null || hero.belongings.artifact != null
                || hero.belongings.misc != null || hero.belongings.ring != null) return false;
        if (hero.belongings.getItem(Waterskin.class) != null
                || hero.belongings.getItem(VelvetPouch.class) != null) return false;

        boolean water = false;
        boolean pouch = false;
        for (Heap heap : Dungeon.level.heaps.valueList()) {
            for (Item item : heap.items) {
                if (item instanceof Waterskin) water = true;
                if (item instanceof VelvetPouch) pouch = true;
            }
        }
        return water && pouch;
    }
}
