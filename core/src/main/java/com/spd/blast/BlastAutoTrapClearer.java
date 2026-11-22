package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ConfusionGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.CorrosiveGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BlazingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BurningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ChillingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ConfusionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CorrosionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CursingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DisarmingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlockTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GeyserTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.OozeTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ShockingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TeleportationTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ToxicTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WarpingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WeakeningTrap;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;

import java.util.Arrays;
import java.util.function.Predicate;

/** Safely trigger supported traps using ordinary inventory throws. */
final class BlastAutoTrapClearer {
    private static final int MAX_THROW_DISTANCE = 8;
    private static final int GAS_MIN_DISTANCE = 2;
    private static final int GAS_ESCAPE_DISTANCE = 4;
    private static final int MAX_ESCAPE_STEPS = 8;
    private static final int LOG_REPEAT_INTERVAL = 10;
    private static String lastRejection;
    private static int repeatedRejections;
    private static int gasOrigin = -1;
    private static Class<? extends Blob> gasType;
    private static int heldDoor = -1;
    private static int heldDoorTrap = -1;

    enum Action { NONE, MOVE, THROW }

    private BlastAutoTrapClearer() {}

    static void reset() {
        lastRejection = null;
        repeatedRejections = 0;
        gasOrigin = -1;
        gasType = null;
        heldDoor = -1;
        heldDoorTrap = -1;
    }

