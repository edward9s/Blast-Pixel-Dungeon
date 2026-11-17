package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.RegrowthBomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;

/** Blast-only idle farming. Portable auto-play and pickup remain independent. */
public final class BlastAutoFarming {
    enum Action { NONE, MOVE, BOMB }

    static final int MIN_GROWABLE_CELLS = 10;
    private static final float BOMB_INTERVAL = 20f;

    private static Hero currentHero;
    private static Level currentLevel;
    private static float nextBombAt = Float.NEGATIVE_INFINITY;

    private BlastAutoFarming() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        nextBombAt = Float.NEGATIVE_INFINITY;
    }

    /** Select one farming action; idle/roaming remains the caller's responsibility. */
    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        if (currentHero != hero || currentLevel != Dungeon.level) {
            reset();
            currentHero = hero;
            currentLevel = Dungeon.level;
        }
        if (!BlastSettings.testAutoPlay() || !BlastSettings.autoFarming()
                || hero.flying || BlastAutoPlay.hasVisibleEnemy(hero)) return Action.NONE;

        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid farming distance map");
        }

        // Allow another blast only after its cooldown and all Regrowth clouds end.
        if (!plantingReady(Actor.now(), nextBombAt, regrowthActive(level))) return Action.NONE;

        // Only planting new grass requires remote mode and an available Regrowth bomb.
        if (BlastRemoteBomb.isTimedMode() || hero.buff(BlastRemoteBomb.class) == null) {
            return Action.NONE;
        }
        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null) return Action.NONE;
        int index = findRegrowthType(maniac);
        if (!regrowthReady(maniac.remaining(), index)) return Action.NONE;

        RegrowthBomb bomb = (RegrowthBomb) maniac.bombAt(index);
        boolean remoteThrow = BlastSettings.testRemoteThrow();
        Site site = findNearbySite(hero, level, safe, distances, bomb, remoteThrow);
        if (site == null) return Action.NONE;
        if (hero.pos != site.standCell) {
            hero.curAction = new HeroAction.Move(site.standCell);
            return Action.MOVE;
        }

        int previous = maniac.selectedIndex();
        boolean launched;
        maniac.select(index);
        try {
            launched = remoteThrow
                    ? maniac.blastAutoThrowAndDetonate(site.blastCell)
                    : maniac.blastAutoPlaceAndDetonate();
        } finally {
            maniac.select(previous);
        }
        if (!launched) return Action.NONE;
        nextBombAt = Actor.now() + BOMB_INTERVAL;
        if (!remoteThrow) hero.spendAndNext(1f);
        return Action.BOMB;
    }

    static boolean regrowthReady(int remainingBombs, int regrowthIndex) {
        return remainingBombs > 0 && regrowthIndex >= 0;
    }

    static boolean plantingReady(float now, float nextBombAt, boolean cloudActive) {
        return !cloudActive && now >= nextBombAt;
    }

    static boolean sufficientArea(int count) {
        return count >= MIN_GROWABLE_CELLS;
    }

    /** Match RegrowthBomb's plant-placement terrain, including tall grass. */
    static boolean canHostRegrowth(int terrain) {
        return terrain == Terrain.EMPTY || terrain == Terrain.EMPTY_DECO
                || terrain == Terrain.EMBERS || terrain == Terrain.GRASS
                || terrain == Terrain.FURROWED_GRASS || terrain == Terrain.HIGH_GRASS;
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

    /** One local decision, with no stale global scan cursor or retained target. */
    private static final class Site {
        final int standCell;
        final int blastCell;

        Site(int standCell, int blastCell) {
            this.standCell = standCell;
            this.blastCell = blastCell;
        }
    }

    private static Site findNearbySite(Hero hero, Level level, boolean[] safe, int[] distances,
                                       RegrowthBomb bomb, boolean remoteThrow) {
        int radius = Math.min(20, bomb.blastExplosionRange());
        if (radius < 0) throw new IllegalStateException("Invalid Regrowth bomb radius");

        if (remoteThrow) {
            // Throw from the current tile. Nearby landings are cheap to reconsider
            // on every idle turn, even after walls, grass, or visibility change.
            for (int offset : PathFinder.NEIGHBOURS8) {
                int center = hero.pos + offset;
                if (!level.insideMap(center) || !safe[center]) continue;
                if (BlastBombTrajectory.throwPos(hero.pos, center) != center) continue;
                if (suitableBlast(level, bomb, center, hero.pos, radius)) {
                    return new Site(hero.pos, center);
                }
            }
            return null;
        }

        // Plant under the hero first; step only one tile if a better location
        // is required. Ordinary roaming explores areas outside this local range.
        if (!level.pit[hero.pos] && suitableBlast(level, bomb, hero.pos, hero.pos, radius)) {
            return new Site(hero.pos, hero.pos);
        }
        if (hero.rooted) return null;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int stand = hero.pos + offset;
            if (!level.insideMap(stand) || !safe[stand]
                    || distances[stand] != 1 || level.pit[stand]) continue;
            if (suitableBlast(level, bomb, stand, stand, radius)) {
                return new Site(stand, stand);
            }
        }
        return null;
    }

    private static boolean suitableBlast(Level level, RegrowthBomb bomb,
                                         int center, int standCell, int radius) {
        // Cheap geometric screen; only successful sites need full ray-traced blasts.
        return sufficientArea(localPlantable(level, center, standCell, radius))
                && sufficientArea(countPlantable(bomb.blastWarningCells(center), level, standCell));
    }

    static boolean withinBlastRadius(int dx, int dy, int radius) {
        if (radius < 0) throw new IllegalArgumentException("Invalid bomb radius");
        long diameter = 2L * radius + 1;
        return 4L * ((long) dx * dx + (long) dy * dy) <= diameter * diameter;
    }

    /** Count terrain candidates in the bomb's actual radius, before ray obstruction. */
    static int localPlantable(Level level, int center, int standCell, int radius) {
        if (radius < 0 || center < 0 || center >= level.length()
                || standCell < 0 || standCell >= level.length()) {
            throw new IllegalArgumentException("Invalid Regrowth sample parameters");
        }
        int width = level.width();
        int height = level.height();
        int x = center % width;
        int y = center / width;
        int count = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            int yy = y + dy;
            if (yy < 0 || yy >= height) continue;
            for (int dx = -radius; dx <= radius; dx++) {
                int xx = x + dx;
                if (xx < 0 || xx >= width
                        || !withinBlastRadius(dx, dy, radius)) continue;
                int cell = yy * width + xx;
                if (cell != standCell && level.plants.get(cell) == null
                        && canHostRegrowth(level.map[cell])) {
                    if (++count >= MIN_GROWABLE_CELLS) return count;
                }
            }
        }
        return count;
    }

    /** Count cells where Regrowth can actually plant, including unseen or unwalked floor. */
    static int countPlantable(boolean[] blast, Level level, int standCell) {
        if (blast.length != level.length() || standCell < 0 || standCell >= blast.length) {
            throw new IllegalArgumentException("Invalid Regrowth blast footprint");
        }
        int count = 0;
        for (int cell = 0; cell < blast.length; cell++) {
            if (cell != standCell && blast[cell]
                    && level.plants.get(cell) == null
                    && canHostRegrowth(level.map[cell])
                    && Actor.findChar(cell) == null) {
                if (++count >= MIN_GROWABLE_CELLS) return count;
            }
        }
        return count;
    }

}
