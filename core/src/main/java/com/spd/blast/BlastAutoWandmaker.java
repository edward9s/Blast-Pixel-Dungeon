package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.CeremonialCandle;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.RitualSiteRoom;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;

/** Place quest candles using the original ritual and item-throwing rules. */
final class BlastAutoWandmaker {
    private BlastAutoWandmaker() {}

    private static RitualSiteRoom ritualRoom(Level level) {
        int center = CeremonialCandle.ritualPos;
        if (Dungeon.branch != 0 || !(level instanceof RegularLevel) || !level.insideMap(center)) return null;
        Room room = ((RegularLevel) level).room(center);
        return room instanceof RitualSiteRoom ? (RitualSiteRoom) room : null;
    }

    static boolean isPlacedCandle(Level level, int cell, Item item) {
        if (!(item instanceof CeremonialCandle) || !((CeremonialCandle) item).aflame
                || ritualRoom(level) == null) return false;
        int center = CeremonialCandle.ritualPos;
        for (int offset : PathFinder.NEIGHBOURS4) {
            if (cell == center + offset) return true;
        }
        return false;
    }

    static boolean placeCandles(Hero hero, boolean[] safe, int[] distances) {
        if (hero.rooted || hero.belongings.lostInventory()) return false;
        CeremonialCandle candle = hero.belongings.getItem(CeremonialCandle.class);
        if (candle == null || candle.quantity() <= 0) return false;
        Level level = Dungeon.level;
        RitualSiteRoom room = ritualRoom(level);
        if (room == null) return false;
        int center = CeremonialCandle.ritualPos;
        if (!(hero.fieldOfView[center] || level.visited[center] || level.mapped[center])
                || BlastAutoPlay.hasVisibleEnemy(hero)) return false;

        int missing = 0;
        int destination = -1;
        int stand = -1;
        int shortest = Integer.MAX_VALUE;
        for (int offset : PathFinder.NEIGHBOURS4) {
            int cell = center + offset;
            Heap heap = level.heaps.get(cell);
            if (heap != null && heap.type == Heap.Type.HEAP
                    && BlastAutoPickup.firstEligibleItem(heap, item -> item instanceof CeremonialCandle) != null) {
                continue;
            }
            missing++;
            if (!safe[cell] || (heap != null && heap.type != Heap.Type.HEAP)) continue;
            for (int adjacent : PathFinder.NEIGHBOURS8) {
                int candidate = cell + adjacent;
                if (!level.insideMap(candidate) || candidate == center || !safe[candidate]
                        || distances[candidate] >= shortest
                        || !room.inside(level.cellToPoint(candidate))
                        || new Ballistica(candidate, cell, Ballistica.PROJECTILE).collisionPos != cell) continue;
                destination = cell;
                stand = candidate;
                shortest = distances[candidate];
            }
        }

        if (destination < 0 || candle.quantity() < missing) return false;
        if (stand != hero.pos) {
            hero.curAction = new HeroAction.Move(BlastAutoPlay.firstStep(stand, safe, distances));
            return true;
        }
        if (candle.throwPos(hero, destination) != destination) return false;
        BlastLog.debug("auto-wandmaker action=throw-candle target=" + destination + " missing=" + missing);
        candle.setCurrent(hero);
        candle.cast(hero, destination);
        return true;
    }
}
