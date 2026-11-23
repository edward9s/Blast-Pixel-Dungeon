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

        // The shared rule must not reclaim the entrance when a room is completed
        // or its remaining objective is unreachable.
        boolean[] safe = {true, true, true, true, true, true};
        int[] distances = {0, 1, 2, 3, 4, 5};
        check(BlastAutoSpecialRooms.canApproachEntrance(5, 2, 0, false, safe, distances),
                "known reachable objective permits entrance approach");
        check(!BlastAutoSpecialRooms.canApproachEntrance(-1, 2, 0, false, safe, distances),
                "no objective means no return to the room entrance");
        check(!BlastAutoSpecialRooms.canApproachEntrance(5, 2, 2, false, safe, distances),
                "already at entrance");
        check(!BlastAutoSpecialRooms.canApproachEntrance(5, 2, 0, true, safe, distances),
                "rooted hero cannot approach");
        safe[2] = false;
        check(!BlastAutoSpecialRooms.canApproachEntrance(5, 2, 0, false, safe, distances),
                "blocked entrance cannot be approached");
        safe[2] = true;
        distances[2] = Integer.MAX_VALUE;
        check(!BlastAutoSpecialRooms.canApproachEntrance(5, 2, 0, false, safe, distances),
                "unreachable entrance cannot be approached");
        try {
            BlastAutoSpecialRooms.canApproachEntrance(6, 2, 0, false, safe, distances);
            throw new AssertionError("out-of-range objective must fail");
        } catch (IllegalArgumentException expected) {
            // Invalid navigation state is a programming error.
        }

        System.out.println("BlastAutoSpecialRoomsTest passed");
    }
}
