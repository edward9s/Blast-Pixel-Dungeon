package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Amok;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.ChampionEnemy;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.MobSpawner;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.List;

/** One-step evasive walking for champions and rare mobs threatened by visible bombs. */
public final class BlastBombDodge {
    private BlastBombDodge() {}

    /**
     * Returns one ordinary walking step for a threatened champion or rare mob, or -1.
     * Rare mobs attempt a kick first when eligible, then use this shared dodge.
     * Each visible lit bomb contributes its actual warning footprint.
     */
    public static int dodgeStep(Mob mob) {
        Level level = Dungeon.level;
        if (level == null || Dungeon.hero == null || !Dungeon.hero.isAlive()
                || mob.alignment != Char.Alignment.ENEMY
                || mob.state == mob.SLEEPING || mob.state == mob.PASSIVE
                || mob.state == mob.FLEEING || mob.rooted
                || Char.hasProp(mob, Char.Property.IMMOVABLE)
                || (mob.buffs(ChampionEnemy.class).isEmpty()
                    && !MobSpawner.RARE_ALTS.containsValue(mob.getClass()))
                || mob.fieldOfView == null || mob.fieldOfView.length != level.length()) {
            return -1;
        }

        int[] danger = null;
        ArrayList<Integer> bombPositions = new ArrayList<>();
        for (Heap heap : level.heaps.valueList()) {
            if (!level.insideMap(heap.pos) || !mob.fieldOfView[heap.pos]) continue;
            for (Item item : heap.items) {
                if (!(item instanceof Bomb) || ((Bomb) item).fuse == null) continue;
                if (danger == null) danger = new int[level.length()];
                bombPositions.add(heap.pos);
                boolean[] footprint = ((Bomb) item).blastWarningCells(heap.pos);
                if (footprint.length != danger.length) {
                    throw new IllegalStateException("Invalid bomb danger footprint");
                }
                for (int cell = 0; cell < footprint.length; cell++) {
                    if (footprint[cell]) danger[cell]++;
                }
            }
        }

        if (danger == null) return -1;
        int currentDanger = danger[mob.pos];
        if (currentDanger == 0) return -1;

        float currentDistance = closestBombDistance(level, mob.pos, bombPositions);
        int bestCell = -1;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = mob.pos + offset;
            if (!level.insideMap(cell) || !level.adjacent(mob.pos, cell)
                    || (!level.passable[cell]
                        && !((mob.flying || mob.buff(Amok.class) != null) && level.avoid[cell]))
                    || (Char.hasProp(mob, Char.Property.LARGE) && !level.openSpace[cell])
                    || Actor.findChar(cell) != null) {
                continue;
            }

            int candidateDanger = danger[cell];
            float candidateDistance = closestBombDistance(level, cell, bombPositions);
            if (candidateDanger < currentDanger
                    || (candidateDanger == currentDanger && candidateDistance > currentDistance)) {
                bestCell = cell;
                currentDanger = candidateDanger;
                currentDistance = candidateDistance;
            }
        }
        return bestCell;
    }

    private static float closestBombDistance(Level level, int cell, List<Integer> bombPositions) {
        float distance = Float.MAX_VALUE;
        for (int pos : bombPositions) {
            distance = Math.min(distance, level.trueDistance(cell, pos));
        }
        return distance;
    }
}
