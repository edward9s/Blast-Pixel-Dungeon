package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;

/** Blast-only exception for non-stackable pickups consumed on collection. */
public final class BlastAutoPickupPolicy {
    private BlastAutoPickupPolicy() {}

    public static boolean isInstantPickup(Item item) {
        return item instanceof BlastPickup;
    }
}
