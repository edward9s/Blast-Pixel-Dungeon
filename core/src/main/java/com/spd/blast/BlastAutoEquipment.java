package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.BrokenSeal;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.Alchemize;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Chilling;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Grim;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Lucky;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Projecting;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Vampiric;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Venomous;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.enchantments.Vorpal;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.AntiMagic;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Brimstone;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Camouflage;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Obfuscation;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Swiftness;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.glyphs.Thorns;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Random;

import java.util.ArrayList;

/** Idle equipment maintenance, restricted to melee weapons and armor. */
final class BlastAutoEquipment {

    private static final ThreadLocal<Boolean> automaticItemUse = new ThreadLocal<>();
    private static boolean pendingStrengthCleanup;

    private BlastAutoEquipment() {}

    static void reset() {
        pendingStrengthCleanup = false;
    }

    static boolean isUsingAutomatically() {
        return Boolean.TRUE.equals(automaticItemUse.get());
    }

    private static boolean isCursed(Item item) {
        if (item.cursed) return true;
        if (item instanceof MeleeWeapon) {
            return ((MeleeWeapon) item).hasCurseEnchant();
        }
        return item instanceof Armor && ((Armor) item).hasCurseGlyph();
    }

    private static Alchemize availableAlchemize(Hero hero) {
        if (hero.buff(MagicImmune.class) != null) return null;
        Alchemize spell = hero.belongings.getItem(Alchemize.class);
        return spell != null && spell.quantity() > 0 ? spell : null;
    }

    private enum Pickup { SKIP, KEEP, SELL }

    static boolean canPickup(Hero hero, Item item) {
        return pickup(hero, item) != Pickup.SKIP;
    }

    /** Save the last backpack slot unless the gear can be equipped, sold, or is valuable. */
    static boolean useLastSlot(Hero hero, Item item) {
        if (!(item instanceof MeleeWeapon || item instanceof Armor)) return true;
        if (pickup(hero, item) == Pickup.SELL || isProtectedEquipment(item)) return true;
        return isUpgrade(hero, item);
    }

    private static boolean isUpgrade(Hero hero, Item item) {
        if (item instanceof MeleeWeapon) {
            return betterWeapon(hero, (MeleeWeapon) item, hero.belongings.weapon());
        }
        return item instanceof Armor && betterArmor(hero, (Armor) item, hero.belongings.armor());
    }

    static boolean canMakeRoom(Hero hero, Item item) {
        if (!isEquipment(item) || hero.belongings.lostInventory()
                || availableAlchemize(hero) == null) return false;
        return spareForPickup(hero, item, pickup(hero, item)) != null;
    }

    private static boolean isEquipment(Item item) {
        return item instanceof MeleeWeapon || item instanceof Armor;
    }

    /** Find a spare to remove from the main backpack before pickup. */
    private static Item spareForPickup(Hero hero, Item item, Pickup decision) {
        Bag backpack = hero.belongings.backpack;
        if (backpack.items.size() != backpack.capacity()) return null;
        Item upgrade = decision == Pickup.KEEP && isUpgrade(hero, item) ? item : null;
        if (upgrade == null && decision != Pickup.SELL) return null;

        for (Item spare : backpack.items) {
            if (canSellSpare(hero, spare, upgrade)) return spare;
        }
        return null;
    }

    /** Reserve space only during native pickup, so failed pickups leave the inventory intact. */
    static boolean pickUp(Hero hero, Item item) {
        if (!isEquipment(item) || hero.belongings.lostInventory()) return item.doPickUp(hero);
        Alchemize spell = availableAlchemize(hero);
        if (spell == null) return item.doPickUp(hero);

        Pickup decision = pickup(hero, item);
        Bag backpack = hero.belongings.backpack;
        Item spare = spareForPickup(hero, item, decision);
        int slot = -1;
        if (spare != null) {
            slot = Dungeon.quickslot.getSlot(spare);
            spare.detachAll(backpack);
        }
        if (!item.doPickUp(hero)) {
            if (spare != null) {
                if (!spare.collect(backpack)) {
                    throw new IllegalStateException("Could not restore spare after failed pickup");
                }
                if (slot >= 0) Dungeon.quickslot.setSlot(slot, spare);
            }
            return false;
        }

        // Accumulate ordinary resale loot while there is room. Cursed gear is sold immediately.
        if (spare != null || (decision == Pickup.SELL && isCursed(item))) {
            Item upgrade = decision == Pickup.KEEP ? item : null;
            ArrayList<Item> batch = saleBatch(hero, upgrade);
            if (decision == Pickup.SELL && !batch.contains(item)) batch.add(item);
            sellBatch(hero, spell, batch, spare);
        }
        return true;
    }

