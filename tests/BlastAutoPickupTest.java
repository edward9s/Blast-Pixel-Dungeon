package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;

/** Pickup selection follows Item.stackable, without an item type allowlist. */
public final class BlastAutoPickupTest {
    private static void check(Item item) {
        if (BlastAutoPickup.isPickupType(item) != item.stackable) {
            throw new AssertionError("Stackability eligibility mismatch: "
                    + item.getClass().getName());
        }
    }

    public static void main(String[] args) {
        check(new Dewdrop());
        check(new Gold());
        check(new Sungrass.Seed());
        check(new ScrollOfIdentify());
        check(new PotionOfHealing());
        check(new Runestone.PlaceHolder());
        check(new BlastPhasePotion());
        check(new BlastBombproofScroll());
        check(new Bomb());

        // These two pickups are instant-consumption, non-stackable items,
        // so they are excluded without changing how their drops behave.
        check(new BlastRadiusPickup());
        check(new BlastHastePickup());
        if (BlastAutoPickup.isPickupType(new BlastRadiusPickup())
                || BlastAutoPickup.isPickupType(new BlastHastePickup())) {
            throw new AssertionError("Non-stackable drops must not be auto-picked");
        }
        if (!BlastAutoPickup.isPickupType(new Bomb())) {
            throw new AssertionError("Stackable bombs must be eligible");
        }
        System.out.println("BlastAutoPickupTest passed");
    }
}
