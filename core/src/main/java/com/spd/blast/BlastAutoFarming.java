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
    private static final int SAMPLE_RADIUS = 3;
    private static final int MAX_SITE_CHECKS = 8;
    private static final float SEARCH_INTERVAL = 8f;
    private static final float BOMB_INTERVAL = 20f;

    private static Hero currentHero;
    private static Level currentLevel;
    private static float nextBombAt = Float.NEGATIVE_INFINITY;
    private static int nextCandidateCell = -1;
    private static int farmingSite = -1;
    private static int farmingBlastCell = -1;
    private static boolean siteUsesRemoteThrow;
    private static float nextSearchAt = Float.NEGATIVE_INFINITY;

    private BlastAutoFarming() {}

    public static void reset() {
        currentHero = null;
        currentLevel = null;
        nextBombAt = Float.NEGATIVE_INFINITY;
        nextCandidateCell = -1;
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
        if ((!remoteThrow && level.pit[hero.pos])
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
        nextBombAt = Actor.now() + BOMB_INTERVAL;
        farmingSite = farmingBlastCell = -1;
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
                        && level.plants.get(cell) == null && canHostRegrowth(level.map[cell])) count++;
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
                    && level.plants.get(cell) == null && canHostRegrowth(level.map[cell])) count++;
        }
        return count;
    }

    /** Advance the level scan between searches; never retry only the first eight sites. */
    static int nextSiteIndex(int site, int length) {
        if (length <= 0 || site < 0 || site >= length) {
            throw new IllegalArgumentException("Invalid farming scan index");
        }
        return site + 1 == length ? 0 : site + 1;
    }

    /** Bound precise blast checks to eight per search, eight game ticks apart. */
    private static void findFarmingSite(Level level, boolean[] safe, int[] distances,
                                        RegrowthBomb bomb, boolean remoteThrow) {
        int length = level.length();
        if (nextCandidateCell < 0) nextCandidateCell = currentHero.pos;

        int checked = 0;
        int selectedSite = -1;
        int selectedCenter = -1;
        int selectedDistance = Integer.MAX_VALUE;
        int selectedArea = -1;

        for (int scanned = 0; scanned < length && checked < MAX_SITE_CHECKS; scanned++) {
            int site = nextCandidateCell;
            nextCandidateCell = nextSiteIndex(site, length);
            if (!safe[site] || distances[site] == Integer.MAX_VALUE || level.pit[site]) {
                continue;
            }

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

            checked++;
            if (remoteThrow && BlastBombTrajectory.throwPos(site, center) != center) {
                continue;
            }
            int area = countGrowable(
                    bomb.blastWarningCells(center), level, safe, distances, site);
            if (!sufficientArea(area)) continue;

            int distance = distances[site];
            if (selectedSite < 0 || distance < selectedDistance
                    || (distance == selectedDistance && area > selectedArea)) {
                selectedSite = site;
                selectedCenter = center;
                selectedDistance = distance;
                selectedArea = area;
            }
        }

        if (selectedSite >= 0) {
            farmingSite = selectedSite;
            farmingBlastCell = selectedCenter;
            siteUsesRemoteThrow = remoteThrow;
            nextCandidateCell = -1;
        }
    }
}