    private static Pickup pickup(Hero hero, Item item) {
        if (!(item instanceof MeleeWeapon || item instanceof Armor)) return Pickup.SKIP;
        boolean canSell = !hero.belongings.lostInventory() && availableAlchemize(hero) != null
                && Shopkeeper.canSell(item);
        if (isCursed(item)) return canSell ? Pickup.SELL : Pickup.SKIP;
        if (worthPickingWithoutAlchemize(
                item, hero.belongings, hero.belongings.armor(), hero.STR())) return Pickup.KEEP;
        return canSell && isSaleCandidate(hero, item) ? Pickup.SELL : Pickup.SKIP;
    }

    static boolean blocksCursedPickup(Hero hero, Item item) {
        return (item instanceof MeleeWeapon || item instanceof Armor) && isCursed(item)
                && !canPickup(item, !hero.belongings.lostInventory()
                        && availableAlchemize(hero) != null);
    }

    /** A cursed item is collected only when it can be sold using Alchemize. */
    static boolean canPickup(Item item, boolean alchemizeAvailable) {
        if (!(item instanceof MeleeWeapon || item instanceof Armor)) return false;
        return !isCursed(item) || (alchemizeAvailable && Shopkeeper.canSell(item));
    }

    /**
     * Without Alchemize, only collect valuable effects or equipment that adds a
     * real option beyond the gear already worn or carried. A currently wearable
     * candidate is not blocked by an unusably heavy item in the backpack.
     */
    static boolean worthPickingWithoutAlchemize(Item candidate, Iterable<Item> owned,
                                                 Armor equippedArmor, int strength) {
        if (isCursed(candidate)) return false;
        if (isProtectedEquipment(candidate)) return true;
        if (candidate instanceof MeleeWeapon) {
            MeleeWeapon weapon = (MeleeWeapon) candidate;
            boolean wearable = weapon.STRReq(weapon.trueLevel()) <= strength;
            for (Item item : owned) {
                if (!(item instanceof MeleeWeapon) || isCursed(item)) continue;
                MeleeWeapon other = (MeleeWeapon) item;
                if (wearable && other.STRReq(other.trueLevel()) > strength) continue;
                if (weaponAtLeastAsGood(other, weapon)
                        || weaponOverwhelms(other, weapon)) return false;
            }
            return true;
        }
        if (candidate instanceof Armor) {
            Armor armor = (Armor) candidate;
            BrokenSeal seal = equippedArmor == null ? null : equippedArmor.checkSeal();
            boolean wearable = canWearArmor(armor, strength, seal);
            int rating = armorRatingWithSeal(armor, seal);
            for (Item item : owned) {
                if (!(item instanceof Armor) || isCursed(item)) continue;
                Armor other = (Armor) item;
                BrokenSeal otherSeal = other == equippedArmor || other.checkSeal() != null
                        ? null : seal;
                if (wearable && !canWearArmor(other, strength, otherSeal)) continue;
                int otherRating = other == equippedArmor
                        ? armorRating(other) : armorRatingWithSeal(other, otherSeal);
                if (otherRating >= rating) return false;
            }
            return true;
        }
        return false;
    }

    private static boolean isSaleCandidate(Hero hero, Item item) {
        Item equipped = item instanceof MeleeWeapon ? hero.belongings.weapon()
                : item instanceof Armor ? hero.belongings.armor() : null;
        return isRedundant(item, equipped);
    }

