package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.MissileSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.Image;
import com.watabou.utils.Bundle;
import com.watabou.utils.Callback;
import com.watabou.utils.Reflection;

import java.util.ArrayList;

/** Permanent Bomb Maniac buff: capacity limits deployed bombs. */
public final class BlastBombManiac extends Buff {
    private static final String CAPACITY = "blast_bomb_capacity";
    private static final String SELECTED = "blast_bomb_selected";
    private static final String ACTIVE = "blast_bomb_active";
    private static final String TYPES = "blast_bomb_types";
    private static final String SAVED_TYPES = "blast_bomb_saved_types";

    private int capacity = 1;
    private int activeBombs;
    private int selected;
    private final ArrayList<Class<? extends Bomb>> types = new ArrayList<>();
    private ArrayList<Class<? extends Bomb>> savedTypes;
    private transient CellSelector.Listener targeting;
    private transient Mob autoTarget;

    {
        type = buffType.POSITIVE;
        revivePersists = true;
    }

    public BlastBombManiac() {
        types.add(Bomb.class);
    }

    /** Every living hero past the tutorial receives this buff, independently of test settings. */
    public static void ensureHero() {
        if (SPDSettings.intro() || Dungeon.hero == null || !Dungeon.hero.isAlive()) return;
        BlastBombManiac buff = Dungeon.hero.buff(BlastBombManiac.class);
        if (buff == null) buff = Buff.affect(Dungeon.hero, BlastBombManiac.class);
        buff.setInfiniteBombTypes(BlastSettings.testInfiniteBomb());
    }

    public static BlastBombManiac current() {
        return Dungeon.hero == null ? null : Dungeon.hero.buff(BlastBombManiac.class);
    }

    public int capacity() {
        return capacity;
    }

    public boolean unlimited() {
        return BlastSettings.testInfiniteBomb();
    }

    /** Infinite bombs unlocks all kinds while preserving normally imbued kinds. */
    public void setInfiniteBombTypes(boolean enabled) {
        if (target != Dungeon.hero) {
            throw new IllegalStateException("Bomb type change requires the current hero");
        }
        if (enabled && savedTypes == null) {
            savedTypes = new ArrayList<>(types);
            for (Class<? extends Bomb> kind : Bomb.EnhanceBomb.validIngredients.values()) {
                if (!types.contains(kind)) types.add(kind);
            }
        } else if (!enabled && savedTypes != null) {
            Class<? extends Bomb> previousType = types.get(selected);
            types.clear();
            types.addAll(savedTypes);
            savedTypes = null;
            selected = types.indexOf(previousType);
            if (selected < 0) selected = 0;
        }
    }

    public int deployed() {
        return activeBombs;
    }

    /** Return a deployment slot when its charged bomb is consumed. */
    public static void onConsumed(Bomb bomb) {
        if (!bomb.blastManiacCharge()) return;
        BlastBombManiac buff = current();
        if (buff != null && buff.activeBombs == 0) {
            throw new IllegalStateException("No deployed bomb slot to release");
        }
        bomb.blastSetManiacCharge(false);
        if (buff != null) {
            buff.activeBombs--;
            BlastRemoteBomb.restoreRemoteModeIfNecessary();
        }
    }

    /** Consume a charged bomb at a chasm instead of sending it to the next floor. */
    public static boolean discardInChasm(Item item) {
        if (!(item instanceof Bomb)) return false;
        Bomb bomb = (Bomb) item;
        if (!bomb.blastManiacCharge()) return false;
        onConsumed(bomb);
        if (bomb.fuse != null) {
            bomb.fuse.snuff();
            bomb.fuse = null;
        }
        return true;
    }

    public int remaining() {
        return unlimited() ? Integer.MAX_VALUE : Math.max(0, capacity - activeBombs);
    }

    public int typeCount() {
        return types.size();
    }

    public int selectedIndex() {
        return selected;
    }

    public Bomb bombAt(int index) {
        if (index < 0 || index >= types.size()) {
            throw new IndexOutOfBoundsException("Bomb type index: " + index);
        }
        Bomb bomb = Reflection.newInstance(types.get(index));
        if (bomb == null) throw new IllegalStateException("Cannot instantiate bomb: " + types.get(index));
        return bomb;
    }

    public Bomb selectedBomb() {
        return bombAt(selected);
    }

    /** Stable lookup without changing the player's selected bomb. */
    public int typeIndex(Class<? extends Bomb> kind) {
        if (kind == null) throw new IllegalArgumentException("Bomb type is required");
        return types.indexOf(kind);
    }

    public void select(int index) {
        bombAt(index);
        selected = index;
    }

