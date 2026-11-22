package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
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
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ToxicTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WeakeningTrap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;

import java.util.function.Predicate;

/** Clear a blocked one-tile corridor trap using an ordinary inventory throw. */
final class BlastAutoTrapClearer {
    private static final int MAX_THROW_DISTANCE = 8;
    private static final int TOXIC_MIN_DISTANCE = 4;
    private static final int TOXIC_RETREAT_STEPS = 2;
    private static final int LOG_REPEAT_INTERVAL = 10;
    private static String lastRejection;
    private static int repeatedRejections;
    private static int toxicOrigin = -1;

    enum Action { NONE, MOVE, THROW }

    private BlastAutoTrapClearer() {}

    static void reset() {
        lastRejection = null;
        repeatedRejections = 0;
        toxicOrigin = -1;
    }

    /** A rejected trap is logged only when near the hero; suppress repeated reasons. */
    private static void logRejection(Hero hero, Trap trap, String reason) {
        if (!BlastSettings.debugLog() || trap == null
                || Dungeon.level.distance(hero.pos, trap.pos) > MAX_THROW_DISTANCE) return;
        String key = trap.pos + ":" + trap.getClass().getName() + ":" + reason;
        if (key.equals(lastRejection)) {
            if (++repeatedRejections < LOG_REPEAT_INTERVAL) return;
            repeatedRejections = 0;
        } else {
            lastRejection = key;
            repeatedRejections = 0;
        }
        BlastLog.debug("auto-trap pos=" + hero.pos + " trap="
                + trap.getClass().getSimpleName() + "@" + trap.pos
                + " hidden=" + !trap.visible + " status=" + reason);
    }

    /** Effect-specific stand-off distance, with a retreat for spreading toxic gas. */
    static int minimumThrowDistance(Trap trap) {
        Class<?> type = trap.getClass();
        if (type == CursingTrap.class || type == FlashingTrap.class || type == GrippingTrap.class
                || type == WeakeningTrap.class) return 1;
        // Ooze is applied to the trap and eight adjoining cells, without a lingering blob.
        if (type == OozeTrap.class) return 2;
        if (type == ToxicTrap.class) return TOXIC_MIN_DISTANCE;
        return -1;
    }

    /** Both revealed and secret terrain can activate a trap when an item lands. */
    static boolean canTrigger(Trap trap, int terrain) {
        return trap.active && trap.disarmedByActivation
                && minimumThrowDistance(trap) >= 0
                && (terrain == Terrain.TRAP || terrain == Terrain.SECRET_TRAP);
    }

    /** Return the accessible corridor-side offset only when the trap separates its two ends. */
    static int reachableSide(int cell, int width, boolean[] solid, boolean[] passable,
                             boolean[] pit, boolean[] safe, boolean[] visited,
                             boolean[] mapped, boolean[] visible, int[] distances) {
        if (width < 3 || solid.length != passable.length || passable.length != pit.length
                || pit.length != safe.length || safe.length != visited.length
                || visited.length != mapped.length || mapped.length != visible.length
                || visible.length != distances.length || solid.length % width != 0
                || cell <= width || cell >= solid.length - width
                || cell % width == 0 || cell % width == width - 1) {
            throw new IllegalArgumentException("Invalid trap corridor map");
        }
        if (solid[cell - width] && solid[cell + width]) {
            return separatedSide(cell - 1, cell + 1, cell, passable, pit,
                    safe, visited, mapped, visible, distances);
        }
        if (solid[cell - 1] && solid[cell + 1]) {
            return separatedSide(cell - width, cell + width, cell, passable, pit,
                    safe, visited, mapped, visible, distances);
        }
        return 0;
    }

