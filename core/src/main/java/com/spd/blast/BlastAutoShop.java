package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.MasterThievesArmband;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.MasterThievesArmband.Thievery;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.MagicalHolster;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ShopRoom;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndTradeItem;

/** Trades during idle auto-play once the hero enters a shop. */
final class BlastAutoShop {
    private static final int MAX_ARMBAND_LEVEL = 10;

    private enum Payment { GOLD, STEAL }

    private BlastAutoShop() {}

    static boolean chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (!(level instanceof RegularLevel) || hero.belongings.lostInventory()) return false;
        Room room = ((RegularLevel) level).room(hero.pos);
        if (!(room instanceof ShopRoom)) return false;
        Shopkeeper shop = null;
        for (Mob mob : level.mobs) {
            if (mob instanceof Shopkeeper && mob.isAlive() && room.inside(level.cellToPoint(mob.pos))) {
                shop = (Shopkeeper) mob;
                break;
            }
        }
        if (shop == null) return false;

        MasterThievesArmband armband = equippedArmband(hero);
        Thievery thievery = hero.buff(Thievery.class);
        boolean hasHolster = hero.belongings.getItem(MagicalHolster.class) != null;
        Heap target = null;
        Payment payment = null;
        boolean targetIsBag = false;
        int nearest = Integer.MAX_VALUE;
        for (Heap heap : level.heaps.valueList()) {
            int cell = heap.pos;
            if (heap.type != Heap.Type.FOR_SALE || heap.size() != 1
                    || !room.inside(level.cellToPoint(cell)) || !safe[cell]
                    || distances[cell] == Integer.MAX_VALUE || (hero.rooted && cell != hero.pos)) continue;

            Item item = heap.peek();
            boolean bag = item instanceof Bag;
            if ((targetIsBag && !bag) || (bag == targetIsBag && distances[cell] >= nearest)) continue;
            if (bag ? hero.belongings.getItem(item.getClass()) != null : !isSupply(item, hasHolster)) continue;
            if (!BlastAutoPickup.canCollect(hero, item, BlastAutoPickupPolicy::isInstantPickup)) continue;
            Payment offered = paymentFor(item, armband, thievery);
            if (offered == null) continue;

            target = heap;
            payment = offered;
            targetIsBag = bag;
            nearest = distances[cell];
        }

        if (!targetIsBag && BlastAutoEquipment.sellSpares(hero, shop)) return true;
        if (target == null) return false;
        if (hero.pos != target.pos) {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(target.pos, safe, distances));
            return true;
        }

        Item item = target.peek();
        if (payment == Payment.STEAL) {
            int charges = thievery.chargesToUse(item);
            if (!thievery.steal(item)) throw new IllegalStateException("Guaranteed shop theft failed");
            Item stolen = target.pickUp();
            BlastLog.debug("auto-shop action=steal item=" + item.getClass().getSimpleName()
                    + " charges=" + charges);
            if (stolen.doPickUp(hero)) return true;
            level.drop(stolen, target.pos).sprite.drop();
            return false;
        }

        int price = Shopkeeper.sellPrice(item);
        boolean pickedUp = WndTradeItem.buy(target);
        BlastLog.debug("auto-shop action=buy item=" + item.getClass().getSimpleName() + " price=" + price);
        return pickedUp;
    }

    private static boolean isSupply(Item item, boolean hasHolster) {
        return item instanceof Runestone || item instanceof Scroll || item instanceof Potion
                || item instanceof Food || (hasHolster && item instanceof MissileWeapon);
    }

    private static MasterThievesArmband equippedArmband(Hero hero) {
        if (hero.belongings.artifact() instanceof MasterThievesArmband) {
            return (MasterThievesArmband) hero.belongings.artifact();
        }
        if (hero.belongings.misc() instanceof MasterThievesArmband) {
            return (MasterThievesArmband) hero.belongings.misc();
        }
        return null;
    }

    private static Payment paymentFor(Item item, MasterThievesArmband armband, Thievery thievery) {
        if (armband != null) {
            if (thievery == null) return null;
            if (thievery.chargesToUse(item) > 0) {
                // A failed theft closes the shop and removes its stock.
                return !thievery.isCursed() && thievery.stealChance(item) >= 1f ? Payment.STEAL : null;
            }
            if (armband.level() < MAX_ARMBAND_LEVEL) return null;
        }
        return Shopkeeper.sellPrice(item) <= Dungeon.gold ? Payment.GOLD : null;
    }
}
