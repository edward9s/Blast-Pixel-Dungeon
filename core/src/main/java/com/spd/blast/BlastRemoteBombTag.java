package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIcon;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.watabou.input.GameAction;
import com.watabou.noosa.BitmapText;

/** Remote detonation Tag, immediately below the Bomb Maniac Tag. */
public final class BlastRemoteBombTag extends Tag {

    private static final int TAG_COLOR = 0x7B8073;
    private final ItemSprite bombIcon;
    private final BuffIcon heart;
    private final BitmapText pending;
    private final BitmapText fuseTime;
    private int lastCount = -1;
    private String lastFuse = "";

    public BlastRemoteBombTag() {
        super(TAG_COLOR);
        setColor(TAG_COLOR);

        bombIcon = new ItemSprite(ItemSpriteSheet.BOMB);
        add(bombIcon);

        heart = new BuffIcon(BuffIndicator.HEART, false);
        heart.hardlight(0xFFD54A);
        add(heart);

        pending = new BitmapText(PixelScene.pixelFont);
        pending.hardlight(0xFFFF66);
        add(pending);

        fuseTime = new BitmapText(PixelScene.pixelFont);
        fuseTime.hardlight(0xFFD54A);
        add(fuseTime);
        setSize(SIZE, SIZE);
    }

    @Override
    public GameAction keyAction() {
        return SPDAction.TAG_BLAST_REMOTE;
    }

    @Override
    protected void layout() {
        super.layout();
        bombIcon.x = x + (width - bombIcon.width()) / 2f;
        bombIcon.y = y + (height - bombIcon.height()) / 2f;
        PixelScene.align(bombIcon);
        heart.x = x + width - heart.width() - 3f;
        heart.y = y + height - heart.height() - 3f;
        PixelScene.align(heart);
        fuseTime.x = x + width - fuseTime.width() - 3f;
        fuseTime.y = y + height - fuseTime.height() - 3f;
        PixelScene.align(fuseTime);
        // Keep the counter at the SMM Store button offset regardless of icon size.
        pending.x = x + (flipped ? 2f : 4f);
        pending.y = y + 3f;
        PixelScene.align(pending);
    }

    @Override
    public void update() {
        boolean timed = BlastRemoteBomb.isTimedMode();
        int count = timed ? BlastBombWarnings.litCount() : BlastBombWarnings.remoteCount();
        String countdown = timed ? BlastBombWarnings.nextTimedCountdown() : "";
        if (count != lastCount || !countdown.equals(lastFuse)) {
            lastCount = count;
            lastFuse = countdown;
            pending.text(Integer.toString(count));
            pending.measure();
            fuseTime.text(countdown);
            fuseTime.measure();
            layout();
        }
        heart.visible = !timed;
        fuseTime.visible = timed;
        boolean ready = Dungeon.hero != null && Dungeon.hero.ready;
        float alpha = ready ? 1f : 0.4f;
        bombIcon.alpha(alpha);
        heart.alpha(alpha);
        fuseTime.alpha(alpha);
        pending.alpha(alpha);
        super.update();
    }

    @Override
    protected void onClick() {
        if (Dungeon.hero == null || !Dungeon.hero.ready
                || Dungeon.hero.buff(BlastRemoteBomb.class) == null) return;
        super.onClick();
        if (!BlastRemoteBomb.isTimedMode()) BlastRemoteBomb.detonateNext();
    }

    @Override
    protected boolean onLongClick() {
        if (Dungeon.hero == null || !Dungeon.hero.ready
                || Dungeon.hero.buff(BlastRemoteBomb.class) == null) return false;
        BlastRemoteBomb.toggleMode();
        return true;
    }

    @Override
    protected String hoverText() {
        return BlastMessages.get(BlastRemoteBomb.isTimedMode()
                ? "remote_bomb.timed_mode" : "remote_bomb.detonate");
    }
}
