package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.Image;
import com.watabou.utils.Bundle;


/** Permanent player buff for selectable remote and timed-fuse modes. */
public final class BlastRemoteBomb extends Buff {

    private static final String NEXT_SEQUENCE = "blast_next_remote_bomb_sequence";
    private static final String TIMED_MODE = "blast_timed_bomb_mode";
    private int nextSequence;
    private boolean timedMode;

    {
        type = buffType.POSITIVE;
        revivePersists = true;
    }

    /** Grant remote control after the first-run tutorial has ended. */
    public static void ensureHero() {
        if (!SPDSettings.intro() && Dungeon.hero != null && Dungeon.hero.isAlive()
                && Dungeon.hero.buff(BlastRemoteBomb.class) == null) {
            Buff.affect(Dungeon.hero, BlastRemoteBomb.class);
        }
    }

    /** The mode is independent of whether the permanent buff exists. */
    public boolean timedMode() {
        return timedMode;
    }

    public static boolean isTimedMode() {
        return Dungeon.hero != null && Dungeon.hero.buff(BlastRemoteBomb.class) != null
                && Dungeon.hero.buff(BlastRemoteBomb.class).timedMode;
    }

    /** Lit bombs prevent mode changes, including ones unrelated to Bomb Maniac. */
    private static boolean hasLitBombs() {
        if (Dungeon.level == null) return false;
        for (Heap heap : Dungeon.level.heaps.valueList()) {
            for (Item item : heap.items) {
                if (item instanceof Bomb && ((Bomb) item).fuse != null) return true;
            }
        }
        return false;
    }

    /** Automatically restore remote control once the last timed throw has resolved. */
    public static void restoreRemoteModeIfNecessary() {
        if (Dungeon.hero == null || Dungeon.level == null) return;
        BlastRemoteBomb remote = Dungeon.hero.buff(BlastRemoteBomb.class);
        BlastBombManiac maniac = BlastBombManiac.current();
        if (remote == null || maniac == null || !remote.timedMode
                || maniac.unlimited() || maniac.capacity() > 1 || hasLitBombs()) return;
        maniac.cancelAim();
        remote.timedMode = false;
        GLog.i(BlastMessages.get("remote_bomb.remote_enabled"));
    }

    /** Enter timed mode only with spare permanent capacity and no lit bombs. */
    public static void toggleMode() {
        if (Dungeon.hero == null || !Dungeon.hero.ready || Dungeon.level == null) return;
        BlastRemoteBomb remote = Dungeon.hero.buff(BlastRemoteBomb.class);
        if (remote == null) return;
        BlastBombManiac maniac = BlastBombManiac.current();
        if (!remote.timedMode && (maniac == null || (!maniac.unlimited() && maniac.capacity() <= 1))) {
            GLog.w(BlastMessages.get("remote_bomb.capacity_required"));
            return;
        }
        if (hasLitBombs()) {
            GLog.w(BlastMessages.get("remote_bomb.switch_blocked"));
            return;
        }
        if (maniac != null) maniac.cancelAim();
        remote.timedMode = !remote.timedMode;
        GLog.i(BlastMessages.get(remote.timedMode
                ? "remote_bomb.timed_enabled" : "remote_bomb.remote_enabled"));
    }

    /** Called only when throwing an ignited bomb; plain throws stay unchanged. */
    public int allocateSequence() {
        nextSequence = Math.addExact(nextSequence, 1);
        return nextSequence;
    }

    /** One press detonates the oldest surviving remote bomb. */
    public static void detonateNext() {
        if (Dungeon.hero == null || isTimedMode()
                || Dungeon.hero.buff(BlastRemoteBomb.class) == null
                || Dungeon.level == null) return;
        Level level = Dungeon.level;
        RemoteFuse next = null;
        for (Heap heap : level.heaps.valueList()) {
            for (Item item : heap.items) {
                if (!(item instanceof Bomb)) continue;
                Bomb bomb = (Bomb) item;
                if (!(bomb.fuse instanceof RemoteFuse)) continue;
                RemoteFuse fuse = (RemoteFuse) bomb.fuse;
                if (fuse.isArmed(bomb)
                        && (next == null || fuse.sequence() < next.sequence())) next = fuse;
            }
        }
        if (next != null) next.detonateIfPresent(level);
    }

    @Override
    public boolean act() {
        restoreRemoteModeIfNecessary();
        spend(TICK);
        return true;
    }

    @Override
    public int icon() {
        return BuffIndicator.RECHARGING;
    }

    @Override
    public void tintIcon(Image icon) {
        icon.hardlight(0xFFAA33);
    }

    @Override
    public String iconTextDisplay() {
        return timedMode ? "T" : "R";
    }

    @Override
    public String name() {
        return BlastMessages.get("remote_bomb.buff_name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("remote_bomb.buff_desc");
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(NEXT_SEQUENCE, nextSequence);
        bundle.put(TIMED_MODE, timedMode);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        nextSequence = bundle.getInt(NEXT_SEQUENCE);
        timedMode = bundle.getBoolean(TIMED_MODE);
    }

    /** A fuse that never counts down, but still obeys vanilla snuff/freeze/explode. */
    public static final class RemoteFuse extends Bomb.Fuse {
        private static final String SEQUENCE = "blast_remote_bomb_sequence";
        private int sequence;

        /** Required for SPD bundle restoration. */
        public RemoteFuse() {}

        public RemoteFuse(int sequence) {
            if (sequence <= 0) {
                throw new IllegalArgumentException("Remote bomb sequence must be positive");
            }
            this.sequence = sequence;
        }

        public int sequence() {
            return sequence;
        }

        public boolean isArmed(Bomb item) {
            return bomb == item && item.fuse == this;
        }

        public void detonateIfPresent(Level level) {
            if (bomb == null || bomb.fuse != this) {
                return;
            }
            for (Heap heap : level.heaps.valueList()) {
                if (heap.items.contains(bomb)) {
                    // Fuse.trigger removes the bomb before exploding it and
                    // snuffs this actor. Subsequent entries will be invalid.
                    trigger(heap);
                    return;
                }
            }
        }

        @Override
        protected boolean act() {
            if (bomb == null || bomb.fuse != this) {
                snuff();
            } else {
                spend(TICK);
            }
            return true;
        }

        @Override
        public void storeInBundle(Bundle bundle) {
            super.storeInBundle(bundle);
            bundle.put(SEQUENCE, sequence);
        }

        @Override
        public void restoreFromBundle(Bundle bundle) {
            super.restoreFromBundle(bundle);
            sequence = bundle.getInt(SEQUENCE);
            if (sequence <= 0) {
                throw new IllegalStateException("Invalid saved remote bomb sequence: " + sequence);
            }
        }
    }
}