    /** Explain the two failed routes for floor gear already rejected by pickup(). */
    static String pickupRejection(Hero hero, Item item) {
        if (!(item instanceof MeleeWeapon || item instanceof Armor)) return null;
        String sale;
        if (hero.belongings.lostInventory()) sale = "lost-inventory";
        else if (availableAlchemize(hero) == null) sale = "no-usable-alchemize";
        else if (!Shopkeeper.canSell(item)) sale = "cannot-sell";
        else sale = "not-redundant-vs-equipped";
        String reason = "keep=" + (isCursed(item) ? "cursed" : "owned-equal-or-better")
                + " sell=" + sale;
        if (item instanceof Armor) {
            Armor worn = hero.belongings.armor();
            BrokenSeal seal = worn == null ? null : worn.checkSeal();
            reason += " defense=" + armorRatingWithSeal((Armor) item, seal)
                    + " equipped-defense=" + (worn == null ? "-" : armorRating(worn));
        }
        return reason;
    }

    /** Use persistent true upgrades, never unknown UI values or temporary buffs. */
    static int weaponRating(MeleeWeapon weapon) {
        int level = weapon.trueLevel();
        return weapon.augment.damageFactor(weapon.min(level))
                + weapon.augment.damageFactor(weapon.max(level));
    }

    static int armorRating(Armor armor) {
        return armorRatingAtLevel(armor, armor.trueLevel());
    }

    /** Keep only selected high-value magical effects and augmented equipment. */
    static boolean isProtectedEquipment(Item item) {
        if (item instanceof MeleeWeapon) {
            MeleeWeapon weapon = (MeleeWeapon) item;
            return weapon.augment != Weapon.Augment.NONE
                    || weapon.enchantment instanceof Grim
                    || weapon.enchantment instanceof Chilling
                    || weapon.enchantment instanceof Venomous
                    || weapon.enchantment instanceof Vorpal
                    || weapon.enchantment instanceof Vampiric
                    || weapon.enchantment instanceof Projecting
                    || weapon.enchantment instanceof Lucky;
        }
        if (item instanceof Armor) {
            Armor armor = (Armor) item;
            return armor instanceof ClassArmor || armor.checkSeal() != null
                    || armor.augment != Armor.Augment.NONE || isProtectedGlyph(armor.glyph);
        }
        return false;
    }

    private static boolean isProtectedGlyph(Armor.Glyph glyph) {
        return glyph instanceof Swiftness || glyph instanceof Camouflage
                || glyph instanceof Obfuscation || glyph instanceof Brimstone
                || glyph instanceof AntiMagic || glyph instanceof Thorns;
    }

    /** Automatic transfer must not discard a selected valuable inscription. */
    static boolean wouldLoseProtectedClassGlyph(ClassArmor source, Armor replacement) {
        if (!isProtectedGlyph(source.glyph)) return false;
        if (replacement.glyph != null
                && source.glyph.getClass() == replacement.glyph.getClass()) return false;
        return replacement.glyph != null || source.checkSeal() == null
                || !source.checkSeal().canTransferGlyph();
    }

    static boolean hasGlyphConflict(Armor current, Armor replacement) {
        return current != null && current.glyph != null && replacement.glyph != null
                && current.glyph.getClass() != replacement.glyph.getClass();
    }

    /** Equal equipment also dominates a floor pickup; never hoard duplicates. */
    private static boolean weaponAtLeastAsGood(MeleeWeapon superior, MeleeWeapon inferior) {
        return weaponRating(superior) >= weaponRating(inferior)
                && superior.augment.delayFactor(superior.DLY)
                    <= inferior.augment.delayFactor(inferior.DLY)
                && superior.ACC >= inferior.ACC && superior.RCH >= inferior.RCH;
    }

    /**
     * At double the raw damage, the stronger weapon outweighs a reach-only
     * advantage if its attack speed and accuracy are no worse. Other trade-offs
     * still use the strict multi-stat comparison.
     */
    static boolean weaponOverwhelms(MeleeWeapon superior, MeleeWeapon inferior) {
        return (long) weaponRating(superior) >= 2L * weaponRating(inferior)
                && superior.augment.delayFactor(superior.DLY)
                    <= inferior.augment.delayFactor(inferior.DLY)
                && superior.ACC >= inferior.ACC;
    }

