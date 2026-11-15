package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
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

/** Blast-only idle farming. Portable auto-play and pickup remain independent. */
public final class BlastAutoFarming {
    enum Action { NONE, MOVE, BOMB }

    static final int MIN_GROWABLE_CELLS = 10;
    private static final int SAMPLE_RADIUS = 3;
    private static final int MAX_SITE_CHECKS = 8;
    private static final float SEARCH_INTERVAL = 8f;

    private static Hero currentHero;
    private static Level currentLevel;
    private static boolean planted;
    private static boolean harvested;
    private static int farmingSite = -1;
    private static int farmingBlastCell = -1;
    private static boolean siteUsesRemoteThrow;
    private static float nextSearchAt = Float.NEGATIVE_INFINITY;

    private BlastAutoFarming() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        planted = false;
        harvested = false;
        farmingSite = farmingBlastCell = -1;
        nextSearchAt = Float.NEGATIVE_INFINITY;
    }

    /** Select one farming action; idle/roaming remains the caller's responsibility. */
    static Action chooseAction(Hero hero, boolean[] safe, int[] distances) {
        if (currentHero != hero || currentLevel != Dungeon.level) {
            reset();
            currentHero = hero;
            currentLevel = Dungeon.level;
        }
        if (!BlastSettings.testAutoPlay() || !BlastSettings.autoFarming()
                || BlastRemoteBomb.isTimedMode() || hero.flying
                || hero.buff(BlastRemoteBomb.class) == null
                || hasVisibleEnemy(hero)) return Action.NONE;

        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid farming distance map");
        }

        // Farming, including intentional grass trampling, needs an available Regrowth bomb.
        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null) return Action.NONE;
        int index = findRegrowthType(maniac);
        if (!regrowthReady(maniac.remaining(), index)) return Action.NONE;

        int nearest = nearestCrop(hero, safe, distances);
        if (nearest >= 0) {
            int next = nearest == hero.pos ? hero.pos
                    : BlastAutoPlay.firstStep(nearest, safe, distances);
            hero.curAction = new HeroAction.Move(next);
            if (planted && harvestable(level.map[next], level.plants.get(next))) {
                harvested = true;
            }
            return Action.MOVE;
        }

        if (planted) {
            if (!canPlantAgain(planted, harvested, regrowthActive(level))) return Action.NONE;
            planted = false;
            harvested = false;
            farmingSite = farmingBlastCell = -1;
        }

        // An earlier manual Regrowth blast should finish before farming begins.
        if (regrowthActive(level)) return Action.NONE;

        RegrowthBomb bomb = (RegrowthBomb) maniac.bombAt(index);
        boolean remoteThrow = BlastSettings.testRemoteThrow();

        if (farmingSite >= 0
                && (siteUsesRemoteThrow != remoteThrow
                    || !safe[farmingSite] || distances[farmingSite] == Integer.MAX_VALUE)) {
            farmingSite = farmingBlastCell = -1;
        }
        if (farmingSite < 0) {
            if (Actor.now() < nextSearchAt) return Action.NONE;
            nextSearchAt = Actor.now() + SEARCH_INTERVAL;
            findFarmingSite(level, safe, distances, bomb, remoteThrow);
            if (farmingSite < 0) return Action.NONE;
        }
        if (hero.pos != farmingSite) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(farmingSite, safe, distances));
            return Action.MOVE;
        }

        int destination = farmingBlastCell;
        if ((!remoteThrow && (level.pit[hero.pos]
                || BlastBombCollision.hasLitBomb(level, hero.pos)))
                || (remoteThrow && BlastBombTrajectory.throwPos(hero.pos, destination) != destination)
                || !sufficientArea(countGrowable(
                        bomb.blastWarningCells(destination), level, safe, distances,
                        hero.pos))) {
            farmingSite = farmingBlastCell = -1;
            return Action.NONE;
        }

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
        if (!launched) return Action.NONE;
        planted = true;
        harvested = false;
        farmingSite = farmingBlastCell = -1;
        if (!remoteThrow) hero.spendAndNext(1f);
        return Action.BOMB;
    }

    static boolean regrowthReady(int remainingBombs, int regrowthIndex) {
        return remainingBombs > 0 && regrowthIndex >= 0;
    }

    static boolean canPlantAgain(boolean planted, boolean harvested, boolean cloudActive) {
        return !planted || (harvested && !cloudActive);
    }

    static boolean harvestable(int terrain, Plant plant) {
        return terrain == Terrain.HIGH_GRASS
                || (plant != null && !AutoPlayPlantSafety.isHarmful(plant));
    }

    static boolean sufficientArea(int count) {
        return count >= MIN_GROWABLE_CELLS;
    }

    /** Regrowth upgrades ground only on these five terrain types. */
    static boolean canGrow(int terrain) {
        return terrain == Terrain.EMPTY || terrain == Terrain.EMPTY_DECO
                || terrain == Terrain.EMBERS || terrain == Terrain.GRASS
                || terrain == Terrain.FURROWED_GRASS;
    }

    private static boolean hasVisibleEnemy(Hero hero) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                    && mob.invisible <= 0 && hero.fieldOfView[mob.pos]
                    && BlastAutoPlay.isAwake(mob)) return true;
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

    /** Use the existing route map and avoid harmful plants and traps. */
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

    /** Cheap 7x7 local screen: O(1) area per candidate, independent of blast radius. */
    static int localGrowable(Level level, int center, int standCell,
                             boolean[] safe, int[] distances) {
        int width = level.width();
        int x = center % width;
        int y = center / width;
        int count = 0;
        int radius = SAMPLE_RADIUS;
        int squaredLimit = (2 * radius + 1) * (2 * radius + 1);
        for (int dy = -radius; dy <= radius; dy++) {
            int yy = y + dy;
            if (yy < 0 || yy >= level.height()) continue;
            for (int dx = -radius; dx <= radius; dx++) {
                int xx = x + dx;
                if (xx < 0 || xx >= width
                        || 4 * (dx * dx + dy * dy) > squaredLimit) continue;
                int cell = yy * width + xx;
                if (cell != standCell && safe[cell] && distances[cell] != Integer.MAX_VALUE
                        && level.plants.get(cell) == null && canGrow(level.map[cell])) count++;
            }
        }
        return count;
    }

    /** Actual blast footprint accounts for walls, radius upgrades, and accessible crops. */
    static int countGrowable(boolean[] blast, Level level, boolean[] safe, int[] distances,
                             int standCell) {
        if (blast.length != level.length()) {
            throw new IllegalArgumentException("Invalid farming blast footprint");
        }
        int count = 0;
        for (int cell = 0; cell < blast.length; cell++) {
            if (cell != standCell && blast[cell] && safe[cell]
                    && distances[cell] != Integer.MAX_VALUE
                    && level.plants.get(cell) == null && canGrow(level.map[cell])) count++;
        }
        return count;
    }

    /** Scan only every eight game ticks; evaluate at most eight exact bomb footprints. */
    private static void findFarmingSite(Level level, boolean[] safe, int[] distances,
                                        RegrowthBomb bomb, boolean remoteThrow) {
        int[] candidates = new int[MAX_SITE_CHECKS];
        int[] centers = new int[MAX_SITE_CHECKS];
        int[] scores = new int[MAX_SITE_CHECKS];
        int[] steps = new int[MAX_SITE_CHECKS];
        int found = 0;

        for (int site = 0; site < level.length(); site++) {
            if (!safe[site] || distances[site] == Integer.MAX_VALUE || level.pit[site]) continue;

            int center = site;
            int score = -1;
            if (remoteThrow) {
                for (int offset : PathFinder.NEIGHBOURS8) {
                    int cell = site + offset;
                    if (!level.insideMap(cell) || !safe[cell]) continue;
                    int local = localGrowable(level, cell, site, safe, distances);
                    if (local > score) {
                        score = local;
                        center = cell;
                    }
                }
            } else {
                score = localGrowable(level, site, site, safe, distances);
            }
            if (!sufficientArea(score)) continue;

            int insertion = found;
            while (insertion > 0 && (distances[site] < steps[insertion - 1]
                    || (distances[site] == steps[insertion - 1]
                        && score > scores[insertion - 1]))) insertion--;
            if (insertion >= MAX_SITE_CHECKS) continue;
            if (found < MAX_SITE_CHECKS) found++;
            for (int i = found - 1; i > insertion; i--) {
                candidates[i] = candidates[i - 1];
                centers[i] = centers[i - 1];
                scores[i] = scores[i - 1];
                steps[i] = steps[i - 1];
            }
            candidates[insertion] = site;
            centers[insertion] = center;
            scores[insertion] = score;
            steps[insertion] = distances[site];
        }

        for (int i = 0; i < found; i++) {
            int site = candidates[i];
            int center = centers[i];
            if (BlastBombCollision.hasLitBomb(level, site)
                    || (remoteThrow && BlastBombTrajectory.throwPos(site, center) != center)) {
                continue;
            }
            if (!sufficientArea(countGrowable(
                    bomb.blastWarningCells(center), level, safe, distances, site))) continue;
            farmingSite = site;
            farmingBlastCell = center;
            siteUsesRemoteThrow = remoteThrow;
            return;
        }
    }
}
