package com.spd.blast;

import com.badlogic.gdx.Preferences;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;
import com.shatteredpixel.shatteredpixeldungeon.levels.HallsLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.SentryRoom;
import com.watabou.noosa.Game;
import com.watabou.utils.GameSettings;
import com.watabou.utils.PathFinder;
import com.watabou.utils.SparseArray;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/** Test the room planner with enough time and with an unseen treasure route. */
public final class BlastAutoSentryRoomTest {
    private static final int WIDTH = 13;
    private static final int HALL = 5 * WIDTH + 2;
    private static final int DOOR = HALL + 1;
    private static final int CHEST = HALL + 7;

    private static final class DarkRoom extends HallsLevel {
        private final SentryRoom room = new SentryRoom() {
            @Override
            public Door entrance() {
                return new Door(3, 5);
            }
        };

        DarkRoom() {
            room.set(3, 2, 10, 8);
            rooms = new ArrayList<>();
            rooms.add(room);
            mobs = new HashSet<>();
            heaps = new SparseArray<>();
            blobs = new HashMap<>();
            plants = new SparseArray<>();
            traps = new SparseArray<>();
            customTiles = new ArrayList<>();
            customTerrain = new ArrayList<>();
            customWalls = new ArrayList<>();
            setSize(WIDTH, 11);
            Arrays.fill(map, Terrain.WALL);
            Arrays.fill(map, HALL, CHEST + 1, Terrain.EMPTY);
            Arrays.fill(map, DOOR + 2, DOOR + 5, Terrain.EMPTY_SP);
            map[DOOR] = Terrain.OPEN_DOOR;
            map[CHEST] = Terrain.PEDESTAL;
            buildFlagMaps();
            Heap treasure = new Heap();
            treasure.pos = CHEST;
            treasure.type = Heap.Type.CHEST;
            heaps.put(CHEST, treasure);
            SentryRoom.Sentry sentry = new SentryRoom.Sentry();
            sentry.pos = 3 * WIDTH + 9;
            mobs.add(sentry);
            Actor.add(sentry);
        }
    }

    private static void check(boolean expected, boolean actual, String label) {
        if (expected != actual) throw new AssertionError(label);
    }

    private static BlastAutoSpecialRooms.Action plan(Hero hero, DarkRoom room) {
        boolean[] safe = hero.fieldOfView.clone();
        PathFinder.buildDistanceMap(hero.pos, safe);
        return BlastAutoSentryRoom.chooseAction(hero, safe, PathFinder.distance.clone(),
                new Blob[0], BlastAutoEnvironment.activeSpecialBarriers(room));
    }

    private static void lowVision() {
        Game.version = "test";
        GameSettings.set((Preferences) Proxy.newProxyInstance(Preferences.class.getClassLoader(),
                new Class<?>[] {Preferences.class}, (proxy, method, args) -> args[1]));
        Actor.clear();
        BlastAutoSentryRoom.reset();
        Dungeon.depth = 24;
        DarkRoom room = new DarkRoom();
        Dungeon.level = room;
        Hero hero = new Hero() {
            @Override
            public float speed() {
                return 3f;
            }
        };
        Dungeon.hero = hero;
        hero.pos = HALL;
        hero.fieldOfView = room.heroFOV;
        hero.fieldOfView[HALL] = true;
        Buff.affect(hero, Haste.class, Haste.DURATION);
        check(true, plan(hero, room) == BlastAutoSpecialRooms.Action.NONE,
                "unseen entrance cannot claim the hero");

        hero.fieldOfView[DOOR] = true;
        check(true, plan(hero, room) == BlastAutoSpecialRooms.Action.MOVE
                && hero.curAction instanceof HeroAction.Move && hero.curAction.dst == DOOR,
                "Haste can stage at the entrance despite an unseen chest and red floor");

        int[] landing = {-1};
        hero.belongings.backpack.items.add(new AquaBrew() {
            @Override
            public void cast(Hero user, int dst) {
                landing[0] = dst;
            }
        });
        check(true, plan(hero, room) == BlastAutoSpecialRooms.Action.USE
                && landing[0] >= 0 && room.distance(hero.pos, landing[0]) >= 2,
                "Aqua Brew can safely throw at the unseen red floor");

        Arrays.fill(room.map, DOOR + 2, DOOR + 5, Terrain.WATER);
        room.buildFlagMaps();
        check(true, plan(hero, room) == BlastAutoSpecialRooms.Action.MOVE
                && hero.curAction.dst == DOOR,
                "after flooding, walk toward the unseen chest using the dry or flooded route");

        room.heaps.get(CHEST).type = Heap.Type.HEAP;
        check(true, plan(hero, room) == BlastAutoSpecialRooms.Action.NONE,
                "opened chest releases the room to ordinary exploration");
    }

    public static void main(String[] args) {
        check(true, BlastAutoSentryRoom.enoughHaste(20f, 6, 6, 1f / 3f),
                "fast round trip");
        check(false, BlastAutoSentryRoom.enoughHaste(3f, 6, 6, 1f / 3f),
                "insufficient duration");
        check(false, BlastAutoSentryRoom.enoughHaste(20f, Integer.MAX_VALUE, 6, 1f / 3f),
                "unreachable chest");
        check(false, BlastAutoSentryRoom.enoughHaste(20f, 6, Integer.MAX_VALUE, 1f / 3f),
                "unreachable exit");
        check(false, BlastAutoSentryRoom.enoughHaste(20f, 6, 6, 0f), "invalid speed");
        lowVision();
        System.out.println("BlastAutoSentryRoomTest passed");
    }
}