    /** Prevent automatic pickup from undoing a door-opening throw mid-clearance. */
    static boolean holdingDoorOpen(int cell) {
        if (cell != heldDoor) return false;
        Level level = Dungeon.level;
        Trap trap = level.insideMap(heldDoorTrap) ? level.traps.get(heldDoorTrap) : null;
        if (trap == null || !trap.active || level.map[cell] != Terrain.OPEN_DOOR
                || level.heaps.get(cell) == null) {
            heldDoor = -1;
            heldDoorTrap = -1;
            return false;
        }
        return true;
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

    /** Minimum throwing distances based on each trap's effect. */
    static int minimumThrowDistance(Trap trap) {
        Class<?> type = trap.getClass();
        if (type == CursingTrap.class || type == WeakeningTrap.class
                || type == DisarmingTrap.class) return 1;
        // Flashing and Gripping traps remain armed after activation.
        if (type == TeleportationTrap.class || type == WarpingTrap.class
                || type == GeyserTrap.class) return 2;
        if (type == ShockingTrap.class || type == FlockTrap.class) return 3;
        // Ooze is applied to the trap and eight adjoining cells, without a lingering blob.
        if (type == OozeTrap.class || type == ChillingTrap.class) return 2;
        // Frost seeds freezing blobs as far as two connected floor cells from the trap.
        // A three-cell stand-off keeps the hero outside the initial frost area.
        if (type == FrostTrap.class) return 3;
        // Flames cover one neighboring cell or two reachable path steps.
        if (type == BurningTrap.class) return 2;
        if (type == BlazingTrap.class) return 3;
        if (type == ToxicTrap.class || type == ConfusionTrap.class) return GAS_MIN_DISTANCE;
        if (type == CorrosionTrap.class) return 3;
        return -1;
    }

    private static Class<? extends Blob> spreadingGas(Trap trap) {
        Class<?> type = trap.getClass();
        if (type == ToxicTrap.class) return ToxicGas.class;
        if (type == ConfusionTrap.class) return ConfusionGas.class;
        if (type == CorrosionTrap.class) return CorrosiveGas.class;
        return null;
    }

    /** Sheep may press other traps within their two-tile spawning radius. */
    private static boolean safeToActivate(Level level, Trap trap) {
        if (trap.getClass() != FlockTrap.class) return true;
        for (Trap nearby : level.traps.valueList()) {
            if (nearby != trap && nearby.active && level.distance(nearby.pos, trap.pos) <= 2) {
                return false;
            }
        }
        return true;
    }

    private static boolean standSafeForEffect(Level level, Trap trap, int stand) {
        return trap == null || trap.getClass() != ShockingTrap.class || !level.water[stand];
    }

    private static boolean fireTrap(Trap trap) {
        return trap.getClass() == BurningTrap.class || trap.getClass() == BlazingTrap.class;
    }

    /** Both revealed and secret terrain can activate a trap when an item lands. */
    static boolean canTrigger(Trap trap, int terrain) {
        return trap.active && trap.disarmedByActivation
                && minimumThrowDistance(trap) >= 0
                && (terrain == Terrain.TRAP || terrain == Terrain.SECRET_TRAP);
    }

    /** Return a preferred corridor approach; zero allows an off-axis stand. */
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
        // Exactly two walkable cardinal openings also includes L-shaped corridors.
        // The other two sides must be walls, not unseen pits or other hazards.
        int first = -1;
        int second = -1;
        for (int offset : new int[] {-width, 1, width, -1}) {
            int adjacent = cell + offset;
            if (passable[adjacent] && !pit[adjacent]) {
                if (first < 0) first = adjacent;
                else if (second < 0) second = adjacent;
                else return 0;
            } else if (!solid[adjacent]) {
                return 0;
            }
        }
        return second < 0 ? 0 : separatedSide(first, second, cell, passable, pit,
                safe, visited, mapped, visible, distances);
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

    /** Find a route around corners without stepping closer to the gas. */
    static int gasEscapeStep(int trapCell, int origin, int width,
                               boolean[] safe, int minimumDistance) {
        if (width < 3 || safe == null || safe.length % width != 0
                || trapCell < 0 || trapCell >= safe.length
                || origin < 0 || origin >= safe.length || !safe[origin]
                || minimumDistance < 1) {
            throw new IllegalArgumentException("Invalid gas escape map");
        }
        int originDistance = gridDistance(origin, trapCell, width);
        int[] firstSteps = new int[safe.length];
        Arrays.fill(firstSteps, -1);
        int[] routeSteps = new int[safe.length];
        int[] queue = new int[safe.length];
        int head = 0;
        int tail = 1;
        queue[0] = origin;
        firstSteps[origin] = origin;
        while (head < tail) {
            int cell = queue[head++];
            int distance = gridDistance(cell, trapCell, width);
            // Breadth-first search gives the shortest escape; no full-floor scan.
            if (distance >= minimumDistance && distance > originDistance) {
                return firstSteps[cell];
            }
            if (routeSteps[cell] >= MAX_ESCAPE_STEPS) continue;
            int x = cell % width;
            int y = cell / width;
            for (int dy = -1; dy <= 1; dy++) {
                int nextY = y + dy;
                if (nextY < 0 || nextY >= safe.length / width) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    int nextX = x + dx;
                    if ((dx == 0 && dy == 0) || nextX < 0 || nextX >= width) continue;
                    int next = nextX + nextY * width;
                    if (!safe[next] || firstSteps[next] >= 0
                            || gridDistance(next, trapCell, width) < originDistance
                            || (dx != 0 && dy != 0 && !safe[cell + dx]
                                    && !safe[cell + dy * width])) continue;
                    firstSteps[next] = cell == origin ? next : firstSteps[cell];
                    routeSteps[next] = routeSteps[cell] + 1;
                    queue[tail++] = next;
                }
            }
        }
        return -1;
    }

    private static int gridDistance(int a, int b, int width) {
        return Math.max(Math.abs(a % width - b % width),
                Math.abs(a / width - b / width));
    }

    private static boolean hasGasRetreat(Level level, int trapCell, int stand,
                                           boolean[] safe) {
        int distance = level.distance(stand, trapCell);
        return distance >= MAX_THROW_DISTANCE
                || gasEscapeStep(trapCell, stand, level.width(), safe,
                        Math.max(GAS_ESCAPE_DISTANCE, distance + 1)) >= 0;
    }

