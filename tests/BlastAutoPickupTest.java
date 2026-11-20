package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Dewdrop;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.WornKey;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
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
        if (!BlastAutoPickup.knownLoot(false, true)
                || !BlastAutoPickup.knownLoot(true, false)
                || BlastAutoPickup.knownLoot(false, false)) {
            throw new AssertionError("Visible loot must be eligible even if its seen flag is stale");
        }

        check(true, new EnergyCrystal());
        check(true, new DriedRose.Petal());
        check(true, new GoldenKey());
        check(true, new IronKey());
        check(true, new CrystalKey());
        check(true, new WornKey());
        check(true, new Sungrass.Seed());
        check(true, new ScrollOfIdentify());
        check(true, new PotionOfHealing());
        check(true, new Runestone.PlaceHolder());
        check(true, new BlastPhasePotion());
        check(true, new BlastBombproofScroll());
        check(true, new Bomb());

        // A non-pickable item on top must not hide collectible loot below it.
        Heap heap = new Heap();
        Item covered = new DriedRose();
        Item gold = new Gold();
        heap.items.add(covered);
        heap.items.add(gold);
        Item eligible = BlastAutoPickup.firstEligibleItem(heap,
                item -> BlastAutoPickup.isPickupType(item, BLAST_INSTANT));
        if (eligible != gold) {
            throw new AssertionError("Eligible loot beneath other items was skipped");
        }
        BlastAutoPickup.exposeForPickup(heap, eligible);
        if (heap.peek() != gold || heap.items.size() != 2
                || !heap.items.contains(covered)) {
            throw new AssertionError("Exposing buried loot must preserve the heap");
        }
        if (BlastAutoPickup.firstEligibleItem(heap, item -> false) != null) {
            throw new AssertionError("Ineligible heaps must remain unselected");
        }
        // Capacity restrictions can also reject an otherwise auto-pickable top item.
        heap.items.addFirst(new PotionOfHealing());
        if (BlastAutoPickup.firstEligibleItem(heap, item -> item == gold) != gold) {
            throw new AssertionError("Rejected top items must not block lower loot");
        }

        Item radius = new BlastRadiusPickup();
        Item haste = new BlastHastePickup();
        if (radius.stackable || haste.stackable) {
            throw new AssertionError("Instant Blast pickups must stay non-stackable");
        }
        check(true, radius);
        check(true, haste);

        // Keys are journal entries and remain collectible with a full backpack.
        for (Item key : new Item[]{new GoldenKey(), new IronKey(),
                new CrystalKey(), new WornKey()}) {
            if (!BlastAutoPickup.bypassesBackpack(key, BLAST_INSTANT)) {
                throw new AssertionError("Key incorrectly needs backpack space: "
                        + key.getClass().getName());
            }
        }
        if (!BlastAutoPickup.bypassesBackpack(new Gold(), BLAST_INSTANT)
                || !BlastAutoPickup.bypassesBackpack(new EnergyCrystal(), BLAST_INSTANT)
                || !BlastAutoPickup.bypassesBackpack(new DriedRose.Petal(), BLAST_INSTANT)
                || BlastAutoPickup.bypassesBackpack(new Bomb(), BLAST_INSTANT)) {
            throw new AssertionError("Incorrect backpack bypass rule");
        }

        // Petals only upgrade a rose that is present and below its native level cap.
        DriedRose rose = new DriedRose();
        if (BlastAutoPickup.canUpgradeRose(null)
                || !BlastAutoPickup.canUpgradeRose(rose)) {
            throw new AssertionError("Rose petal eligibility must require an upgradeable rose");
        }
        rose.level(10);
        if (BlastAutoPickup.canUpgradeRose(rose)) {
            throw new AssertionError("A max-level rose must not attract auto-pickup");
        }

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
