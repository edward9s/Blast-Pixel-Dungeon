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
    public static final int MAX_STACKS = 1;
    private int stacks;

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

    /** Set the actual buff stack count, including zero to remove it. */
    public static void setStacks(Hero hero, int count) {
        if (hero == null) throw new IllegalArgumentException("Hero required");
        if (count < 0) throw new IllegalArgumentException("Negative blast radius stacks");
        BlastRadius radius = hero.buff(BlastRadius.class);
        if (count == 0) {
            if (radius != null) radius.detach();
        } else {
            if (radius == null) radius = Buff.affect(hero, BlastRadius.class);
            if (radius == null) throw new IllegalStateException("Cannot apply blast radius buff");
            radius.stacks = count;
        }
        BuffIndicator.refreshHero();
        if (Dungeon.hero == hero && Dungeon.level != null) {
            BlastBombWarnings.refresh(Dungeon.level);
        }
    }

    /** Whether the Hero has any added blast reach. */
    public static boolean hasBoost() {
        Hero hero = Dungeon.hero;
        BlastRadius radius = hero == null ? null : hero.buff(BlastRadius.class);
        return radius != null && radius.stacks > 0;
    }

    public static int apply(int original) {
        if (original < 0) throw new IllegalArgumentException("Negative explosion radius");
        Hero hero = Dungeon.hero;
        BlastRadius radius = hero == null ? null : hero.buff(BlastRadius.class);
        return Math.addExact(original, radius == null ? 0 : radius.stacks);
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
        return "+" + stacks;
    }

    @Override
    public String name() {
        return BlastMessages.get("radius.buff_name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("radius.buff_desc", stacks);
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(STACKS, stacks);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        stacks = bundle.getInt(STACKS);
    }
}
