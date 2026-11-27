package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.LeatherArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.WarriorArmor;
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
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Cudgel;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Glaive;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Gloves;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Greatsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Crystal;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Shortsword;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Blazing;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.MailArmor;

import java.util.Arrays;
import java.util.Collections;

/** Automatic equipment checks true upgrade levels even for unidentified gear. */
public final class BlastAutoEquipmentTest {

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void testStarterSale() {
        Hero hero = new Hero();
        Dagger replacement = new Dagger();
        replacement.level(2);
        hero.belongings.weapon = replacement;
        Gloves gloves = new Gloves();
        Cudgel cudgel = new Cudgel();

        check(BlastAutoEquipment.isSaleCandidate(hero, gloves)
                        && BlastAutoEquipment.isSaleCandidate(hero, cudgel),
                "A weaker plain starter can be sold after switching to a better weapon");

        gloves.level(1);
        cudgel.enchant(new Grim());
        check(!BlastAutoEquipment.isSaleCandidate(hero, gloves)
                        && !BlastAutoEquipment.isSaleCandidate(hero, cudgel),
                "Upgraded or protected starters must retain the usual spare comparison");

        Gloves stronger = new Gloves();
        stronger.level(5);
        hero.belongings.weapon = new Gloves();
        check(!BlastAutoEquipment.isSaleCandidate(hero, stronger),
                "A stronger starter must remain available to equip");
    }

    private static void testReachUpgrade() {
        Hero hero = new Hero();
        Dagger worn = new Dagger();
        worn.level(1);
        hero.belongings.weapon = worn;
        Dagger candidate = new Dagger();
        candidate.DLY = 0.75f;
        check(!BlastAutoEquipment.useLastSlot(hero, candidate),
                "Speed alone should leave this lower-damage candidate behind");
        candidate.RCH = 2;
        check(BlastAutoEquipment.useLastSlot(hero, candidate),
                "The opening attack should turn the same candidate into an upgrade");
    }

    private static void testEngagementScore() {
        double normal = BlastAutoEquipment.engagementScore(20, 1, 1, 1);
        check(Math.abs(normal - 36) < 0.0001, "A reference weapon should hit half the time");
        check(Math.abs(normal - BlastAutoEquipment.engagementScore(10, 0.5, 1, 1)) < 0.0001,
                "Twice the speed should offset half the damage");
        check(Math.abs(normal - BlastAutoEquipment.engagementScore(40, 1, 0.5, 1)) < 0.0001,
                "Accuracy should affect expected damage rather than veto the comparison");
        check(BlastAutoEquipment.engagementScore(16, 1, 2, 1) > normal,
                "An accurate weapon may beat a harder-hitting option");
        check(BlastAutoEquipment.engagementScore(18, 1, 1, 2) > normal,
                "An extra cell of reach can offset a small damage deficit");
        check(BlastAutoEquipment.engagementScore(18, 1, 1, 3)
                        > BlastAutoEquipment.engagementScore(18, 1, 1, 2),
                "A longer opening should add another attack opportunity");
        check(BlastAutoEquipment.engagementScore(10, 1, 1, 3) < normal,
                "Reach alone should not offset a large damage deficit");
    }

