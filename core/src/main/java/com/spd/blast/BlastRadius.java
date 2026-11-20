package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.Image;
import com.watabou.utils.Bundle;

/** At most one additional explosion tile of reach, granted by a radius pickup. */
public final class BlastRadius extends Buff {
    private static final String STACKS = "blast_radius_stacks";
    private static final String TEST_BOOST = "blast_radius_test_boost";
    public static final int MAX_STACKS = 1;
    private int stacks;
    private int testBoost = -1;

    {
        type = buffType.POSITIVE;
        announced = true;
        revivePersists = false;
    }

    /** Return false when the permanent radius boost is already at its cap. */
    public static boolean acquire(Hero hero) {
        int current = stacks(hero);
        if (current >= MAX_STACKS) return false;
        setStacks(hero, Math.addExact(current, 1));
        return true;
    }

    public static int stacks(Hero hero) {
        if (hero == null) throw new IllegalArgumentException("Hero required");
        BlastRadius radius = hero.buff(BlastRadius.class);
        return radius == null ? 0 : radius.stacks;
    }

    /** Normal pickup bonus is always either zero or one. */
    public static void setStacks(Hero hero, int count) {
        if (hero == null) throw new IllegalArgumentException("Hero required");
        if (count < 0 || count > MAX_STACKS) {
            throw new IllegalArgumentException("Invalid pickup radius boost: " + count);
        }
        BlastRadius radius = hero.buff(BlastRadius.class);
        if (radius == null && count > 0) {
            radius = Buff.affect(hero, BlastRadius.class);
            if (radius == null) throw new IllegalStateException("Cannot apply blast radius buff");
        }
        if (radius != null) {
            radius.stacks = count;
            if (count == 0 && radius.testBoost < 0) radius.detach();
        }
        refresh(hero);
    }

    /** Test boost overrides the visible radius without changing the pickup bonus. */
    public static void setTestBoost(Hero hero, int count) {
        if (hero == null) throw new IllegalArgumentException("Hero required");
        if (count < 0) throw new IllegalArgumentException("Negative test radius boost");
        BlastRadius radius = hero.buff(BlastRadius.class);
        if (radius == null) radius = Buff.affect(hero, BlastRadius.class);
        if (radius == null) throw new IllegalStateException("Cannot apply blast radius buff");
        radius.testBoost = count;
        refresh(hero);
    }

    public static void clearTestBoost(Hero hero) {
        if (hero == null) return;
        BlastRadius radius = hero.buff(BlastRadius.class);
        if (radius == null || radius.testBoost < 0) return;
        radius.testBoost = -1;
        if (radius.stacks == 0) radius.detach();
        refresh(hero);
    }

    /** Effective boost used by explosions and the test slider. */
    public static int boost(Hero hero) {
        if (hero == null) return 0;
        BlastRadius radius = hero.buff(BlastRadius.class);
        if (radius == null) return 0;
        return radius.testBoost >= 0 ? radius.testBoost : radius.stacks;
    }

    private static void refresh(Hero hero) {
        BuffIndicator.refreshHero();
        if (Dungeon.hero == hero && Dungeon.level != null) {
            BlastBombWarnings.refresh(Dungeon.level);
        }
    }

    public static boolean hasBoost() {
        return boost(Dungeon.hero) > 0;
    }

    public static int apply(int original) {
        if (original < 0) throw new IllegalArgumentException("Negative explosion radius");
        return Math.addExact(original, boost(Dungeon.hero));
    }

    @Override
    public boolean act() {
        spend(TICK);
        return true;
    }

    @Override
    public int icon() {
        return BuffIndicator.UPGRADE;
    }

    @Override
    public void tintIcon(Image icon) {
        icon.hardlight(0xFFD700);
    }

    @Override
    public String iconTextDisplay() {
        return "+" + (testBoost >= 0 ? testBoost : stacks);
    }

    @Override
    public String name() {
        return BlastMessages.get("radius.buff_name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("radius.buff_desc", testBoost >= 0 ? testBoost : stacks);
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(STACKS, stacks);
        if (testBoost >= 0) bundle.put(TEST_BOOST, testBoost);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        stacks = bundle.getInt(STACKS);
        if (stacks < 0 || stacks > MAX_STACKS) {
            throw new IllegalStateException("Invalid pickup radius boost");
        }
        testBoost = bundle.contains(TEST_BOOST) ? bundle.getInt(TEST_BOOST) : -1;
        if (testBoost < -1) throw new IllegalStateException("Invalid test radius boost");
    }
}
