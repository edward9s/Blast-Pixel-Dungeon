package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Shortsword;
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

        check(BlastAutoEquipment.canPickup(plain, false),
                "Non-cursed equipment needs no Alchemize");
        plain.cursed = true;
        check(!BlastAutoEquipment.canPickup(plain, false),
                "Cursed gear must be rejected without Alchemize");
        check(BlastAutoEquipment.canPickup(plain, true),
                "Sellable cursed gear may be collected when Alchemize is present");
        check(!BlastAutoEquipment.canPickup(new DriedRose(), true),
                "Artifacts must remain outside the automatic equipment policy");

        System.out.println("BlastAutoEquipmentTest passed");
    }
}
