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
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.Key;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.WornKey;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Dagger;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SentryRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
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

    private static class SilentItemSprite extends ItemSprite {
        @Override public void texture(Object texture) {}
        @Override public ItemSprite view(int image, Glowing glowing) { return this; }
        @Override public ItemSprite view(Heap heap) { return this; }
        @Override public void link() {}
        @Override public void drop() {}
        @Override public void place(int cell) {}
    }

    private static class TestPotion extends Potion {
        int shatters;
        @Override public void shatter(int cell) { shatters++; }
    }

    private static class TestScroll extends Scroll {
        @Override public void doRead() {}
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
        heap.sprite = new SilentItemSprite();
        heap.items.add(new Food());
        heap.items.add(new Food());
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
        check(locked.type == Heap.Type.HEAP && locked.size() == 2,
                "locked chest opens and preserves contents for first blast");
        check(level.map[cell(5, 5)] == Terrain.LOCKED_DOOR, "door blocks entire chain");
        new WideBomb().explode(cell(4, 5));
        check(normal.isEmpty() && locked.isEmpty() && crystal.isEmpty(),
                "later linked blast destroys exposed items by native rules");
        BlastBombVisuals.end();
        for (Heap heap : new Heap[]{normal, locked, crystal}) {
            check(level.heaps.get(heap.pos) == null, "empty heap removed after linked blast");
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
        Level.set(cell(4, 6), Terrain.SECRET_TRAP, level);
        SentryRoom.Sentry sentry = mob(level, new SentryRoom.Sentry(), 3, 5);
        new ArcaneBomb().explode(cell(4, 5));
        check(!level.mobs.contains(sentry), "non-destructive bomb also removes red sentry");
        check(chest.type == Heap.Type.CRYSTAL_CHEST && chest.size() == 2,
                "effect bomb retains native container behavior");
        check(level.map[cell(4, 6)] == Terrain.SECRET_TRAP,
                "effect bomb does not gain terrain/trap destruction");
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

    private static void keys() {
        for (boolean enabled : new boolean[]{false, true}) {
            TestLevel level = setup();
            BlastSettings.setTestDemolition(enabled);
            Heap ground = chest(level, 4, 4, Heap.Type.HEAP);
            ground.items.clear();
            Key iron = new IronKey(1);
            iron.quantity(3);
            Key gold = new GoldenKey(1);
            Key crystal = new CrystalKey(1);
            Key worn = new WornKey(5);
            ground.items.addAll(Arrays.asList(iron, gold, crystal, worn));
            Heap container = chest(level, 3, 5, Heap.Type.LOCKED_CHEST);
            Key contained = new IronKey(1);
            Key containedWorn = new WornKey(5);
            container.items.add(contained);
            container.items.add(containedWorn);
            Heap single = chest(level, 4, 6, Heap.Type.HEAP);
            single.items.clear();
            single.items.add(new CrystalKey(1));
            Level.set(cell(5, 5), Terrain.LOCKED_DOOR, level);
            Heap sheltered = chest(level, 6, 5, Heap.Type.HEAP);
            sheltered.items.clear();
            Key shelteredKey = new IronKey(1);
            sheltered.items.add(shelteredKey);
            new Bomb().explode(cell(4, 5));
            for (Key key : new Key[]{iron, gold, crystal}) {
                check(ground.items.contains(key) == !enabled, "ground key destruction follows switch");
            }
            check(ground.items.contains(worn), "fifth-floor Worn Key survives on the ground");
            check(container.items.contains(contained) == !enabled, "contained key destruction follows switch");
            check(container.items.contains(containedWorn), "Worn Key survives inside a chest");
            check(level.heaps.get(single.pos) == (enabled ? null : single), "last key removal cleans up heap");
            check(sheltered.items.contains(shelteredKey), "keys behind locked door survive");
        }

        TestLevel level = setup();
        BlastSettings.setTestDemolition(true);
        Heap effectHeap = chest(level, 4, 4, Heap.Type.HEAP);
        effectHeap.items.clear();
        Key key = new IronKey(1);
        Key worn = new WornKey(5);
        effectHeap.items.addAll(Arrays.asList(key, worn));
        new ArcaneBomb().explode(cell(4, 5));
        check(effectHeap.items.contains(key) && effectHeap.items.contains(worn),
                "effect bombs retain native item protection");
    }

    private static void decorations() {
        for (boolean enabled : new boolean[]{false, true}) {
            for (boolean destructive : new boolean[]{false, true}) {
                for (int terrain : new int[]{Terrain.REGION_DECO, Terrain.REGION_DECO_ALT,
                        Terrain.STATUE, Terrain.STATUE_SP, Terrain.BOOKSHELF, Terrain.BARRICADE}) {
                    TestLevel level = setup();
                    BlastSettings.setTestDemolition(enabled);
                    for (int y = 1; y < SIZE - 1; y++) Level.set(cell(5, y), Terrain.WALL, level);
                    int obstacle = cell(5, 5);
                    Level.set(obstacle, terrain, level);
                    Heap behind = chest(level, 6, 5, Heap.Type.CHEST);
                    Bomb bomb = destructive ? new WideBomb() : new ArcaneBomb();
                    boolean nativeBreakable = terrain == Terrain.BOOKSHELF || terrain == Terrain.BARRICADE;
                    boolean destroyed = destructive && (nativeBreakable || enabled);
                    BlastBombVisuals.begin(cell(4, 5));
                    bomb.explode(cell(4, 5));
                    check(behind.type == Heap.Type.CHEST, "solid prop shields first blast");
                    BlastBombVisuals.end();
                    check(level.solid[obstacle] == !destroyed,
                            "decoration follows destructive bomb classification");
                    check(level.map[cell(5, 3)] == Terrain.WALL, "ordinary wall remains intact");
                }
            }
        }
    }

    private static void nativeItems() {
        for (boolean enabled : new boolean[]{false, true}) {
            TestLevel level = setup();
            BlastSettings.setTestDemolition(enabled);
            Heap heap = chest(level, 4, 4, Heap.Type.HEAP);
            heap.items.clear();
            Item ordinary = new Food();
            Item unique = new Food();
            unique.unique = true;
            Item equipment = new Dagger();
            Item scroll = new TestScroll();
            TestPotion potion = new TestPotion();
            heap.items.addAll(Arrays.asList(ordinary, unique, equipment, scroll, potion));
            new Bomb().explode(cell(4, 5));
            check(!heap.items.contains(ordinary) && !heap.items.contains(scroll),
                    "native destruction of ordinary items and scrolls");
            check(!heap.items.contains(potion) && potion.shatters == 1, "native potion shatter");
            check(heap.items.contains(unique) && heap.items.contains(equipment),
                    "native protection of unique items and equipment");
        }
    }

    public static void main(String[] args) {
        disabled();
        chain();
        effectBomb();
        heroProtection();
        tenguTransitions();
        keys();
        decorations();
        nativeItems();
        System.out.println("Passed " + checks + " demolition checks");
    }
}
