package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.plants.Blindweed;
import com.shatteredpixel.shatteredpixeldungeon.plants.Fadeleaf;
import com.shatteredpixel.shatteredpixeldungeon.plants.Firebloom;
import com.shatteredpixel.shatteredpixeldungeon.plants.Icecap;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sorrowmoss;
import com.shatteredpixel.shatteredpixeldungeon.plants.Stormvine;

/** SPD plants that inflict hazards or disrupt automatic movement when triggered. */
public final class AutoPlayPlantSafety {
    private AutoPlayPlantSafety() {}

    public static boolean isHarmful(Plant plant) {
        return plant instanceof Blindweed
                || plant instanceof Fadeleaf
                || plant instanceof Firebloom
                || plant instanceof Icecap
                || plant instanceof Rotberry
                || plant instanceof Sorrowmoss
                || plant instanceof Stormvine;
    }
}
