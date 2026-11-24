package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfStrength;
import com.shatteredpixel.shatteredpixeldungeon.items.spells.Alchemize;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTradeItem;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Random;

import java.util.ArrayDeque;

/** Idle equipment maintenance, restricted to melee weapons and armor. */
final class BlastAutoEquipment {

    private static final ThreadLocal<Boolean> automaticItemUse = new ThreadLocal<>();
    private static final ArrayDeque<Item> replacedEquipment = new ArrayDeque<>();
    private static Item pendingCursedPickup;

    private BlastAutoEquipment() {}

    static void reset() {
        pendingCursedPickup = null;
        replacedEquipment.clear();
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

    static boolean canPickup(Hero hero, Item item) {
        if (!(item instanceof MeleeWeapon || item instanceof Armor)) return false;
        return canPickup(item, !hero.belongings.lostInventory()
                && availableAlchemize(hero) != null);
    }

    /** A cursed item is collected only when it can be sold using Alchemize. */
    static boolean canPickup(Item item, boolean alchemizeAvailable) {
        if (!(item instanceof MeleeWeapon || item instanceof Armor)) return false;
        return !isCursed(item) || (alchemizeAvailable && Shopkeeper.canSell(item));
    }

    /** Native pickup runs on the next hero turn; sale follows a successful pickup. */
    static void onPickupScheduled(Item item) {
        if (isCursed(item)) pendingCursedPickup = item;
    }

    /** Use persistent true upgrades, never unknown UI values or temporary buffs. */
    static int weaponRating(MeleeWeapon weapon) {
        int level = weapon.trueLevel();
        return weapon.augment.damageFactor(weapon.min(level))
                + weapon.augment.damageFactor(weapon.max(level));
    }

    static int armorRating(Armor armor) {
        int level = armor.trueLevel();
        return armor.DRMin(level) + armor.DRMax(level);
    }

    private static boolean canEquip(Hero hero, MeleeWeapon weapon) {
        return !isCursed(weapon) && weapon.STRReq(weapon.trueLevel()) <= hero.STR();
    }

    private static boolean canEquip(Hero hero, Armor armor) {
        return !isCursed(armor) && armor.STRReq(armor.trueLevel()) <= hero.STR();
    }

    private static boolean sellWithAlchemize(Hero hero, Item item) {
        Alchemize spell = availableAlchemize(hero);
        if (spell == null || item.isEquipped(hero)
                || !hero.belongings.contains(item) || !Shopkeeper.canSell(item)) return false;

        // Native trade grants gold and removes the item from the backpack.
        WndTradeItem.sell(item);
        spell.detach(hero.belongings.backpack);
        Sample.INSTANCE.play(Assets.Sounds.TELEPORT);
        Catalog.countUse(Alchemize.class);
        // Match Alchemize's native one-in-eight scroll talent chance.
        if (Random.Float() < 1f / 8f) {
            Talent.onScrollUsed(hero, hero.pos, 1f, Alchemize.class);
        }
        return true;
    }

    private static boolean drinkStrength(Hero hero) {
        PotionOfStrength potion = hero.belongings.getItem(PotionOfStrength.class);
        if (potion == null || potion.quantity() <= 0) return false;
        automaticItemUse.set(true);
        try {
            potion.execute(hero, Potion.AC_DRINK);
        } finally {
            automaticItemUse.remove();
        }
        return true;
    }

    private static boolean sellCursedPickup(Hero hero) {
        if (pendingCursedPickup == null) return false;
        Item item = pendingCursedPickup;
        pendingCursedPickup = null;
        if (!hero.belongings.contains(item)) return false;
        if (!sellWithAlchemize(hero, item)) {
            throw new IllegalStateException("Automatically collected cursed gear could not be sold");
        }
        return true;
    }

    private static boolean sellReplaced(Hero hero) {
        while (!replacedEquipment.isEmpty()) {
            Item item = replacedEquipment.peekFirst();
            if (!hero.belongings.contains(item) || item.isEquipped(hero)
                    || !Shopkeeper.canSell(item)) {
                replacedEquipment.removeFirst();
                continue;
            }
            if (availableAlchemize(hero) == null) {
                replacedEquipment.removeFirst();
                continue;
            }
            if (!sellWithAlchemize(hero, item)) {
                throw new IllegalStateException("Replaced equipment could not be sold");
            }
            replacedEquipment.removeFirst();
            return true;
        }
        return false;
    }

    private static boolean equipBetterWeapon(Hero hero) {
        KindOfWeapon current = hero.belongings.weapon();
        if (current != null && (!(current instanceof MeleeWeapon) || isCursed(current))) {
            return false;
        }
        int currentRating = current == null ? Integer.MIN_VALUE
                : weaponRating((MeleeWeapon) current);
        MeleeWeapon best = null;
        int bestRating = currentRating;
        for (MeleeWeapon candidate : hero.belongings.getAllItems(MeleeWeapon.class)) {
            if (candidate.isEquipped(hero) || !canEquip(hero, candidate)) continue;
            int rating = weaponRating(candidate);
            if (rating > bestRating) {
                best = candidate;
                bestRating = rating;
            }
        }
        if (best == null) return false;
        if (!best.doEquip(hero)) {
            throw new IllegalStateException("Eligible weapon upgrade failed to equip");
        }
        if (current != null && availableAlchemize(hero) != null
                && Shopkeeper.canSell(current)) replacedEquipment.addLast(current);
        return true;
    }

    private static boolean equipBetterArmor(Hero hero) {
        Armor current = hero.belongings.armor();
        // Native warrior-seal transfer requires a decision dialog; preserve it.
        if (current != null && (isCursed(current) || current.checkSeal() != null)) return false;
        int currentRating = current == null ? Integer.MIN_VALUE : armorRating(current);
        Armor best = null;
        int bestRating = currentRating;
        for (Armor candidate : hero.belongings.getAllItems(Armor.class)) {
            if (candidate.isEquipped(hero) || !canEquip(hero, candidate)) continue;
            int rating = armorRating(candidate);
            if (rating > bestRating) {
                best = candidate;
                bestRating = rating;
            }
        }
        if (best == null) return false;
        if (!best.doEquip(hero)) {
            throw new IllegalStateException("Eligible armor upgrade failed to equip");
        }
        if (current != null && availableAlchemize(hero) != null
                && Shopkeeper.canSell(current)) replacedEquipment.addLast(current);
        return true;
    }

    /** One native item action at a time. */
    static boolean manage(Hero hero) {
        if (hero.belongings.lostInventory()) return false;
        if (sellReplaced(hero)) return true;
        if (sellCursedPickup(hero)) return true;
        if (drinkStrength(hero)) return true;
        return equipBetterWeapon(hero) || equipBetterArmor(hero);
    }
}
