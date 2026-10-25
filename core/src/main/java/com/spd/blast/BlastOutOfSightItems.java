package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.watabou.noosa.Group;

import java.util.HashMap;
import java.util.Map;

/** Draw Blast pickups and lit ground bombs above the fog of war. */
public final class BlastOutOfSightItems extends Group {

    private enum Kind { RADIUS, HASTE, BOMB }

    private static final class Marker {
        final Kind kind;
        final int image;
        Marker(Kind kind, int image) {
            this.kind = kind;
            this.image = image;
        }
    }

    private static final class Snapshot {
        final Level level;
        final HashMap<Integer, Marker> markers;

        Snapshot(Level level, HashMap<Integer, Marker> markers) {
            this.level = level;
            this.markers = markers;
        }
    }

    // Heap changes are published by the actor thread; only the render thread owns sprites.
    private static volatile Snapshot snapshot;

    private final Level level;
    private final HashMap<Integer, ItemSprite> sprites = new HashMap<>();
    private Snapshot rendered;

    public BlastOutOfSightItems() {
        level = Dungeon.level;
        publish(level);
    }

    /** Read only the changed heap and leave sprites intact if its marker is unchanged. */
    public static void refreshForHeap(Level level, int cell) {
        Snapshot previous = snapshot;
        if (previous == null || previous.level != level) return;

        Marker before = previous.markers.get(cell);
        Marker after = markerAt(level.heaps.get(cell));
        if (before == null && after == null) return;
        if (before != null && after != null
                && before.kind == after.kind && before.image == after.image) return;

        HashMap<Integer, Marker> markers = new HashMap<>(previous.markers);
        if (after == null) markers.remove(cell);
        else markers.put(cell, after);
        snapshot = new Snapshot(level, markers);
    }

    private static void publish(Level level) {
        HashMap<Integer, Marker> markers = new HashMap<>();
        for (Heap heap : level.heaps.valueList()) {
            Marker marker = markerAt(heap);
            if (marker != null) markers.put(heap.pos, marker);
        }
        snapshot = new Snapshot(level, markers);
    }

    private static Marker markerAt(Heap heap) {
        if (heap == null || heap.type != Heap.Type.HEAP) return null;
        for (Item item : heap.items) {
            Marker marker = marker(item);
            if (marker != null) return marker;
        }
        return null;
    }

    private static Marker marker(Item item) {
        if (item instanceof BlastRadiusPickup) {
            return new Marker(Kind.RADIUS, 0);
        }
        if (item instanceof BlastHastePickup) {
            return new Marker(Kind.HASTE, 0);
        }
        if (item instanceof Bomb && ((Bomb) item).fuse != null) {
            return new Marker(Kind.BOMB, item.image());
        }
        return null;
    }

    public static void resetSnapshot() {
        snapshot = null;
    }

    private static ItemSprite createSprite(Marker marker) {
        switch (marker.kind) {
            case RADIUS:
                ItemSprite radius = new ItemSprite();
                BlastRadiusPickup.renderBurning(radius);
                return radius;
            case HASTE:
                ItemSprite haste = new ItemSprite();
                BlastHastePickup.renderHaste(haste);
                return haste;
            case BOMB:
                return new ItemSprite(marker.image, new ItemSprite.Glowing(0xFF0000, 0.6f));
            default:
                throw new IllegalStateException("Unsupported out-of-sight item: " + marker.kind);
        }
    }

    @Override
    public void update() {
        Snapshot current = snapshot;
        if (current != rendered) {
            for (ItemSprite sprite : sprites.values()) sprite.killAndErase();
            sprites.clear();
            if (current != null && current.level == level && Dungeon.level == level) {
                for (Map.Entry<Integer, Marker> entry : current.markers.entrySet()) {
                    ItemSprite sprite = createSprite(entry.getValue());
                    sprite.place(entry.getKey());
                    add(sprite);
                    sprites.put(entry.getKey(), sprite);
                }
            }
            rendered = current;
        }

        super.update();

        // ItemSprite.update() resets unlinked sprites to visible. Set visibility after it.
        for (Map.Entry<Integer, ItemSprite> entry : sprites.entrySet()) {
            entry.getValue().visible = Dungeon.level == level
                    && !level.heroFOV[entry.getKey()];
        }
    }
}
