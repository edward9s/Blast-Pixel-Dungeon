package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.plants.Firebloom;
import com.shatteredpixel.shatteredpixeldungeon.plants.Icecap;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sorrowmoss;
import com.shatteredpixel.shatteredpixeldungeon.plants.Starflower;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;

/** Regression checks for Blast-only farming decisions. */
public final class BlastAutoFarmingTest {
    private static void plantAgain(boolean expected, boolean planted,
                                   boolean harvested, boolean cloudActive) {
        if (BlastAutoFarming.canPlantAgain(planted, harvested, cloudActive) != expected) {
            throw new AssertionError("Unexpected farming cycle decision");
        }
    }

    private static void crop(boolean expected, int terrain,
                             com.shatteredpixel.shatteredpixeldungeon.plants.Plant plant) {
        if (BlastAutoFarming.harvestable(terrain, plant) != expected) {
            throw new AssertionError("Unexpected farming harvest eligibility");
        }
    }

    public static void main(String[] args) {
        // Bomb readiness affects new planting only; harvest runs without bombs.
        if (BlastAutoFarming.regrowthReady(0, 0)
                || BlastAutoFarming.regrowthReady(1, -1)
                || !BlastAutoFarming.regrowthReady(1, 0)) {
            throw new AssertionError("Planting must require an available Regrowth bomb");
        }

        // First blast is allowed, but the next waits for at least one harvest
        // and the end of the previous Regrowth cloud.
        plantAgain(true, false, false, false);
        plantAgain(false, true, false, true);
        plantAgain(false, true, false, false);
        plantAgain(false, true, true, true);
        plantAgain(true, true, true, false);

        // Require at least ten reachable, growable cells under the actual blast.
        if (BlastAutoFarming.sufficientArea(0)
                || BlastAutoFarming.sufficientArea(9)
                || !BlastAutoFarming.sufficientArea(10)
                || !BlastAutoFarming.sufficientArea(15)) {
            throw new AssertionError("Incorrect minimum farming area threshold");
        }
        for (int ground : new int[]{Terrain.EMPTY, Terrain.EMPTY_DECO,
                Terrain.EMBERS, Terrain.GRASS, Terrain.FURROWED_GRASS}) {
            if (!BlastAutoFarming.canGrow(ground)) {
                throw new AssertionError("Expected growable terrain: " + ground);
            }
        }
        for (int ground : new int[]{Terrain.WALL, Terrain.CHASM, Terrain.WATER,
                Terrain.EXIT, Terrain.ENTRANCE, Terrain.HIGH_GRASS,
                Terrain.LOCKED_DOOR, Terrain.TRAP, Terrain.EMPTY_WELL}) {
            if (BlastAutoFarming.canGrow(ground)) {
                throw new AssertionError("Non-growable terrain: " + ground);
            }
        }

        // Harvest targets remain eligible even when no Regrowth bomb is available.
        crop(true, Terrain.HIGH_GRASS, null);
        crop(false, Terrain.GRASS, null);
        crop(false, Terrain.EMPTY, null);
        crop(true, Terrain.EMPTY, new Sungrass());
        crop(true, Terrain.EMPTY, new Starflower());
        crop(false, Terrain.EMPTY, new Firebloom());
        crop(false, Terrain.EMPTY, new Icecap());
        crop(false, Terrain.EMPTY, new Sorrowmoss());

        System.out.println("BlastAutoFarmingTest passed");
    }
}
