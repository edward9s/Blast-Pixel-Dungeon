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
        check(BlastAutoSummoningRoom.eligibleBomb(true, false, false, false),
                "registered arcane bomb is usable");
        check(BlastAutoSummoningRoom.eligibleBomb(false, true, false, false),
                "registered shrapnel bomb is usable");
        check(BlastAutoSummoningRoom.eligibleBomb(false, false, true, false),
                "held arcane bomb can be imbued");
        check(BlastAutoSummoningRoom.eligibleBomb(false, false, false, true),
                "held shrapnel bomb can be imbued");
        check(!BlastAutoSummoningRoom.eligibleBomb(false, false, false, false),
                "do not deliberately summon enemies without a special bomb");
        check(BlastAutoSummoningRoom.canBomb(true, false),
                "remote targeting is allowed if the blast can avoid the hero");
        check(BlastAutoSummoningRoom.canBomb(false, true),
                "protected heroes can place and detonate bombs in place");
        check(!BlastAutoSummoningRoom.canBomb(false, false),
                "do not start a wave if even a placement cannot be safe");
        System.out.println("BlastAutoSummoningRoomTest passed");
    }
}
