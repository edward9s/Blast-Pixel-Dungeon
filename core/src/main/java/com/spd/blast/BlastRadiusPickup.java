package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.TextureFilm;

/** Fire-themed pickup: grant a single permanent +1 bomb radius upgrade. */
public final class BlastRadiusPickup extends BlastPickup {
    {
        image = ItemSpriteSheet.SOMETHING;
    }

    /** Display the same built-in icon as the Burning buff. */
    public static void renderBurning(ItemSprite sprite) {
        if (sprite == null) throw new IllegalArgumentException("Item sprite required");
        sprite.glow(null);
        sprite.texture(Assets.Interfaces.BUFFS_LARGE);
        sprite.frame(new TextureFilm(sprite.texture, 16, 16).get(BuffIndicator.FIRE));
    }

    @Override
    protected boolean grant(Hero hero) {
        if (hero.buff(BlastRadius.class) != null) return false;
        return BlastRadius.acquire(hero);
    }

    @Override
    public String name() {
        return BlastMessages.get("radius_pickup.name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("radius_pickup.desc");
    }
}
