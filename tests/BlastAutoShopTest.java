package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Shopkeeper;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.MasterThievesArmband;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.MagicalHolster;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.PotionBandolier;
import com.shatteredpixel.shatteredpixeldungeon.items.food.SmallRation;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAugmentation;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.darts.Dart;
import com.shatteredpixel.shatteredpixeldungeon.levels.HallsLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.ShopRoom;
import com.watabou.noosa.Game;
import com.watabou.utils.PathFinder;
import com.watabou.utils.SparseArray;

import java.util.ArrayList;
import java.util.HashSet;

public final class BlastAutoShopTest {
    private static final int HERO = 24;
    private static final int SUPPLY = 25;
    private static final int BAG = 22;

    private static final class Shop extends HallsLevel {
        final Hero hero;
        final boolean[] safe;

        Shop() {
            Actor.clear();
            Dungeon.level = this;
            Dungeon.depth = 6;
            Dungeon.gold = 1000;
            hero = Dungeon.hero = new Hero();
            hero.pos = HERO;

            setSize(7, 7);
            rooms = new ArrayList<>();
            ShopRoom room = new ShopRoom();
            room.set(0, 0, 6, 6);
            rooms.add(room);
            heaps = new SparseArray<>();
            mobs = new HashSet<>();
            Shopkeeper shopkeeper = new Shopkeeper();
            shopkeeper.pos = 17;
            mobs.add(shopkeeper);

            safe = new boolean[length()];
            for (int cell = BAG; cell <= SUPPLY; cell++) safe[cell] = true;
        }

        void stock(int cell, Item item) {
            Heap heap = new Heap();
            heap.pos = cell;
            heap.type = Heap.Type.FOR_SALE;
            heap.items.add(item);
            heaps.put(cell, heap);
        }

        int nextStep() {
            hero.curAction = null;
            PathFinder.buildDistanceMap(hero.pos, safe);
            if (!BlastAutoShop.chooseAction(hero, safe, PathFinder.distance.clone())) return -1;
            check(hero.curAction instanceof HeroAction.Move, "Expected an approach step");
            return hero.curAction.dst;
        }
    }

    private static final class Armband extends MasterThievesArmband {
        void setCharge(int amount) {
            charge = amount;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void supplies() {
        Shop shop = new Shop();
        Scroll scroll = new Scroll() {
            @Override public void doRead() {}
        };
        for (Item item : new Item[]{new SmallRation(), scroll, new Potion(), new StoneOfAugmentation()}) {
            shop.stock(SUPPLY, item);
            check(shop.nextStep() == SUPPLY, "Eligible supply skipped: " + item.getClass());
        }
        shop.stock(SUPPLY, new Ankh());
        check(shop.nextStep() == -1, "Unrequested merchandise was selected");
        shop.stock(SUPPLY, new Dart());
        check(shop.nextStep() == -1, "Missile selected without a holster");
        shop.hero.belongings.backpack.items.add(new MagicalHolster());
        check(shop.nextStep() == SUPPLY, "Missile skipped with a holster");
        shop.mobs.clear();
        check(shop.nextStep() == -1, "Trade attempted without a shopkeeper");
    }

    private static void bagsAndCapacity() {
        Shop shop = new Shop();
        PotionBandolier bandolier = new PotionBandolier();
        shop.stock(BAG, bandolier);
        shop.stock(SUPPLY, new Potion());
        check(shop.nextStep() == HERO - 1, "Nearby potion took priority over a new bag");

        while (shop.hero.belongings.backpack.items.size() < shop.hero.belongings.backpack.capacity()) {
            shop.hero.belongings.backpack.items.add(new Item());
        }
        check(shop.nextStep() == HERO - 1, "Full backpack prevented adding a bag");
        shop.hero.belongings.backpack.items.add(bandolier);
        shop.hero.belongings.backpack.items.add(new Item());
        check(shop.nextStep() == SUPPLY, "Missed a potion that fits in the bandolier");
        shop.stock(SUPPLY, new SmallRation());
        check(shop.nextStep() == -1, "Selected a ration with no space in the backpack");
    }

    private static void armband() {
        Shop shop = new Shop();
        shop.stock(BAG, new MagicalHolster());
        shop.stock(SUPPLY, new SmallRation());
        Armband armband = new Armband();
        shop.hero.belongings.backpack.items.add(armband);
        check(shop.nextStep() == HERO - 1, "Carried armband affected gold purchases");
        shop.hero.belongings.backpack.items.remove(armband);
        shop.hero.belongings.misc = armband;
        armband.activate(shop.hero);
        check(shop.nextStep() == -1, "Uncharged, unfinished armband allowed spending gold");
        armband.setCharge(1);
        check(shop.nextStep() == SUPPLY, "Risky bag theft took priority over guaranteed food theft");
        armband.level(3);
        armband.setCharge(6);
        Dungeon.gold = 0;
        check(shop.nextStep() == HERO - 1, "Stealable bag was skipped when gold ran out");
        Dungeon.gold = 1000;
        armband.level(10);
        armband.setCharge(1);
        check(shop.nextStep() == SUPPLY, "Partially charged armband allowed a cash purchase");
        armband.setCharge(0);
        check(shop.nextStep() == HERO - 1, "Fully leveled, empty armband blocked a cash purchase");
    }

    public static void main(String[] args) {
        Game.version = "test";
        supplies();
        bagsAndCapacity();
        armband();
        System.out.println("BlastAutoShopTest passed");
    }
}
