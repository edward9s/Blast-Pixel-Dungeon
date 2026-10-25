package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.Room;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.standard.StandardRoom;
import com.shatteredpixel.shatteredpixeldungeon.tiles.CustomTilemap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/** Adds isolated, destructible cover without closing off existing room routes. */
public final class BlastRoomCover {

    private BlastRoomCover() {}

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    public static void place(RegularLevel level, Collection<Room> rooms) {
        int width = level.width();
        long seed = Dungeon.seedCurDepth();

        for (Room room : rooms) {
            if (!(room instanceof StandardRoom) || room.isEntrance() || room.isExit()) continue;

            int innerWidth = room.right - room.left - 1;
            int innerHeight = room.bottom - room.top - 1;
            if (innerWidth < 3 || innerHeight < 3) continue;

            // Bookshelves are the native wooden-wall terrain. Detect them before
            // adding cover, so our own placements do not change the room style.
            boolean woodenRoom = hasWoodenWalls(level, room, width);

            ArrayList<Integer> candidates = new ArrayList<>();
            int freeTiles = 0;
            for (int y = room.top + 1; y < room.bottom; y++) {
                for (int x = room.left + 1; x < room.right; x++) {
                    int cell = x + y * width;
                    if (!openGround(level.map[cell])) continue;
                    freeTiles++;

                    // A single floor tile may touch walls or water; only functional
                    // objects, an actual doorway, or a blocked route rule it out.
                    // Keep the four inner corners of each room free.
                    if (roomCorner(room, x, y)
                            || doorApproach(room, x, y)
                            || level.findMob(cell) != null
                            || level.heaps.get(cell) != null
                            || level.traps.get(cell) != null
                            || level.plants.get(cell) != null
                            || coveredByCustomTile(level, x, y)) continue;
                    candidates.add(cell);
                }
            }

            // Small 5x5 rooms are now eligible, and natural pillars do not
            // subtract from the desired amount of additional cover.
            if (freeTiles < 9) continue;
            int target = Math.min(8, Math.max(1, freeTiles / 12));
            Collections.sort(candidates, (a, b) -> Long.compare(
                    mix(seed ^ (a.longValue() * 0x9E3779B97F4A7C15L)),
                    mix(seed ^ (b.longValue() * 0x9E3779B97F4A7C15L))));

            ArrayList<Integer> placed = new ArrayList<>();
            for (int cell : candidates) {
                if (placed.size() >= target) break;
                if (!openGround(level.map[cell]) || tooClose(cell, placed, width)
                        || narrowPassage(level, cell, width)
                        || cutsRoomRoute(level, room, cell, width)) continue;

                // All generated cover is a wall. Wooden rooms use their
                // native wooden terrain except when touching a stone wall.
                level.map[cell] = woodenRoom && !nextToStoneWall(level, cell, width)
                        ? Terrain.BOOKSHELF : Terrain.WALL;
                placed.add(cell);
            }
        }
    }

    private static boolean hasWoodenWalls(RegularLevel level, Room room, int width) {
        for (int y = room.top; y <= room.bottom; y++) {
            for (int x = room.left; x <= room.right; x++) {
                if (level.map[x + y * width] == Terrain.BOOKSHELF) return true;
            }
        }
        return false;
    }

    private static boolean nextToStoneWall(RegularLevel level, int cell, int width) {
        return stoneWall(level.map[cell - width])
                || stoneWall(level.map[cell - 1])
                || stoneWall(level.map[cell + 1])
                || stoneWall(level.map[cell + width]);
    }

    private static boolean stoneWall(int tile) {
        return tile == Terrain.WALL || tile == Terrain.WALL_DECO
                || tile == Terrain.SECRET_DOOR;
    }

    private static boolean openGround(int terrain) {
        int flags = Terrain.flags[terrain];
        if ((flags & Terrain.PASSABLE) == 0 || (flags & Terrain.SOLID) != 0) return false;
        // Passable does not necessarily mean an ordinary, replaceable floor.
        switch (terrain) {
            case Terrain.DOOR:
            case Terrain.OPEN_DOOR:
            case Terrain.ENTRANCE:
            case Terrain.ENTRANCE_SP:
            case Terrain.EXIT:
            case Terrain.UNLOCKED_EXIT:
            case Terrain.PEDESTAL:
            case Terrain.EMPTY_WELL:
            case Terrain.SECRET_TRAP:
            case Terrain.INACTIVE_TRAP:
            case Terrain.CUSTOM_DECO_EMPTY:
                return false;
            default:
                return true;
        }
    }

    private static boolean roomCorner(Room room, int x, int y) {
        return (x == room.left + 1 || x == room.right - 1)
                && (y == room.top + 1 || y == room.bottom - 1);
    }

    private static boolean doorApproach(Room room, int x, int y) {
        // Protect only the square directly inside a door, not a four-tile radius.
        for (Room.Door door : room.connected.values()) {
            if (door != null && Math.abs(x - door.x) + Math.abs(y - door.y) == 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean walkable(int terrain) {
        return (Terrain.flags[terrain] & Terrain.PASSABLE) != 0;
    }

    private static boolean narrowPassage(RegularLevel level, int cell, int width) {
        int[] map = level.map;
        return (!walkable(map[cell - 1]) && !walkable(map[cell + 1]))
                || (!walkable(map[cell - width]) && !walkable(map[cell + width]));
    }

    private static boolean cutsRoomRoute(RegularLevel level, Room room, int blocked, int width) {
        int[] offsets = {-width, width, -1, 1};
        int start = -1;
        int[] neighbours = new int[4];
        int count = 0;
        for (int offset : offsets) {
            int next = blocked + offset;
            if (insideRoom(next, room, width) && walkable(level.map[next])) {
                neighbours[count++] = next;
                start = next;
            }
        }
        if (count < 2) return false;

        // A candidate is safe if all its walkable neighbours can still reach
        // one another without stepping on it (i.e. it is not a cut vertex).
        boolean[] visited = new boolean[level.length()];
        int[] queue = new int[(room.right - room.left + 1) * (room.bottom - room.top + 1)];
        int head = 0, tail = 0;
        visited[start] = true;
        queue[tail++] = start;
        while (head < tail) {
            int current = queue[head++];
            for (int offset : offsets) {
                int next = current + offset;
                if (next != blocked && insideRoom(next, room, width)
                        && !visited[next] && walkable(level.map[next])) {
                    visited[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (!visited[neighbours[i]]) return true;
        }
        return false;
    }

    private static boolean insideRoom(int cell, Room room, int width) {
        int x = cell % width;
        int y = cell / width;
        return x >= room.left && x <= room.right && y >= room.top && y <= room.bottom;
    }

    private static boolean coveredByCustomTile(RegularLevel level, int x, int y) {
        for (CustomTilemap tile : level.customTiles) {
            if (covers(tile, x, y)) return true;
        }
        for (CustomTilemap tile : level.customTerrain) {
            if (covers(tile, x, y)) return true;
        }
        for (CustomTilemap tile : level.customWalls) {
            if (covers(tile, x, y)) return true;
        }
        return false;
    }

    private static boolean covers(CustomTilemap tile, int x, int y) {
        return x >= tile.tileX && x < tile.tileX + tile.tileW
                && y >= tile.tileY && y < tile.tileY + tile.tileH;
    }

    private static boolean tooClose(int cell, ArrayList<Integer> placed, int width) {
        int x = cell % width;
        int y = cell / width;
        for (int other : placed) {
            if (Math.abs(x - other % width) <= 1 && Math.abs(y - other / width) <= 1) {
                return true;
            }
        }
        return false;
    }
}
