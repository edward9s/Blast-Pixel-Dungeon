package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.BlastParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.SmokeParticle;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.noosa.audio.Sample;
import com.shatteredpixel.shatteredpixeldungeon.tiles.CustomTilemap;
import com.watabou.utils.Bundle;
import com.watabou.utils.Point;
import com.watabou.utils.Random;

import java.util.ArrayList;

/** Per-floor, seeded wall and statue durability. Never consumes SPD's level-generation RNG. */
public final class BlastWalls {
    private static final String STRENGTH = "blast_wall_strength";
    private static final String RUBBLE = "blast_rubble";
    private static final float COLLAPSE_CHANCE = 0.35f;
    private static final float WALL_BREAK_COLLAPSE_CHANCE = 0.75f;
    private static final int MIN_COLLAPSE_CELLS = 1;
    private static final int MAX_COLLAPSE_CELLS = 3;

    private BlastWalls() {}

    private static boolean wall(int terrain) {
        return terrain == Terrain.WALL || terrain == Terrain.WALL_DECO;
    }

    private static boolean statue(int terrain) {
        return terrain == Terrain.STATUE || terrain == Terrain.STATUE_SP;
    }

    private static boolean destructible(int terrain) {
        return wall(terrain) || statue(terrain);
    }

    /** Distinguish Blast cave-in boulders from ordinary mining-quest boulders. */
    public static boolean rubbleAt(Level level, int cell) {
        return level.blastRubble != null && level.blastRubble[cell]
                && level.map[cell] == Terrain.MINE_BOULDER;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static boolean eligible(Level level, int cell) {
        int width = level.width();
        int x = cell % width;
        int y = cell / width;
        return x > 0 && y > 0 && x < width - 1 && y < level.height() - 1
                && destructible(level.map[cell]);
    }

    private static boolean passable(int terrain) {
        return (Terrain.flags[terrain] & Terrain.PASSABLE) != 0;
    }

    /** Prefer walls the Hero can already reach from a room or corridor. */
    private static boolean adjacentToPassable(Level level, int cell) {
        int width = level.width();
        return passable(level.map[cell - width])
                || passable(level.map[cell - 1])
                || passable(level.map[cell + 1])
                || passable(level.map[cell + width]);
    }

    /** Initialize once, after the map has been generated and painted. */
    public static void initialize(Level level) {
        if (level.blastWallStrength != null) {
            throw new IllegalStateException("Wall strength initialized twice");
        }
        level.blastWallStrength = new int[level.length()];
        level.blastRubble = new boolean[level.length()];

        // Scripted boss and quest maps are not random room/corridor generation.
        if (!(level instanceof RegularLevel)) return;

        // Separate exposure ratios retain the original seeded wall results.
        int[] eligibleCounts = new int[2];
        int[] adjacentCounts = new int[2];
        for (int cell = 0; cell < level.length(); cell++) {
            if (!eligible(level, cell)) continue;
            int category = statue(level.map[cell]) ? 1 : 0;
            eligibleCounts[category]++;
            if (adjacentToPassable(level, cell)) adjacentCounts[category]++;
        }

        // Each category keeps the same expected rates:
        // 50% one hit, 15% three hits, and 35% indestructible.
        long seed = Dungeon.seedCurDepth();
        for (int cell = 0; cell < level.length(); cell++) {
            if (!eligible(level, cell)) continue;
            int category = statue(level.map[cell]) ? 1 : 0;
            double adjacentFraction = (double) adjacentCounts[category] / eligibleCounts[category];
            boolean adjacent = adjacentToPassable(level, cell);
            double oneHitChance = 0.50 + 0.25 * (adjacent ? 1.0 - adjacentFraction : -adjacentFraction);
            double roll = (mix(seed + 0x9E3779B97F4A7C15L * (cell + 1L)) >>> 11)
                    * 0x1.0p-53;
            if (roll < oneHitChance) level.blastWallStrength[cell] = 1;
            else if (roll < oneHitChance + 0.15) level.blastWallStrength[cell] = 3;
        }
    }

    public static void store(Level level, Bundle bundle) {
        int[] strength = required(level);
        // Some vanilla terrain changes paint the map array directly.
        for (int cell = 0; cell < strength.length; cell++) {
            if (!destructible(level.map[cell]) && !rubbleAt(level, cell)) {
                strength[cell] = 0;
                level.blastRubble[cell] = false;
            }
        }
        bundle.put(STRENGTH, strength);
        bundle.put(RUBBLE, level.blastRubble);
    }

    public static void restore(Level level, Bundle bundle) {
        if (!bundle.contains(STRENGTH)) {
            throw new IllegalStateException("Missing Blast wall strength in level save");
        }
        int[] strength = bundle.getIntArray(STRENGTH);
        if (strength == null || strength.length != level.length()) {
            throw new IllegalStateException("Invalid Blast wall strength array");
        }
        for (int value : strength) {
            if (value < 0 || value > 3) {
                throw new IllegalStateException("Invalid Blast wall strength: " + value);
            }
        }
        level.blastWallStrength = strength;
        // Floors saved before visual differentiation simply have no rubble markers.
        boolean[] rubble = bundle.contains(RUBBLE) ? bundle.getBooleanArray(RUBBLE) : new boolean[level.length()];
        if (rubble == null || rubble.length != level.length()) {
            throw new IllegalStateException("Invalid Blast rubble array");
        }
        level.blastRubble = rubble;
    }

    private static int[] required(Level level) {
        if (level.blastWallStrength == null || level.blastWallStrength.length != level.length()) {
            throw new IllegalStateException("Blast wall strength not initialized");
        }
        return level.blastWallStrength;
    }

    /** Other terrain transformations must not leave durability on a new floor tile. */
    public static void terrainChanged(Level level, int cell) {
        if (level.blastWallStrength != null && !destructible(level.map[cell]) && !rubbleAt(level, cell)) {
            level.blastWallStrength[cell] = 0;
            if (level.blastRubble != null) level.blastRubble[cell] = false;
        }
    }

    /** Can cave-in onto ordinary ground without erasing another game mechanic. */
    private static boolean canCollapse(Level level, boolean[] footprint, int cell) {
        if (!footprint[cell] || level.map[cell] != Terrain.EMPTY) return false;
        int width = level.width();
        int x = cell % width, y = cell / width;
        if (x <= 0 || y <= 0 || x >= width - 1 || y >= level.height() - 1) return false;
        if (Actor.findChar(cell) != null || level.heaps.get(cell) != null
                || level.traps.get(cell) != null || level.plants.get(cell) != null) return false;

        Point point = level.cellToPoint(cell);
        for (CustomTilemap tile : level.customTiles) {
            if (point.x >= tile.tileX && point.x < tile.tileX + tile.tileW
                    && point.y >= tile.tileY && point.y < tile.tileY + tile.tileH) return false;
        }
        return true;
    }

    /** Scatter up to three rubble cells after the complete linked explosion. */
    private static void collapse(Level level, Bomb bomb, boolean[] footprint, boolean[] brokenWalls,
                                 boolean destroyedWall) {
        if (Dungeon.level != level || !(level instanceof RegularLevel)) return;

        int width = level.width();
        ArrayList<Integer> candidates = new ArrayList<>();
        int[] offsets = {-width, -1, 1, width};
        for (int cell = 0; cell < footprint.length; cell++) {
            if (!canCollapse(level, footprint, cell)) continue;
            // Each boulder must be near a wall, but need not touch other boulders.
            for (int offset : offsets) {
                int next = cell + offset;
                if (wall(level.map[next]) || rubbleAt(level, next) || brokenWalls[next]) {
                    candidates.add(cell);
                    break;
                }
            }
        }
        if (candidates.isEmpty()) return;
        float chance = destroyedWall ? WALL_BREAK_COLLAPSE_CHANCE : COLLAPSE_CHANCE;
        if (Random.Float() >= chance) return;

        int desired = Math.min(Random.IntRange(MIN_COLLAPSE_CELLS, MAX_COLLAPSE_CELLS), candidates.size());
        ArrayList<Integer> collapsed = new ArrayList<>();
        for (int i = 0; i < desired; i++) {
            collapsed.add(candidates.remove(Random.Int(candidates.size())));
        }

        ArrayList<Integer> visible = new ArrayList<>();
        for (int cell : collapsed) {
            if (level.heroFOV[cell]) visible.add(cell);
            Level.set(cell, Terrain.MINE_BOULDER, level);
            required(level)[cell] = 1;
            level.blastRubble[cell] = true;
            GameScene.updateMap(cell);
        }
        level.cleanWalls();
        Dungeon.observe();
        BlastBombWarnings.refresh(level);
        if (!visible.isEmpty()) {
            BlastBombVisuals.effect(bomb, () -> {
                for (int cell : visible) {
                    CellEmitter.get(cell - width).start(Speck.factory(Speck.ROCK), 0.07f, 10);
                    CellEmitter.center(cell).burst(SmokeParticle.FACTORY, 6);
                }
                Sample.INSTANCE.play(Assets.Sounds.ROCKS, 0.65f);
            });
        }
    }

    /**
     * Each bomb deals one point to each touched marked wall. All map mutations
     * occur immediately; only the small wall-break particles are deferred.
     */
    public static boolean damage(Bomb bomb, int origin) {
        Level level = Dungeon.level;
        int[] strength = required(level);
        if (origin < 0 || origin >= level.length()) {
            throw new IllegalArgumentException("Bomb outside the level");
        }
        boolean[] reached = BlastBombGeometry.reached(level, origin, bomb.blastExplosionRange());
        boolean[] footprint = bomb.blastWarningCells(origin);
        if (footprint.length != level.length()) {
            throw new IllegalStateException("Invalid collapse blast footprint");
        }
        boolean[] brokenWalls = new boolean[level.length()];
        ArrayList<Integer> visibleBroken = new ArrayList<>();
        boolean broken = false;
        boolean destroyedWall = false;

        for (int cell = 0; cell < strength.length; cell++) {
            if (strength[cell] == 0 || (!destructible(level.map[cell]) && !rubbleAt(level, cell))
                    || !reached[cell]) continue;
            if (--strength[cell] == 0) {
                // Statues use the same durability but do not change the
                // existing wall-specific cave-in calculations.
                if (!statue(level.map[cell])) {
                    if (!rubbleAt(level, cell)) destroyedWall = true;
                    brokenWalls[cell] = true;
                }
                if (level.heroFOV[cell]) visibleBroken.add(cell);
                Level.set(cell, Terrain.EMPTY, level);
                GameScene.updateMap(cell);
                broken = true;
            }
        }

        if (broken) {
            level.cleanWalls();
            BlastBombWarnings.refresh(level);
            if (!visibleBroken.isEmpty()) {
                BlastBombVisuals.effect(bomb, () -> {
                    for (int cell : visibleBroken) {
                        CellEmitter.center(cell).burst(BlastParticle.FACTORY, 8);
                        CellEmitter.get(cell).burst(SmokeParticle.FACTORY, 3);
                    }
                });
            }
        }
        final boolean wallWasDestroyed = destroyedWall;
        BlastBombVisuals.afterChain(() -> collapse(level, bomb, footprint, brokenWalls, wallWasDestroyed));
        return broken;
    }
}
