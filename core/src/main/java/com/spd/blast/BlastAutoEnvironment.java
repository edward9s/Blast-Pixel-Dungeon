package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blizzard;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ConfusionGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.CorrosiveGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Electricity;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Fire;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Freezing;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.GooWarn;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Inferno;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ParalyticGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SacrificialFire;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SmokeScreen;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.StenchGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.StormCloud;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.VaultFlameTraps;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Web;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

import java.util.Arrays;
import java.util.List;

/** Blast auto-play only: exclude active environmental hazards from movement. */
public final class BlastAutoEnvironment {
    static final int MAX_WATER_STEPS = 4;

    private static final List<Class<? extends Blob>> HAZARD_TYPES = Arrays.asList(
            Fire.class, Inferno.class, VaultFlameTraps.class,
            ToxicGas.class, CorrosiveGas.class, ParalyticGas.class,
            StenchGas.class, ConfusionGas.class, Electricity.class,
            StormCloud.class, Freezing.class, Blizzard.class,
            SmokeScreen.class, Web.class, GooWarn.class, SacrificialFire.class);

    private BlastAutoEnvironment() {}

    /** Resolve active blobs once per actor turn, not once per cell. */
    static Blob[] activeHazards(Level level) {
        Blob[] active = new Blob[HAZARD_TYPES.size()];
        int count = 0;
        for (Class<? extends Blob> type : HAZARD_TYPES) {
            Blob blob = level.blobs.get(type);
            if (blob == null || blob.volume <= 0) continue;
            if (blob.cur == null || blob.cur.length != level.length()) {
                throw new IllegalStateException("Invalid environmental blob: " + type.getName());
            }
            active[count++] = blob;
        }
        return active;
    }

    /** Positive blob volume at a cell marks it unsuitable for automatic movement. */
    static boolean hazardousAt(int cell, Blob[] hazards) {
        for (Blob blob : hazards) {
            if (blob == null) break;
            if (blob.cur[cell] > 0) return true;
        }
        return false;
    }

    /**
     * Pick the nearest safe water cell using the auto-play distance map.
     * Never seek water farther than four moves; an unreachable or unsafe
     * water cell is not an extinguishing destination.
     */
    static int nearbyWater(int origin, boolean[] water, boolean[] safe, int[] distances) {
        if (water.length != safe.length || safe.length != distances.length) {
            throw new IllegalArgumentException("Inconsistent water navigation arrays");
        }
        if (water[origin]) return -1;
        int target = -1;
        int shortest = MAX_WATER_STEPS + 1;
        for (int cell = 0; cell < water.length; cell++) {
            if (water[cell] && safe[cell] && distances[cell] > 0
                    && distances[cell] < shortest) {
                shortest = distances[cell];
                target = cell;
            }
        }
        return target;
    }
}
