package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CursingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlashingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrippingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.OozeTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WeakeningTrap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;

import java.util.function.Predicate;

/** Clear an observed one-tile corridor trap using an ordinary inventory throw. */
final class BlastAutoTrapClearer {
    private static final int MAX_THROW_DISTANCE = 6;

    enum Action { NONE, MOVE, THROW }

    private BlastAutoTrapClearer() {}

    /** Unknown, remote-targeting, persistent and chain-reaction traps are never auto-triggered. */
    static int minimumThrowDistance(Trap trap) {
        Class<?> type = trap.getClass();
        if (type == CursingTrap.class || type == FlashingTrap.class || type == GrippingTrap.class
                || type == WeakeningTrap.class) return 1;
        // Ooze is applied to the trap and eight adjoining cells, without a lingering blob.
        if (type == OozeTrap.class) return 2;
        return -1;
    }

    /** Return the accessible corridor-side offset only when the trap separates its two ends. */
    static int reachableSide(int cell, int width, boolean[] solid, boolean[] safe,
                             boolean[] visible, int[] distances) {
        if (width < 3 || solid.length != safe.length || safe.length != visible.length
                || visible.length != distances.length || solid.length % width != 0
                || cell <= width || cell >= solid.length - width
                || cell % width == 0 || cell % width == width - 1) {
            throw new IllegalArgumentException("Invalid trap corridor map");
        }
        if (solid[cell - width] && solid[cell + width]) {
            return separatedSide(cell - 1, cell + 1, cell, safe, visible, distances);
        }
        if (solid[cell - 1] && solid[cell + 1]) {
            return separatedSide(cell - width, cell + width, cell, safe, visible, distances);
        }
        return 0;
    }

    private static int separatedSide(int first, int second, int trap, boolean[] safe,
                                     boolean[] visible, int[] distances) {
        if (!visible[first] || !visible[second] || !safe[first] || !safe[second]) return 0;
        boolean firstReached = distances[first] != Integer.MAX_VALUE;
        boolean secondReached = distances[second] != Integer.MAX_VALUE;
        if (firstReached == secondReached) return 0;
        return (firstReached ? first : second) - trap;
    }

    /** Only spare weapons/armor and the largest food stack may be sacrificed. */
    static Item chooseThrowItem(Iterable<Item> inventory, Predicate<Item> equipped) {
        if (inventory == null || equipped == null) {
            throw new IllegalArgumentException("Inventory and equipment policy are required");
        }
        Item spareGear = null;
        Food largestFood = null;
        for (Item item : inventory) {
            if (item.quantity() <= 0 || equipped.test(item)) continue;
            if (item instanceof Weapon || item instanceof Armor) {
                if (spareGear == null) spareGear = item;
            } else if (item instanceof Food
                    && (largestFood == null || item.quantity() > largestFood.quantity())) {
                largestFood = (Food) item;
            }
        }
        return spareGear != null ? spareGear : largestFood;
    }

    private static int throwingStand(Level level, int trapCell, int side, int minimum,
                                     boolean[] safe, int[] distances) {
        int best = -1;
        int shortest = Integer.MAX_VALUE;
        for (int steps = 1; steps <= MAX_THROW_DISTANCE; steps++) {
            int stand = trapCell + side * steps;
            if (!level.insideMap(stand) || !level.passable[stand] || !safe[stand]) break;
            if (steps < minimum || distances[stand] == Integer.MAX_VALUE
                    || distances[stand] >= shortest) continue;
            // Terrain and characters may stop an item before it presses the trap.
            if (new Ballistica(stand, trapCell, Ballistica.PROJECTILE).collisionPos != trapCell) {
                continue;
            }
            best = stand;
            shortest = distances[stand];
        }
        return best;
    }

    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid trap clearance navigation map");
        }
        if (hero.belongings.lostInventory()) return Action.NONE;

        Trap chosen = null;
        int bestStand = -1;
        int shortest = Integer.MAX_VALUE;
        for (Trap trap : level.traps.valueList()) {
            int cell = trap.pos;
            int minimum = minimumThrowDistance(trap);
            if (minimum < 0 || !trap.active || !trap.visible || !trap.disarmedByActivation
                    || !level.insideMap(cell) || level.map[cell] != Terrain.TRAP
                    || !hero.fieldOfView[cell] || safe[cell]
                    || Actor.findChar(cell) != null || level.plants.get(cell) != null) continue;
            int side = reachableSide(cell, level.width(), level.solid, safe,
                    hero.fieldOfView, distances);
            if (side == 0) continue;
            int stand = throwingStand(level, cell, side, minimum, safe, distances);
            if (stand < 0 || (hero.rooted && stand != hero.pos)
                    || distances[stand] >= shortest) continue;
            chosen = trap;
            bestStand = stand;
            shortest = distances[stand];
        }
        if (chosen == null) return Action.NONE;

        Item item = chooseThrowItem(hero.belongings.backpack, candidate -> candidate.isEquipped(hero));
        if (item == null) return Action.NONE;
        if (bestStand != hero.pos) {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(bestStand, safe, distances));
            BlastLog.debug("auto-trap pos=" + hero.pos + " trap=" + chosen.getClass().getSimpleName()
                    + "@" + chosen.pos + " stand=" + bestStand + " action=move");
            return Action.MOVE;
        }
        // Use the actual item's native trajectory; a miss must never consume a throw.
        if (item.throwPos(hero, chosen.pos) != chosen.pos) return Action.NONE;
        BlastLog.debug("auto-trap pos=" + hero.pos + " trap=" + chosen.getClass().getSimpleName()
                + "@" + chosen.pos + " item=" + item.getClass().getSimpleName()
                + " action=throw");
        item.setCurrent(hero);
        item.cast(hero, chosen.pos);
        return Action.THROW;
    }
}
