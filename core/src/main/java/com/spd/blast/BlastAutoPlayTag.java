package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.watabou.input.GameAction;
import com.watabou.noosa.ColorBlock;

/** Toggle autonomous turns without changing the auto-play setting. */
public final class BlastAutoPlayTag extends Tag {

    private final ColorBlock[] play = new ColorBlock[5];
    private final ColorBlock[] pause = new ColorBlock[2];

    public BlastAutoPlayTag() {
        super(0x7B8073);
        for (int i = 0; i < play.length; i++) {
            play[i] = new ColorBlock(2, 11 - i * 2, 0xFFFFFFFF);
            add(play[i]);
        }
        for (int i = 0; i < pause.length; i++) {
            pause[i] = new ColorBlock(3, 11, 0xFFFFFFFF);
            add(pause[i]);
        }
        setSize(SIZE, SIZE);
    }

    @Override
    public GameAction keyAction() {
        return SPDAction.TAG_BLAST_AUTO_PLAY;
    }

    @Override
    protected void layout() {
        super.layout();
        float left = x + (width - 10f) / 2f;
        for (int i = 0; i < play.length; i++) {
            play[i].x = left + i * 2f;
            play[i].y = y + (height - play[i].height()) / 2f;
            PixelScene.align(play[i]);
        }
        pause[0].x = x + (width - 9f) / 2f;
        pause[1].x = pause[0].x + 6f;
        for (ColorBlock bar : pause) {
            bar.y = y + (height - bar.height()) / 2f;
            PixelScene.align(bar);
        }
    }

    @Override
    public void update() {
        boolean paused = BlastAutoPlay.isPaused();
        for (ColorBlock bar : play) bar.visible = paused;
        for (ColorBlock bar : pause) bar.visible = !paused;
        super.update();
    }

    @Override
    protected void onClick() {
        if (!BlastSettings.testAutoPlay() || Dungeon.hero == null) return;
        super.onClick();
        BlastAutoPlay.togglePause();
    }

    @Override
    protected String hoverText() {
        return BlastMessages.get("settings.auto_play");
    }
}
