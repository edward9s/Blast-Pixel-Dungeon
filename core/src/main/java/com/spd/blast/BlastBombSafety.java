package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

/** Hero-only protection from harmful bomb effects. */
public final class BlastBombSafety {
    private BlastBombSafety() {}

    /** Both the temporary and testing Bombproof buffs protect the hero. */
    public static boolean protects(Char target) {
        return target != null && target == Dungeon.hero
                && (target.buff(BlastIndestructible.class) != null
                    || target.buff(BlastTestBuffs.Indestructible.class) != null);
    }
}
