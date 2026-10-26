package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Tengu;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.Key;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.WornKey;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.PrisonBossLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;

import java.util.ArrayList;

/** Optional bomb destruction, using the footprint before any terrain changes. */
public final class BlastDemolition {
    private final Level level;
    private final Bomb bomb;
    private final PrisonBossLevel.State prisonPhase;
    private final ArrayList<Integer> obstacles = new ArrayList<>();
    private final ArrayList<Mob> mobs = new ArrayList<>();

    private BlastDemolition(Level level, Bomb bomb) {
        this.level = level;
        this.bomb = bomb;
        prisonPhase = level instanceof PrisonBossLevel ? ((PrisonBossLevel) level).state() : null;
    }

    private static boolean obstacle(int terrain) {
        switch (terrain) {
            case Terrain.LOCKED_DOOR:
            case Terrain.CRYSTAL_DOOR:
            case Terrain.HERO_LKD_DR:
            case Terrain.REGION_DECO:
            case Terrain.REGION_DECO_ALT:
            case Terrain.STATUE:
            case Terrain.STATUE_SP:
                return true;
            default:
                return false;
        }
    }

    /** Solid obstacles can be hit endpoints, while still blocking the ray behind them. */
    public static void includeObstacles(Bomb bomb, boolean[] explodable) {
        if (!bomb.explodesDestructively() || !BlastSettings.testDemolition()) return;
        Level level = Dungeon.level;
        for (int cell = 0; cell < explodable.length; cell++) {
            if (obstacle(level.map[cell])) explodable[cell] = true;
        }
    }

    /** Locked chests use the same opening and subsequent item damage as ordinary chests. */
    public static void explode(Heap heap) {
        if (BlastSettings.testDemolition()
                && (heap.type == Heap.Type.LOCKED_CHEST || heap.type == Heap.Type.CRYSTAL_CHEST)) {
            heap.type = Heap.Type.CHEST;
        }
        heap.explode();
    }

    public static BlastDemolition prepare(Bomb bomb, int center) {
        if (!BlastSettings.testDemolition()) return null;
        Level level = Dungeon.level;
        BlastDemolition demolition = new BlastDemolition(level, bomb);
        boolean destructive = bomb.explodesDestructively();
        boolean[] cells = bomb.blastWarningCells(center);
        for (int cell = 0; cell < cells.length; cell++) {
            if (!cells[cell]) continue;
            int terrain = level.map[cell];
            if (destructive && obstacle(terrain)) demolition.obstacles.add(cell);
            Heap heap = level.heaps.get(cell);
            if (heap != null && destructive) {
                // Keys are normally unique and explosion-proof. WornKey is the
                // fifth-floor progression key and must retain that protection.
                for (Item item : heap.items.toArray(new Item[0])) {
                    if (item instanceof Key && !(item instanceof WornKey)) heap.remove(item);
                }
            }
            Char ch = Actor.findChar(cell);
            if (ch instanceof Mob) demolition.mobs.add((Mob) ch);

            // Remove traps before potions, fire, or other bomb effects can trigger them.
            if (destructive && (level.traps.get(cell) != null || terrain == Terrain.TRAP
                    || terrain == Terrain.SECRET_TRAP || terrain == Terrain.INACTIVE_TRAP)) {
                Level.set(cell, Terrain.EMPTY, level);
                GameScene.updateMap(cell);
            }
        }
        return demolition;
    }

    public void finish() {
        // Use death hooks for loot and boss cleanup, bypassing evasion, armor,
        // invulnerability, and overrides which discard damage (such as the red sentry).
        // Membership avoids killing a target twice after an earlier linked explosion.
        for (Mob mob : mobs) {
            if (!level.mobs.contains(mob)) continue;
            if (mob instanceof Tengu && level instanceof PrisonBossLevel) {
                // Tengu's room transitions live in Level.progress(), outside die().
                PrisonBossLevel prison = (PrisonBossLevel) level;
                PrisonBossLevel.State phase = prison.state();
                if (phase == PrisonBossLevel.State.FIGHT_START
                        || phase == PrisonBossLevel.State.FIGHT_ARENA) {
                    mob.HP = phase == PrisonBossLevel.State.FIGHT_START ? mob.HT / 2 : 0;
                    BlastBombVisuals.afterChain(() -> {
                        if (Dungeon.level == level && prison.state() == phase) prison.progress();
                    });
                    continue;
                }
            }
            mob.die(bomb);
        }
        BlastBombVisuals.afterChain(() -> {
            if (Dungeon.level != level) return;
            // Tengu can repaint this same Level instance during a phase transition.
            if (level instanceof PrisonBossLevel
                    && ((PrisonBossLevel) level).state() != prisonPhase) return;
            boolean changed = false;
            for (int cell : obstacles) {
                if (!obstacle(level.map[cell])) continue;
                Level.set(cell, Terrain.EMPTY, level);
                GameScene.updateMap(cell);
                changed = true;
            }
            if (changed) {
                level.cleanWalls();
                Dungeon.observe();
                BlastBombWarnings.refresh(level);
            }
        });
    }
}
