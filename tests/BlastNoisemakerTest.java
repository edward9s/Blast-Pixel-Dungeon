import com.badlogic.gdx.Preferences;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.spd.blast.BlastBombVisuals;
import com.watabou.utils.GameSettings;
import com.watabou.utils.SparseArray;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;

import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Noisemaker;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.spd.blast.BlastRemoteBomb;
import com.watabou.noosa.Group;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exercises real remote throws, native beckoning, saves, freezing, and kick fuses. */
public final class BlastNoisemakerTest {
    private static final int SIZE = 11;
    private static int checks;
    private static class TestLevel extends Level {
        protected boolean build() { return true; }
        protected void createMobs() {}
        protected void createItems() {}
        @Override public void updateFieldOfView(Char ch, boolean[] fov) {
            Arrays.fill(fov, true);
        }
    }

    private static class SilentItemSprite extends ItemSprite {
        @Override public void texture(Object texture) {}
        @Override public ItemSprite view(int image, Glowing glowing) { return this; }
        @Override public ItemSprite view(Heap heap) { return this; }
        @Override public void link() {}
        @Override public void drop() {}
        @Override public void drop(int from) {}
        @Override public void place(int cell) {}
    }

    private static int cell(int x, int y) { return y * SIZE + x; }

    private static TestLevel setup() {
        return setup(new TestLevel());
    }

