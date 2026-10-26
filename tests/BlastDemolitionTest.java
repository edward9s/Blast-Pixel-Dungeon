import com.badlogic.gdx.Preferences;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.ArcaneBomb;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SentryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.spd.blast.BlastBombVisuals;
import com.spd.blast.BlastDemolition;
import com.spd.blast.BlastSettings;
import com.watabou.utils.GameSettings;
import com.watabou.utils.SparseArray;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;

/** Run against a compiled, Blast-patched SPD checkout; no graphics/audio backend needed. */
public final class BlastDemolitionTest {
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

    private static class SilentSprite extends CharSprite {
        @Override public void die() {}
    }

    private static class TestPrisonLevel extends PrisonBossLevel {
        private State phase;
        int transitions;
        TestPrisonLevel(State phase) { this.phase = phase; }
        @Override public State state() { return phase; }
        @Override public void progress() {
            transitions++;
            phase = phase == State.FIGHT_START ? State.FIGHT_PAUSE : State.WON;
        }
        @Override public void updateFieldOfView(Char ch, boolean[] fov) {
            Arrays.fill(fov, true);
        }
    }

    private static class InvulnerableMob extends Mob {
        int deaths;
        Object cause;
        @Override public void damage(int damage, Object source) {}
        @Override public int defenseSkill(Char enemy) { return INFINITE_EVASION; }
        @Override public void die(Object source) {
            deaths++;
            cause = source;
            HP = 0;
            Dungeon.level.mobs.remove(this);
            Actor.remove(this);
        }
    }

    private static class WideBomb extends Bomb {
        @Override protected int explosionRange() { return 3; }
    }

    private static int cell(int x, int y) { return y * SIZE + x; }

    private static TestLevel setup() {
        return setup(new TestLevel());
    }

