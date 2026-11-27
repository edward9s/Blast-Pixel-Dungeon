package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Sheep;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.MiningLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.Pickaxe;
import com.watabou.utils.PathFinder;

/** Shared phase state; movement rules below remain Hero-only. */
public final class BlastMovement {
    private BlastMovement() {}

    public static boolean enabled() {
        return BlastSettings.testFreeMovement() || BlastAutoBlacksmith.phase()
                || (Dungeon.hero != null
                && Dungeon.hero.buff(BlastFreeMovement.class) != null);
    }

    public static boolean canPhase(int cell) {
        if (BlastAutoCrystalRooms.canCrossDoor(Dungeon.hero, cell)) return true;
        if (!enabled() || Dungeon.level == null) {
            return false;
        }
        Level level = Dungeon.level;
        // Never allow moving outside the playable map, even when its edge is a wall.
        if (!level.insideMap(cell)) {
            return false;
        }
        // A carried Pickaxe prioritizes mining dark gold only. Ordinary
        // walls, mining crystals and boulders remain phaseable.
        int terrain = level.map[cell];
        if (level instanceof MiningLevel
                && terrain == Terrain.WALL_DECO
                && Dungeon.hero != null
                && Dungeon.hero.belongings.getItem(Pickaxe.class) != null) {
            return false;
        }
        if (BlastBombCollision.hasLitBomb(level, cell)) {
            return true;
        }
        switch (terrain) {
            case Terrain.WALL:
            case Terrain.WALL_DECO:
            // Fixed obstacles are phaseable without changing terrain for anyone else.
            case Terrain.BARRICADE:
            case Terrain.BOOKSHELF:
            case Terrain.STATUE:
            case Terrain.STATUE_SP:
            case Terrain.CUSTOM_DECO:
            case Terrain.CUSTOM_DECO_WTR:
            case Terrain.REGION_DECO:
            case Terrain.REGION_DECO_ALT:
            case Terrain.MINE_CRYSTAL:
            case Terrain.MINE_BOULDER:
            case Terrain.ALCHEMY:
            case Terrain.SECRET_DOOR:
            case Terrain.LOCKED_DOOR:
            case Terrain.HERO_LKD_DR:
            case Terrain.CRYSTAL_DOOR:
                return true;
            default:
                return false;
        }
    }

    /** The hero may share a sheep's cell only while free movement is active. */
    public static boolean canPassSheep(int cell) {
        return enabled() && Dungeon.level != null && Dungeon.level.insideMap(cell)
                && Actor.findChar(cell) instanceof Sheep;
    }

    /** Reject live DM-300 electrical tiles even on an already cached route. */
    public static boolean pylonDanger(int cell) {
        Level level = Dungeon.level;
        return BlastAutoEnvironment.pylonDanger(
                BlastAutoEnvironment.pylonEnergy(level), level.water[cell], cell);
    }

    /** Preserve normal character collisions during hero pathfinding, except for sheep. */
    public static PathFinder.Path findPath(int target, boolean[] traversable, boolean[] visible) {
        boolean phase = enabled();
        Blob energy = BlastAutoEnvironment.pylonEnergy(Dungeon.level);
        boolean live = energy != null && energy.volume > 0;
        if (!phase && !live) {
            return Dungeon.findPath(Dungeon.hero, target, traversable, visible, true);
        }

        boolean[] pathPassable = Dungeon.findPassable(Dungeon.hero, traversable, visible, true);
        if (phase) {
            for (Char character : Actor.chars()) {
                if (character instanceof Sheep && visible[character.pos]) {
                    pathPassable[character.pos] = traversable[character.pos];
                }
            }
        }
        if (live) {
            // The pathfinder permits an occupied goal; do not permit a hazardous
            // empty destination. An enemy can still be approached from safe ground.
            if (pylonDanger(target) && Actor.findChar(target) == null) return null;
            for (int cell = 0; cell < pathPassable.length; cell++) {
                if (BlastAutoEnvironment.pylonDanger(energy, Dungeon.level.water[cell], cell)) {
                    pathPassable[cell] = false;
                }
            }
        }
        return PathFinder.find(Dungeon.hero.pos, target, pathPassable);
    }

    /** Open a locked door normally when a matching key is available. */
    public static boolean shouldUnlock(int cell) {
        if (!enabled() || Dungeon.level == null || Dungeon.branch != 0
                || !Dungeon.level.insideMap(cell)) {
            return false;
        }
        switch (Dungeon.level.map[cell]) {
            case Terrain.LOCKED_DOOR:
                return Notes.keyCount(new IronKey(Dungeon.depth)) > 0;
            case Terrain.CRYSTAL_DOOR:
                return Notes.keyCount(new CrystalKey(Dungeon.depth)) > 0;
            default:
                return false;
        }
    }

    public static boolean pathPassable(int cell, boolean ordinaryPassable,
                                        boolean knownOrVisible) {
        return ordinaryPassable || (knownOrVisible && canPhase(cell));
    }
}
