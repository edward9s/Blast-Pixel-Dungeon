package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.TormentedSpirit;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.PathFinder;

/** Opens discovered tombs and cleanses the rare red wraith. */
final class BlastAutoGraves {
    private static Level lastTombLevel;
    private static String lastTombBlock;

    private BlastAutoGraves() {}

    static BlastAutoSpecialRooms.Action cleanse(Hero hero, boolean[] safe, int[] distances) {
        if (!BlastSettings.testAutoPlay() || hero.belongings.lostInventory()) {
            return BlastAutoSpecialRooms.Action.NONE;
        }
        ScrollOfRemoveCurse scroll = hero.belongings.getItem(ScrollOfRemoveCurse.class);
        if (scroll == null || scroll.quantity() <= 0) return BlastAutoSpecialRooms.Action.NONE;
        Level level = Dungeon.level;
        int stand = -1, best = Integer.MAX_VALUE;
        for (Mob mob : level.mobs) {
            if (!(mob instanceof TormentedSpirit) || !mob.isAlive()
                    || mob.invisible > 0 || !hero.fieldOfView[mob.pos]) continue;
            if (level.distance(hero.pos, mob.pos) <= 1) {
                BlastLog.debug("auto-grave action=cleanse spirit=" + mob.pos);
                scroll.execute(hero, Scroll.AC_READ);
                return BlastAutoSpecialRooms.Action.USE;
            }
            for (int offset : PathFinder.NEIGHBOURS8) {
                int cell = mob.pos + offset;
                if (!level.insideMap(cell) || !safe[cell]
                        || distances[cell] >= best) continue;
                best = distances[cell];
                stand = cell;
            }
        }
        if (stand < 0 || hero.rooted) return BlastAutoSpecialRooms.Action.NONE;
        hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(stand, safe, distances));
        BlastLog.debug("auto-grave action=approach-spirit stand=" + stand);
        return BlastAutoSpecialRooms.Action.MOVE;
    }

    static BlastAutoSpecialRooms.Action openTomb(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        int observed = -1, nearest = Integer.MAX_VALUE;
        int target = -1, stand = -1, best = Integer.MAX_VALUE;
        for (Heap heap : level.heaps.valueList()) {
            if (heap.type != Heap.Type.TOMB || !(heap.seen || hero.fieldOfView[heap.pos])) continue;
            int distance = level.distance(hero.pos, heap.pos);
            if (distance < nearest) {
                observed = heap.pos;
                nearest = distance;
            }
            int candidate = heap.pos;
            if (safe[candidate] && distances[candidate] < best) {
                stand = candidate;
                target = heap.pos;
                best = distances[candidate];
            }
            for (int offset : PathFinder.NEIGHBOURS8) {
                candidate = heap.pos + offset;
                if (!level.insideMap(candidate) || !safe[candidate]
                        || distances[candidate] >= best) continue;
                stand = candidate;
                target = heap.pos;
                best = distances[candidate];
            }
        }
        if (observed < 0) return BlastAutoSpecialRooms.Action.NONE;
        if (target < 0) return tombBlocked(observed, "no-safe-stand");
        if (hero.rooted && hero.pos != stand) return tombBlocked(target, "rooted");
        if (hero.pos == stand) {
            hero.curAction = new HeroAction.OpenChest(target);
            lastTombBlock = null;
            BlastLog.debug("auto-grave action=open tomb=" + target);
        } else {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(stand, safe, distances));
        }
        return BlastAutoSpecialRooms.Action.MOVE;
    }

    private static BlastAutoSpecialRooms.Action tombBlocked(int tomb, String reason) {
        if (BlastSettings.debugLog()) {
            String trace = "auto-grave tomb=" + tomb + " blocked=" + reason;
            if (lastTombLevel != Dungeon.level || !trace.equals(lastTombBlock)) {
                lastTombLevel = Dungeon.level;
                lastTombBlock = trace;
                BlastLog.debug(trace);
            }
        }
        return BlastAutoSpecialRooms.Action.NONE;
    }
}