    /** Evaluate independent weapon traits without inventing trade-off weights. */
    static boolean weaponDominates(MeleeWeapon superior, MeleeWeapon inferior) {
        if (!weaponAtLeastAsGood(superior, inferior)) return false;
        return weaponRating(superior) > weaponRating(inferior)
                || superior.augment.delayFactor(superior.DLY)
                    < inferior.augment.delayFactor(inferior.DLY)
                || superior.ACC > inferior.ACC || superior.RCH > inferior.RCH;
    }

    /** Keep protected gear; ordinary spare armor can be sold even at equal defense. */
    static boolean isRedundant(Item candidate, Item equipped) {
        if (isProtectedEquipment(candidate)) return false;
        if (candidate instanceof MeleeWeapon && equipped instanceof MeleeWeapon) {
            MeleeWeapon worn = (MeleeWeapon) equipped;
            MeleeWeapon spare = (MeleeWeapon) candidate;
            return weaponDominates(worn, spare) || weaponOverwhelms(worn, spare)
                    || (worn.getClass() == spare.getClass() && spare.enchantment == null
                        && weaponAtLeastAsGood(worn, spare));
        }
        if (candidate instanceof Armor && equipped instanceof Armor) {
            return armorRating((Armor) equipped) >= armorRating((Armor) candidate);
        }
        return false;
    }

    private static int armorRatingAtLevel(Armor armor, int level) {
        return armor.DRMin(level) + armor.DRMax(level);
    }

    /** A transferred upgraded warrior seal adds one real level to the new armor. */
    private static int armorLevelWithSeal(Armor armor, BrokenSeal seal) {
        return armor.trueLevel() + (seal != null && seal.level() > 0 ? 1 : 0);
    }

    static int armorRatingWithSeal(Armor armor, BrokenSeal seal) {
        return armorRatingAtLevel(armor, armorLevelWithSeal(armor, seal));
    }

    private static boolean canEquip(Hero hero, MeleeWeapon weapon) {
        // Hero.STR() includes equipped Ring of Might and all active strength bonuses.
        return !isCursed(weapon) && weapon.STRReq(weapon.trueLevel()) <= hero.STR();
    }

    static boolean canWearArmor(Armor armor, int strength, BrokenSeal seal) {
        return !isCursed(armor) && armor.STRReq(armorLevelWithSeal(armor, seal)) <= strength;
    }

    /** After all strength potions, sell only unusable unprotected equipment. */
    static boolean isUnwearableForSale(Item item, int strength) {
        if (isProtectedEquipment(item)) return false;
        if (item instanceof MeleeWeapon) {
            MeleeWeapon weapon = (MeleeWeapon) item;
            return weapon.STRReq(weapon.trueLevel()) > strength;
        }
        if (item instanceof Armor) {
            Armor armor = (Armor) item;
            return armor.STRReq(armor.trueLevel()) > strength;
        }
        return false;
    }

    private static boolean canSellSpare(Hero hero, Item spare, Item upgrade) {
        return isEquipment(spare) && !spare.isEquipped(hero)
                && (isSaleCandidate(hero, spare) || isRedundant(spare, upgrade))
                && Shopkeeper.canSell(spare);
    }

    private static ArrayList<Item> saleBatch(Hero hero, Item upgrade) {
        ArrayList<Item> batch = new ArrayList<>();
        for (Item spare : hero.belongings.backpack.items) {
            if (spare == upgrade) continue;
            if (canSellSpare(hero, spare, upgrade)) batch.add(spare);
        }
        return batch;
    }

