package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Regrowth;

/** Bomb Regrowth roots everyone except a Bombproof hero. */
public final class BombRegrowth extends Regrowth {

    @Override
    protected boolean shouldRoot(Char target) {
        return !BlastBombSafety.protects(target);
    }
}