    private static int separatedSide(int first, int second, int trap,
                                     boolean[] passable, boolean[] pit, boolean[] safe,
                                     boolean[] visited, boolean[] mapped, boolean[] visible,
                                     int[] distances) {
        // The exit may be unexplored. Requiring it to be "safe" would make
        // a frontier corridor trap impossible to clear before discovering the exit.
        boolean firstKnown = visited[first] || mapped[first] || visible[first];
        boolean secondKnown = visited[second] || mapped[second] || visible[second];
        boolean firstReached = firstKnown && safe[first] && distances[first] != Integer.MAX_VALUE;
        boolean secondReached = secondKnown && safe[second] && distances[second] != Integer.MAX_VALUE;
        if (firstReached == secondReached) return 0;

        int approach = firstReached ? first : second;
        int exit = firstReached ? second : first;
        boolean exitKnown = firstReached ? secondKnown : firstKnown;
        if (!passable[exit] || pit[exit] || distances[exit] != Integer.MAX_VALUE
                || (exitKnown && !safe[exit])) return 0;
        return approach - trap;
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

    private static boolean hasToxicRetreat(Level level, int stand, int side, boolean[] safe) {
        for (int step = 1; step <= TOXIC_RETREAT_STEPS; step++) {
            int cell = stand + side * step;
            if (!level.insideMap(cell) || !safe[cell]) return false;
        }
        return true;
    }

    private static int throwingStand(Level level, int trapCell, int side, int minimum,
                                     boolean toxic, boolean[] safe, int[] distances) {
        int best = -1;
        int shortest = Integer.MAX_VALUE;
        for (int steps = 1; steps <= MAX_THROW_DISTANCE; steps++) {
            int stand = trapCell + side * steps;
            if (!level.insideMap(stand) || !level.passable[stand] || !safe[stand]) break;
            if (steps < minimum || distances[stand] == Integer.MAX_VALUE
                    || distances[stand] >= shortest
                    || (toxic && !hasToxicRetreat(level, stand, side, safe))) continue;
            // Terrain and characters may stop an item before it presses the trap.
            if (new Ballistica(stand, trapCell, Ballistica.PROJECTILE).collisionPos != trapCell) {
                continue;
            }
            best = stand;
            shortest = distances[stand];
        }
        return best;
    }

    /** A thrown item lands on the closed door and keeps it open as a floor heap. */
    private static int doorStand(Level level, int trapCell, int side, int minimum,
                                 boolean toxic, boolean[] safe, int[] distances) {
        int door = -1;
        for (int steps = 1; steps <= MAX_THROW_DISTANCE; steps++) {
            int cell = trapCell + side * steps;
            if (!level.insideMap(cell) || !level.passable[cell] || !safe[cell]) break;
            if (level.map[cell] == Terrain.DOOR) {
                door = cell;
                break;
            }
        }
        if (door < 0) return -1;

        int best = -1;
        int shortest = Integer.MAX_VALUE;
        for (int steps = minimum; steps <= MAX_THROW_DISTANCE; steps++) {
            int stand = trapCell + side * steps;
            if (!level.insideMap(stand) || !level.passable[stand] || !safe[stand]) break;
            if (stand <= 0 || stand == door || level.map[stand] == Terrain.DOOR
                    || distances[stand] == Integer.MAX_VALUE || distances[stand] >= shortest
                    || (toxic && !hasToxicRetreat(level, stand, side, safe))) continue;
            if (new Ballistica(stand, door, Ballistica.PROJECTILE).collisionPos != door) {
                continue;
            }
            best = stand;
            shortest = distances[stand];
        }
        return best;
    }

    private static int closedDoor(Level level, int trapCell, int side) {
        for (int steps = 1; steps <= MAX_THROW_DISTANCE; steps++) {
            int cell = trapCell + side * steps;
            if (!level.insideMap(cell) || !level.passable[cell]) break;
            if (level.map[cell] == Terrain.DOOR) return cell;
        }
        return -1;
    }

    /** Retreat before a newly triggered gas plume can enter the hero's position. */
    static int toxicRetreatStep(Hero hero, boolean[] safe) {
        if (toxicOrigin < 0) return -1;
        Level level = Dungeon.level;
        ToxicGas gas = level.blobs.get(ToxicGas.class);
        if (gas == null || gas.volume <= 0) {
            toxicOrigin = -1;
            return -1;
        }
        int currentDistance = level.distance(hero.pos, toxicOrigin);
        if (currentDistance >= MAX_THROW_DISTANCE) return -1;
        int best = -1;
        int farthest = currentDistance;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (!level.insideMap(cell) || !safe[cell]) continue;
            int distance = level.distance(cell, toxicOrigin);
            if (distance > farthest) {
                best = cell;
                farthest = distance;
            }
        }
        return best;
    }