    /** Find a reachable throwing cell with a true line of fire, even around a bend. */
    private static int nearbyStand(Level level, int trapCell, int target, int minimum,
                                   boolean gas, Trap trap, boolean[] safe, int[] distances) {
        int width = level.width();
        int centerX = trapCell % width;
        int centerY = trapCell / width;
        int best = -1;
        int shortest = Integer.MAX_VALUE;
        for (int dy = -MAX_THROW_DISTANCE; dy <= MAX_THROW_DISTANCE; dy++) {
            int y = centerY + dy;
            if (y < 1 || y >= level.length() / width - 1) continue;
            for (int dx = -MAX_THROW_DISTANCE; dx <= MAX_THROW_DISTANCE; dx++) {
                int x = centerX + dx;
                if (x < 1 || x >= width - 1) continue;
                int stand = x + y * width;
                if (level.distance(stand, trapCell) < minimum
                        || !level.passable[stand] || !safe[stand]
                        || distances[stand] == Integer.MAX_VALUE || distances[stand] >= shortest
                        || (target != trapCell && stand == target)) continue;
                if (new Ballistica(stand, target, Ballistica.PROJECTILE).collisionPos != target
                        || !standSafeForEffect(level, trap, stand)
                        || (gas && !hasGasRetreat(level, trapCell, stand, safe))) {
                    continue;
                }
                best = stand;
                shortest = distances[stand];
            }
        }
        return best;
    }

    private static int throwingStand(Level level, int trapCell, int side, int minimum,
                                     boolean gas, Trap trap, boolean[] safe, int[] distances) {
        int best = -1;
        int shortest = Integer.MAX_VALUE;
        for (int steps = 1; steps <= MAX_THROW_DISTANCE; steps++) {
            int stand = trapCell + side * steps;
            if (!level.insideMap(stand) || !level.passable[stand] || !safe[stand]) break;
            if (steps < minimum || distances[stand] == Integer.MAX_VALUE
                    || distances[stand] >= shortest
                    || !standSafeForEffect(level, trap, stand)
                    || (gas && !hasGasRetreat(level, trapCell, stand, safe))) continue;
            // Terrain and characters may stop an item before it presses the trap.
            if (new Ballistica(stand, trapCell, Ballistica.PROJECTILE).collisionPos != trapCell) {
                continue;
            }
            best = stand;
            shortest = distances[stand];
        }
        return best >= 0 ? best
                : nearbyStand(level, trapCell, trapCell, minimum, gas, trap, safe, distances);
    }

