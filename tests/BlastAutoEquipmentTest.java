package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Camouflage;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Brimstone;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Swiftness;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Obfuscation;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.AntiMagic;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Thorns;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Chilling;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Grim;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Lucky;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Projecting;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Vampiric;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Venomous;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Vorpal;
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
        check(BlastAutoEquipment.isUnwearableForSale(plain, insufficientStrength),
                "Nonpriority enchants must not block cleanup of unusable weapons");

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

        Dagger privileged = new Dagger();
        Weapon.Enchantment[] valuedEnchants = {
                new Grim(), new Chilling(), new Venomous(), new Vorpal(),
                new Vampiric(), new Projecting(), new Lucky()
        };
        for (Weapon.Enchantment enchantment : valuedEnchants) {
            privileged.enchant(enchantment);
            check(BlastAutoEquipment.isProtectedEquipment(privileged),
                    "Listed weapon enchantments must survive cleanup: " + enchantment.getClass());
            check(!BlastAutoEquipment.isUnwearableForSale(privileged,
                            privileged.STRReq(privileged.trueLevel()) - 1),
                    "Valued weapon enchantments must survive strength cleanup");
        }
        privileged.enchant(new Blazing());
        check(!BlastAutoEquipment.isProtectedEquipment(privileged),
                "Unlisted weapon enchantments should not block ordinary cleanup");
        privileged.enchant(null);
        privileged.augment = Weapon.Augment.SPEED;
        check(BlastAutoEquipment.isProtectedEquipment(privileged),
                "Stone-augmented weapons must be protected");

        LeatherArmor valuedArmor = new LeatherArmor();
        Armor.Glyph[] valuedGlyphs = {
                new Swiftness(), new Camouflage(), new Obfuscation(),
                new Brimstone(), new AntiMagic(), new Thorns()
        };
        for (Armor.Glyph glyph : valuedGlyphs) {
            valuedArmor.inscribe(glyph);
            check(BlastAutoEquipment.isProtectedEquipment(valuedArmor),
                    "Listed armor glyphs must survive cleanup: " + glyph.getClass());
            check(!BlastAutoEquipment.isUnwearableForSale(valuedArmor,
                            valuedArmor.STRReq(valuedArmor.trueLevel()) - 1),
                    "Valued armor glyphs must survive strength cleanup");
        }
        valuedArmor.inscribe(null);
        valuedArmor.augment = Armor.Augment.DEFENSE;
        check(BlastAutoEquipment.isProtectedEquipment(valuedArmor),
                "Stone-augmented armor must be protected");

        Dagger inferiorWeapon = new Dagger();
        Dagger superiorWeapon = new Dagger();
        superiorWeapon.level(4);
        check(BlastAutoEquipment.weaponDominates(superiorWeapon, inferiorWeapon),
                "Higher damage with equal speed, accuracy and range dominates");
        check(BlastAutoEquipment.isRedundant(inferiorWeapon, superiorWeapon),
                "Clearly inferior ordinary weapon should be eligible for cleanup");
        inferiorWeapon.RCH++;
        check(!BlastAutoEquipment.weaponDominates(superiorWeapon, inferiorWeapon),
                "Extra attack range prevents a weapon from being unambiguously dominated");
        inferiorWeapon.RCH--;
        inferiorWeapon.ACC *= 2;
        check(!BlastAutoEquipment.isRedundant(inferiorWeapon, superiorWeapon),
                "Better accuracy should protect an otherwise weaker weapon");
        inferiorWeapon.ACC /= 2;
        inferiorWeapon.DLY *= 0.5f;
        check(!BlastAutoEquipment.isRedundant(inferiorWeapon, superiorWeapon),
                "Faster attacks should protect an otherwise weaker weapon");
        check(!BlastAutoEquipment.isRedundant(privileged, superiorWeapon),
                "Augmented weapon cannot be marked redundant");

        LeatherArmor inferiorArmor = new LeatherArmor();
        LeatherArmor superiorArmor = new LeatherArmor();
        superiorArmor.level(4);
        check(BlastAutoEquipment.isRedundant(inferiorArmor, superiorArmor),
                "Inferior unprotected armor should be eligible for cleanup");
        inferiorArmor.augment = Armor.Augment.EVASION;
        check(!BlastAutoEquipment.isRedundant(inferiorArmor, superiorArmor),
                "Augmented evasion armor should be preserved");


        LeatherArmor oldInscribed = new LeatherArmor();
        oldInscribed.inscribe(new Camouflage());
        LeatherArmor newInscribed = new LeatherArmor();
        newInscribed.inscribe(new Brimstone());
        check(BlastAutoEquipment.hasGlyphConflict(oldInscribed, newInscribed),
                "Different old/new armor glyphs must trigger inscription protection");
        check(BlastAutoEquipment.isProtectedEquipment(oldInscribed),
                "Valued old glyph must prevent selling on replacement");

        LeatherArmor uninscribed = new LeatherArmor();
        check(!BlastAutoEquipment.hasGlyphConflict(oldInscribed, uninscribed),
                "An empty destination glyph has no competing inscription");
        check(BlastAutoEquipment.isProtectedEquipment(oldInscribed),
                "Valued old glyph must survive even when unavailable for transfer");

        LeatherArmor sameInscription = new LeatherArmor();
        sameInscription.inscribe(new Camouflage());
        check(!BlastAutoEquipment.hasGlyphConflict(oldInscribed, sameInscription),
                "Matching glyph types should not count as a conflict");
        check(BlastAutoEquipment.isProtectedEquipment(oldInscribed),
                "Valued glyph stays protected even when a replacement has the same glyph");
        check(!BlastAutoEquipment.isProtectedEquipment(uninscribed),
                "Uninscribed old armor does not require special protection");

        System.out.println("BlastAutoEquipmentTest passed");
    }
}
