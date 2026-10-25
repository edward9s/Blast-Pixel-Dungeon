package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Adrenaline;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.MobSpawner;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.Random;
import com.watabou.utils.Reflection;

import java.util.ArrayList;

/** Optional mob loot and destruction penalties for special floor pickups. */
public final class BlastLoot {
    private static final int SUMMON_COUNT = 8;

    private BlastLoot() {}

    /** The Loot Tag can only pick up the current heap's first item. */
    public static boolean showLootTag(Heap heap) {
        if (heap == null) return false;
        if (heap.type != Heap.Type.HEAP) return true;
        Item item = heap.peek();
        return !(item instanceof Bomb && ((Bomb) item).fuse != null)
                && !(item instanceof BlastDeathBomb);
    }

    /** Quantity also catches existing heap stacks increased by normal mob loot. */
    public static int groundQuantity(int pos) {
        Heap heap = Dungeon.level.heaps.get(pos);
        int quantity = 0;
        if (heap != null) {
            for (Item item : heap.items) quantity = Math.addExact(quantity, item.quantity());
        }
        return quantity;
    }

    /** Called after vanilla (including subclass) loot has had first refusal. */
    public static void dropIfNoOtherLoot(Mob mob, int previousQuantity) {
        if (groundQuantity(mob.pos) != previousQuantity) return;
        float roll = Random.Float();
        Item item;
        if (roll < 1f / 12) {
            item = new BlastRadiusPickup();
        } else if (roll < 2f / 12) {
            item = new BlastHastePickup();
        } else if (roll < 2f / 12 + 1f / 48) {
            item = new BlastPhasePotion();
        } else if (roll < 2f / 12 + 2f / 48) {
            item = new BlastBombproofScroll();
        } else {
            return;
        }
        Dungeon.level.drop(item, mob.pos).sprite.drop();
    }

    public static void onDestroyed(BlastPickup pickup, int origin) {
        Level level = Dungeon.level;
        if (level == null || !level.insideMap(origin)) {
            throw new IllegalStateException("Destroyed pickup has no floor");
        }

        ArrayList<Class<? extends Mob>> rotation = MobSpawner.getMobRotation(Dungeon.depth);
        if (rotation.isEmpty()) throw new IllegalStateException("Floor has no monster rotation");

        int width = level.width();
        int height = level.length() / width;
        boolean[] visited = new boolean[level.length()];
        int[] queue = new int[level.length()];
        ArrayList<Integer> available = new ArrayList<>();
        visited[origin] = true;
        queue[0] = origin;
        int head = 0, tail = 1;
        while (head < tail) {
            int cell = queue[head++];
            if (cell != origin && (level.passable[cell] || level.avoid[cell]) && !level.pit[cell]) {
                available.add(cell);
            }
            int x = cell % width, y = cell / width;
            for (int dy = -1; dy <= 1; dy++) {
                int ny = y + dy;
                if (ny < 0 || ny >= height) continue;
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    if (dx == 0 && dy == 0 || nx < 0 || nx >= width) continue;
                    int next = ny * width + nx;
                    if (!visited[next] && (level.passable[next] || level.avoid[next])
                            && !level.pit[next]) {
                        visited[next] = true;
                        queue[tail++] = next;
                    }
                }
            }
        }

        for (int i = 0; i < SUMMON_COUNT; i++) {
            Mob mob = Reflection.newInstance(rotation.get(Random.Int(rotation.size())));
            if (mob == null) throw new IllegalStateException("Cannot create floor mob");
            int position = -1;
            for (int cell : available) {
                if (Actor.findChar(cell) == null
                        && (!Char.hasProp(mob, Char.Property.LARGE) || level.openSpace[cell])) {
                    position = cell;
                    break;
                }
            }
            if (position == -1) break; // A physically full floor has no valid cells.
            mob.pos = position;
            if (mob.state != mob.PASSIVE) mob.state = mob.WANDERING;
            // Adrenaline is a FlavourBuff, so its lifetime is set to the
            // fixed duration of one million turns.
            Buff.affect(mob, Adrenaline.class, 1_000_000f);
            GameScene.add(mob, 1f);
            level.occupyCell(mob);
        }
    }
}
