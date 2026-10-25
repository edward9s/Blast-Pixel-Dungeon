package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;

/** Identified, readable scroll granting six turns of bomb immunity. */
public final class BlastBombproofScroll extends Scroll {
    {
        image = ItemSpriteSheet.SCROLL_HOLDER;
        icon = ItemSpriteSheet.Icons.RING_TENACITY;
    }

    @Override
    public void reset() {
        super.reset();
        image = ItemSpriteSheet.SCROLL_HOLDER;
    }

    @Override
    public boolean isKnown() {
        return true;
    }

    @Override
    public String name() {
        return BlastMessages.get("bombproof_scroll.name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("bombproof_scroll.desc");
    }

    @Override
    public void doRead() {
        detach(curUser.belongings.backpack);
        BlastIndestructible.grant(curUser);
        readAnimation();
    }
}