    /** A thrown item lands on the closed door and keeps it open as a floor heap. */
    private static int doorStand(Level level, int trapCell, int side, int minimum,
                                 boolean[] safe, int[] distances) {
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
            if (stand == door || level.map[stand] == Terrain.DOOR
                    || distances[stand] == Integer.MAX_VALUE || distances[stand] >= shortest) continue;
            if (new Ballistica(stand, door, Ballistica.PROJECTILE).collisionPos != door) {
                continue;
            }
            best = stand;
            shortest = distances[stand];
        }
        return best >= 0 ? best
                : nearbyStand(level, trapCell, door, minimum, false, null, safe, distances);
    }

    private static int closedDoor(Level level, int trapCell, int side) {
        for (int steps = 1; steps <= MAX_THROW_DISTANCE; steps++) {
            int cell = trapCell + side * steps;
            if (!level.insideMap(cell) || !level.passable[cell]) break;
            if (level.map[cell] == Terrain.DOOR) return cell;
        }
        return -1;
    }

    private static boolean gasNear(Level level, Blob gas, int cell) {
        if (gas.cur == null || gas.cur.length != level.length()) {
            throw new IllegalStateException("Invalid toxic gas state");
        }
        for (int offset : PathFinder.NEIGHBOURS9) {
            int adjacent = cell + offset;
            if (level.insideMap(adjacent) && gas.cur[adjacent] > 0) return true;
        }
        return false;
    }

    private static Blob activeTriggeredGas(Level level) {
        if (gasOrigin < 0) return null;
        if (gasType == null) throw new IllegalStateException("Gas origin without gas type");
        Blob gas = level.blobs.get(gasType);
        if (gas == null || gas.volume <= 0) {
            gasOrigin = -1;
            gasType = null;
            return null;
        }
        return gas;
    }

    /** Retreat before gas expands onto the hero's current tile. */
    static int gasRetreatStep(Hero hero, boolean[] safe) {
        Level level = Dungeon.level;
        Blob gas = activeTriggeredGas(level);
        if (gas == null || hero.rooted) return -1;
        int distance = level.distance(hero.pos, gasOrigin);
        if (distance >= MAX_THROW_DISTANCE && !gasNear(level, gas, hero.pos)) return -1;
        return gasEscapeStep(gasOrigin, hero.pos, level.width(), safe, distance + 1);
    }

    /** Keep later auto-play tasks from immediately returning toward active gas. */
    static void avoidGasReturn(Hero hero, boolean[] safe) {
        Level level = Dungeon.level;
        if (activeTriggeredGas(level) == null) return;
        int minimumDistance = Math.min(MAX_THROW_DISTANCE,
                level.distance(hero.pos, gasOrigin));
        for (int cell = 0; cell < safe.length; cell++) {
            if (cell != hero.pos && level.distance(cell, gasOrigin) < minimumDistance) {
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
        boolean bestFrost = false;
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
            else if (!safeToActivate(level, trap)) reason = "chain-reaction";
            else if (safe[cell]) reason = "traversable";
            else if (Actor.findChar(cell) != null) reason = "occupied";
            else if (level.plants.get(cell) != null) reason = "plant";
            else {
                int side = reachableSide(cell, level.width(), level.solid, level.passable,
                        level.pit, safe, level.visited, level.mapped, hero.fieldOfView, distances);
                int minimum = minimumThrowDistance(trap);
                boolean gas = spreadingGas(trap) != null;
                int stand = side == 0
                        ? nearbyStand(level, cell, cell, minimum, gas, trap, safe, distances)
                        : throwingStand(level, cell, side, minimum, gas, trap, safe, distances);
                // A short-range gas throw is only valid with a known escape route.
                if (stand < 0 && gas) {
                    // Corrosive gas needs extra distance even on a retreat-capable route.
                    int fallback = trap.getClass() == CorrosionTrap.class ? 2 : 1;
                    stand = side == 0
                            ? nearbyStand(level, cell, cell, fallback, true, trap, safe, distances)
                            : throwingStand(level, cell, side, fallback, true, trap, safe, distances);
                }
                int target = cell;
                boolean frost = false;
                if (stand < 0 && fireTrap(trap)) {
                    PotionOfFrost potion = hero.belongings.getItem(PotionOfFrost.class);
                    if (potion != null && potion.quantity() > 0 && potion.isKnown()) {
                        stand = side == 0
                                ? nearbyStand(level, cell, cell, 1, false, trap, safe, distances)
                                : throwingStand(level, cell, side, 1, false, trap, safe, distances);
                        frost = stand >= 0;
                    }
                }
                if (stand < 0 && side != 0) {
                    // Door opening alone cannot activate gas; recheck on the next turn.
                    stand = doorStand(level, cell, side, 1, safe, distances);
                    if (stand >= 0) target = closedDoor(level, cell, side);
                }
                if (stand < 0) reason = "no-safe-stand";
                else if (hero.rooted && (stand != hero.pos || gas)) reason = "rooted";
                else if (distances[stand] < shortest) {
                    chosen = trap;
                    bestStand = stand;
                    bestTarget = target;
                    bestFrost = frost;
                    shortest = distances[stand];
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
        Item item = bestFrost ? hero.belongings.getItem(PotionOfFrost.class)
                : chooseThrowItem(hero.belongings.backpack, candidate -> candidate.isEquipped(hero));
        // If no spare item exists, a known frost potion can still clear a fire trap.
        if (item == null && bestTarget == chosen.pos && fireTrap(chosen)) {
            item = hero.belongings.getItem(PotionOfFrost.class);
        }
        if (item == null || (item instanceof PotionOfFrost
                && (!((PotionOfFrost) item).isKnown() || item.quantity() <= 0))) {
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
                + " target=" + bestTarget + " action="
                + (openDoor ? "open-door" : item instanceof PotionOfFrost ? "frost" : "throw"));
        if (openDoor) {
            heldDoor = bestTarget;
            heldDoorTrap = chosen.pos;
        } else if (spreadingGas(chosen) != null) {
            gasOrigin = chosen.pos;
            gasType = spreadingGas(chosen);
        }
        item.setCurrent(hero);
        item.cast(hero, bestTarget);
        return Action.THROW;
    }
}
