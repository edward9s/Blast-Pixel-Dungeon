package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.plants.Firebloom;
import com.shatteredpixel.shatteredpixeldungeon.plants.Icecap;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sorrowmoss;
import com.shatteredpixel.shatteredpixeldungeon.plants.Starflower;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;

/** Regression checks for Regrowth farming and ordinary auto-play harvest targets. */
public final class BlastAutoFarmingTest {
    private static void plantingReady(boolean expected, float now,
                                      float nextBombAt, boolean cloudActive) {
        if (BlastAutoFarming.plantingReady(now, nextBombAt, cloudActive) != expected) {
            throw new AssertionError("Unexpected farming cooldown decision");
        }
    }

    private static void crop(boolean expected, int terrain,
                             com.shatteredpixel.shatteredpixeldungeon.plants.Plant plant) {
        if (BlastAutoHarvest.harvestable(terrain, plant) != expected) {
            throw new AssertionError("Unexpected farming harvest eligibility");
        }
    }

    public static void main(String[] args) {
        // Bomb readiness affects planting only; basic auto-play harvest is independent.
        if (BlastAutoFarming.regrowthReady(0, 0)
                || BlastAutoFarming.regrowthReady(1, -1)
                || !BlastAutoFarming.regrowthReady(1, 0)) {
            throw new AssertionError("Planting must require an available Regrowth bomb");
        }

        // Plant without a prior harvest; guard subsequent blasts by time and cloud.
        plantingReady(true, 0f, Float.NEGATIVE_INFINITY, false);
        plantingReady(false, 10f, 20f, false);
        plantingReady(true, 20f, 20f, false);
        plantingReady(false, 30f, 20f, true);
        plantingReady(true, 30f, 20f, false);

        // Rebased Actor time must not extend the twenty-turn farming cooldown.
        float beforeSave = BlastAutoFarming.gameTime(40f, 100f);
        float afterSave = BlastAutoFarming.gameTime(130f, 10f);
        if (beforeSave != afterSave
                || BlastAutoFarming.plantingReady(afterSave + 19f, beforeSave + 20f, false)
                || !BlastAutoFarming.plantingReady(afterSave + 20f, beforeSave + 20f, false)) {
            throw new AssertionError("Farming cooldown changed after Actor.fixTime");
        }

        // Local geometric filter matches the blast footprint and radius upgrades.
        if (!BlastAutoFarming.withinBlastRadius(3, 0, 3)
                || BlastAutoFarming.withinBlastRadius(3, 3, 3)
                || BlastAutoFarming.withinBlastRadius(4, 0, 3)
                || !BlastAutoFarming.withinBlastRadius(4, 0, 5)) {
            throw new AssertionError("Incorrect Regrowth blast radius filter");
        }
        try {
            BlastAutoFarming.withinBlastRadius(0, 0, -1);
            throw new AssertionError("Invalid Regrowth radius was accepted");
        } catch (IllegalArgumentException expected) {
            // Invalid parameters must fail immediately.
        }

        // Require at least ten plantable cells under the actual blast.
        if (BlastAutoFarming.sufficientArea(0)
                || BlastAutoFarming.sufficientArea(9)
                || !BlastAutoFarming.sufficientArea(10)
                || !BlastAutoFarming.sufficientArea(15)) {
            throw new AssertionError("Incorrect minimum farming area threshold");
        }
        for (int ground : new int[]{Terrain.EMPTY, Terrain.EMPTY_DECO,
                Terrain.EMBERS, Terrain.GRASS, Terrain.FURROWED_GRASS, Terrain.HIGH_GRASS}) {
            if (!BlastAutoFarming.canHostRegrowth(ground)) {
                throw new AssertionError("Expected plantable terrain: " + ground);
            }
        }
        for (int ground : new int[]{Terrain.WALL, Terrain.CHASM, Terrain.WATER,
                Terrain.EXIT, Terrain.ENTRANCE,
                Terrain.LOCKED_DOOR, Terrain.TRAP, Terrain.EMPTY_WELL}) {
            if (BlastAutoFarming.canHostRegrowth(ground)) {
                throw new AssertionError("Non-plantable terrain: " + ground);
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
