package com.spd.blast;

import com.badlogic.gdx.Preferences;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Levitation;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.HallsLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.VaultLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.quest.vault.treasure.VaultLaserTreasureRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretChestChasmRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretSummoningRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.MagicalFireRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.TrapsRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.standard.EmptyRoom;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.FrostTrap;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.SummoningTrap;
import com.watabou.noosa.Game;
import com.watabou.utils.GameSettings;
import com.watabou.utils.PathFinder;
import com.watabou.utils.SparseArray;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public final class BlastAutoRoomVisionTest {
    private static final int WIDTH = 13;
    private static final int START = 5 * WIDTH + 2;
    private static final int DOOR = START + 1;
    private static final int TREASURE = START + 7;
    private static final Blob[] NO_HAZARDS = new Blob[0];

    private static final class Hall extends HallsLevel {
        Hall(Room room) {
            rooms = new ArrayList<>();
            rooms.add(room);
            setup(this, room);
        }
    }

    private static final class Vault extends VaultLevel {
        Vault(Room room) {
            rooms = new ArrayList<>();
            rooms.add(room);
            setup(this, room);
        }
    }

    private static final class Brew extends AquaBrew {
        int landing = -1;

        @Override
        public void cast(Hero user, int dst) {
            landing = dst;
        }
    }

    private static void setup(Level level, Room room) {
        Actor.clear();
        BlastAutoSpecialRooms.reset();
        Dungeon.level = level;
        room.set(3, 2, 10, 8);
        room.connected.put(new EmptyRoom(), new Room.Door(3, 5));
        level.mobs = new HashSet<>();
        level.heaps = new SparseArray<>();
        level.blobs = new HashMap<>();
        level.plants = new SparseArray<>();
        level.traps = new SparseArray<>();
        level.customTiles = new ArrayList<>();
        level.customTerrain = new ArrayList<>();
        level.customWalls = new ArrayList<>();
        level.setSize(WIDTH, 11);
        Arrays.fill(level.map, Terrain.WALL);
        Arrays.fill(level.map, START - 1, TREASURE + 1, Terrain.EMPTY);
        level.map[DOOR] = Terrain.OPEN_DOOR;
        level.map[TREASURE] = Terrain.PEDESTAL;
        level.buildFlagMaps();
    }

    private static Hero hero(Level level, int position) {
        Hero hero = new Hero() {
            @Override public float speed() { return 1f; }
        };
        Dungeon.hero = hero;
        hero.pos = position;
        hero.fieldOfView = level.heroFOV;
        hero.fieldOfView[position] = true;
        level.visited[DOOR] = true;
        return hero;
    }

    private static Heap treasure(Level level, Heap.Type type) {
        Heap heap = new Heap();
        heap.pos = TREASURE;
        heap.type = type;
        level.heaps.put(TREASURE, heap);
        return heap;
    }

    private static boolean[] safe(Hero hero) {
        boolean[] path = hero.fieldOfView.clone();
        path[DOOR] = Dungeon.level.passable[DOOR]
                && BlastAutoSpecialRooms.knownCell(Dungeon.level, hero, DOOR);
        return path;
    }

    private static BlastAutoSpecialRooms.Action plan(Level level, Hero hero) {
        boolean[] path = safe(hero);
        PathFinder.buildDistanceMap(hero.pos, path);
        return BlastAutoSpecialRooms.chooseAction(hero, path, PathFinder.distance.clone(),
                NO_HAZARDS, BlastAutoEnvironment.activeSpecialBarriers(level));
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void trappedChest() {
        Hall level = new Hall(new TrapsRoom());
        Hero hero = hero(level, START);
        Heap chest = treasure(level, Heap.Type.CHEST);
        int trap = DOOR + 3;
        level.map[trap] = Terrain.TRAP;
        level.traps.put(trap, new FrostTrap().set(trap));
        level.buildFlagMaps();
        Brew brew = new Brew();
        hero.belongings.backpack.items.add(brew);
        level.visited[DOOR] = false;
        check(plan(level, hero) == BlastAutoSpecialRooms.Action.NONE,
                "do not solve the puzzle before its entrance is discovered");
        level.visited[DOOR] = true;
        check(plan(level, hero) == BlastAutoSpecialRooms.Action.USE && brew.landing >= 0,
                "douse an unseen trap on the route to an unopened chest");
        chest.type = Heap.Type.HEAP;
        check(plan(level, hero) == BlastAutoSpecialRooms.Action.NONE,
                "opened chest releases the trapped room");
    }

    private static void flight(Room room, boolean secret) {
        Hall level = new Hall(room);
        Hero hero = hero(level, DOOR + 1);
        Heap heap = treasure(level, secret ? Heap.Type.HEAP : Heap.Type.CHEST);
        if (secret) heap.items.add(new GoldenKey(Dungeon.depth));
        Arrays.fill(level.map, DOOR + 2, TREASURE, Terrain.CHASM);
        level.buildFlagMaps();
        Buff.affect(hero, Levitation.class, Levitation.DURATION);
        boolean[] path = safe(hero);
        PathFinder.buildDistanceMap(hero.pos, path);
        check(BlastAutoSpecialRooms.continueFlight(hero, path, PathFinder.distance.clone(),
                NO_HAZARDS, new boolean[level.length()]) == BlastAutoSpecialRooms.Action.MOVE
                && hero.curAction instanceof HeroAction.Move && hero.curAction.dst == DOOR + 2,
                "continue floating toward unseen treasure and its solid landing tile");
    }

    private static void eternalFire() {
        Hall level = new Hall(new MagicalFireRoom());
        Hero hero = hero(level, START);
        MagicalFireRoom.EternalFire fire = new MagicalFireRoom.EternalFire();
        fire.seed(level, DOOR + 3, 1);
        level.blobs.put(MagicalFireRoom.EternalFire.class, fire);
        Brew brew = new Brew();
        hero.belongings.backpack.items.add(brew);
        check(plan(level, hero) == BlastAutoSpecialRooms.Action.USE && brew.landing >= 0,
                "douse a discovered room's fire from beyond the hero's current vision");
    }

    private static void summoning() {
        Hall level = new Hall(new SecretSummoningRoom());
        Hero hero = hero(level, START);
        int trap = DOOR + 3;
        level.map[trap] = Terrain.SECRET_TRAP;
        SummoningTrap summoning = new SummoningTrap();
        level.traps.put(trap, summoning.set(trap));
        level.buildFlagMaps();
        Buff.affect(hero, BlastBombManiac.class).setInfiniteBombTypes(true);
        Buff.affect(hero, BlastTestBuffs.Indestructible.class);
        check(plan(level, hero) == BlastAutoSpecialRooms.Action.MOVE
                && hero.curAction.dst == DOOR, "approach a room with an unseen active summoning trap");
        summoning.active = false;
        check(plan(level, hero) == BlastAutoSpecialRooms.Action.NONE,
                "do not approach a completed summoning room");
    }

    private static void vault() throws ReflectiveOperationException {
        Vault level = new Vault(new VaultLaserTreasureRoom());
        Hero hero = hero(level, DOOR);
        Heap chest = treasure(level, Heap.Type.CHEST);
        boolean[] path = safe(hero);
        check(BlastAutoVault.timedTreasureGoal(hero, level, path) == -1,
                "unreachable unresolved treasure does not select the exit");
        path[TREASURE] = true;
        check(BlastAutoVault.timedTreasureGoal(hero, level, path) == TREASURE,
                "an unseen vault chest is a route goal at the entrance");
        Method timedStep = BlastAutoPlay.class.getDeclaredMethod("vaultTimedStep",
                Hero.class, int.class);
        timedStep.setAccessible(true);
        check((int) timedStep.invoke(null, hero, -1) == DOOR + 1,
                "auto-play begins the timed route without seeing inside from the doorway");
        chest.type = Heap.Type.HEAP;
        check(BlastAutoVault.timedTreasureGoal(hero, level, path) == DOOR,
                "a cleared vault room can finish at the exit");
        check((int) timedStep.invoke(null, hero, -1) == -1,
                "a completed room does not claim the doorway again");
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Game.version = "test";
        GameSettings.set((Preferences) Proxy.newProxyInstance(Preferences.class.getClassLoader(),
                new Class<?>[] {Preferences.class}, (proxy, method, params) ->
                        "blast_test_auto_play".equals(params[0]) ? true : params[1]));
        Dungeon.depth = 24;
        Notes.reset();
        trappedChest();
        flight(new TrapsRoom(), false);
        flight(new SecretChestChasmRoom(), true);
        eternalFire();
        summoning();
        vault();
        System.out.println("BlastAutoRoomVisionTest passed");
    }
}
