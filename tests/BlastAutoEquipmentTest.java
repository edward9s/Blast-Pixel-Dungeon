package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Camouflage;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Shortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Blazing;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;

/** Automatic equipment checks true upgrade levels even for unidentified gear. */
public final class BlastAutoEquipmentTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Dagger plain = new Dagger();
        Dagger upgraded = new Dagger();
        upgraded.level(6);
        check(!plain.levelKnown && !upgraded.levelKnown, "Gear must remain unidentified");
        check(BlastAutoEquipment.weaponRating(upgraded)
                        > BlastAutoEquipment.weaponRating(plain),
                "Actual upgrades must affect weapon selection without identification");
        check(upgraded.STRReq(upgraded.trueLevel()) < plain.STRReq(plain.trueLevel()),
                "Strength requirement must use the real upgrade level");

        Shortsword higherTier = new Shortsword();
        check(BlastAutoEquipment.weaponRating(upgraded)
                        > BlastAutoEquipment.weaponRating(higherTier),
                "High-level weapons may outrank a higher tier's base stats");

        LeatherArmor basicArmor = new LeatherArmor();
        LeatherArmor upgradedArmor = new LeatherArmor();
        upgradedArmor.level(5);
        check(BlastAutoEquipment.armorRating(upgradedArmor)
                        > BlastAutoEquipment.armorRating(basicArmor),
                "Actual upgrades must affect armor selection without identification");

        BrokenSeal seal = new BrokenSeal();
        seal.level(1);
        check(BlastAutoEquipment.armorRatingWithSeal(basicArmor, seal)
                        == basicArmor.DRMin(basicArmor.trueLevel() + 1)
                            + basicArmor.DRMax(basicArmor.trueLevel() + 1),
                "A transferred upgraded seal must affect candidate armor rating");
        check(BlastAutoEquipment.armorRatingWithSeal(basicArmor, null)
                        == BlastAutoEquipment.armorRating(basicArmor),
                "Armor without a seal uses its actual defensive rating");

        int requiredWithoutSeal = basicArmor.STRReq(basicArmor.trueLevel());
        int requiredWithSeal = basicArmor.STRReq(basicArmor.trueLevel() + 1);
        check(BlastAutoEquipment.canWearArmor(basicArmor, requiredWithSeal, seal),
                "Wearability must account for the level gained from a transferred seal");
        check(BlastAutoEquipment.canWearArmor(basicArmor, requiredWithoutSeal, null),
                "Wearability must use effective hero strength, including ring bonuses");
        check(!BlastAutoEquipment.canWearArmor(basicArmor, requiredWithoutSeal - 1, null),
                "Unmet actual strength requirements must reject armor");

        check(BlastAutoEquipment.canPickup(plain, false),
                "Non-cursed equipment needs no Alchemize");
        plain.cursed = true;
        check(!BlastAutoEquipment.canPickup(plain, false),
                "Cursed gear must be rejected without Alchemize");
        check(BlastAutoEquipment.canPickup(plain, true),
                "Sellable cursed gear may be collected when Alchemize is present");
        check(!BlastAutoEquipment.canPickup(new DriedRose(), true),
                "Artifacts must remain outside the automatic equipment policy");

        int insufficientStrength = plain.STRReq(plain.trueLevel()) - 1;
        check(BlastAutoEquipment.isUnwearableForSale(plain, insufficientStrength),
                "Unenchanted weapons above the real strength limit may be sold");
        check(!BlastAutoEquipment.isUnwearableForSale(plain, insufficientStrength + 1),
                "Weapons within the strength limit must be preserved");
        plain.enchant(new Blazing());
        check(!BlastAutoEquipment.isUnwearableForSale(plain, insufficientStrength),
                "Enchanted weapons must never be sold during strength cleanup");

        int insufficientArmorStrength = basicArmor.STRReq(basicArmor.trueLevel()) - 1;
        check(BlastAutoEquipment.isUnwearableForSale(basicArmor, insufficientArmorStrength),
                "Uninscribed armor above the real strength limit may be sold");
        check(!BlastAutoEquipment.isUnwearableForSale(basicArmor, insufficientArmorStrength + 1),
                "Armor within the strength limit must be preserved");
        basicArmor.inscribe(new Camouflage());
        check(!BlastAutoEquipment.isUnwearableForSale(basicArmor, insufficientArmorStrength),
                "Inscribed armor must never be sold during strength cleanup");
        check(!BlastAutoEquipment.isUnwearableForSale(new DriedRose(), 0),
                "Only weapons and armor are eligible for strength cleanup");

        System.out.println("BlastAutoEquipmentTest passed");
    }
}
