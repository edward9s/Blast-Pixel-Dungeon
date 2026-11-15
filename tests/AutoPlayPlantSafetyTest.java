package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.plants.BlandfruitBush;
import com.shatteredpixel.shatteredpixeldungeon.plants.Blindweed;
import com.shatteredpixel.shatteredpixeldungeon.plants.Earthroot;
import com.shatteredpixel.shatteredpixeldungeon.plants.Fadeleaf;
import com.shatteredpixel.shatteredpixeldungeon.plants.Firebloom;
import com.shatteredpixel.shatteredpixeldungeon.plants.Icecap;
import com.shatteredpixel.shatteredpixeldungeon.plants.Mageroyal;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sorrowmoss;
import com.shatteredpixel.shatteredpixeldungeon.plants.Starflower;
import com.shatteredpixel.shatteredpixeldungeon.plants.Stormvine;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import com.shatteredpixel.shatteredpixeldungeon.plants.Swiftthistle;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfRegrowth;

/** Verify auto-play avoids detrimental plants and permits beneficial plants. */
public final class AutoPlayPlantSafetyTest {
    private static void harmful(boolean expected, Plant plant) {
        if (AutoPlayPlantSafety.isHarmful(plant) != expected) {
            throw new AssertionError("Incorrect plant hazard: "
                    + (plant == null ? "null" : plant.getClass().getName()));
        }
    }

    public static void main(String[] args) {
        harmful(true, new Blindweed());
        harmful(true, new Fadeleaf());
        harmful(true, new Firebloom());
        harmful(true, new Icecap());
        harmful(true, new Rotberry());
        harmful(true, new Sorrowmoss());
        harmful(true, new Stormvine());

        harmful(false, null);
        harmful(false, new BlandfruitBush());
        harmful(false, new Earthroot());
        harmful(false, new Mageroyal());
        harmful(false, new Starflower());
        harmful(false, new Sungrass());
        harmful(false, new Swiftthistle());
        harmful(false, new WandOfRegrowth.Dewcatcher());
        harmful(false, new WandOfRegrowth.Seedpod());
        System.out.println("AutoPlayPlantSafetyTest passed");
    }
}
