package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClothArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.BurningTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.CursingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ExplosiveTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FlashingTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.OozeTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.PoisonDartTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.ToxicTrap;

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
        equal(1, BlastAutoTrapClearer.minimumThrowDistance(new FlashingTrap()), "local trap");
        equal(1, BlastAutoTrapClearer.minimumThrowDistance(new CursingTrap()), "item curse");
        equal(2, BlastAutoTrapClearer.minimumThrowDistance(new OozeTrap()), "adjacent ooze");
        equal(4, BlastAutoTrapClearer.minimumThrowDistance(new ToxicTrap()), "spreading toxic gas");
        equal(-1, BlastAutoTrapClearer.minimumThrowDistance(new BurningTrap()), "spreading fire");
        equal(-1, BlastAutoTrapClearer.minimumThrowDistance(new ExplosiveTrap()), "blast");
        equal(-1, BlastAutoTrapClearer.minimumThrowDistance(new PoisonDartTrap()), "remote dart");
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
        ToxicTrap hiddenGas = new ToxicTrap();
        hiddenGas.visible = false;
        equal(1, BlastAutoTrapClearer.canTrigger(hiddenGas, Terrain.SECRET_TRAP) ? 1 : 0,
                "hidden gas trap eligible with a safe retreat");
        hiddenGas.active = false;
        equal(0, BlastAutoTrapClearer.canTrigger(hiddenGas, Terrain.SECRET_TRAP) ? 1 : 0,
                "inactive gas trap remains excluded");

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
        System.out.println("BlastAutoTrapClearerTest passed");
    }
}
