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
        check(!BlastAutoSpecialRooms.finishFlight(true, true, false),
                "do not abandon a newly started crossing at the entrance");
        check(BlastAutoSpecialRooms.finishFlight(true, false, false),
                "complete the crossing at the entrance after opening the chest");
        check(BlastAutoSpecialRooms.finishFlight(true, true, true),
                "cancel at the entrance when floating is about to expire");
        check(!BlastAutoSpecialRooms.finishFlight(false, false, true),
                "retreat is still required while inside the room");
        System.out.println("BlastAutoSpecialRoomsTest passed");
    }
}
