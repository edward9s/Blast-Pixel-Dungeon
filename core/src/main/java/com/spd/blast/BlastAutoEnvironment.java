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
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SmokeScreen;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.StenchGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.StormCloud;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.VaultFlameTraps;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Web;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SentryRoom;
import com.watabou.utils.Rect;

import java.util.Arrays;
import java.util.List;

/** Blast auto-play only: exclude active environmental hazards from movement. */
public final class BlastAutoEnvironment {
    static final int MAX_WATER_STEPS = 4;

    private static final List<Class<? extends Blob>> HAZARD_TYPES = Arrays.asList(
            Fire.class, BombFire.class, Inferno.class, VaultFlameTraps.class,
            ToxicGas.class, CorrosiveGas.class, ParalyticGas.class,
            StenchGas.class, ConfusionGas.class, Electricity.class,
            StormCloud.class, Freezing.class, BombFreezing.class, Blizzard.class,
            SmokeScreen.class, Web.class, GooWarn.class);

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

    /** Mark the red sentry's special floor only while its sentry is alive. */
    static boolean[] activeSentryFloors(Level level) {
        boolean[] dangerous = new boolean[level.length()];
        if (!(level instanceof RegularLevel)) return dangerous;

        List<Room> rooms = null;
        for (Mob mob : level.mobs) {
            if (!(mob instanceof SentryRoom.Sentry) || !mob.isAlive()) continue;
            if (rooms == null) rooms = ((RegularLevel) level).rooms();
            for (Room room : rooms) {
                if (room instanceof SentryRoom
                        && room.inside(level.cellToPoint(mob.pos))) {
                    markSentryFloors(level.map, level.width(), room, dangerous);
                    break;
                }
            }
        }
        return dangerous;
    }

    /** Special floors outside the sentry room must remain traversable. */
    static void markSentryFloors(int[] terrain, int width, Rect room, boolean[] dangerous) {
        if (width <= 0 || terrain.length != dangerous.length
                || terrain.length % width != 0 || room.left < 0 || room.top < 0
                || room.right >= width || room.bottom >= terrain.length / width) {
            throw new IllegalArgumentException("Invalid sentry room map");
        }
        for (int y = room.top + 1; y < room.bottom; y++) {
            for (int x = room.left + 1; x < room.right; x++) {
                int cell = x + y * width;
                if (terrain[cell] == Terrain.EMPTY_SP) dangerous[cell] = true;
            }
        }
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
