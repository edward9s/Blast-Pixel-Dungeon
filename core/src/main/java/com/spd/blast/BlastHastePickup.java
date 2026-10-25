package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.TextureFilm;

/** Speed-boosting pickup with the same collection and destruction rules as BlastRadiusPickup. */
public final class BlastHastePickup extends BlastPickup {
    {
        image = ItemSpriteSheet.SOMETHING;
    }

    /** Reuse the native Haste buff icon and its yellow tint. */
    public static void renderHaste(ItemSprite sprite) {
        if (sprite == null) throw new IllegalArgumentException("Item sprite required");
        sprite.glow(null);
        sprite.texture(Assets.Interfaces.BUFFS_LARGE);
        sprite.frame(new TextureFilm(sprite.texture, 16, 16).get(BuffIndicator.HASTE));
        sprite.hardlight(1f, 0.8f, 0f);
    }

    @Override
    protected boolean grant(Hero hero) {
        // Buff.affect adds 20 to the remaining time, unlike Buff.prolong.
        Buff.affect(hero, Haste.class, Haste.DURATION);
        SpellSprite.show(hero, SpellSprite.HASTE, 1, 1, 0);
        return true;
    }

    @Override
    public String name() {
        return BlastMessages.get("haste_pickup.name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("haste_pickup.desc");
    }
}
