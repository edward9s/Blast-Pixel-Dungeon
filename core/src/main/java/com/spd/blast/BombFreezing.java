package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Freezing;

/** Freezing from a bomb; independent of environmental freezing. */
public final class BombFreezing extends Freezing {
    @Override
    protected boolean shouldFreeze(Char target) {
        return !BlastBombSafety.protects(target);
    }
}
