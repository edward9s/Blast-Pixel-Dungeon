package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.watabou.noosa.Image;

/** Persistent visual indicators for globally enabled experimental abilities. */
public final class BlastTestBuffs {
    private BlastTestBuffs() {}

    public static void syncHero() {
        Hero hero = Dungeon.hero;
        if (hero == null || !hero.isAlive()) return;
        sync(hero, Indestructible.class, BlastSettings.testIndestructible());
        sync(hero, FreeMovement.class, BlastSettings.testFreeMovement());
        sync(hero, RemoteThrow.class, BlastSettings.testRemoteThrow());
    }

    private static void sync(Hero hero, Class<? extends TestBuff> buffClass, boolean enabled) {
        if (enabled) {
            Buff.affect(hero, buffClass);
        } else if (hero.buff(buffClass) != null) {
            Buff.detach(hero, buffClass);
        }
    }

    public abstract static class TestBuff extends Buff {
        {
            type = buffType.POSITIVE;
            revivePersists = true;
        }

        @Override
        public boolean act() {
            spend(TICK);
            return true;
        }

        @Override
        public String iconTextDisplay() {
            return "∞";
        }
    }

    public static final class Indestructible extends TestBuff {
        @Override
        public int icon() {
            return BuffIndicator.ARMOR;
        }

        @Override
        public void tintIcon(Image icon) {
            icon.hardlight(0xFFD700);
        }

        @Override
        public String name() {
            return BlastMessages.get("settings.indestructible");
        }

        @Override
        public String desc() {
            return BlastMessages.get("settings.indestructible_test_desc");
        }
    }

    public static final class FreeMovement extends TestBuff {
        @Override
        public int icon() {
            return BuffIndicator.LEVITATION;
        }

        @Override
        public void tintIcon(Image icon) {
            icon.hardlight(BlastPhasePotion.ICON_TINT);
        }

        @Override
        public String name() {
            return BlastMessages.get("settings.free_movement");
        }

        @Override
        public String desc() {
            return BlastMessages.get("settings.free_movement_test_desc");
        }
    }

    public static final class RemoteThrow extends TestBuff {
        @Override
        public int icon() {
            return BuffIndicator.PREPARATION;
        }

        @Override
        public void tintIcon(Image icon) {
            icon.hardlight(BlastPhasePotion.ICON_TINT);
        }

        @Override
        public String name() {
            return BlastMessages.get("settings.remote_throw");
        }

        @Override
        public String desc() {
            return BlastMessages.get("settings.remote_throw_test_desc");
        }
    }
}
