package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Fire;

/** Fire from a bomb; independent of environmental fire. */
public final class BombFire extends Fire {
    @Override
    protected boolean shouldBurn(Char target) {
        return !BlastBombSafety.protects(target);
    }
}