    /** Ordinary bombs expand capacity; recovered charged bombs return their occupied slot. */
    public void imbue(Bomb bomb) {
        if (target != Dungeon.hero || bomb == null || bomb.fuse != null
                || !Dungeon.hero.belongings.backpack.contains(bomb)) {
            throw new IllegalArgumentException("Imbue requires an uncharged inventory bomb");
        }
        if (!bomb.blastManiacCharge() && capacity == Integer.MAX_VALUE) {
            throw new IllegalStateException("Bomb capacity overflow");
        }
        Item consumed = bomb.detach(Dungeon.hero.belongings.backpack);
        if (consumed == null) throw new IllegalStateException("Unable to consume bomb");
        if (((Bomb) consumed).blastManiacCharge()) {
            onConsumed((Bomb) consumed);
        } else {
            capacity++;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Bomb> kind = (Class<? extends Bomb>) consumed.getClass();
        if (savedTypes != null && !savedTypes.contains(kind)) savedTypes.add(kind);
        if (!types.contains(kind)) {
            types.add(kind);
            selected = types.size() - 1;
        }
        Item.updateQuickslot();
    }

    /** The crosshair target while an aimed throw is being selected. */
    public Mob aimedTarget() {
        return targeting == null || !aimedThrowEnabled() ? null : autoTarget;
    }

    public static boolean aimedThrowEnabled() {
        return BlastRemoteBomb.isTimedMode() || BlastSettings.testRemoteThrow();
    }

    /** Cancel any pending timed throw when switching back to remote mode. */
    public void cancelAim() {
        if (targeting != null) GameScene.ready();
    }

    /** First press chooses a target; second press confirms the aimed throw. */
    public void aim() {
        if (!aimedThrowEnabled() || target != Dungeon.hero
                || (!Dungeon.hero.ready && !BlastSettings.testAutoPlay()) || Dungeon.level == null
                || GameScene.showingWindow()) return;
        if (BlastRemoteBomb.isTimedMode() && !unlimited() && capacity <= 1) {
            GLog.w(BlastMessages.get("remote_bomb.capacity_required"));
            return;
        }
        if (remaining() == 0) {
            GLog.w(BlastMessages.get("bomb_maniac.no_charges"));
            return;
        }

        if (targeting != null) {
            if (autoTarget != null && autoTarget.isAlive()
                    && autoTarget.alignment == Char.Alignment.ENEMY
                    && Dungeon.level.heroFOV[autoTarget.pos]
                    && autoTarget.invisible <= 0) {
                GameScene.handleCell(autoTarget.pos);
            } else {
                GameScene.ready();
            }
            return;
        }

        Hero hero = Dungeon.hero;
        Mob nearest = null;
        int best = Integer.MAX_VALUE;
        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive() || mob.alignment != Char.Alignment.ENEMY
                    || !hero.fieldOfView[mob.pos] || mob.invisible > 0) continue;
            int dest = BlastBombTrajectory.throwPos(hero.pos, mob.pos);
            if (dest != mob.pos) continue;
            int distance = Dungeon.level.distance(hero.pos, mob.pos);
            if (distance < best) {
                best = distance;
                nearest = mob;
            }
        }

