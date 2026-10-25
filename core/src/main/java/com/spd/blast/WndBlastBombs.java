package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.Button;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndInfoItem;
import com.watabou.input.PointerEvent;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.PointF;

/** Responsive bomb selector with a scrollable, persistent selection outline. */
public final class WndBlastBombs extends Window {
    private static final int MAX_WIDTH = 144;
    private static final int ROW_HEIGHT = 20;
    private static final int GAP = 2;
    private static final int MAX_LIST_HEIGHT = 110;

    private final ScrollPane list;
    private final float listTop;
    private final int listHeight;
    private final BombOption[] choices;
    private final int[] typeIndices;
    private float lastCameraX = Float.NaN;
    private float lastCameraY = Float.NaN;

    public WndBlastBombs(final BlastBombManiac buff) {
        super();

        // Account for the window chrome so portrait devices never clip the window.
        final int width = Math.min(MAX_WIDTH,
                (int) PixelScene.uiCamera.width - chrome.marginHor() - 4);
        if (width <= 0) {
            throw new IllegalStateException("Not enough room for bomb selection window");
        }

        RenderedTextBlock heading = PixelScene.renderTextBlock(
                BlastMessages.get("bomb_maniac.window_title"), 9);
        heading.hardlight(TITLE_COLOR);
        heading.maxWidth(width);
        add(heading);
        heading.setPos((width - heading.width()) / 2f, 2);

        RenderedTextBlock capacity = PixelScene.renderTextBlock(
                BlastMessages.get("bomb_maniac.capacity",
                        buff.unlimited() ? "∞" : buff.capacity(),
                        buff.unlimited() ? "∞" : buff.remaining()), 7);
        capacity.maxWidth(width);
        add(capacity);
        capacity.setPos(0, heading.bottom() + 4);

        RedButton imbue = new RedButton(BlastMessages.get("bomb_maniac.imbue"), 8) {
            @Override
            protected void onClick() {
                super.onClick();
                hide();
                selectBombToImbue(buff);
            }
        };
        imbue.icon(new ItemSprite(new Bomb()));
        add(imbue);
        imbue.setRect(0, capacity.bottom() + 4, width, 18);

        // Keep two columns on portrait and landscape, including narrow displays.
        final int columns = 2;
        final int rows = (buff.typeCount() + columns - 1) / columns;
        final int contentHeight = rows * (ROW_HEIGHT + GAP);
        final float columnWidth = (width - (columns - 1) * GAP - 2f) / columns;
        final Component content = new Component() {};
        content.setSize(width, contentHeight);

        // Match the journal's bomb order without changing saved selection indices.
        Bomb[] bombs = new Bomb[buff.typeCount()];
        typeIndices = new int[bombs.length];
        boolean[] placed = new boolean[bombs.length];
        for (int i = 0; i < bombs.length; i++) {
            bombs[i] = buff.bombAt(i);
        }
        int count = 0;
        for (Class<?> journalType : Catalog.BOMBS.items()) {
            for (int i = 0; i < bombs.length; i++) {
                if (!placed[i] && bombs[i].getClass() == journalType) {
                    typeIndices[count++] = i;
                    placed[i] = true;
                }
            }
        }
        // Any future modded bomb types follow the journal entries in unlock order.
        for (int i = 0; i < bombs.length; i++) {
            if (!placed[i]) typeIndices[count++] = i;
        }

        choices = new BombOption[bombs.length];
        for (int i = 0; i < choices.length; i++) {
            final int index = typeIndices[i];
            final Bomb bomb = bombs[index];
            BombOption option = new BombOption(bomb.name()) {
                @Override
                protected void onClick() {
                    super.onClick();
                    choose(buff, index);
                }

                @Override
                protected boolean onLongClick() {
                    GameScene.show(new WndInfoItem(bomb));
                    return true;
                }
            };
            option.icon(new ItemSprite(bomb));
            option.leftJustify = true;
            content.add(option);
            option.setRect((i % columns) * (columnWidth + GAP),
                    (i / columns) * (ROW_HEIGHT + GAP), columnWidth, ROW_HEIGHT);
            choices[i] = option;
        }
        highlight(buff.selectedIndex());

        // Some devices dispatch taps to ScrollPane's controller instead of the
        // nested button. Handle either route with the same selection logic.
        list = new ScrollPane(content) {
            // The scroll controller can receive taps instead of the nested buttons.
            // Handle long presses on that input path as well.
            @Override
            protected void createChildren() {
                controller = new PointerController() {
                    private int heldIndex = -1;
                    private float heldTime;

                    @Override
                    protected void onPointerDown(PointerEvent event) {
                        super.onPointerDown(event);
                        PointF point = content.camera.screenToCamera(
                                (int) event.current.x, (int) event.current.y);
                        heldIndex = -1;
                        for (int i = 0; i < choices.length; i++) {
                            if (choices[i].inside(point.x, point.y)) {
                                heldIndex = i;
                                break;
                            }
                        }
                        heldTime = 0f;
                    }

                    @Override
                    protected void onDrag(PointerEvent event) {
                        super.onDrag(event);
                        if (PointF.distance(event.current, event.start)
                                > PixelScene.defaultZoom * 8f) {
                            heldIndex = -1;
                        }
                    }

                    @Override
                    protected void onPointerUp(PointerEvent event) {
                        super.onPointerUp(event);
                        heldIndex = -1;
                    }

                    @Override
                    public void update() {
                        super.update();
                        if (curEvent != null && heldIndex >= 0
                                && (heldTime += Game.elapsed) >= Button.longClick) {
                            int index = heldIndex;
                            heldIndex = -1;
                            reset(); // Do not select this bomb when the pointer is released.
                            GameScene.show(new WndInfoItem(bombs[typeIndices[index]]));
                        }
                    }
                };
                add(controller);

                thumb = new ColorBlock(1, 1, THUMB_COLOR);
                thumb.am = THUMB_ALPHA;
                add(thumb);
            }

            @Override
            public void onClick(float x, float y) {
                for (int i = 0; i < choices.length; i++) {
                    if (choices[i].inside(x, y)) {
                        choose(buff, typeIndices[i]);
                        return;
                    }
                }
            }
        };
        add(list);

        listTop = imbue.bottom() + 4;
        int available = (int) (PixelScene.uiCamera.height - chrome.marginVer() - listTop - 8);
        listHeight = Math.min(contentHeight,
                Math.min(MAX_LIST_HEIGHT, Math.max(ROW_HEIGHT, available)));
        resize(width, (int) (listTop + listHeight + 2));
        positionList();
    }

