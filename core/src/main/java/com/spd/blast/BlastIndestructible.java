package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.FlavourBuff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.Image;

/** Six-turn immunity to damage attributed to bombs. */
public final class BlastIndestructible extends FlavourBuff {
    public static final float DURATION = 6f;

    {
        type = buffType.POSITIVE;
        announced = true;
    }

    public static BlastIndestructible grant(Hero hero) {
        if (hero == null) throw new IllegalArgumentException("Hero required");
        return Buff.affect(hero, BlastIndestructible.class, DURATION);
    }

    @Override
    public int icon() {
        return BuffIndicator.ARMOR;
    }

    @Override
    public void tintIcon(Image icon) {
        icon.hardlight(0xFFD700);
    }

    @Override
    public float iconFadePercent() {
        return Math.max(0f, (DURATION - visualcooldown()) / DURATION);
    }

    @Override
    public String name() {
        return BlastMessages.get("indestructible.buff_name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("indestructible.buff_desc", (int)Math.ceil(visualcooldown()));
    }
}
