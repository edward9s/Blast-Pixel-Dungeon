package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.HashMap;

/** Persistent DM-300-style reticles over the blast ranges of armed bombs. */
public final class BlastBombWarnings extends Group {
    private static final class Snapshot {
        final Level level;
        final boolean[] union;
        final boolean[] bombCells;
        final int remoteCount;
        final int litCount;
        final int nextTimedFuse;

        Snapshot(Level level, boolean[] union, boolean[] bombCells,
                 int remoteCount, int litCount, int nextTimedFuse) {
            this.level = level;
            this.union = union;
            this.bombCells = bombCells;
            this.remoteCount = remoteCount;
            this.litCount = litCount;
            this.nextTimedFuse = nextTimedFuse;
        }
    }

    // The actor thread publishes immutable data; the render thread owns the images.
    private static volatile Snapshot snapshot;

    private final Level level;
    private final HashMap<Integer, Image> marks = new HashMap<>();
    private Snapshot rendered;
    private float pulse;

    public BlastBombWarnings() {
        level = Dungeon.level;
        publish(level);
    }

    /** Called only from game state mutations or the hero's actor turn. */
    public static void refresh(Level level) {
        Snapshot previous = snapshot;
        if (previous != null && previous.level == level) {
            publish(level);
        }
    }

    /** Heap changes cannot affect warnings unless this cell held or holds a lit bomb. */
    public static void refreshForHeap(Level level, int cell) {
        Snapshot previous = snapshot;
        if (previous != null && previous.level == level
                && (previous.bombCells[cell] || BlastBombCollision.hasLitBomb(level, cell))) {
            publish(level);
        }
    }

    private static void publish(Level level) {
        boolean[] union = new boolean[level.length()];
        boolean[] bombCells = new boolean[level.length()];
        int remoteCount = 0;
        int litCount = 0;
        int nextTimedFuse = Integer.MAX_VALUE;
        for (Heap heap : level.heaps.valueList()) {
            for (Item item : heap.items) {
                boolean[] cells;
                if (item instanceof BlastDeathBomb) {
                    // Enemy counterbombs are dangerous but not Hero-armed fuses:
                    // they must not change mode switching or the Tag countdown.
                    cells = ((BlastDeathBomb)item).blastWarningCells(heap.pos);
                } else if (item instanceof Bomb && ((Bomb)item).fuse != null) {
                    bombCells[heap.pos] = true;
                    litCount++;
                    Bomb bomb = (Bomb)item;
                    if (bomb.fuse instanceof BlastRemoteBomb.RemoteFuse) {
                        remoteCount++;
                    } else {
                        nextTimedFuse = Math.min(nextTimedFuse,
                                Math.max(0, (int)Math.ceil(bomb.fuse.cooldown())));
                    }
                    cells = bomb.blastWarningCells(heap.pos);
                } else {
                    continue;
                }
                if (cells.length != union.length) {
                    throw new IllegalStateException("Invalid bomb warning footprint size");
                }
                for (int cell = 0; cell < cells.length; cell++) {
                    if (cells[cell]) union[cell] = true;
                }
            }
        }
        snapshot = new Snapshot(level, union, bombCells, remoteCount, litCount,
                nextTimedFuse == Integer.MAX_VALUE ? -1 : nextTimedFuse);
    }

    /** Populate the effect distance map from the same cells as blast warnings. */
    public static void buildEffectDistanceMap(int center, boolean[] passable, int radius) {
        boolean[] cells = reachable(Dungeon.level, center, passable, radius);
        if (PathFinder.distance.length != cells.length) {
            throw new IllegalStateException("Invalid explosion distance map size");
        }
        for (int i = 0; i < cells.length; i++) {
            PathFinder.distance[i] = cells[i] ? 0 : Integer.MAX_VALUE;
        }
    }

    /** Blast footprint for any radius, independent of the Hero's upgrade. */
    public static boolean[] reachable(Level level, int center, boolean[] passable, int radius) {
        int size = level.length();
        if (passable.length != size || center < 0 || center >= size || radius < 0) {
            throw new IllegalArgumentException("Invalid blast warning parameters");
        }
        boolean[] visible = BlastBombGeometry.reached(level, center, radius);
        for (int cell = 0; cell < size; cell++) {
            if (cell != center && !passable[cell]) visible[cell] = false;
        }
        return visible;
    }

    /** Safe render-thread count of armed remote fuses. */
    public static int remoteCount() {
        Snapshot current = snapshot;
        return current != null && current.level == Dungeon.level ? current.remoteCount : 0;
    }

    /** All armed bombs on this floor, including inventory-thrown timed bombs. */
    public static int litCount() {
        Snapshot current = snapshot;
        return current != null && current.level == Dungeon.level ? current.litCount : 0;
    }

    /** Smallest live timed-fuse countdown, or a dash if none remain. */
    public static String nextTimedCountdown() {
        Snapshot current = snapshot;
        return current != null && current.level == Dungeon.level && current.nextTimedFuse >= 0
                ? Integer.toString(current.nextTimedFuse) : "-";
    }

    /** Avoid retaining the previous floor when leaving the game scene. */
    public static void resetSnapshot() {
        snapshot = null;
    }

    @Override
    public void update() {
        Snapshot current = snapshot;
        if (current == null || current.level != level || Dungeon.level != level) {
            for (Image mark : marks.values()) mark.visible = false;
            super.update();
            return;
        }

        if (current != rendered) {
            ArrayList<Integer> expired = new ArrayList<>();
            for (Integer cell : marks.keySet()) {
                if (!current.union[cell]) expired.add(cell);
            }
            for (Integer cell : expired) marks.remove(cell).killAndErase();

            for (int cell = 0; cell < current.union.length; cell++) {
                if (!current.union[cell] || marks.containsKey(cell)) continue;
                Image mark = new Image();
                mark.copy(Icons.get(Icons.TARGET));
                mark.origin.set(mark.width / 2f);
                mark.camera = null;
                mark.point(DungeonTilemap.tileToWorld(cell));
                mark.hardlight(0xFF0000);
                add(mark);
                marks.put(cell, mark);
            }
            rendered = current;
        }

        // Keep every danger tile visible after its source leaves the Hero's FOV.
        pulse += Game.elapsed;
        float alpha = 0.58f + 0.13f * (float)Math.sin(pulse * 5f);
        for (Image mark : marks.values()) {
            mark.visible = true;
            mark.alpha(alpha);
        }
        super.update();
    }
}
