package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.food.MeatPie;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Pasty;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BlazingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BurningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ChillingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CursingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ExplosiveTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlashingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.OozeTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.PoisonDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ToxicTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ConfusionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CorrosionTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DisarmingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.DisintegrationTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlockTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GeyserTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrimTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.GrippingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ShockingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TeleportationTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.TenguDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WarpingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.WornDartTrap;

import java.util.Arrays;

/** Narrow-corridor topology, sacrifice eligibility, and conservative trap safety. */
public final class BlastAutoTrapClearerTest {
    private static void equal(int expected, int actual, String name) {
        if (expected != actual) throw new AssertionError(name + ": " + expected + " != " + actual);
    }

    private static void same(Item expected, Item actual, String name) {
        if (expected != actual) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        equal(-1, BlastAutoTrapClearer.minimumThrowDistance(new FlashingTrap()),
                "persistent flashing trap cannot be cleared");
        equal(-1, BlastAutoTrapClearer.minimumThrowDistance(new GrippingTrap()),
                "persistent gripping trap cannot be cleared");
        equal(1, BlastAutoTrapClearer.minimumThrowDistance(new CursingTrap()), "item curse");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new OozeTrap()), "adjacent ooze");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new ChillingTrap()),
                "one-cell freezing aura");
        equal(3, BlastAutoTrapClearer.minimumThrowDistance(new FrostTrap()),
                "two-cell freezing aura");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new ToxicTrap()), "escape-capable toxic gas");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new ConfusionTrap()), "confusion gas");
        equal(3, BlastAutoTrapClearer.minimumThrowDistance(new CorrosionTrap()), "corrosive gas");
        equal(1, BlastAutoTrapClearer.minimumThrowDistance(new DisarmingTrap()), "floor disarming");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new GeyserTrap()), "geyser knockback");
        equal(3, BlastAutoTrapClearer.minimumThrowDistance(new ShockingTrap()), "adjacent electricity");
        equal(3, BlastAutoTrapClearer.minimumThrowDistance(new FlockTrap()), "sheep chain");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new TeleportationTrap()), "teleport");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new WarpingTrap()), "warping teleport");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new BurningTrap()), "adjacent fire");
        equal(3, BlastAutoTrapClearer.minimumThrowDistance(new BlazingTrap()), "two-step fire");
        equal(-1, BlastAutoTrapClearer.minimumThrowDistance(new ExplosiveTrap()), "blast");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new WornDartTrap()),
                "aimed dart permits predicted hostile");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new PoisonDartTrap()),
                "aimed poison dart permits predicted hostile");
        equal(-1, BlastAutoTrapClearer.minimumThrowDistance(new TenguDartTrap()),
                "boss-specialized dart uses a different target eligibility rule");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new DisintegrationTrap()),
                "aimed disintegration permits predicted hostile");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new GrimTrap()),
                "aimed grim permits predicted hostile");
        CursingTrap hidden = new CursingTrap();
        hidden.visible = false;
        equal(1, BlastAutoTrapClearer.canTrigger(hidden, Terrain.SECRET_TRAP) ? 1 : 0,
                "hidden active trap is eligible");
        equal(1, BlastAutoTrapClearer.canTrigger(hidden, Terrain.TRAP) ? 1 : 0,
                "revealed active trap is eligible");
        equal(0, BlastAutoTrapClearer.canTrigger(hidden, Terrain.EMPTY) ? 1 : 0,
                "non-trap terrain cannot be triggered");
        hidden.active = false;
        equal(0, BlastAutoTrapClearer.canTrigger(hidden, Terrain.SECRET_TRAP) ? 1 : 0,
                "disarmed hidden trap");
        equal(0, BlastAutoTrapClearer.canTrigger(new ExplosiveTrap(), Terrain.SECRET_TRAP) ? 1 : 0,
                "dangerous hidden trap remains excluded");
        equal(1, BlastAutoTrapClearer.canTrigger(new WornDartTrap(), Terrain.TRAP) ? 1 : 0,
                "dart is conditionally supported");
        equal(1, BlastAutoTrapClearer.canTrigger(new PoisonDartTrap(), Terrain.TRAP) ? 1 : 0,
                "poison dart is conditionally supported");
        equal(1, BlastAutoTrapClearer.canTrigger(new DisintegrationTrap(), Terrain.TRAP) ? 1 : 0,
                "disintegration is conditionally supported");
        equal(1, BlastAutoTrapClearer.canTrigger(new GrimTrap(), Terrain.TRAP) ? 1 : 0,
                "grim is conditionally supported");
        equal(0, BlastAutoTrapClearer.canTrigger(new FlashingTrap(), Terrain.TRAP) ? 1 : 0,
                "flashing trap does not disarm");
        equal(0, BlastAutoTrapClearer.canTrigger(new GrippingTrap(), Terrain.TRAP) ? 1 : 0,
                "gripping trap does not disarm");
        equal(1, BlastAutoTrapClearer.canTrigger(new CorrosionTrap(), Terrain.TRAP) ? 1 : 0,
                "corrosion trap remotely cleared");
        equal(1, BlastAutoTrapClearer.canTrigger(new TeleportationTrap(), Terrain.SECRET_TRAP) ? 1 : 0,
                "hidden teleportation trap remotely cleared");
        BurningTrap hiddenFire = new BurningTrap();
        hiddenFire.visible = false;
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenFire, Terrain.SECRET_TRAP) ? 1 : 0,
                "hidden burning trap eligible");
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenFire, Terrain.TRAP) ? 1 : 0,
                "revealed burning trap eligible");
        BlazingTrap hiddenBlaze = new BlazingTrap();
        hiddenBlaze.visible = false;
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenBlaze, Terrain.SECRET_TRAP) ? 1 : 0,
                "hidden blazing trap eligible");
        hiddenBlaze.active = false;
        equal(0, BlastAutoTrapClearer.canTrigger(hiddenBlaze, Terrain.SECRET_TRAP) ? 1 : 0,
                "inactive blazing trap remains excluded");
        ToxicTrap hiddenGas = new ToxicTrap();
        hiddenGas.visible = false;
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenGas, Terrain.SECRET_TRAP) ? 1 : 0,
                "hidden gas trap eligible with a safe retreat");
        hiddenGas.active = false;
        equal(0, BlastAutoTrapClearer.canTrigger(hiddenGas, Terrain.SECRET_TRAP) ? 1 : 0,
                "inactive gas trap remains excluded");

        ChillingTrap hiddenChill = new ChillingTrap();
        hiddenChill.visible = false;
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenChill, Terrain.SECRET_TRAP) ? 1 : 0,
                "hidden chilling trap eligible");
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenChill, Terrain.TRAP) ? 1 : 0,
                "revealed chilling trap eligible");
        hiddenChill.active = false;
        equal(0, BlastAutoTrapClearer.canTrigger(hiddenChill, Terrain.SECRET_TRAP) ? 1 : 0,
                "inactive chilling trap excluded");
        FrostTrap hiddenFrost = new FrostTrap();
        hiddenFrost.visible = false;
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenFrost, Terrain.SECRET_TRAP) ? 1 : 0,
                "hidden frost trap eligible");
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenFrost, Terrain.TRAP) ? 1 : 0,
                "revealed frost trap eligible");
        hiddenFrost.active = false;
        equal(0, BlastAutoTrapClearer.canTrigger(hiddenFrost, Terrain.SECRET_TRAP) ? 1 : 0,
                "inactive frost trap excluded");

        int width = 7;
        int trap = 3 + 3 * width;
        boolean[] solid = new boolean[width * width];
        boolean[] passable = new boolean[width * width];
        boolean[] pit = new boolean[width * width];
        boolean[] safe = new boolean[width * width];
        boolean[] visible = new boolean[width * width];
        boolean[] visited = new boolean[width * width];
        boolean[] mapped = new boolean[width * width];
        int[] distances = new int[width * width];
        Arrays.fill(solid, true);
        Arrays.fill(distances, Integer.MAX_VALUE);
        for (int y = 1; y < width - 1; y++) {
            int cell = 3 + y * width;
            solid[cell] = false;
            passable[cell] = true;
            safe[cell] = true;
            visible[cell] = true;
        }
        safe[trap] = false;
        distances[trap - width] = 1;
        equal(-width, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "blocking corridor");
        distances[trap + width] = 3;
        equal(0, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "alternate access");
        distances[trap + width] = Integer.MAX_VALUE;
        safe[trap + width] = false;
        equal(0, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "hazard on far side");
        safe[trap + width] = false;
        visible[trap + width] = false;
        equal(-width, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "unexplored exit allows trap clearance");
        passable[trap + width] = false;
        equal(0, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "exit wall cannot be cleared");
        passable[trap + width] = true;
        pit[trap + width] = true;
        equal(0, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "unexplored chasm is not an exit");
        pit[trap + width] = false;
        // A known dangerous exit remains excluded.
        mapped[trap + width] = true;
        equal(0, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "known hazardous exit");
        safe[trap + width] = true;
        visited[trap - width] = true;
        visible[trap - width] = false;
        equal(-width, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "remembered corridor sides");
        visible[trap + width] = true;
        solid[trap - 1] = false;
        equal(0, BlastAutoTrapClearer.reachableSide(trap, width, solid, passable, pit, safe,
                visited, mapped, visible, distances), "room instead of corridor");

        // A true elbow has two perpendicular open sides and two blocking walls.
        boolean[] elbowSolid = new boolean[width * width];
        boolean[] elbowPassable = new boolean[width * width];
        boolean[] elbowPit = new boolean[width * width];
        boolean[] elbowSafe = new boolean[width * width];
        boolean[] elbowVisited = new boolean[width * width];
        boolean[] elbowMapped = new boolean[width * width];
        boolean[] elbowVisible = new boolean[width * width];
        int[] elbowDistance = new int[width * width];
        Arrays.fill(elbowSolid, true);
        Arrays.fill(elbowDistance, Integer.MAX_VALUE);
        int approach = trap - width;
        int exit = trap + 1;
        elbowSolid[approach] = elbowSolid[trap] = elbowSolid[exit] = false;
        elbowPassable[approach] = elbowPassable[exit] = true;
        elbowSafe[approach] = elbowVisible[approach] = true;
        elbowDistance[approach] = 1;
        equal(-width, BlastAutoTrapClearer.reachableSide(trap, width, elbowSolid,
                elbowPassable, elbowPit, elbowSafe, elbowVisited, elbowMapped,
                elbowVisible, elbowDistance), "unknown exit after right-angle bend");
        elbowDistance[exit] = 3;
        equal(0, BlastAutoTrapClearer.reachableSide(trap, width, elbowSolid,
                elbowPassable, elbowPit, elbowSafe, elbowVisited, elbowMapped,
                elbowVisible, elbowDistance), "elbow with alternate route");
        elbowDistance[exit] = Integer.MAX_VALUE;
        elbowPassable[trap - 1] = true;
        elbowSolid[trap - 1] = false;
        equal(0, BlastAutoTrapClearer.reachableSide(trap, width, elbowSolid,
                elbowPassable, elbowPit, elbowSafe, elbowVisited, elbowMapped,
                elbowVisible, elbowDistance), "three-way junction is not a choke point");

        // A bend after the throwing cell must still allow a sideways first escape step.
        int gasWidth = 11;
        int gasTrap = 5 + 5 * gasWidth;
        int gasStand = 5 + 7 * gasWidth;
        boolean[] gasSafe = new boolean[gasWidth * gasWidth];
        gasSafe[gasStand] = true;
        gasSafe[6 + 7 * gasWidth] = true;
        gasSafe[7 + 7 * gasWidth] = true;
        gasSafe[8 + 7 * gasWidth] = true;
        equal(-1, BlastAutoTrapClearer.gasEscapeStep(gasTrap, gasStand,
                gasWidth, gasSafe, 4), "short dead-end cannot outrun gas");
        gasSafe[9 + 7 * gasWidth] = true;
        equal(6 + 7 * gasWidth, BlastAutoTrapClearer.gasEscapeStep(gasTrap, gasStand,
                gasWidth, gasSafe, 4), "retreat may begin sideways around an elbow");
        // When only the adjacent throwing cell is visible, retreat straight back first.
        int closeStand = 5 + 6 * gasWidth;
        gasSafe[closeStand] = true;
        equal(gasStand, BlastAutoTrapClearer.gasEscapeStep(gasTrap, closeStand,
                gasWidth, gasSafe, 4), "close toxic throw has an immediate escape step");
        // No diagonal escape through two impassable orthogonal neighbors.
        boolean[] blockedCorner = new boolean[gasWidth * gasWidth];
        blockedCorner[gasStand] = true;
        blockedCorner[6 + 8 * gasWidth] = true;
        blockedCorner[7 + 9 * gasWidth] = true;
        equal(-1, BlastAutoTrapClearer.gasEscapeStep(gasTrap, gasStand,
                gasWidth, blockedCorner, 4), "blocked diagonal corner is not an exit");

        // Exclude tied protected characters, out-of-range shots, and closer heroes.
        BlastAutoTrapClearer.AimedTargets aim = new BlastAutoTrapClearer.AimedTargets();
        equal(0, aim.safeToFire(6.5f, Float.POSITIVE_INFINITY) ? 1 : 0,
                "no valid target");
        aim.consider(2f, true);
        equal(1, aim.safeToFire(6.5f, 3f) ? 1 : 0,
                "closest hostile selected");
        equal(0, aim.safeToFire(6.5f, 2f) ? 1 : 0,
                "hero at equal distance must not be shot");
        equal(0, aim.safeToFire(6.5f, 1.5f) ? 1 : 0,
                "hero closer than hostile must not be shot");
        aim.consider(2f, true);
        equal(1, aim.safeToFire(6.5f, 3f) ? 1 : 0,
                "multiple equally close hostiles are safe");
        aim.consider(2f, false);
        equal(0, aim.safeToFire(6.5f, 3f) ? 1 : 0,
                "tied protected character vetoes firing regardless of order");
        aim.consider(1f, true);
        equal(1, aim.safeToFire(6.5f, 3f) ? 1 : 0,
                "strictly closer hostile overrides previously tied characters");
        aim.consider(0.5f, false);
        equal(0, aim.safeToFire(6.5f, 3f) ? 1 : 0,
                "nearest protected character vetoes firing");

        BlastAutoTrapClearer.AimedTargets distant = new BlastAutoTrapClearer.AimedTargets();
        distant.consider(7f, true);
        equal(0, distant.safeToFire(6.5f, 10f) ? 1 : 0,
                "no ranged shot beyond native targeting distance");
        distant.consider(6.5f, true);
        equal(1, distant.safeToFire(6.5f, Float.POSITIVE_INFINITY) ? 1 : 0,
                "invisible hero outside line of fire cannot be hit");
        distant.consider(6.5f, false);
        equal(0, distant.safeToFire(6.5f, Float.POSITIVE_INFINITY) ? 1 : 0,
                "invisible or protected character at maximum range vetoes tie");

        ClothArmor armor = new ClothArmor();
        WornShortsword sword = new WornShortsword();
        Food small = new Food();
        Food large = new Food();
        small.quantity(2);
        large.quantity(7);
        Item[] inventory = {small, armor, large, sword};
        same(armor, BlastAutoTrapClearer.chooseThrowItem(Arrays.asList(inventory),
                item -> false), "spare armor selected before food");
        same(sword, BlastAutoTrapClearer.chooseThrowItem(Arrays.asList(inventory),
                item -> item == armor), "equipped armor excluded");
        same(large, BlastAutoTrapClearer.chooseThrowItem(Arrays.asList(inventory),
                item -> item == armor || item == sword), "largest food stack");
        same(null, BlastAutoTrapClearer.chooseThrowItem(Arrays.asList(inventory),
                item -> true), "no permitted inventory");
        Pasty pasty = new Pasty();
        MeatPie pie = new MeatPie();
        Food reservedRation = new Food();
        reservedRation.quantity(1);
        same(null, BlastAutoTrapClearer.chooseThrowItem(
                Arrays.asList(pasty, pie, reservedRation), item -> false),
                "preserve meat pie and matching ration for cooking");
        reservedRation.quantity(3);
        same(reservedRation, BlastAutoTrapClearer.chooseThrowItem(
                Arrays.asList(pasty, pie, reservedRation), item -> false),
                "spare ration may be sacrificed after pie reserve");
        System.out.println("BlastAutoTrapClearerTest passed");
    }
}
