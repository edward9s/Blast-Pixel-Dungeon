package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;

/** Overlay lit bomb collision without changing terrain or sight. */
public final class BlastBombCollision {
    private BlastBombCollision() {}

    public static boolean hasLitBomb(Level level, int cell) {
        if (level == null || !level.insideMap(cell)) return false;
        Heap heap = level.heaps.get(cell);
        if (heap == null) return false;
        for (Item item : heap.items) {
            if (item instanceof Bomb && ((Bomb)item).fuse != null) return true;
        }
        return false;
    }

    public static void block(Level level, int cell) {
        if (hasLitBomb(level, cell)) {
            level.passable[cell] = false;
            level.avoid[cell] = false;
        }
    }

    /** Reset terrain and blob passability, then overlay any surviving lit bomb. */
    public static void refresh(Level level, int cell) {
        if (level != null && level.insideMap(cell)) {
            level.updateCellFlags(cell);
            BlastBombWarnings.refreshForHeap(level, cell);
            BlastOutOfSightItems.refreshForHeap(level, cell);
        }
    }

    /** Reload saved lit bombs into the movement flags after map reconstruction. */
    public static void blockAll(Level level) {
        for (Heap heap : level.heaps.valueList()) {
            block(level, heap.pos);
        }
    }
}
