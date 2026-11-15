package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.RegrowthBomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.watabou.utils.PathFinder;

/** Blast-only idle farming. The portable auto-play and pickup logic remain independent. */
public final class BlastAutoFarming {
    private static Hero currentHero;
    private static Level currentLevel;
    private static boolean planted;
    private static boolean harvested;

    private BlastAutoFarming() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        planted = false;
        harvested = false;
    }

    /**
     * Returns true if a farming bomb was launched or a movement action was queued.
     * The caller returns hero.curAction == null to distinguish an asynchronous bomb.
     */
    public static boolean act(Hero hero, boolean[] safe, int[] distances) {
        if (currentHero != hero || currentLevel != Dungeon.level) {
            reset();
            currentHero = hero;
            currentLevel = Dungeon.level;
        }
        if (!BlastSettings.testAutoPlay() || !BlastSettings.autoFarming()
                || BlastRemoteBomb.isTimedMode() || hero.flying
                || hero.buff(BlastRemoteBomb.class) == null
                || hasVisibleEnemy(hero)) return false;

        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid farming distance map");
        }

        if (planted) {
            int nearest = nearestCrop(hero, safe, distances);
            if (nearest >= 0) {
                int next = nearest == hero.pos ? hero.pos
                        : BlastAutoPlay.firstStep(nearest, safe, distances);
                hero.curAction = new HeroAction.Move(next);
                // An action toward an actual crop advances the harvest stage.
                if (harvestable(level.map[next], level.plants.get(next))) harvested = true;
                return true;
            }
            if (!canPlantAgain(planted, harvested, regrowthActive(level))) return false;
        }

        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0) return false;
        int index = findRegrowthType(maniac);
        if (index < 0) return false;

        boolean remoteThrow = BlastSettings.testRemoteThrow();
        int destination = remoteThrow ? throwDestination(hero, safe) : hero.pos;
        if (destination < 0) return false;

        int previous = maniac.selectedIndex();
        boolean launched;
        maniac.select(index);
        try {
            launched = remoteThrow
                    ? maniac.blastAutoThrowAndDetonate(destination)
                    : maniac.blastAutoPlaceAndDetonate();
        } finally {
            maniac.select(previous);
        }
        if (!launched) return false;
        planted = true;
        harvested = false;
        if (!remoteThrow) hero.spendAndNext(1f);
        return true;
    }

    static boolean canPlantAgain(boolean planted, boolean harvested, boolean cloudActive) {
        return !planted || (harvested && !cloudActive);
    }

    static boolean harvestable(int terrain, Plant plant) {
        return terrain == Terrain.HIGH_GRASS
                || (plant != null && !AutoPlayPlantSafety.isHarmful(plant));
    }

    private static boolean hasVisibleEnemy(Hero hero) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                    && mob.invisible <= 0 && hero.fieldOfView[mob.pos]) return true;
        }
        return false;
    }

    private static boolean regrowthActive(Level level) {
        Blob cloud = level.blobs.get(BombRegrowth.class);
        return cloud != null && cloud.volume > 0;
    }

    private static int findRegrowthType(BlastBombManiac maniac) {
        for (int i = 0; i < maniac.typeCount(); i++) {
            if (maniac.bombAt(i) instanceof RegrowthBomb) return i;
        }
        return -1;
    }

    /** Avoid a stationary action if phasing, traps or vegetation forbid access. */
    private static int nearestCrop(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        int target = -1;
        int shortest = Integer.MAX_VALUE;
        for (int cell = 0; cell < level.length(); cell++) {
            if (!hero.fieldOfView[cell] || !safe[cell]
                    || distances[cell] == Integer.MAX_VALUE
                    || !harvestable(level.map[cell], level.plants.get(cell))) continue;
            if (cell == hero.pos && !hero.canSelfTrample()) continue;
            if (distances[cell] < shortest) {
                target = cell;
                shortest = distances[cell];
            }
        }
        return target;
    }

    private static int throwDestination(Hero hero, boolean[] safe) {
        Level level = Dungeon.level;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (level.insideMap(cell) && safe[cell] && hero.fieldOfView[cell]
                    && BlastBombTrajectory.throwPos(hero.pos, cell) == cell) return cell;
        }
        return -1;
    }
}
