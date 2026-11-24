package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalSpire;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalGuardian;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GnollGeomancer;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.Pickaxe;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.MiningLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;

/** Native mining actions and scoped movement for the troll blacksmith quest. */
final class BlastAutoBlacksmith {
    private static boolean enabledPhase;
    private static boolean previousPhase;

    private BlastAutoBlacksmith() {}

    static void update() {
        boolean active = Dungeon.level instanceof MiningLevel
                && Blacksmith.Quest.given() && !Blacksmith.Quest.completed();
        if (active && !enabledPhase) {
            previousPhase = BlastSettings.testFreeMovement();
            enabledPhase = true;
            BlastSettings.setTestFreeMovement(true);
        } else if (!active && enabledPhase) {
            BlastSettings.setTestFreeMovement(previousPhase);
            enabledPhase = false;
        }
    }

    static void reset() {
        if (enabledPhase) BlastSettings.setTestFreeMovement(previousPhase);
        enabledPhase = false;
    }

    static boolean mine(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (!(level instanceof MiningLevel) || !Blacksmith.Quest.given()
                || Blacksmith.Quest.completed()
                || hero.belongings.getItem(Pickaxe.class) == null) return false;
        int target = -1;
        int stand = -1;
        int shortest = Integer.MAX_VALUE;
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.map[cell] != Terrain.WALL_DECO
                    || !(level.visited[cell] || level.mapped[cell]
                        || hero.fieldOfView[cell])) continue;
            for (int offset : PathFinder.NEIGHBOURS8) {
                int next = cell + offset;
                if (!level.insideMap(next) || !safe[next]
                        || distances[next] == Integer.MAX_VALUE
                        || distances[next] >= shortest) continue;
                target = cell;
                stand = next;
                shortest = distances[next];
            }
        }
        if (target < 0) return false;
        if (hero.pos == stand) hero.curAction = new HeroAction.Mine(target);
        else if (!hero.rooted) hero.curAction = new HeroAction.Move(
                BlastAutoPlay.firstStep(stand, safe, distances));
        return hero.curAction != null;
    }
    /** The crystal spire is damaged only by its native pickaxe interaction. */
    static boolean attackBoss(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (!(level instanceof MiningLevel) || !Blacksmith.Quest.given()
                || Blacksmith.Quest.bossBeaten()
                || hero.belongings.getItem(Pickaxe.class) == null) return false;
        for (int cell = 0; cell < level.length(); cell++) {
            if (level.map[cell] == Terrain.WALL_DECO) return false;
        }
        for (Heap heap : level.heaps.valueList()) {
            if (heap.type != Heap.Type.HEAP && heap.type != Heap.Type.FOR_SALE)
                return false;
        }
        for (Mob mob : level.mobs) {
            if (mob instanceof CrystalGuardian && mob.isAlive()) return false;
        }
        for (Mob mob : level.mobs) {
            if (!(mob instanceof CrystalSpire) || !mob.isAlive()) continue;
            int stand = -1;
            int steps = Integer.MAX_VALUE;
            for (int offset : PathFinder.NEIGHBOURS8) {
                int cell = mob.pos + offset;
                if (!level.insideMap(cell) || !safe[cell]
                        || distances[cell] >= steps) continue;
                stand = cell;
                steps = distances[cell];
            }
            if (stand < 0) return false;
            hero.curAction = hero.pos == stand
                    ? new HeroAction.Interact(mob)
                    : new HeroAction.Move(BlastAutoPlay.firstStep(stand, safe, distances));
            return true;
        }
        return false;
    }

}