        CellSelector.Listener selector = new CellSelector.Listener() {
            @Override
            public void onSelect(Integer cell) {
                if (targeting != this) return;
                targeting = null;
                autoTarget = null;
                if (cell != null && BlastBombManiac.current() == BlastBombManiac.this) {
                    if (BlastSettings.testAutoPlay()) {
                        // Clear targeting before queuing the throw so paused auto-play can wake.
                        GameScene.ready();
                        BlastAutoPlay.requestBombThrow(cell);
                    } else {
                        throwAt(cell);
                    }
                }
            }

            @Override
            public String prompt() {
                return BlastMessages.get("bomb_maniac.aim");
            }
        };
        targeting = selector;
        autoTarget = nearest;
        GameScene.selectCell(selector);
        if (nearest != null) {
            com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton.target(nearest);
        }
    }

    /** Timed throws cost one turn; test-enabled remote throws are free. */
    public boolean blastAutoThrow(int destination) {
        if (!BlastSettings.testAutoPlay()) {
            throw new IllegalStateException("Automatic bomb throw requires auto-play mode");
        }
        return throwAt(destination, false);
    }

    /** The AI waits for the throw animation before detonating this exact bomb. */
    public boolean blastAutoThrowAndDetonate(int destination) {
        return blastAutoThrowAndDetonate(destination, selected);
    }

    /** Clear terrain with a basic bomb without changing the player's selected type. */
    public boolean blastAutoThrowOrdinaryAndDetonate(int destination) {
        return blastAutoThrowAndDetonate(destination, 0);
    }

    public boolean blastAutoDemolishChest(int destination) {
        if (!BlastSettings.testRemoteThrow()) return false;
        return throwAt(destination, true, 0, true);
    }

    public boolean blastAutoDemolishChestNearby() {
        if (BlastSettings.testRemoteThrow()) return false;
        Bomb bomb = placeBomb(true, 0);
        if (bomb == null) return false;
        detonateAutoBomb(bomb, true);
        return true;
    }

    boolean blastAutoThrowAndDetonate(int destination, int bombIndex) {
        if (!BlastSettings.testAutoPlay() || !BlastSettings.testRemoteThrow()
                || BlastRemoteBomb.isTimedMode()) {
            throw new IllegalStateException("Automatic throw-and-detonate requires remote throw mode");
        }
        return throwAt(destination, true, bombIndex);
    }

    private boolean throwAt(int destination) {
        return throwAt(destination, false);
    }

    private boolean throwAt(int destination, boolean autoDetonate) {
        return throwAt(destination, autoDetonate, selected);
    }

    private boolean throwAt(int destination, boolean autoDetonate, int bombIndex) {
        return throwAt(destination, autoDetonate, bombIndex, false);
    }

    private boolean throwAt(int destination, boolean autoDetonate, int bombIndex,
                            boolean demolishChest) {
        final Hero hero = Dungeon.hero;
        final boolean timed = BlastRemoteBomb.isTimedMode();
        if (!aimedThrowEnabled() || hero == null
                || (!hero.ready && !BlastSettings.testAutoPlay())
                || current() != this || remaining() == 0
                || Dungeon.level == null) return false;
        if (timed && !unlimited() && capacity <= 1) {
            GLog.w(BlastMessages.get("remote_bomb.capacity_required"));
            return false;
        }
        final Bomb bomb = bombAt(bombIndex);
        bomb.blastSetManiacCharge(true);
        final int cell = bomb.throwPos(hero, destination);
        if (!Dungeon.level.insideMap(cell)) return false;

        hero.sprite.zap(cell);
        hero.busy();
        bomb.throwSound();
        Char enemy = Actor.findChar(cell);
        Callback finish = new Callback() {
            @Override
            public void call() {
                if (activeBombs == Integer.MAX_VALUE) {
                    throw new IllegalStateException("Active bomb overflow");
                }
                if (timed && !unlimited() && capacity <= 1) {
                    throw new IllegalStateException("Timed throw must preserve one remote bomb slot");
                }
                activeBombs++;
                if (timed && !unlimited()) {
                    capacity--;
                    if (capacity == 1) {
                        GLog.i(BlastMessages.get("remote_bomb.last_slot"));
                    }
                }
                bomb.blastThrowLit(cell);
                if (autoDetonate) detonateAutoBomb(bomb, demolishChest);
                // Automated attacks must advance time even when remote throws are free.
                hero.spendAndNext(autoDetonate ? 1f : timed ? 1f : 0f);
            }
        };
        MissileSprite missile = (MissileSprite) hero.sprite.parent.recycle(MissileSprite.class);
        if (enemy != null) {
            missile.reset(hero.sprite, enemy.sprite, bomb, finish);
        } else {
            missile.reset(hero.sprite, cell, bomb, finish);
        }
        return true;
    }

    /** Detonate only the newly deployed bomb, preserving older remote fuses. */
    private static void detonateAutoBomb(Bomb bomb) {
        detonateAutoBomb(bomb, false);
    }

    private static void detonateAutoBomb(Bomb bomb, boolean demolishChest) {
        if (!(bomb.fuse instanceof BlastRemoteBomb.RemoteFuse)) {
            throw new IllegalStateException("Automatic bomb has no remote fuse");
        }
        BlastRemoteBomb.RemoteFuse fuse = (BlastRemoteBomb.RemoteFuse) bomb.fuse;
        if (!fuse.isArmed(bomb)) {
            throw new IllegalStateException("Automatic bomb remote fuse is not armed");
        }
        boolean originalDemolition = BlastSettings.testDemolition();
        if (demolishChest) BlastSettings.setTestDemolition(true);
        try {
            if (!fuse.detonateIfPresent(Dungeon.level)) {
                throw new IllegalStateException("Automatic bomb was not deployed");
            }
        } finally {
            if (demolishChest) BlastSettings.setTestDemolition(originalDemolition);
        }
    }

    /** Place and immediately detonate one bomb during the AI's actor turn. */
    public boolean blastAutoPlaceAndDetonate() {
        return blastAutoPlaceAndDetonate(selected);
    }

    /** Clear terrain with a basic bomb without changing the player's selected type. */
    public boolean blastAutoPlaceOrdinaryAndDetonate() {
        return blastAutoPlaceAndDetonate(0);
    }

    boolean blastAutoPlaceAndDetonate(int bombIndex) {
        if (!BlastSettings.testAutoPlay() || BlastSettings.testRemoteThrow()
                || BlastRemoteBomb.isTimedMode()) {
            throw new IllegalStateException("Automatic place-and-detonate requires remote mode");
        }
        Bomb bomb = placeBomb(true, bombIndex);
        if (bomb == null) return false;
        detonateAutoBomb(bomb);
        return true;
    }

    /** Place a remote-armed bomb on the Hero's cell without spending a turn. */
    public void place() {
        placeBomb(false);
    }

    private Bomb placeBomb(boolean automatic) {
        return placeBomb(automatic, selected);
    }

    private Bomb placeBomb(boolean automatic, int bombIndex) {
        Hero hero = Dungeon.hero;
        if ((!automatic && aimedThrowEnabled()) || target != hero || hero == null
                || (!hero.ready && !BlastSettings.testAutoPlay())
                || Dungeon.level == null || (!automatic && GameScene.showingWindow())) return null;
        if (remaining() == 0) {
            GLog.w(BlastMessages.get("bomb_maniac.no_charges"));
            return null;
        }
        int cell = hero.pos;
        if (Dungeon.level.pit[cell]) {
            GLog.w(BlastMessages.get("bomb_maniac.blocked"));
            return null;
        }
        if (activeBombs == Integer.MAX_VALUE) {
            throw new IllegalStateException("Active bomb overflow");
        }
        Bomb bomb = bombAt(bombIndex);
        bomb.blastSetManiacCharge(true);
        activeBombs++;
        bomb.blastThrowLit(cell);
        return bomb;
    }

    @Override
    public boolean act() {
        spend(TICK);
        return true;
    }

    @Override
    public int icon() {
        return BuffIndicator.RECHARGING;
    }

    @Override
    public void tintIcon(Image icon) {
        icon.hardlight(0x33CCFF);
    }

    @Override
    public String iconTextDisplay() {
        return unlimited() ? "∞" : Integer.toString(remaining());
    }

    @Override
    public String name() {
        return BlastMessages.get("bomb_maniac.buff_name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("bomb_maniac.buff_desc", unlimited() ? "∞" : capacity,
                unlimited() ? "∞" : remaining());
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(CAPACITY, capacity);
        bundle.put(ACTIVE, activeBombs);
        bundle.put(SELECTED, selected);
        storeTypes(bundle, TYPES, types);
        if (savedTypes != null) storeTypes(bundle, SAVED_TYPES, savedTypes);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        capacity = bundle.getInt(CAPACITY);
        activeBombs = bundle.getInt(ACTIVE);
        selected = bundle.getInt(SELECTED);
        if (capacity < 1 || activeBombs < 0) throw new IllegalStateException("Invalid Bomb Maniac save");
        restoreTypes(bundle, TYPES, types);
        if (selected < 0 || selected >= types.size()) {
            throw new IllegalStateException("Invalid Bomb Maniac selection");
        }
        savedTypes = null;
        if (bundle.contains(SAVED_TYPES)) {
            savedTypes = new ArrayList<>();
            restoreTypes(bundle, SAVED_TYPES, savedTypes);
            if (!types.containsAll(savedTypes)) {
                throw new IllegalStateException("Saved bomb types are missing from active types");
            }
        }
    }

    private static void storeTypes(Bundle bundle, String key, ArrayList<Class<? extends Bomb>> kinds) {
        bundle.put(key, kinds.size());
        for (int i = 0; i < kinds.size(); i++) bundle.put(key + i, kinds.get(i).getName());
    }

    private static void restoreTypes(Bundle bundle, String key, ArrayList<Class<? extends Bomb>> kinds) {
        int count = bundle.getInt(key);
        if (count < 1) throw new IllegalStateException("Invalid bomb type count: " + key);
        kinds.clear();
        for (int i = 0; i < count; i++) {
            String name = bundle.getString(key + i);
            try {
                Class<?> loaded = Class.forName(name);
                if (!Bomb.class.isAssignableFrom(loaded)) {
                    throw new IllegalStateException("Not a bomb: " + name);
                }
                @SuppressWarnings("unchecked")
                Class<? extends Bomb> kind = (Class<? extends Bomb>) loaded;
                if (kinds.contains(kind) || Reflection.newInstance(kind) == null) {
                    throw new IllegalStateException("Invalid bomb type: " + name);
                }
                kinds.add(kind);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Unknown bomb type: " + name, e);
            }
        }
        if (kinds.get(0) != Bomb.class) {
            throw new IllegalStateException("Initial bomb type missing: " + key);
        }
    }
}