    private static void sellBatch(Hero hero, Alchemize spell, ArrayList<Item> batch, Item detached) {
        int gold = detached == null ? 0 : detached.value();
        for (Item item : batch) {
            gold += item.value();
            item.detachAll(hero.belongings.backpack);
        }
        // Match native trade timing and pay out the batch as a single gold pickup.
        hero.spend(-hero.cooldown());
        new Gold(gold).doPickUp(hero);
        spell.detach(hero.belongings.backpack);
        Sample.INSTANCE.play(Assets.Sounds.TELEPORT);
        Catalog.countUse(Alchemize.class);
        int count = batch.size() + (detached == null ? 0 : 1);
        BlastLog.debug("auto-gear action=sell count=" + count + " gold=" + gold);
        // Match Alchemize's native one-in-eight scroll talent chance.
        if (Random.Float() < 1f / 8f) {
            Talent.onScrollUsed(hero, hero.pos, 1f, Alchemize.class);
        }
    }

    private static boolean drinkStrength(Hero hero) {
        PotionOfStrength potion = hero.belongings.getItem(PotionOfStrength.class);
        if (potion == null || potion.quantity() <= 0) return false;
        int previousStrength = hero.STR();
        automaticItemUse.set(true);
        try {
            potion.execute(hero, Potion.AC_DRINK);
        } finally {
            automaticItemUse.remove();
        }
        if (hero.STR() <= previousStrength) {
            throw new IllegalStateException("Automatic strength potion did not raise strength");
        }
        pendingStrengthCleanup = true;
        return true;
    }

    static boolean sellSpares(Hero hero) {
        if (hero.belongings.lostInventory()) return false;
        Alchemize spell = availableAlchemize(hero);
        if (spell == null) return false;
        ArrayList<Item> batch = saleBatch(hero, null);
        if (pendingStrengthCleanup) {
            int strength = hero.STR();
            for (Item item : hero.belongings.backpack.items) {
                if (!item.isEquipped(hero) && isUnwearableForSale(item, strength)
                        && Shopkeeper.canSell(item) && !batch.contains(item)) batch.add(item);
            }
            pendingStrengthCleanup = false;
        }
        if (batch.isEmpty()) return false;
        sellBatch(hero, spell, batch, null);
        return true;
    }

    private static boolean betterWeapon(Hero hero, MeleeWeapon candidate, KindOfWeapon current) {
        if (!canEquip(hero, candidate)) return false;
        if (current == null) return true;
        if (!(current instanceof MeleeWeapon) || isCursed(current)) return false;
        MeleeWeapon worn = (MeleeWeapon) current;
        int rating = weaponRating(candidate);
        int currentRating = weaponRating(worn);
        return rating > currentRating || (rating == currentRating && weaponDominates(candidate, worn));
    }

    private static boolean equipBetterWeapon(Hero hero) {
        KindOfWeapon current = hero.belongings.weapon();
        MeleeWeapon best = null;
        for (MeleeWeapon candidate : hero.belongings.getAllItems(MeleeWeapon.class)) {
            if (candidate.isEquipped(hero)
                    || !betterWeapon(hero, candidate, best != null ? best : current)) continue;
            best = candidate;
        }
        if (best == null) return false;
        if (!best.doEquip(hero)) {
            throw new IllegalStateException("Eligible weapon upgrade failed to equip");
        }
        BlastLog.debug("auto-gear action=equip item=" + best.getClass().getSimpleName());
        return true;
    }

    /** Transfer the seal before equipping to avoid the native yes/no dialog. */
    private static boolean equipArmorWithSeal(Hero hero, Armor current, Armor next) {
        BrokenSeal seal = current == null ? null : current.checkSeal();
        if (seal == null) return next.doEquip(hero);
        if (next.checkSeal() != null) {
            throw new IllegalStateException("Cannot transfer warrior seal onto sealed armor");
        }

        Armor.Glyph originalSealGlyph = seal.getGlyph();
        Armor.Glyph originalOldGlyph = current.glyph;
        Armor.Glyph originalNextGlyph = next.glyph;
        boolean preserveOldGlyph = hasGlyphConflict(current, next);

        // Detaching a seal may erase its old armor's inscription when runic
        // transference is unlocked. Clear the seal's glyph FIRST on conflict.
        if (preserveOldGlyph) seal.setGlyph(null);
        if (current.detachSeal() != seal) {
            throw new IllegalStateException("Warrior seal detached unexpectedly");
        }

        next.affixSeal(seal);
        if (preserveOldGlyph) {
            // Keep the destination glyph and record it on the transferred seal.
            next.inscribe(originalNextGlyph);
            if (current.glyph != originalOldGlyph || next.glyph != originalNextGlyph) {
                throw new IllegalStateException("Conflicting armor glyph was overwritten");
            }
        }
        if (next.doEquip(hero)) return true;

        // A failed equip must restore both pieces of armor and the original seal glyph.
        if (next.detachSeal() != seal) {
            throw new IllegalStateException("Could not restore seal after failed equip");
        }
        next.inscribe(originalNextGlyph);
        seal.setGlyph(originalSealGlyph);
        current.affixSeal(seal);
        return false;
    }

