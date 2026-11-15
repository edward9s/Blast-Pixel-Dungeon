package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;

import java.util.function.Predicate;

/** Automatic pickup: stackables plus separate, Blast-only instant pickups. */
public final class BlastAutoPickupTest {
    private static final Predicate<Item> BLAST_INSTANT = BlastAutoPickupPolicy::isInstantPickup;

    private static void check(boolean expected, Item item) {
        if (BlastAutoPickup.isPickupType(item, BLAST_INSTANT) != expected) {
            throw new AssertionError("Incorrect pickup selection for " + item.getClass().getName());
        }
    }

    public static void main(String[] args) {
        check(true, new Dewdrop());
        check(true, new Gold());
        check(true, new Sungrass.Seed());
        check(true, new ScrollOfIdentify());
        check(true, new PotionOfHealing());
        check(true, new Runestone.PlaceHolder());
        check(true, new BlastPhasePotion());
        check(true, new BlastBombproofScroll());
        check(true, new Bomb());

        Item radius = new BlastRadiusPickup();
        Item haste = new BlastHastePickup();
        if (radius.stackable || haste.stackable) {
            throw new AssertionError("Instant Blast pickups must stay non-stackable");
        }
        check(true, radius);
        check(true, haste);

        // Portable auto-pickup with no Blast policy excludes both non-stackables.
        Predicate<Item> noInstantPickups = item -> false;
        if (BlastAutoPickup.isPickupType(radius, noInstantPickups)
                || BlastAutoPickup.isPickupType(haste, noInstantPickups)) {
            throw new AssertionError("Blast instant pickups leaked into generic selection");
        }
        if (BlastAutoPickupPolicy.isInstantPickup(new Bomb())
                || BlastAutoPickupPolicy.isInstantPickup(new Gold())) {
            throw new AssertionError("Only BlastPickup items bypass inventory capacity");
        }
        System.out.println("BlastAutoPickupTest passed");
    }
}