    private static <T extends Level> T setup(T level) {
        Actor.clear();
        new Game(GameScene.class, null);
        BlastBombVisuals.reset();
        Map<String, Object> values = new HashMap<>();
        Preferences prefs = (Preferences) Proxy.newProxyInstance(
                Preferences.class.getClassLoader(), new Class<?>[]{Preferences.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("put")) { values.put((String)args[0], args[1]); return proxy; }
                    if (name.startsWith("get") && args != null && args.length == 2) {
                        return values.getOrDefault(args[0], args[1]);
                    }
                    if (name.equals("contains")) return values.containsKey(args[0]);
                    if (name.equals("flush")) return null;
                    throw new AssertionError("Unexpected preference operation: " + name);
                });
        GameSettings.set(prefs);
        level.mobs = new HashSet<>();
        level.heaps = new SparseArray<>();
        level.blobs = new HashMap<>();
        level.plants = new SparseArray<>();
        level.traps = new SparseArray<>();
        level.customTiles = new ArrayList<>();
        level.customTerrain = new ArrayList<>();
        level.customWalls = new ArrayList<>();
        level.setSize(SIZE, SIZE);
        for (int y = 1; y < SIZE - 1; y++) {
            for (int x = 1; x < SIZE - 1; x++) level.map[cell(x, y)] = Terrain.EMPTY;
        }
        level.buildFlagMaps();
        Arrays.fill(level.heroFOV, true);
        level.blastWallStrength = new int[level.length()];
        level.blastRubble = new boolean[level.length()];
        Dungeon.level = level;
        Dungeon.depth = 1;
        Dungeon.hero = new Hero();
        Dungeon.hero.pos = cell(1, 1);
        Actor.add(Dungeon.hero);
        new BlastBombVisuals();
        return level;
    }


    private static final Method REMOTE_ACT;
    private static final Method NATIVE_ACT;
    private static final Field SCENE;
    static {
        try {
            REMOTE_ACT = BlastRemoteBomb.RemoteFuse.class.getDeclaredMethod("act");
            NATIVE_ACT = Noisemaker.NoisemakerFuse.class.getDeclaredMethod("act");
            SCENE = GameScene.class.getDeclaredField("scene");
            REMOTE_ACT.setAccessible(true);
            NATIVE_ACT.setAccessible(true);
            SCENE.setAccessible(true);
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    private static class Listener extends Mob {
        int alarms;
        int targetCell() { return target; }
        @Override public void notice() {}
        @Override public void beckon(int cell) {
            alarms++;
            super.beckon(cell);
        }
    }

    private static Listener listener(Level level) {
        Listener mob = new Listener();
        mob.pos = cell(8, 8);
        mob.state = mob.SLEEPING;
        level.mobs.add(mob);
        return mob;
    }

    private static Heap heap(Level level, int pos) {
        Heap heap = new Heap();
        heap.pos = pos;
        heap.sprite = new SilentItemSprite();
        level.heaps.put(pos, heap);
        return heap;
    }

    private static void tick(Bomb.Fuse fuse) throws Exception {
        // Only a particle-emitter group is needed; no graphics/audio backend.
        GameScene scene = new GameScene();
        Field emitters = GameScene.class.getDeclaredField("emitters");
        emitters.setAccessible(true);
        emitters.set(scene, new Group());
        SCENE.set(null, scene);
        try {
            (fuse instanceof BlastRemoteBomb.RemoteFuse ? REMOTE_ACT : NATIVE_ACT).invoke(fuse);
        } finally { SCENE.set(null, null); }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void remoteAlarm() throws Exception {
        TestLevel level = setup();
        Buff.affect(Dungeon.hero, BlastRemoteBomb.class);
        Listener mob = listener(level);
        Noisemaker bomb = new Noisemaker();
        Heap heap = heap(level, Dungeon.hero.pos);
        bomb.blastThrowLit(heap.pos);
        check(bomb.fuse instanceof BlastRemoteBomb.RemoteFuse, "real lit throw creates remote fuse");
        Bomb.Fuse fuse = bomb.fuse;
        tick(fuse); tick(fuse);
        check(mob.alarms == 0, "no alarm before two game-time units");
        tick(fuse);
        check(mob.alarms == 1 && mob.targetCell() == heap.pos && mob.state == mob.WANDERING,
                "native beckon wakes sleeping monster and targets bomb");
        check(heap.items.contains(bomb) && bomb.fuse == fuse, "alarm keeps bomb remotely armed under hero");
        for (int i = 0; i < 5; i++) tick(fuse);
        check(mob.alarms == 1, "six game-time units between alarms");
        Bundle save = new Bundle();
        save.put("bomb", bomb);
        Actor.clear();
        heap.items.clear();
        Noisemaker restored = (Noisemaker) save.get("bomb");
        heap.items.add(restored);
        check(restored.fuse instanceof BlastRemoteBomb.RemoteFuse, "restore retains remote fuse");
        check(((BlastRemoteBomb.RemoteFuse) restored.fuse).sequence()
                == ((BlastRemoteBomb.RemoteFuse) fuse).sequence(), "restore retains detonation order");
        tick(restored.fuse);
        check(mob.alarms == 2, "restore retains alarm phase");
        Dungeon.hero.pos = cell(1, 8);
        BlastRemoteBomb.detonateNext();
        check(restored.fuse == null && !heap.items.contains(restored), "remote detonation still explodes immediately");
        tick(fuse);
        check(mob.alarms == 2, "removed bomb cannot emit another alarm");
    }

    private static void freezeAndKick() throws Exception {
        for (boolean kick : new boolean[]{false, true}) {
            TestLevel level = setup();
            Buff.affect(Dungeon.hero, BlastRemoteBomb.class);
            Listener mob = listener(level);
            Noisemaker bomb = new Noisemaker();
            Heap heap = heap(level, cell(4, 4));
            bomb.blastThrowLit(heap.pos);
            Bomb.Fuse remote = bomb.fuse;
            tick(remote); tick(remote); tick(remote);
            if (kick) {
                bomb.blastResetFuseAfterKick();
                check(bomb.fuse instanceof Noisemaker.NoisemakerFuse, "kick restores native noisemaker fuse");
                check(!(bomb.fuse instanceof BlastRemoteBomb.RemoteFuse), "kick removes remote control");
                check(bomb.fuse.cooldown() == Math.max(2, bomb.blastExplosionRange() + 1),
                        "kick uses radius-based countdown");
                Bomb.Fuse timed = bomb.fuse;
                BlastRemoteBomb.detonateNext();
                check(bomb.fuse == timed, "remote button ignores kicked bomb");
                tick(timed); tick(timed);
                check(mob.alarms == 2, "native timed alarm still attracts monsters");
            } else {
                check(remote.freeze() && bomb.fuse == null, "remote noisemaker can still be frozen");
            }
            tick(remote);
            check(mob.alarms == (kick ? 2 : 1), "inactive remote fuse stops alarms");
        }
    }

    private static void ordinaryBomb() throws Exception {
        TestLevel level = setup();
        Buff.affect(Dungeon.hero, BlastRemoteBomb.class);
        Listener mob = listener(level);
        Bomb bomb = new Bomb();
        Heap heap = heap(level, cell(4, 4));
        bomb.blastThrowLit(heap.pos);
        for (int i = 0; i < 12; i++) tick(bomb.fuse);
        check(mob.alarms == 0 && bomb.fuse != null, "ordinary remote bomb stays silent and armed");
        BlastRemoteBomb.detonateNext();
        check(bomb.fuse == null && !heap.items.contains(bomb), "ordinary remote detonation unchanged");
    }

    public static void main(String[] args) throws Exception {
        remoteAlarm();
        freezeAndKick();
        ordinaryBomb();
        System.out.println("Passed " + checks + " remote noisemaker checks");
    }
}
