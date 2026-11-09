package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Regrowth;

/** Regrowth produced by bombs roots all grounded characters. */
public final class BombRegrowth extends Regrowth {

    @Override
    protected boolean shouldRoot(Char target) {
        return true;
    }
}
