package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.CheckBox;
import com.shatteredpixel.shatteredpixeldungeon.ui.OptionSlider;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.DeviceCompat;

/** An independent settings tab, wired only to persistent settings and save transfer. */
public final class BlastSettingsTab extends Component {
    private static final float GAP = 1f;
    private static final int SLIDER_HEIGHT = 21;
    private static final int BUTTON_HEIGHT = 16;
    private static final int RADIUS_BUTTON_WIDTH = 18;
    private static final int RADIUS_VALUE_WIDTH = 24;
    private static final int MAX_TEST_RADIUS_STACKS = 20;

    private RenderedTextBlock title;
    private ColorBlock topLine;
    private RenderedTextBlock spawnLabel;
    private RenderedTextBlock spawnValue;
    private OptionSlider spawnSlider;
    private CheckBox debugLog;

    private CheckBox testInfiniteBomb;
    private CheckBox indestructible;
    private CheckBox freeMovement;
    private CheckBox remoteThrow;
    private CheckBox demolition;
    private RenderedTextBlock radiusLabel;
    private RenderedTextBlock radiusValue;
    private RedButton radiusDecrease;
    private RedButton radiusIncrease;

    private ColorBlock transferLine;
    private RedButton exportSave;
    private RedButton importSave;

    @Override
    protected void createChildren() {
        title = PixelScene.renderTextBlock("Blast", 9);
        title.hardlight(Window.TITLE_COLOR);
        add(title);

        topLine = new ColorBlock(1, 1, 0xFF000000);
        add(topLine);

        spawnLabel = PixelScene.renderTextBlock(BlastMessages.get("settings.enemy_spawn"), 7);
        add(spawnLabel);

        spawnValue = PixelScene.renderTextBlock(multiplierText(), 7);
        add(spawnValue);

        spawnSlider = new OptionSlider("","1x","10x",
                BlastSettings.MIN_SPAWN_MULTIPLIER,
                BlastSettings.MAX_SPAWN_MULTIPLIER) {
            @Override
            protected void onChange() {
                BlastSettings.setSpawnMultiplier(getSelectedValue());
                spawnValue.text(multiplierText());
                layoutSpawnValue();
            }
        };
        spawnSlider.setSelectedValue(BlastSettings.spawnMultiplier());
        add(spawnSlider);

        debugLog = new CheckBox(BlastMessages.get("settings.debug_log")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setDebugLog(checked());
                if (checked() && BlastSecret.canToggleTests()) {
                    BlastSettings.toggleTests();
                    refreshTestVisibility();
                }
            }
        };
        debugLog.checked(BlastSettings.debugLog());
        add(debugLog);


