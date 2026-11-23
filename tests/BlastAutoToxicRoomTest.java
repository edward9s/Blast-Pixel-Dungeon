package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;

/** Deterministic checks for the permanent toxic-room vent and doorway policy. */
public final class BlastAutoToxicRoomTest {
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        check(BlastAutoToxicRoom.sourceTerrain(Terrain.INACTIVE_TRAP, true, true),
                "seeded inactive toxic vent must be floodable");
        check(!BlastAutoToxicRoom.sourceTerrain(Terrain.TRAP, true, true),
                "active ordinary traps must not count as permanent gas vents");
        check(!BlastAutoToxicRoom.sourceTerrain(Terrain.INACTIVE_TRAP, true, false),
                "flooded or exhausted vents are not active");
        check(!BlastAutoToxicRoom.sourceTerrain(Terrain.INACTIVE_TRAP, false, true),
                "decorative inactive traps are not toxic vents");
        check(BlastAutoAqua.waterableTerrain(Terrain.INACTIVE_TRAP),
                "native Aqua Brew can flood inactive trap terrain");

        check(BlastAutoToxicRoom.holdDoor(true, true, true),
                "keep the doorway open while working on active vents");
        check(!BlastAutoToxicRoom.holdDoor(true, false, true),
                "do not expose corridors to permanent gas without Aqua Brew");
        check(BlastAutoToxicRoom.holdDoor(false, false, true),
                "keep the doorway open for remaining gas to dissipate");
        check(!BlastAutoToxicRoom.holdDoor(false, false, false),
                "recover the door wedge after the gas dissipates");
        check(BlastAutoToxicRoom.demolishDoor(false, true),
                "destroy doorway permanently while residual gas remains");
        check(!BlastAutoToxicRoom.demolishDoor(true, true),
                "do not permanently vent a room with active gas sources");
        check(!BlastAutoToxicRoom.demolishDoor(false, false),
                "do not waste a charge after gas has dissipated");
        check(BlastAutoToxicRoom.breakableDoor(Terrain.DOOR),
                "ordinary wooden doors can be bombed");
        check(BlastAutoToxicRoom.breakableDoor(Terrain.OPEN_DOOR),
                "already open wooden doors can be bombed");
        check(!BlastAutoToxicRoom.breakableDoor(Terrain.LOCKED_DOOR),
                "do not apply this rule to locked doors");
        check((Terrain.flags[Terrain.DOOR] & Terrain.FLAMABLE) != 0,
                "native bombs destroy closed flammable doors");
        check((Terrain.flags[Terrain.OPEN_DOOR] & Terrain.FLAMABLE) != 0,
                "native bombs destroy open flammable doors");
        System.out.println("BlastAutoToxicRoomTest passed");
    }
}
