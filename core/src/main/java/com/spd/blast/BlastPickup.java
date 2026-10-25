package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.audio.Sample;

/** Instantly consumed floor pickup, never inserted into the backpack. */
public abstract class BlastPickup extends Item {
    private static final int DUPLICATE_GOLD = 10;

    {
        dropsDownHeap = true;
    }

    /** Returns true when the pickup grants its buff, false when already owned. */
    protected abstract boolean grant(Hero hero);

    @Override
    public final boolean doPickUp(Hero hero, int pos) {
        boolean granted = grant(hero);
        if (!granted) {
            Dungeon.gold += DUPLICATE_GOLD;
            Statistics.goldCollected += DUPLICATE_GOLD;
            Badges.validateGoldCollected();
        }
        Catalog.setSeen(getClass());
        Statistics.itemTypesDiscovered.add(getClass());
        Catalog.countUse(getClass());
        GameScene.pickUp(this, pos);
        if (granted) {
            Sample.INSTANCE.play(Assets.Sounds.DEWDROP);
        } else {
            hero.sprite.showStatusWithIcon(CharSprite.NEUTRAL,
                    Integer.toString(DUPLICATE_GOLD), FloatingText.GOLD);
            Sample.INSTANCE.play(Assets.Sounds.GOLD);
        }
        hero.spendAndNext(0f);
        return true;
    }

    @Override
    public final boolean isUpgradable() {
        return false;
    }

    @Override
    public final boolean isIdentified() {
        return true;
    }
}
