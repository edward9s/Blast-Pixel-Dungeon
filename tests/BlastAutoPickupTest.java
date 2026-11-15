package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;

/** Headless type allowlist regression checks. */
public final class BlastAutoPickupTest {
    private static void check(boolean expected, com.shatteredpixel.shatteredpixeldungeon.items.Item item) {
        if (BlastAutoPickup.isPickupType(item) != expected) {
            throw new AssertionError("Unexpected pickup eligibility: " + item.getClass().getName());
        }
    }

    public static void main(String[] args) {
        check(true, new Dewdrop());
        check(true, new Gold());
        check(true, new Sungrass.Seed());
        check(true, new ScrollOfIdentify());
        check(true, new PotionOfHealing());
        check(true, new Runestone.PlaceHolder());
        check(false, new Bomb());
        System.out.println("BlastAutoPickupTest passed");
    }
}
