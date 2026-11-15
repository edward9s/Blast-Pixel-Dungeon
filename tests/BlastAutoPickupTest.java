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
        check(true, new BlastRadiusPickup());
        check(true, new BlastHastePickup());
        check(true, new BlastPhasePotion());
        check(true, new BlastBombproofScroll());
        check(true, new Sungrass.Seed());
        check(true, new ScrollOfIdentify());
        check(true, new PotionOfHealing());
        check(true, new Runestone.PlaceHolder());
        check(false, new Bomb());

        // The two Blast pickups consume instantly despite being non-stackable.
        if (new BlastRadiusPickup().stackable || new BlastHastePickup().stackable) {
            throw new AssertionError("Blast floor upgrades must remain non-stackable");
        }
        if (!BlastAutoPickup.bypassesBackpack(new BlastRadiusPickup())
                || !BlastAutoPickup.bypassesBackpack(new BlastHastePickup())
                || !BlastAutoPickup.bypassesBackpack(new Gold())
                || BlastAutoPickup.bypassesBackpack(new BlastPhasePotion())
                || BlastAutoPickup.bypassesBackpack(new BlastBombproofScroll())) {
            throw new AssertionError("Incorrect backpack space policy");
        }
        // Bombs are stackable, but are not eligible for automatic pickup.
        if (!new Bomb().stackable) {
            throw new AssertionError("Expected SPD Bomb to be stackable");
        }
        System.out.println("BlastAutoPickupTest passed");
    }
}
