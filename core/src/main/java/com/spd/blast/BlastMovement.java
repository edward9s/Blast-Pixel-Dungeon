package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
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
        return BlastSettings.testFreeMovement() || (Dungeon.hero != null
                && Dungeon.hero.buff(BlastFreeMovement.class) != null);
    }

    public static boolean canPhase(int cell) {
        if (!enabled() || Dungeon.level == null) {
            return false;
        }
        Level level = Dungeon.level;
        // Never allow moving outside the playable map, even when its edge is a wall.
        if (!level.insideMap(cell)) {
            return false;
        }
        // Mining deposits use the native Pickaxe action when carried, but
        // ordinary mine walls remain phaseable while Free Movement is active.
        int terrain = level.map[cell];
        if (level instanceof MiningLevel
                && (terrain == Terrain.WALL_DECO
                    || terrain == Terrain.MINE_CRYSTAL || terrain == Terrain.MINE_BOULDER)
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

    /** Preserve normal character collisions during hero pathfinding, except for sheep. */
    public static PathFinder.Path findPath(int target, boolean[] traversable, boolean[] visible) {
        if (!enabled()) {
            return Dungeon.findPath(Dungeon.hero, target, traversable, visible, true);
        }

        boolean[] pathPassable = Dungeon.findPassable(Dungeon.hero, traversable, visible, true);
        for (Char character : Actor.chars()) {
            if (character instanceof Sheep && visible[character.pos]) {
                pathPassable[character.pos] = traversable[character.pos];
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
