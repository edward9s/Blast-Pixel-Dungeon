package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;

/** Headless checks for special-room airborne travel restrictions. */
public final class BlastAutoSpecialRoomsTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(BlastAutoSpecialRooms.flightTerrain(Terrain.TRAP), "revealed trap");
        check(BlastAutoSpecialRooms.flightTerrain(Terrain.SECRET_TRAP), "hidden trap");
        check(BlastAutoSpecialRooms.flightTerrain(Terrain.CHASM), "chasm");
        check(!BlastAutoSpecialRooms.flightTerrain(Terrain.BARRICADE), "barricade excluded");
        check(!BlastAutoSpecialRooms.flightTerrain(Terrain.WALL), "walls excluded");
        check(!BlastAutoSpecialRooms.flightTerrain(Terrain.EMPTY_SP), "sentry excluded");
        System.out.println("BlastAutoSpecialRoomsTest passed");
    }
}