    private static void selectBombToImbue(final BlastBombManiac buff) {
        GameScene.selectItem(new WndBag.ItemSelector() {
            @Override
            public String textPrompt() {
                return BlastMessages.get("bomb_maniac.imbue_prompt");
            }

            @Override
            public boolean itemSelectable(Item item) {
                return item instanceof Bomb && ((Bomb) item).fuse == null;
            }

            @Override
            public void onSelect(Item item) {
                if (item == null || BlastBombManiac.current() != buff) return;
                buff.imbue((Bomb) item);
                selectBombToImbue(buff);
            }
        });
    }

    private void choose(BlastBombManiac buff, int index) {
        buff.select(index);
        highlight(index);
    }

    private void highlight(int index) {
        for (int i = 0; i < choices.length; i++) {
            choices[i].setSelected(typeIndices[i] == index);
        }
    }

    private void positionList() {
        list.setRect(0, listTop, width, listHeight);
    }

    @Override
    public void offset(int xOffset, int yOffset) {
        super.offset(xOffset, yOffset);
        positionList();
    }

    @Override
    public synchronized void update() {
        super.update();
        if (camera() != null && (lastCameraX != camera().x || lastCameraY != camera().y)) {
            lastCameraX = camera().x;
            lastCameraY = camera().y;
            positionList();
        }
    }

    private static class BombOption extends RedButton {
        private static final float OUTLINE_INSET = 2f;
        private static final float OUTLINE_THICKNESS = 1f;
        private static final int OUTLINE_COLOR = 0xFFFFD36A;

        private final ColorBlock[] outline = new ColorBlock[4];

        BombOption(String name) {
            super(name, 6);
            for (int i = 0; i < outline.length; i++) {
                outline[i] = new ColorBlock(1, 1, OUTLINE_COLOR);
                outline[i].visible = false;
                add(outline[i]);
            }
        }

        void setSelected(boolean value) {
            for (ColorBlock edge : outline) {
                edge.visible = value;
            }
        }

        @Override
        protected void layout() {
            super.layout();
            // StyledButton may lay out before this subclass finishes construction.
            if (outline == null || outline[0] == null) return;

            float left = x + OUTLINE_INSET;
            float top = y + OUTLINE_INSET;
            float right = x + width - OUTLINE_INSET;
            float bottom = y + height - OUTLINE_INSET;
            float horizontal = right - left;
            float vertical = bottom - top;

            outline[0].x = left;
            outline[0].y = top;
            outline[0].size(horizontal, OUTLINE_THICKNESS);

            outline[1].x = left;
            outline[1].y = bottom - OUTLINE_THICKNESS;
            outline[1].size(horizontal, OUTLINE_THICKNESS);

            outline[2].x = left;
            outline[2].y = top;
            outline[2].size(OUTLINE_THICKNESS, vertical);

            outline[3].x = right - OUTLINE_THICKNESS;
            outline[3].y = top;
            outline[3].size(OUTLINE_THICKNESS, vertical);
        }
    }
}
