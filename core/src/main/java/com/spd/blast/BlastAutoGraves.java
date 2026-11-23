package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.TormentedSpirit;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ArcaneBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ShrapnelBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfRemoveCurse;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.PathFinder;

/** Opens tombs with a prepared bomb loadout and cleanses the rare red wraith. */
final class BlastAutoGraves {
    private BlastAutoGraves() {}

    static boolean canOpen(boolean arcane, boolean shrapnel, boolean scroll) {
        return scroll && (arcane || shrapnel);
    }

    private static ScrollOfRemoveCurse prepared(Hero hero) {
        if (!BlastSettings.testAutoPlay() || hero.belongings.lostInventory()) return null;
        BlastBombManiac maniac = BlastBombManiac.current();
        ScrollOfRemoveCurse scroll = hero.belongings.getItem(ScrollOfRemoveCurse.class);
        if (maniac == null || scroll == null
                || !canOpen(maniac.typeIndex(ArcaneBomb.class) >= 0,
                        maniac.typeIndex(ShrapnelBomb.class) >= 0, scroll.quantity() > 0))
            return null;
        return scroll;
    }

    static BlastAutoSpecialRooms.Action cleanse(Hero hero, boolean[] safe, int[] distances) {
        ScrollOfRemoveCurse scroll = prepared(hero);
        if (scroll == null) return BlastAutoSpecialRooms.Action.NONE;
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
        if (prepared(hero) == null) return BlastAutoSpecialRooms.Action.NONE;
        Level level = Dungeon.level;
        int target = -1, stand = -1, best = Integer.MAX_VALUE;
        for (Heap heap : level.heaps.valueList()) {
            if (heap.type != Heap.Type.TOMB || !heap.seen) continue;
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
        if (target < 0 || (hero.rooted && hero.pos != stand))
            return BlastAutoSpecialRooms.Action.NONE;
        if (hero.pos == stand) {
            hero.curAction = new HeroAction.OpenChest(target);
            BlastLog.debug("auto-grave action=open tomb=" + target);
        } else {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(stand, safe, distances));
        }
        return BlastAutoSpecialRooms.Action.MOVE;
    }
}