    /** After retreat, keep roaming away from the active toxic trap's plume. */
    static void avoidToxicReturn(Hero hero, boolean[] safe) {
        if (toxicOrigin < 0) return;
        ToxicGas gas = Dungeon.level.blobs.get(ToxicGas.class);
        if (gas == null || gas.volume <= 0) {
            toxicOrigin = -1;
            return;
        }
        if (Dungeon.level.distance(hero.pos, toxicOrigin) < MAX_THROW_DISTANCE) return;
        for (int cell = 0; cell < safe.length; cell++) {
            if (cell != hero.pos && Dungeon.level.distance(cell, toxicOrigin) < MAX_THROW_DISTANCE) {
                safe[cell] = false;
            }
        }
    }

    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid trap clearance navigation map");
        }
        Trap chosen = null;
        int bestStand = -1;
        int bestTarget = -1;
        int shortest = Integer.MAX_VALUE;
        Trap rejectedTrap = null;
        String rejectedReason = null;
        int rejectedDistance = MAX_THROW_DISTANCE + 1;
        for (Trap trap : level.traps.valueList()) {
            int cell = trap.pos;
            if (!level.insideMap(cell) || !trap.active
                    || !(level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell])) {
                continue;
            }
            String reason = null;
            if (!canTrigger(trap, level.map[cell])) reason = "unsupported";
            else if (safe[cell]) reason = "traversable";
            else if (Actor.findChar(cell) != null) reason = "occupied";
            else if (level.plants.get(cell) != null) reason = "plant";
            else {
                int side = reachableSide(cell, level.width(), level.solid, level.passable,
                        level.pit, safe, level.visited, level.mapped, hero.fieldOfView, distances);
                if (side == 0) {
                    reason = "not-chokepoint";
                } else {
                    int minimum = minimumThrowDistance(trap);
                    boolean toxic = trap.getClass() == ToxicTrap.class;
                    int stand = throwingStand(level, cell, side,
                            minimum, toxic, safe, distances);
                    int target = cell;
                    if (stand < 0) {
                        // Closed doors stop projectiles and reclose after the hero leaves.
                        // A spare item on the door opens it without approaching the trap.
                        stand = doorStand(level, cell, side, minimum, toxic, safe, distances);
                        if (stand >= 0) target = closedDoor(level, cell, side);
                    }
                    if (stand < 0) reason = "no-safe-stand";
                    else if (hero.rooted && stand != hero.pos) reason = "rooted";
                    else if (distances[stand] < shortest) {
                        chosen = trap;
                        bestStand = stand;
                        bestTarget = target;
                        shortest = distances[stand];
                    }
                }
            }
            if (reason != null) {
                int near = level.distance(hero.pos, cell);
                if (near < rejectedDistance) {
                    rejectedDistance = near;
                    rejectedTrap = trap;
                    rejectedReason = reason;
                }
            }
        }
        if (chosen == null) {
            logRejection(hero, rejectedTrap, rejectedReason);
            return Action.NONE;
        }
        if (hero.belongings.lostInventory()) {
            logRejection(hero, chosen, "lost-inventory");
            return Action.NONE;
        }
        Item item = chooseThrowItem(hero.belongings.backpack, candidate -> candidate.isEquipped(hero));
        if (item == null) {
            logRejection(hero, chosen, "no-throwable-item");
            return Action.NONE;
        }
        if (bestStand != hero.pos) {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(bestStand, safe, distances));
            BlastLog.debug("auto-trap pos=" + hero.pos + " trap=" + chosen.getClass().getSimpleName()
                    + "@" + chosen.pos + " stand=" + bestStand + " action=move");
            return Action.MOVE;
        }
        // A cast must use the item's real trajectory for both door and trap.
        if (item.throwPos(hero, bestTarget) != bestTarget) {
            logRejection(hero, chosen, "blocked-trajectory");
            return Action.NONE;
        }
        boolean openDoor = bestTarget != chosen.pos;
        BlastLog.debug("auto-trap pos=" + hero.pos + " trap=" + chosen.getClass().getSimpleName()
                + "@" + chosen.pos + " item=" + item.getClass().getSimpleName()
                + " target=" + bestTarget + " action=" + (openDoor ? "open-door" : "throw"));
        if (!openDoor && chosen.getClass() == ToxicTrap.class) toxicOrigin = chosen.pos;
        item.setCurrent(hero);
        item.cast(hero, bestTarget);
        return Action.THROW;
    }
}