    public static void main(String[] args) {
        testEngagementScore();
        testReachUpgrade();
        testStarterSale();
        Dagger plain = new Dagger();
        Dagger upgraded = new Dagger();
        upgraded.level(6);
        check(!plain.levelKnown && !upgraded.levelKnown, "Gear must remain unidentified");
        check(BlastAutoEquipment.weaponScore(upgraded, 12)
                        > BlastAutoEquipment.weaponScore(plain, 12),
                "Actual upgrades must affect weapon selection without identification");
        check(upgraded.STRReq(upgraded.trueLevel()) < plain.STRReq(plain.trueLevel()),
                "Strength requirement must use the real upgrade level");

        Shortsword higherTier = new Shortsword();
        check(BlastAutoEquipment.weaponScore(upgraded, 12)
                        > BlastAutoEquipment.weaponScore(higherTier, 12),
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
        superiorWeapon.DLY = 1.1f;
        superiorWeapon.ACC = 0.9f;
        check(BlastAutoEquipment.isRedundant(inferiorWeapon, superiorWeapon, 20),
                "A clear expected damage advantage can offset lower speed and accuracy");

        Dagger longerWeapon = new Dagger();
        longerWeapon.RCH = 2;
        Dagger modestReplacement = new Dagger();
        modestReplacement.level(1);
        check(!BlastAutoEquipment.isRedundant(longerWeapon, modestReplacement, 20),
                "A small lead against a longer weapon should keep the backup");
        check(BlastAutoEquipment.isRedundant(inferiorWeapon, new Dagger(), 20),
                "Identical ordinary weapons remain sellable");

        Greatsword dominantGreatsword = new Greatsword();
        dominantGreatsword.level(14);
        dominantGreatsword.enchant(new Crystal());
        Glaive obsoleteGlaive = new Glaive();
        check(BlastAutoEquipment.isRedundant(obsoleteGlaive, dominantGreatsword, 20),
                "A much stronger greatsword should outweigh the glaive's reach");
        check(!BlastAutoEquipment.worthPickingWithoutAlchemize(obsoleteGlaive,
                        Collections.<Item>singletonList(dominantGreatsword), null, 20),
                "An overwhelmingly weaker floor weapon may be skipped without Alchemize");

        obsoleteGlaive.DLY = 0.3f;
        check(!BlastAutoEquipment.isRedundant(obsoleteGlaive, dominantGreatsword, 20),
                "A sufficiently fast longer weapon can still justify a slot");
        obsoleteGlaive.DLY = 1.5f;
        obsoleteGlaive.enchant(new Grim());
        check(!BlastAutoEquipment.isRedundant(obsoleteGlaive, dominantGreatsword, 20),
                "Protected weapon enchantments override numeric obsolescence");
        obsoleteGlaive.enchant(null);
        obsoleteGlaive.augment = Weapon.Augment.DAMAGE;
        check(!BlastAutoEquipment.isRedundant(obsoleteGlaive, dominantGreatsword, 20),
                "Stone-augmented weapons must not be sold automatically");
        check(!BlastAutoEquipment.isRedundant(privileged, superiorWeapon, 20),
                "Augmented weapon cannot be marked redundant");

        LeatherArmor inferiorArmor = new LeatherArmor();
        LeatherArmor superiorArmor = new LeatherArmor();
        LeatherArmor equalArmor = new LeatherArmor();
        check(BlastAutoEquipment.isRedundant(equalArmor, inferiorArmor, 20),
                "Ordinary spare armor with equal defense should be eligible for cleanup");
        equalArmor.augment = Armor.Augment.EVASION;
        check(!BlastAutoEquipment.isRedundant(equalArmor, inferiorArmor, 20),
                "Equal-defense augmented armor should be preserved");
        superiorArmor.level(4);
        check(BlastAutoEquipment.isRedundant(inferiorArmor, superiorArmor, 20),
                "Inferior unprotected armor should be eligible for cleanup");
        inferiorArmor.augment = Armor.Augment.EVASION;
        check(!BlastAutoEquipment.isRedundant(inferiorArmor, superiorArmor, 20),
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

        // Without Alchemize, redundant floor equipment must not fill the backpack.
        Dagger wornWeapon = new Dagger();
        wornWeapon.level(5);
        Dagger weakFloorWeapon = new Dagger();
        check(!BlastAutoEquipment.worthPickingWithoutAlchemize(weakFloorWeapon,
                        Collections.<Item>singletonList(wornWeapon), null, 12),
                "Never collect a dominated low-tier weapon without Alchemize");
        check(!BlastAutoEquipment.worthPickingWithoutAlchemize(weakFloorWeapon,
                        Collections.<Item>singletonList(new Dagger()), null, 12),
                "Identical weapon duplicates must be rejected");
        Dagger strongerFloorWeapon = new Dagger();
        strongerFloorWeapon.level(6);
        check(BlastAutoEquipment.worthPickingWithoutAlchemize(strongerFloorWeapon,
                        Collections.<Item>singletonList(wornWeapon), null, 12),
                "Genuine weapon upgrades must still be collectible");
        check(BlastAutoEquipment.worthPickingWithoutAlchemize(weakFloorWeapon,
                        Collections.<Item>emptyList(), null, 12),
                "The first available weapon remains collectible");

        Dagger fasterFloorWeapon = new Dagger();
        fasterFloorWeapon.DLY = 0.35f;
        check(BlastAutoEquipment.worthPickingWithoutAlchemize(fasterFloorWeapon,
                        Collections.<Item>singletonList(wornWeapon), null, 12),
                "A faster but weaker weapon is a valid tactical alternative");
        Dagger cursedFloorWeapon = new Dagger();
        cursedFloorWeapon.enchant(new Grim());
        cursedFloorWeapon.cursed = true;
        check(!BlastAutoEquipment.worthPickingWithoutAlchemize(cursedFloorWeapon,
                        Collections.<Item>emptyList(), null, 12),
                "Cursed weapons remain excluded even with valuable enchantments");
        Dagger rareFloorWeapon = new Dagger();
        rareFloorWeapon.enchant(new Grim());
        check(BlastAutoEquipment.worthPickingWithoutAlchemize(rareFloorWeapon,
                        Collections.<Item>singletonList(wornWeapon), null, 12),
                "Valuable enchantments still justify picking weaker weapons");

        LeatherArmor wornArmor = new LeatherArmor();
        wornArmor.level(5);
        LeatherArmor weakFloorArmor = new LeatherArmor();
        check(!BlastAutoEquipment.worthPickingWithoutAlchemize(weakFloorArmor,
                        Collections.<Item>singletonList(wornArmor), wornArmor, 12),
                "Never collect inferior armor when a superior one is equipped");
        check(!BlastAutoEquipment.worthPickingWithoutAlchemize(weakFloorArmor,
                        Collections.<Item>singletonList(new LeatherArmor()), null, 12),
                "Identical armor duplicates must be rejected");
        LeatherArmor strongerFloorArmor = new LeatherArmor();
        strongerFloorArmor.level(6);
        check(BlastAutoEquipment.worthPickingWithoutAlchemize(strongerFloorArmor,
                        Collections.<Item>singletonList(wornArmor), wornArmor, 12),
                "Genuine armor upgrades remain collectible");
        weakFloorArmor.inscribe(new Camouflage());
        check(BlastAutoEquipment.worthPickingWithoutAlchemize(weakFloorArmor,
                        Collections.<Item>singletonList(wornArmor), wornArmor, 12),
                "Valuable armor glyphs must survive pickup filtering");
        weakFloorArmor.cursed = true;
        check(!BlastAutoEquipment.worthPickingWithoutAlchemize(weakFloorArmor,
                        Collections.<Item>singletonList(wornArmor), wornArmor, 12),
                "Cursed armor must never pass the no-Alchemize selection");

        MailArmor tooHeavyArmor = new MailArmor();
        LeatherArmor wearableFloorArmor = new LeatherArmor();
        int wearableStrength = wearableFloorArmor.STRReq(wearableFloorArmor.trueLevel());
        check(tooHeavyArmor.STRReq(tooHeavyArmor.trueLevel()) > wearableStrength,
                "The ownership comparison requires a genuinely unwearable reference");
        check(BlastAutoEquipment.worthPickingWithoutAlchemize(wearableFloorArmor,
                        Arrays.<Item>asList(tooHeavyArmor), null, wearableStrength),
                "A heavy future upgrade must not hide the only wearable armor");

        // Crown-upgraded class armor must stay equipped so the hero's armor
        // ability remains available after an automatic defense upgrade.
        WarriorArmor classArmor = new WarriorArmor();
        classArmor.charge = 73f;
        check(BlastAutoEquipment.isProtectedEquipment(classArmor),
                "Class armor itself must always be retained");
        check(!BlastAutoEquipment.isRedundant(classArmor, superiorArmor, 20),
                "Class armor cannot be discarded as an ordinary inferior armor");

        classArmor.inscribe(new Camouflage());
        LeatherArmor differentValuedGlyph = new LeatherArmor();
        differentValuedGlyph.inscribe(new Brimstone());
        check(BlastAutoEquipment.wouldLoseProtectedClassGlyph(
                        classArmor, differentValuedGlyph),
                "Auto transfer must not erase a valuable existing class-armor glyph");
        check(BlastAutoEquipment.wouldLoseProtectedClassGlyph(
                        classArmor, new LeatherArmor()),
                "No-seal transfer must not clear a valuable class-armor glyph");
        LeatherArmor sameValuedGlyph = new LeatherArmor();
        sameValuedGlyph.inscribe(new Camouflage());
        check(!BlastAutoEquipment.wouldLoseProtectedClassGlyph(
                        classArmor, sameValuedGlyph),
                "Matching valuable glyphs permit class-armor stat transfer");

        WarriorArmor plainClassArmor = new WarriorArmor();
        check(!BlastAutoEquipment.wouldLoseProtectedClassGlyph(
                        plainClassArmor, differentValuedGlyph),
                "Class armor without a protected glyph may inherit a better glyph");

        System.out.println("BlastAutoEquipmentTest passed");
    }
}