    private static <T extends Level> T setup(T level) {
        Actor.clear();
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

    private static Heap chest(Level level, int x, int y, Heap.Type type) {
        Heap heap = new Heap();
        heap.pos = cell(x, y);
        heap.type = type;
        heap.items.add(new Item());
        heap.items.add(new Item());
        level.heaps.put(heap.pos, heap);
        return heap;
    }

    private static <T extends Mob> T mob(Level level, T mob, int x, int y) {
        mob.pos = cell(x, y);
        mob.sprite = new SilentSprite();
        level.mobs.add(mob);
        Actor.add(mob);
        return mob;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void disabled() {
        TestLevel level = setup();
        check(!BlastSettings.testDemolition(), "option must default to off");
        Heap heap = chest(level, 4, 4, Heap.Type.LOCKED_CHEST);
        Level.set(cell(5, 5), Terrain.LOCKED_DOOR, level);
        Level.set(cell(4, 6), Terrain.SECRET_TRAP, level);
        SentryRoom.Sentry sentry = mob(level, new SentryRoom.Sentry(), 3, 5);
        Bomb bomb = new Bomb();
        check(BlastDemolition.prepare(bomb, cell(4, 5)) == null, "disabled helper must do nothing");
        check(!bomb.blastWarningCells(cell(4, 5))[cell(5, 5)], "disabled locked door warning");
        check(!BlastDemolition.protect(heap), "disabled container handling");
        bomb.explode(cell(4, 5));
        check(heap.type == Heap.Type.LOCKED_CHEST && heap.size() == 2, "locked chest survives normally");
        check(level.map[cell(5, 5)] == Terrain.LOCKED_DOOR, "locked door survives normally");
        check(level.map[cell(4, 6)] == Terrain.SECRET_TRAP, "trap survives normally");
        check(level.mobs.contains(sentry), "red sentry survives normally");
    }

    private static void chain() {
        TestLevel level = setup();
        BlastSettings.setTestDemolition(true);
        for (int y = 1; y < SIZE - 1; y++) Level.set(cell(5, y), Terrain.WALL, level);
        Level.set(cell(5, 5), Terrain.LOCKED_DOOR, level);
        Level.set(cell(5, 6), Terrain.CRYSTAL_DOOR, level);
        Level.set(cell(5, 4), Terrain.HERO_LKD_DR, level);
        Level.set(cell(2, 5), Terrain.LOCKED_EXIT, level);
        Heap normal = chest(level, 4, 4, Heap.Type.CHEST);
        Heap locked = chest(level, 4, 6, Heap.Type.LOCKED_CHEST);
        Heap crystal = chest(level, 3, 5, Heap.Type.CRYSTAL_CHEST);
        Heap behind = chest(level, 6, 5, Heap.Type.LOCKED_CHEST);
        int[] traps = {cell(3, 3), cell(4, 3), cell(2, 4)};
        int[] terrain = {Terrain.TRAP, Terrain.SECRET_TRAP, Terrain.INACTIVE_TRAP};
        int[] triggers = {0};
        for (int i = 0; i < traps.length; i++) {
            Level.set(traps[i], terrain[i], level);
            level.setTrap(new Trap() { public void activate() { triggers[0]++; } }, traps[i]);
        }
        SentryRoom.Sentry sentry = mob(level, new SentryRoom.Sentry(), 3, 6);
        InvulnerableMob immune = mob(level, new InvulnerableMob(), 3, 4);
        InvulnerableMob sheltered = mob(level, new InvulnerableMob(), 6, 4);
        Bomb first = new WideBomb();
        boolean[] warning = first.blastWarningCells(cell(4, 5));
        check(warning[cell(5, 5)] && warning[cell(5, 6)], "both door types must be warned");
        check(warning[cell(5, 4)], "Skeleton Key locked door must be warned");
        check(!warning[behind.pos], "door blocks blast behind it");
        BlastBombVisuals.begin(cell(4, 5));
        first.explode(cell(4, 5));
        check(locked.type == Heap.Type.LOCKED_CHEST, "chest remains enclosed during chain");
        check(level.map[cell(5, 5)] == Terrain.LOCKED_DOOR, "door blocks entire chain");
        new WideBomb().explode(cell(4, 5));
        check(normal.size() == 2 && locked.size() == 2 && crystal.size() == 2,
                "later linked blast must preserve chest contents");
        BlastBombVisuals.end();
        for (Heap heap : new Heap[]{normal, locked, crystal}) {
            check(heap.type == Heap.Type.HEAP && heap.size() == 2, "chest contents become collectible");
        }
        check(behind.type == Heap.Type.LOCKED_CHEST, "sheltered chest remains locked");
        check(level.map[cell(5, 5)] == Terrain.EMPTY && level.map[cell(5, 6)] == Terrain.EMPTY,
                "both doors destroyed after chain");
        check(level.map[cell(5, 4)] == Terrain.EMPTY, "Skeleton Key locked door destroyed");
        check(level.map[cell(2, 5)] == Terrain.LOCKED_EXIT, "locked exit remains intact");
        for (int pos : traps) {
            check(level.map[pos] == Terrain.EMPTY && level.traps.get(pos) == null, "trap fully removed");
        }
        check(triggers[0] == 0, "destroying traps must not trigger them");
        check(!level.mobs.contains(sentry) && !Actor.chars().contains(sentry), "red sentry removed");
        check(immune.deaths == 1 && immune.cause == first, "direct death retains bomb attribution exactly once");
        check(sheltered.deaths == 0, "wall shields other characters");
        check(Dungeon.hero.HP == Dungeon.hero.HT, "hero excluded from forced death");
        BlastSettings.setTestDemolition(false);
        check(!first.blastWarningCells(cell(4, 5))[cell(2, 5)], "disabled warnings restored");
    }

    private static void effectBomb() {
        TestLevel level = setup();
        BlastSettings.setTestDemolition(true);
        Heap chest = chest(level, 4, 4, Heap.Type.CRYSTAL_CHEST);
        SentryRoom.Sentry sentry = mob(level, new SentryRoom.Sentry(), 3, 5);
        new ArcaneBomb().explode(cell(4, 5));
        check(!level.mobs.contains(sentry), "non-destructive bomb also removes red sentry");
        check(chest.type == Heap.Type.HEAP && chest.size() == 2, "effect bomb releases chest loot");
    }

    private static void heroProtection() {
        setup();
        BlastSettings.setTestDemolition(true);
        BlastSettings.setTestIndestructible(true);
        Dungeon.hero.pos = cell(4, 5);
        new Bomb().explode(Dungeon.hero.pos);
        check(Dungeon.hero.HP == Dungeon.hero.HT && Actor.chars().contains(Dungeon.hero),
                "hero inside blast retains normal Bombproof protection");
        Level.set(cell(5, 5), Terrain.LOCKED_DOOR);
        Bomb bomb = new Bomb();
        check(bomb.blastWarningCells(Dungeon.hero.pos)[cell(5, 5)], "enabled door warning");
        BlastSettings.setTestDemolition(false);
        check(!bomb.blastWarningCells(Dungeon.hero.pos)[cell(5, 5)], "warning follows disabled setting");
        bomb.explode(Dungeon.hero.pos);
        check(Dungeon.level.map[cell(5, 5)] == Terrain.LOCKED_DOOR,
                "switching off restores door protection");
    }

    private static void tenguTransitions() {
        for (PrisonBossLevel.State phase : new PrisonBossLevel.State[]{
                PrisonBossLevel.State.FIGHT_START, PrisonBossLevel.State.FIGHT_ARENA}) {
            TestPrisonLevel level = setup(new TestPrisonLevel(phase));
            BlastSettings.setTestDemolition(true);
            Tengu tengu = mob(level, new Tengu(), 3, 5);
            BlastBombVisuals.begin(cell(4, 5));
            new Bomb().explode(cell(4, 5));
            new Bomb().explode(cell(4, 5));
            check(level.transitions == 0, "Tengu map transition waits for full chain");
            check(tengu.HP == (phase == PrisonBossLevel.State.FIGHT_START ? tengu.HT / 2 : 0),
                    "Tengu reaches current phase's health threshold");
            BlastBombVisuals.end();
            check(level.transitions == 1, "linked bombs advance Tengu phase exactly once");
        }
    }

    public static void main(String[] args) {
        disabled();
        chain();
        effectBomb();
        heroProtection();
        tenguTransitions();
        System.out.println("Passed " + checks + " demolition checks");
    }
}
