package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Regrowth;

/** Regrowth produced by bombs only roots hostile and neutral characters. */
public final class BombRegrowth extends Regrowth {

    @Override
    protected boolean shouldRoot(Char target) {
        return target != Dungeon.hero && target.alignment != Char.Alignment.ALLY;
    }
}
