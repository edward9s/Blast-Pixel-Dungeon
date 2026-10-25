package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.ui.Icons;
import com.shatteredpixel.shatteredpixeldungeon.ui.Tag;
import com.watabou.input.GameAction;
import com.watabou.noosa.BitmapText;
import com.watabou.noosa.Image;

/** Independent tag immediately above the remote detonation tag. */
public final class BlastBombManiacTag extends Tag {
    private final ItemSprite icon;
    private final Image crosshair;
    private final BitmapText remaining;
    private int lastSelected = -1;
    private String lastCount = "";

    public BlastBombManiacTag() {
        super(0x7B8073);
        setColor(0x7B8073);
        icon = new ItemSprite(ItemSpriteSheet.BOMB);
        add(icon);
        crosshair = Icons.TARGET.get();
        remaining = new BitmapText(PixelScene.pixelFont);
        remaining.hardlight(0xFFFFFF);
        add(remaining);
        setSize(SIZE, SIZE);
    }

    @Override
    public GameAction keyAction() {
        return SPDAction.TAG_BLAST_MANIAC;
    }

    @Override
    protected void layout() {
        super.layout();
        icon.x = x + (width - icon.width()) / 2f;
        icon.y = y + (height - icon.height()) / 2f;
        PixelScene.align(icon);

        // Match the SMM Store count position on a 24-pixel Tag button.
        // Anchor to the button, not the currently selected bomb's sprite size.
        remaining.x = x + (flipped ? 2f : 4f);
        remaining.y = y + 3f;
        PixelScene.align(remaining);
    }

    @Override
    public void update() {
        BlastBombManiac buff = BlastBombManiac.current();
        if (buff != null) {
            if (lastSelected != buff.selectedIndex()) {
                icon.view(buff.selectedBomb());
                lastSelected = buff.selectedIndex();
                layout();
            }
            String count = buff.unlimited() ? "∞" : Integer.toString(buff.remaining());
            if (!count.equals(lastCount)) {
                lastCount = count;
                remaining.text(count);
                remaining.measure();
                layout();
            }
        }
        boolean ready = Dungeon.hero != null && Dungeon.hero.ready;
        icon.alpha(ready ? 1f : 0.4f);
        remaining.alpha(ready ? 1f : 0.4f);
        refreshCrosshair();
        super.update();
    }

    @Override
    protected void onClick() {
        BlastBombManiac buff = BlastBombManiac.current();
        if (buff == null || Dungeon.hero == null || !Dungeon.hero.ready) return;
        super.onClick();
        if (BlastBombManiac.aimedThrowEnabled()) buff.aim();
        else buff.place();
        refreshCrosshair();
    }

    private void refreshCrosshair() {
        BlastBombManiac buff = BlastBombManiac.current();
        Mob target = buff == null ? null : buff.aimedTarget();
        if (target == null || !target.isAlive() || target.sprite == null
                || target.sprite.parent == null) {
            if (crosshair.parent != null) crosshair.remove();
            return;
        }
        if (crosshair.parent != target.sprite.parent) {
            if (crosshair.parent != null) crosshair.remove();
            target.sprite.parent.addToFront(crosshair);
        }
        crosshair.point(target.sprite.center(crosshair));
    }

    @Override
    public void destroy() {
        if (crosshair.parent != null) crosshair.remove();
        super.destroy();
    }

    @Override
    protected boolean onLongClick() {
        if (BlastBombManiac.current() == null || Dungeon.hero == null || !Dungeon.hero.ready) {
            return false;
        }
        GameScene.centerNextWndOnInvPane();
        GameScene.show(new WndBlastBombs(BlastBombManiac.current()));
        return true;
    }

    @Override
    protected String hoverText() {
        return BlastMessages.get(BlastRemoteBomb.isTimedMode()
                ? "bomb_maniac.timed_throw"
                : BlastSettings.testRemoteThrow()
                ? "bomb_maniac.remote_throw" : "bomb_maniac.throw");
    }
}
