package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;

/** Trap classification and bomb prerequisites for summon-room automation. */
public final class BlastAutoSummoningRoomTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(BlastAutoSummoningRoom.summoningTerrain(Terrain.SECRET_TRAP, true),
                "hidden summoning trap is eligible");
        check(BlastAutoSummoningRoom.summoningTerrain(Terrain.TRAP, true),
                "revealed summoning trap is eligible");
        check(!BlastAutoSummoningRoom.summoningTerrain(Terrain.SECRET_TRAP, false),
                "deactivated trap must not be stepped on");
        check(!BlastAutoSummoningRoom.summoningTerrain(Terrain.INACTIVE_TRAP, true),
                "inactive terrain is not an active summoning trap");
        check(!BlastAutoSummoningRoom.summoningTerrain(Terrain.EMPTY, true),
                "ordinary floor is not a trap target");
        check(BlastAutoSummoningRoom.canTriggerWave(true, false, true),
                "registered Arcane Bomb with indestructibility allows triggering");
        check(BlastAutoSummoningRoom.canTriggerWave(false, true, true),
                "registered Shrapnel Bomb with indestructibility allows triggering");
        check(BlastAutoSummoningRoom.canTriggerWave(true, true, true),
                "either registered type suffices");
        check(!BlastAutoSummoningRoom.canTriggerWave(false, false, true),
                "indestructibility alone is insufficient");
        check(!BlastAutoSummoningRoom.canTriggerWave(true, false, false),
                "registered Arcane Bomb alone is insufficient");
        check(!BlastAutoSummoningRoom.canTriggerWave(false, true, false),
                "registered Shrapnel Bomb alone is insufficient");
        check(!BlastAutoSummoningRoom.canTriggerWave(false, false, false),
                "neither condition cannot start a wave");
        System.out.println("BlastAutoSummoningRoomTest passed");
    }
}