    private static boolean betterArmor(Hero hero, Armor candidate, Armor current) {
        BrokenSeal seal = current == null ? null : current.checkSeal();
        return !(candidate instanceof ClassArmor)
                && canWearArmor(candidate, hero.STR(), seal)
                && (current == null || !isCursed(current))
                && (seal == null || candidate.checkSeal() == null)
                && (!(current instanceof ClassArmor)
                    || !wouldLoseProtectedClassGlyph((ClassArmor) current, candidate))
                && (current == null || armorRatingWithSeal(candidate, seal) > armorRating(current));
    }

    private static boolean equipBetterArmor(Hero hero) {
        Armor current = hero.belongings.armor();
        if (current != null && isCursed(current)) return false;
        BrokenSeal seal = current == null ? null : current.checkSeal();

        // A crown-upgraded ClassArmor carries the hero's active armor ability.
        // Recover existing saves where auto-play previously replaced it with
        // ordinary armor, then transfer the ordinary armor's stats next turn.
        if (!(current instanceof ClassArmor)) {
            ClassArmor classArmor = hero.belongings.getItem(ClassArmor.class);
            if (classArmor != null && classArmor != current
                    && canWearArmor(classArmor, hero.STR(), seal)
                    && (seal == null || classArmor.checkSeal() == null)) {
                if (!equipArmorWithSeal(hero, current, classArmor)) {
                    throw new IllegalStateException("Could not restore hero ability armor");
                }
                return true;
            }
        }

        int currentRating = current == null ? Integer.MIN_VALUE : armorRating(current);
        Armor best = null;
        int bestRating = currentRating;
        for (Armor candidate : hero.belongings.getAllItems(Armor.class)) {
            if (candidate.isEquipped(hero) || !betterArmor(hero, candidate, current)) continue;
            int rating = armorRatingWithSeal(candidate, seal);
            if (rating > bestRating) {
                best = candidate;
                bestRating = rating;
            }
        }
        if (best == null) return false;
        if (current instanceof ClassArmor) {
            ClassArmor classArmor = (ClassArmor) current;
            float charge = classArmor.charge;
            com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.ArmorAbility ability =
                    hero.armorAbility;
            classArmor.transferStatsFrom(hero, best);
            if (hero.belongings.armor() != classArmor || classArmor.charge != charge
                    || hero.armorAbility != ability || hero.belongings.contains(best)) {
                throw new IllegalStateException("Hero armor ability transfer was incomplete");
            }
            BlastLog.debug("auto-gear action=transfer-armor item=" + best.getClass().getSimpleName());
            hero.spendAndNext(com.shatteredpixel.shatteredpixeldungeon.actors.Actor.TICK);
            return true;
        }
        if (!equipArmorWithSeal(hero, current, best)) {
            throw new IllegalStateException("Eligible armor upgrade failed to equip");
        }
        BlastLog.debug("auto-gear action=equip item=" + best.getClass().getSimpleName());
        if (seal != null && best.checkSeal() != seal) {
            throw new IllegalStateException("Warrior seal was not attached to replacement armor");
        }
        return true;
    }

    /** One native item action at a time. */
    static boolean manage(Hero hero) {
        if (hero.belongings.lostInventory()) return false;
        // Finish all strength potions before making irreversible sale decisions.
        if (drinkStrength(hero)) return true;
        return equipBetterWeapon(hero) || equipBetterArmor(hero);
    }
}
