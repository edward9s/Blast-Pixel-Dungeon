package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
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
    private static final int VALUE_WIDTH = 24;
    private static final int[] SPAWN_MULTIPLIER_VALUES = {1, 2, 3, 4, 5, 10, 20, 40, 70, 100};
    private static final int[] RADIUS_BOOST_VALUES = {0, 1, 2, 3, 5, 10, 20, 40, 70, 100};

    private RenderedTextBlock title;
    private ColorBlock topLine;
    private RenderedTextBlock spawnLabel;
    private RenderedTextBlock spawnValue;
    private OptionSlider spawnSlider;
    private CheckBox debugLog;
    private CheckBox weakMobDrops;
    private CheckBox weakMobExpEffects;

    private CheckBox testInfiniteBomb;
    private CheckBox indestructible;
    private CheckBox freeMovement;
    private CheckBox remoteThrow;
    private CheckBox demolition;
    private CheckBox autoPlay;
    private CheckBox autoFarming;
    private CheckBox enchantmentReverseAlchemy;
    private CheckBox[] testOptions;
    private Runnable heightChanged;

    public void setHeightChanged(Runnable callback) {
        heightChanged = callback;
    }
    private RenderedTextBlock radiusLabel;
    private RenderedTextBlock radiusValue;
    private OptionSlider radiusSlider;

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

        spawnSlider = new OptionSlider("", "1x", "100x", 0, SPAWN_MULTIPLIER_VALUES.length - 1) {
            @Override
            protected void onChange() {
                BlastSettings.setSpawnMultiplier(SPAWN_MULTIPLIER_VALUES[getSelectedValue()]);
                refreshSpawn();
            }

            @Override
            protected void onDragPreview(int value) {
                spawnValue.text(SPAWN_MULTIPLIER_VALUES[value] + "x");
                layoutSettingValue(spawnLabel, spawnValue);
            }
        };
        spawnSlider.setSelectedValue(spawnMultiplierIndex(BlastSettings.spawnMultiplier()));
        add(spawnSlider);

        refreshSpawn();

        debugLog = new CheckBox(BlastMessages.get("settings.debug_log")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setDebugLog(checked());
                if (checked() && BlastSecret.canToggleTests()) {
                    if (BlastSettings.showTests()) disableTestOptions();
                    BlastSettings.toggleTests();
                    refreshTestVisibility();
                    layout();
                    if (heightChanged != null) heightChanged.run();
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
                if (BlastSecret.canToggleTests()) {
                    BlastBombManiac buff = BlastBombManiac.current();
                    if (buff == null) throw new IllegalStateException("Missing Bomb Maniac buff");
                    buff.setSecretBombTypes(checked());
                    Item.updateQuickslot();
                }
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

        autoPlay = new CheckBox(BlastMessages.get("settings.auto_play")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setTestAutoPlay(checked());
                BlastAutoPlay.onSettingChanged();
            }
        };
        autoPlay.checked(BlastSettings.testAutoPlay());
        add(autoPlay);

        autoFarming = new CheckBox(BlastMessages.get("settings.auto_farming")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setAutoFarming(checked());
                BlastAutoPlay.onSettingChanged();
            }
        };
        autoFarming.checked(BlastSettings.autoFarming());
        add(autoFarming);

        enchantmentReverseAlchemy = new CheckBox(BlastMessages.get("settings.enchantment_reverse")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setEnchantmentStoneReverseAlchemy(checked());
            }
        };
        enchantmentReverseAlchemy.checked(BlastSettings.enchantmentStoneReverseAlchemy());
        add(enchantmentReverseAlchemy);

        weakMobDrops = new CheckBox(BlastMessages.get("settings.weak_mob_drops")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setWeakMobDrops(checked());
            }
        };
        weakMobDrops.checked(BlastSettings.weakMobDrops());
        add(weakMobDrops);

        weakMobExpEffects = new CheckBox(BlastMessages.get("settings.weak_mob_exp_effects")) {
            @Override
            protected void onClick() {
                super.onClick();
                BlastSettings.setWeakMobExpEffects(checked());
            }
        };
        weakMobExpEffects.checked(BlastSettings.weakMobExpEffects());
        add(weakMobExpEffects);

        testOptions = new CheckBox[]{
                testInfiniteBomb, indestructible, freeMovement, remoteThrow,
                demolition, autoPlay, autoFarming, enchantmentReverseAlchemy,
                weakMobDrops, weakMobExpEffects
        };
        for (CheckBox option : testOptions) {
            option.multiline = true;
        }

        radiusLabel = PixelScene.renderTextBlock(BlastMessages.get("settings.radius"), 7);
        add(radiusLabel);
        radiusValue = PixelScene.renderTextBlock("", 7);
        add(radiusValue);

        radiusSlider = new OptionSlider("", "0", "100", 0, RADIUS_BOOST_VALUES.length - 1) {
            @Override
            protected void onChange() {
                Hero hero = Dungeon.hero;
                if (hero == null || !hero.isAlive() || !BlastSettings.showTests()) return;
                BlastBombManiac buff = BlastBombManiac.current();
                if (buff == null) throw new IllegalStateException("Missing Bomb Maniac buff");
                buff.setTestRadius(RADIUS_BOOST_VALUES[getSelectedValue()]);
                refreshRadius();
            }

            @Override
            protected void onDragPreview(int value) {
                radiusValue.text("+" + RADIUS_BOOST_VALUES[value]);
                layoutSettingValue(radiusLabel, radiusValue);
            }
        };
        add(radiusSlider);

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

    /** Clear enabled test features before hiding their controls. */
    private void disableTestOptions() {
        BlastBombManiac buff = BlastBombManiac.current();
        if (buff != null) {
            buff.setSecretBombTypes(false);
            buff.restoreTestRadius();
        }
        refreshRadius();

        BlastSettings.setTestInfiniteBomb(false);
        BlastSettings.setTestIndestructible(false);
        BlastSettings.setTestFreeMovement(false);
        BlastSettings.setTestRemoteThrow(false);
        BlastSettings.setTestDemolition(false);
        BlastSettings.setTestAutoPlay(false);
        BlastSettings.setAutoFarming(false);
        BlastSettings.setEnchantmentStoneReverseAlchemy(false);
        BlastSettings.setWeakMobDrops(false);
        BlastSettings.setWeakMobExpEffects(false);

        for (CheckBox option : testOptions) option.checked(false);

        BlastTestBuffs.syncHero();
        BlastAutoPlay.onSettingChanged();
        BlastRemoteBomb.restoreRemoteModeIfNecessary();
        if (Dungeon.level != null) BlastBombWarnings.refresh(Dungeon.level);
        Item.updateQuickslot();
    }

    private void refreshTestVisibility() {
        boolean show = BlastSettings.showTests();
        for (CheckBox option : testOptions) {
            option.visible = option.active = show;
        }
        boolean radiusVisible = show && Dungeon.hero != null && Dungeon.hero.isAlive();
        radiusLabel.visible = radiusValue.visible = radiusVisible;
        radiusSlider.visible = radiusVisible;
        refreshRadius();
    }

    private void refreshSpawn() {
        int multiplier = BlastSettings.spawnMultiplier();
        spawnValue.text(multiplier + "x");
        spawnSlider.setSelectedValue(spawnMultiplierIndex(multiplier));
        layoutSettingValue(spawnLabel, spawnValue);
    }

    private static int spawnMultiplierIndex(int current) {
        int closest = 0;
        for (int i = 1; i < SPAWN_MULTIPLIER_VALUES.length; i++) {
            if (Math.abs(SPAWN_MULTIPLIER_VALUES[i] - current)
                    < Math.abs(SPAWN_MULTIPLIER_VALUES[closest] - current)) {
                closest = i;
            }
        }
        // Opening settings does not rewrite a saved multiplier between slider steps.
        return closest;
    }

    private void refreshRadius() {
        Hero hero = Dungeon.hero;
        int count = hero == null ? 0 : BlastRadius.stacks(hero);
        radiusValue.text("+" + count);
        radiusSlider.setSelectedValue(radiusBoostIndex(count));
        boolean active = BlastSettings.showTests() && hero != null && hero.isAlive();
        radiusSlider.enable(active);
        layoutSettingValue(radiusLabel, radiusValue);
    }

    private static int radiusBoostIndex(int current) {
        if (current < 0) throw new IllegalArgumentException("Negative explosion boost");
        int closest = 0;
        for (int i = 1; i < RADIUS_BOOST_VALUES.length; i++) {
            if (Math.abs((long) RADIUS_BOOST_VALUES[i] - current)
                    < Math.abs((long) RADIUS_BOOST_VALUES[closest] - current)) {
                closest = i;
            }
        }
        // Keep the actual boost unchanged until the player selects a slider value.
        return closest;
    }

    private void layoutSettingValue(RenderedTextBlock label, RenderedTextBlock value) {
        value.setPos(Math.max(0, width - value.width()), label.top());
    }

    @Override
    protected void layout() {
        title.setPos((width - title.width()) / 2f, y + GAP);
        topLine.size(width, 1);
        topLine.y = title.bottom() + 3 * GAP;

        spawnLabel.maxWidth(Math.max(1, (int)Math.floor(width - VALUE_WIDTH - GAP)));
        spawnLabel.setPos(0, topLine.y + 1 + GAP);
        layoutSettingValue(spawnLabel, spawnValue);

        float spawnTop = Math.max(spawnLabel.bottom(), spawnValue.bottom()) + GAP;
        spawnSlider.setRect(0, spawnTop, width, SLIDER_HEIGHT);
        debugLog.setRect(0, spawnSlider.bottom() + GAP, width, BUTTON_HEIGHT);

        float columnWidth = (width - GAP) / 2f;
        float bottom = debugLog.bottom();
        if (BlastSettings.showTests()) {
            for (int i = 0; i < testOptions.length; i += 2) {
                CheckBox left = testOptions[i];
                CheckBox right = i + 1 < testOptions.length ? testOptions[i + 1] : null;
                float top = bottom + GAP;

                left.setRect(0, top, columnWidth, BUTTON_HEIGHT);
                float rowHeight = Math.max(BUTTON_HEIGHT, left.reqHeight());
                if (right != null) {
                    right.setRect(columnWidth + GAP, top, columnWidth, BUTTON_HEIGHT);
                    rowHeight = Math.max(rowHeight, right.reqHeight());
                }

                left.setRect(0, top, columnWidth, rowHeight);
                if (right != null) {
                    right.setRect(columnWidth + GAP, top, columnWidth, rowHeight);
                }
                bottom = top + rowHeight;
            }
        }

        if (radiusLabel.visible) {
            float radiusTop = bottom + GAP;
            radiusLabel.maxWidth(Math.max(1, (int)Math.floor(width - VALUE_WIDTH - GAP)));
            radiusLabel.setPos(0, radiusTop);
            layoutSettingValue(radiusLabel, radiusValue);

            float controlsTop = Math.max(radiusLabel.bottom(), radiusValue.bottom()) + GAP;
            radiusSlider.setRect(0, controlsTop, width, SLIDER_HEIGHT);
            bottom = radiusSlider.bottom();
        }

        if (transferLine != null) {
            transferLine.size(width, 1);
            transferLine.y = bottom + 5 * GAP;
            float buttonWidth = (width - GAP) / 2f;
            exportSave.setRect(0, transferLine.y + 1 + GAP, buttonWidth, BUTTON_HEIGHT);
            importSave.setRect(exportSave.right() + GAP, exportSave.top(),
                    buttonWidth, BUTTON_HEIGHT);
            bottom = importSave.bottom();
        }
        height = bottom;
    }

    private String multiplierText() {
        return BlastSettings.spawnMultiplier() + "x";
    }
}
