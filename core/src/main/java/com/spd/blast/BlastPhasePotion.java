package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.watabou.noosa.Image;

/** Identified consumable that grants six turns of phase movement. */
public final class BlastPhasePotion extends Potion {
    public static final int ICON_TINT = 0x66DDFF;

    /** Replace the source icon's green hue, rather than multiply it with cyan. */
    public static void tintRunningIcon(Image icon) {
        icon.tint(ICON_TINT, 0.95f);
    }
    {
        image = ItemSpriteSheet.POTION_HOLDER;
        icon = ItemSpriteSheet.Icons.RING_HASTE;
    }

    @Override
    public void reset() {
        super.reset();
        image = ItemSpriteSheet.POTION_HOLDER;
    }

    @Override
    public boolean isKnown() {
        return true;
    }

    @Override
    public String name() {
        return BlastMessages.get("phase_potion.name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("phase_potion.desc");
    }

    @Override
    public void apply(Hero hero) {
        BlastFreeMovement.grant(hero);
    }
}
