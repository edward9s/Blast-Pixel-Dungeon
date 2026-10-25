package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.MobSpawner;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Bundle;

import java.util.List;

/** Rare monsters dodge shared blast danger and otherwise teleport to kick bombs. */
public final class BlastBombKick {
    private BlastBombKick() {}

    /** Three mob actions without another kick after each successful kick. */
    public static final class Recovery extends Buff {
        private static final String TURNS_REMAINING = "turns_remaining";
        private static final int DODGE_TURNS = 3;
        private int turnsRemaining;

        private static void start(Mob mob) {
            Recovery recovery = Buff.affect(mob, Recovery.class);
            if (recovery.target != mob || recovery.turnsRemaining != 0) {
                throw new IllegalStateException("Cannot start rare bomb kick recovery");
            }
            recovery.turnsRemaining = DODGE_TURNS;
        }

        /** Consume one AI action, whether a safe dodge step is available or not. */
        private static boolean consume(Mob mob) {
            Recovery recovery = mob.buff(Recovery.class);
            if (recovery == null) return false;
            if (recovery.turnsRemaining <= 0) {
                throw new IllegalStateException("Invalid rare bomb kick recovery");
            }
            if (--recovery.turnsRemaining == 0) recovery.detach();
            return true;
        }

        @Override
        public boolean act() {
            spend(TICK);
            return true;
        }

        @Override
        public void storeInBundle(Bundle bundle) {
            super.storeInBundle(bundle);
            bundle.put(TURNS_REMAINING, turnsRemaining);
        }

        @Override
        public void restoreFromBundle(Bundle bundle) {
            super.restoreFromBundle(bundle);
            turnsRemaining = bundle.getInt(TURNS_REMAINING);
            if (turnsRemaining < 1 || turnsRemaining > DODGE_TURNS) {
                throw new IllegalStateException("Invalid rare bomb kick recovery turns");
            }
        }
    }

    /**
     * Called after a mob's incapacitation checks, before its normal AI action.
     * The caller spends exactly one turn on a successful kick.
     */
    public static boolean tryKick(Mob mob) {
        if (!MobSpawner.RARE_ALTS.containsValue(mob.getClass())) return false;
        if (Recovery.consume(mob)) return false;

        Level level = Dungeon.level;
        if (level == null || Dungeon.hero == null || !Dungeon.hero.isAlive()
                || mob.alignment != Char.Alignment.ENEMY
                || mob.state == mob.SLEEPING || mob.state == mob.PASSIVE
                || mob.state == mob.FLEEING || mob.rooted
                || Char.hasProp(mob, Char.Property.IMMOVABLE)
                || mob.fieldOfView == null || mob.fieldOfView.length != level.length()) {
            return false;
        }
        Bomb selected = null;
        Heap source = null;
        int teleportTo = -1;
        int kickTo = -1;
        float nearest = Float.MAX_VALUE;

        for (Heap heap : level.heaps.valueList()) {
            if (!level.insideMap(heap.pos) || !mob.fieldOfView[heap.pos]) continue;

            // A heap can contain other items or multiple bombs. Only lit ones
            // count; moving the very same instance preserves its identity.
            for (Item item : heap.items) {
                if (!(item instanceof Bomb) || ((Bomb) item).fuse == null) continue;
                Bomb bomb = (Bomb) item;
                boolean[] blast = bomb.blastWarningCells(heap.pos);
                if (!blast[mob.pos]) continue;

                // Shared blast danger takes priority over kicking: let the
                // following BlastBombDodge action choose an ordinary escape step.
                if (blast[Dungeon.hero.pos]) return false;

                float distance = level.trueDistance(mob.pos, heap.pos);

                Ballistica line = BlastBombTrajectory.flight(heap.pos, Dungeon.hero.pos);
                List<Integer> traveled = line.subPath(1, line.dist);
                int landing = BlastBombTrajectory.landing(line);
                if (landing == heap.pos) continue;

                for (int offset : PathFinder.NEIGHBOURS8) {
                    int cell = heap.pos + offset;
                    if (!level.insideMap(cell) || !level.passable[cell]
                            || level.pit[cell] || traveled.contains(cell)
                            || (Actor.findChar(cell) != null && cell != mob.pos)
                            || (Char.hasProp(mob, Char.Property.LARGE)
                                && !level.openSpace[cell])) continue;

                    if (distance < nearest) {
                        selected = bomb;
                        source = heap;
                        teleportTo = cell;
                        kickTo = landing;
                        nearest = distance;
                    }
                }
            }
        }

        if (selected == null) return false;

        if (mob.pos != teleportTo) {
            ScrollOfTeleportation.appear(mob, teleportTo);
            level.occupyCell(mob);
        }
        // Teleporting can itself trigger environmental effects.
        if (!mob.isAlive()) return true;

        if (!source.items.contains(selected) || selected.fuse == null) {
            return true;
        }
        source.remove(selected);
        // Preserve remote fuse and detonation order; timed fuses scale with blast radius.
        if (!(selected.fuse instanceof BlastRemoteBomb.RemoteFuse)) {
            selected.blastResetFuseAfterKick();
        }
        level.drop(selected, kickTo).sprite.drop(source.pos);
        Recovery.start(mob);
        return true;
    }
}
