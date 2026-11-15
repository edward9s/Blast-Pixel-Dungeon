package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;

/** Key eligibility and container classification for auto-opening. */
public final class BlastAutoInteractTest {
    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        check(true, BlastAutoInteract.canOpen(Heap.Type.CHEST, 0, 0), "ordinary chest");
        check(true, BlastAutoInteract.canOpen(Heap.Type.SKELETON, 0, 0), "skeleton");
        check(true, BlastAutoInteract.canOpen(Heap.Type.REMAINS, 0, 0), "remains");
        check(false, BlastAutoInteract.canOpen(Heap.Type.TOMB, 0, 0), "tomb excluded");
        check(false, BlastAutoInteract.canOpen(Heap.Type.FOR_SALE, 0, 0), "shop excluded");
        check(false, BlastAutoInteract.canOpen(Heap.Type.HEAP, 0, 0), "loose items excluded");
        check(false, BlastAutoInteract.canOpen(Heap.Type.LOCKED_CHEST, 0, 0), "no gold key");
        check(true, BlastAutoInteract.canOpen(Heap.Type.LOCKED_CHEST, 1, 0), "gold key");
        check(false, BlastAutoInteract.canOpen(Heap.Type.CRYSTAL_CHEST, 1, 0),
                "gold cannot open crystal chest");
        check(true, BlastAutoInteract.canOpen(Heap.Type.CRYSTAL_CHEST, 0, 1),
                "crystal key opens crystal chest");

        check(false, BlastAutoInteract.canUnlock(Terrain.LOCKED_DOOR, 0, 0, 0),
                "ordinary door without key");
        check(true, BlastAutoInteract.canUnlock(Terrain.LOCKED_DOOR, 1, 0, 0),
                "iron key opens locked door");
        check(false, BlastAutoInteract.canUnlock(Terrain.CRYSTAL_DOOR, 1, 0, 0),
                "iron key cannot open crystal door");
        check(true, BlastAutoInteract.canUnlock(Terrain.CRYSTAL_DOOR, 0, 1, 0),
                "crystal key opens crystal door");
        check(false, BlastAutoInteract.canUnlock(Terrain.LOCKED_EXIT, 1, 1, 0),
                "locked exit needs worn key");
        check(true, BlastAutoInteract.canUnlock(Terrain.LOCKED_EXIT, 0, 0, 1),
                "worn key opens locked exit");
        check(false, BlastAutoInteract.canUnlock(Terrain.HERO_LKD_DR, 1, 1, 1),
                "special door stays native");

        System.out.println("BlastAutoInteractTest passed");
    }
}
