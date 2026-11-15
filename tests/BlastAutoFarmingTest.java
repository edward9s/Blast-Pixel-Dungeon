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
        // First blast is allowed, but the next waits for at least one harvest
        // and the end of the previous Regrowth cloud.
        plantAgain(true, false, false, false);
        plantAgain(false, true, false, true);
        plantAgain(false, true, false, false);
        plantAgain(false, true, true, true);
        plantAgain(true, true, true, false);

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