        testInfiniteBomb = new CheckBox(BlastMessages.get("settings.infinite_bomb")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setTestInfiniteBomb(checked());
            }
        };
        testInfiniteBomb.checked(BlastSettings.testInfiniteBomb());
        add(testInfiniteBomb);

        indestructible = new CheckBox(BlastMessages.get("settings.indestructible")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setTestIndestructible(checked());
                BlastTestBuffs.syncHero();
            }
        };
        indestructible.checked(BlastSettings.testIndestructible());
        add(indestructible);

        freeMovement = new CheckBox(BlastMessages.get("settings.free_movement")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setTestFreeMovement(checked());
                BlastTestBuffs.syncHero();
            }
        };
        freeMovement.checked(BlastSettings.testFreeMovement());
        add(freeMovement);

        remoteThrow = new CheckBox(BlastMessages.get("settings.remote_throw")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setTestRemoteThrow(checked());
                BlastTestBuffs.syncHero();
            }
        };
        remoteThrow.checked(BlastSettings.testRemoteThrow());
        add(remoteThrow);

        demolition = new CheckBox(BlastMessages.get("settings.demolition")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setTestDemolition(checked());
                if (Dungeon.level != null) BlastBombWarnings.refresh(Dungeon.level);
            }
        };
        demolition.checked(BlastSettings.testDemolition());
        add(demolition);

        radiusLabel = PixelScene.renderTextBlock(BlastMessages.get("settings.radius"), 7);
        add(radiusLabel);
        radiusValue = PixelScene.renderTextBlock("", 7);
        add(radiusValue);

        radiusDecrease = new RedButton("-", 8) {
            @Override
            protected void onClick() {
                super.onClick();
                changeRadius(-1);
            }
        };
        add(radiusDecrease);

        radiusIncrease = new RedButton("+", 8) {
            @Override
            protected void onClick() {
                super.onClick();
                changeRadius(1);
            }
        };
        add(radiusIncrease);

        if (DeviceCompat.isAndroid() || DeviceCompat.isDesktop()) {
            transferLine = new ColorBlock(1, 1, 0xFF000000);
            add(transferLine);

            exportSave = new RedButton(BlastMessages.get("save_transfer.export"), 8) {
                @Override
                protected void onClick() {
                    super.onClick();
                    BlastSaveTransfer.exportSave();
                }
            };
            add(exportSave);

            importSave = new RedButton(BlastMessages.get("save_transfer.import"), 8) {
                @Override
                protected void onClick() {
                    super.onClick();
                    BlastSaveTransfer.importSave();
                }
            };
            add(importSave);
        }
        refreshTestVisibility();
    }

    private void refreshTestVisibility() {
        boolean show = BlastSettings.showTests();
        testInfiniteBomb.visible = testInfiniteBomb.active = show;
        indestructible.visible = indestructible.active = show;
        freeMovement.visible = freeMovement.active = show;
        remoteThrow.visible = remoteThrow.active = show;
        demolition.visible = demolition.active = show;
        boolean radiusVisible = show && Dungeon.hero != null && Dungeon.hero.isAlive();
        radiusLabel.visible = radiusValue.visible = radiusVisible;
        radiusDecrease.visible = radiusIncrease.visible = radiusVisible;
        refreshRadius();
    }

    private void changeRadius(int delta) {
        Hero hero = Dungeon.hero;
        if (hero == null || !hero.isAlive() || !BlastSettings.showTests()) return;
        int current = BlastRadius.stacks(hero);
        if ((delta < 0 && current == 0) || (delta > 0 && current >= MAX_TEST_RADIUS_STACKS)) return;
        BlastRadius.setStacks(hero, Math.addExact(current, delta));
        refreshRadius();
    }

    private void refreshRadius() {
        Hero hero = Dungeon.hero;
        int count = hero == null ? 0 : BlastRadius.stacks(hero);
        radiusValue.text(Integer.toString(count));
        boolean active = BlastSettings.showTests() && hero != null && hero.isAlive();
        radiusDecrease.active = active && count > 0;
        radiusIncrease.active = active && count < MAX_TEST_RADIUS_STACKS;
        layoutRadiusValue();
    }

    private void layoutRadiusValue() {
        float left = Math.max(0, width - 2 * RADIUS_BUTTON_WIDTH - RADIUS_VALUE_WIDTH - 2 * GAP);
        radiusValue.setPos(left + RADIUS_BUTTON_WIDTH + GAP
                + (RADIUS_VALUE_WIDTH - radiusValue.width()) / 2f,
                radiusDecrease.top() + (BUTTON_HEIGHT - radiusValue.height()) / 2f);
    }

    @Override
    protected void layout() {
        title.setPos((width - title.width()) / 2f, y + GAP);
        topLine.size(width, 1);
        topLine.y = title.bottom() + 3 * GAP;

        spawnLabel.maxWidth(Math.max(1, (int)Math.floor(width - spawnValue.width() - 2)));
        spawnLabel.setPos(0, topLine.y + 1 + GAP);
        layoutSpawnValue();

        spawnSlider.setRect(0, Math.max(spawnLabel.bottom(), spawnValue.bottom()) + GAP,
                width, SLIDER_HEIGHT);
        debugLog.setRect(0, spawnSlider.bottom() + GAP, width, BUTTON_HEIGHT);

        testInfiniteBomb.setRect(0, debugLog.bottom() + GAP, width, BUTTON_HEIGHT);
        indestructible.setRect(0, testInfiniteBomb.bottom() + GAP, width, BUTTON_HEIGHT);
        freeMovement.setRect(0, indestructible.bottom() + GAP, width, BUTTON_HEIGHT);

        remoteThrow.setRect(0, freeMovement.bottom() + GAP, width, BUTTON_HEIGHT);

        demolition.setRect(0, remoteThrow.bottom() + GAP, width, BUTTON_HEIGHT);

        float radiusTop = demolition.bottom() + GAP;
        float radiusLeft = Math.max(0, width - 2 * RADIUS_BUTTON_WIDTH - RADIUS_VALUE_WIDTH - 2 * GAP);
        radiusLabel.maxWidth(Math.max(1, (int)Math.floor(radiusLeft - GAP)));
        float rowHeight = Math.max(BUTTON_HEIGHT, radiusLabel.height());
        radiusLabel.setPos(0, radiusTop + (rowHeight - radiusLabel.height()) / 2f);
        float buttonTop = radiusTop + (rowHeight - BUTTON_HEIGHT) / 2f;
        radiusDecrease.setRect(radiusLeft, buttonTop, RADIUS_BUTTON_WIDTH, BUTTON_HEIGHT);
        radiusIncrease.setRect(width - RADIUS_BUTTON_WIDTH, buttonTop, RADIUS_BUTTON_WIDTH, BUTTON_HEIGHT);
        layoutRadiusValue();

        float bottom = radiusTop + rowHeight;
        if (transferLine != null) {
            transferLine.size(width, 1);
            transferLine.y = bottom + GAP;
            float buttonWidth = (width - GAP) / 2f;
            exportSave.setRect(0, transferLine.y + 1 + GAP, buttonWidth, BUTTON_HEIGHT);
            importSave.setRect(exportSave.right() + GAP, exportSave.top(),
                    buttonWidth, BUTTON_HEIGHT);
            bottom = importSave.bottom();
        }
        height = bottom;
    }

    private void layoutSpawnValue() {
        if (spawnValue != null) {
            spawnValue.setPos(Math.max(0, width - spawnValue.width()),
                    spawnLabel == null ? y : spawnLabel.top());
        }
    }

    private String multiplierText() {
        return BlastSettings.spawnMultiplier() + "x";
    }
}
