package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.FlavourBuff;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.Image;

/** Temporary hero phase movement, independent of the test switch. */
public final class BlastFreeMovement extends FlavourBuff {
    public static final float DURATION = 6f;

    {
        type = buffType.POSITIVE;
        announced = true;
    }

    public static BlastFreeMovement grant(Hero hero) {
        if (hero == null) throw new IllegalArgumentException("A hero is required");
        return Buff.affect(hero, BlastFreeMovement.class, DURATION);
    }

    @Override
    public int icon() {
        return BuffIndicator.LEVITATION;
    }

    @Override
    public void tintIcon(Image icon) {
        icon.hardlight(BlastPhasePotion.ICON_TINT);
    }

    @Override
    public float iconFadePercent() {
        return Math.max(0f, (DURATION - visualcooldown()) / DURATION);
    }

    @Override
    public String name() {
        return BlastMessages.get("free_movement.buff_name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("free_movement.buff_desc", (int)Math.ceil(visualcooldown()));
    }
}
