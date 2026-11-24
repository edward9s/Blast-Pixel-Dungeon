package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;

/** Store armed Bomb Maniac bombs in container cells without changing loot order. */
public final class BlastBombHeap {
    private BlastBombHeap() {}

    /** Shared with the native Heap.explode path for loose items. */
    public static boolean destroysLooseItem(Item item) {
        return item instanceof BlastPickup
                || (!item.unique && !item.isUpgradable() && !(item instanceof EquipableItem));
    }

    /** Only deployed, ignited Bomb Maniac bombs bypass locked-chest drop redirection. */
    public static boolean isExposedBomb(Item item) {
        if (!(item instanceof Bomb)) return false;
        Bomb bomb = (Bomb) item;
        return bomb.blastManiacCharge() && bomb.fuse != null;
    }

    public static boolean isContainer(Heap heap) {
        return heap.type == Heap.Type.CHEST
                || heap.type == Heap.Type.LOCKED_CHEST
                || heap.type == Heap.Type.CRYSTAL_CHEST;
    }

    /** Keep native heap drop behavior, but leave its original loot before the bomb. */
    public static void dropOnContainer(Heap heap, Item item) {
        if (!isContainer(heap) || !isExposedBomb(item)) {
            throw new IllegalArgumentException("Expected an armed Bomb Maniac bomb on a container");
        }
        heap.drop(item);
        if (!heap.items.remove(item)) {
            throw new IllegalStateException("Deployed bomb missing from container heap");
        }
        heap.items.addLast(item);
        if (heap.sprite != null) heap.sprite.view(heap).place(heap.pos);
    }

    /** Freezing still extinguishes exposed fuses even while the chest is closed. */
    public static void freezeExposed(Heap heap) {
        if (!isContainer(heap)) return;
        for (Item item : heap.items) {
            if (isExposedBomb(item)) ((Bomb) item).fuse.freeze();
        }
    }

    /** Container contents remain protected; exposed armed bombs still chain-react. */
    public static void detonateExposed(Heap heap) {
        if (!isContainer(heap)) return;
        for (Item item : heap.items.toArray(new Item[0])) {
            // Earlier explosions may have already consumed another bomb in this snapshot.
            if (!heap.items.contains(item) || !isExposedBomb(item)) continue;
            heap.remove(item);
            ((Bomb) item).explode(heap.pos);
        }
    }
}
