package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;

/** Bomb flight rules shared by Hero throws and rare-mob kicks. */
public final class BlastBombTrajectory {
    private BlastBombTrajectory() {}

    public static Ballistica flight(int from, int target) {
        return new Ballistica(from, target, BlastMovement.enabled()
                ? Ballistica.STOP_TARGET : Ballistica.PROJECTILE);
    }

    /** Preserve ordinary throwing when phase movement is disabled. */
    public static int throwPos(int from, int target) {
        Ballistica flight = flight(from, target);
        return BlastMovement.enabled() ? landing(flight) : flight.collisionPos;
    }

    /**
     * Find the last safe resting tile on the flight path.
     * While phasing, terrain, chasms, and characters do not stop flight.
     * Without phasing, kicks still stop at the first impassable tile or chasm.
     */
    public static int landing(Ballistica flight) {
        Level level = Dungeon.level;
        if (level == null) throw new IllegalStateException("Cannot throw a bomb without a level");
        boolean phasing = BlastMovement.enabled();
        int landing = flight.sourcePos;
        for (int cell : flight.subPath(1, flight.dist)) {
            if (!level.insideMap(cell)) break;
            if (level.pit[cell] || (!level.passable[cell] && !level.avoid[cell])) {
                if (!phasing) break;
                continue;
            }
            landing = cell;
        }
        return landing;
    }
}
