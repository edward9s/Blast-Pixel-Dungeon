package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.BlastParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.SmokeParticle;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.particles.Emitter;
import com.watabou.utils.BArray;
import com.watabou.utils.Bundle;
import com.watabou.utils.PointF;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * Independent Tengu-style bomb for monsters killed by Bomb Maniac explosions.
 * It is not a Bomb: it cannot enter the Hero's supply or be kicked by rare mobs.
 */
public final class BlastDeathBomb extends Item {
    private static final String FUSE = "death_bomb_fuse";
    private static final int RADIUS = 2;
    private static final int TRIGGER_CHANCE = 4;

    // All kills within a synchronous explosion chain share one candidate pool.
    private static final ThreadLocal<ArrayList<Integer>> candidates = new ThreadLocal<>();
    private Fuse fuse;

    {
        image = ItemSpriteSheet.TENGU_BOMB;
        dropsDownHeap = true;
        unique = true;
    }

    /** Called after both vanilla and Blast loot rolls for an enemy death. */
    public static void onMobDeath(Mob mob, Object cause, int previousGroundQuantity) {
        if (!(cause instanceof Bomb) || !((Bomb) cause).blastManiacExplosion()
                || Char.hasProp(mob, Char.Property.BOSS)
                || Char.hasProp(mob, Char.Property.MINIBOSS)
                || BlastLoot.groundQuantity(mob.pos) != previousGroundQuantity
                || Random.Int(TRIGGER_CHANCE) != 0) {
            return;
        }

        final Level level = Dungeon.level;
        if (level == null || Dungeon.hero == null || !Dungeon.hero.isAlive()
                || hasActiveBomb(level)) return;

        ArrayList<Integer> pool = candidates.get();
        if (pool == null) {
            pool = new ArrayList<>();
            candidates.set(pool);
            final ArrayList<Integer> selectedPool = pool;
            // The after-chain callback runs only after every linked explosion,
            // including subclass effects, has resolved.
            BlastBombVisuals.afterChain(() -> {
                candidates.remove();
                if (level != Dungeon.level || Dungeon.hero == null || !Dungeon.hero.isAlive()
                        || hasActiveBomb(level) || selectedPool.isEmpty()) return;

                int target = Dungeon.hero.pos;
                if (!level.insideMap(target) || level.pit[target]) return;
                int source = selectedPool.get(Random.Int(selectedPool.size()));
                BlastDeathBomb bomb = new BlastDeathBomb();
                level.drop(bomb, target).sprite.drop();
                bomb.fuse = new Fuse().ignite(bomb);
                // A ready Hero can act at Actor.now() before the newly added fuse
                // gets its first tick. Start "3..." now so that action counts.
                if (Dungeon.hero.ready) {
                    bomb.fuse.warnAt(target);
                }
                Actor.add(bomb.fuse);
                // The explosion visuals and the game actor thread advance separately.
                // Start the visible throw after the linked blast visuals, on the render thread.
                BlastBombVisuals.throwDeathBomb(bomb, source, target);
                BlastBombWarnings.refresh(level);
            });
        }
        pool.add(mob.pos);
    }

    /** Fixed radius-two ray-occluded blast area, unaffected by the Hero's boost. */
    public boolean[] blastWarningCells(int cell) {
        Level level = Dungeon.level;
        return BlastBombWarnings.reachable(level, cell,
                BArray.not(level.solid, null), RADIUS);
    }

    private static boolean hasActiveBomb(Level level) {
        for (Heap heap : level.heaps.valueList()) {
            for (Item item : heap.items) {
                if (item instanceof BlastDeathBomb) return true;
            }
        }
        return false;
    }

    @Override
    public boolean doPickUp(Hero hero, int pos) {
        GLog.w(BlastMessages.get("death_bomb.cant_pickup"));
        return false;
    }

    @Override
    public String name() {
        return BlastMessages.get("death_bomb.name");
    }

    @Override
    public String desc() {
        return BlastMessages.get("death_bomb.desc");
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(FUSE, fuse);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        fuse = ((Fuse) bundle.get(FUSE)).ignite(this);
        Actor.add(fuse);
    }

    /** Owns countdown, warning and damage even though the thrower is dead. */
    public static final class Fuse extends Actor {
        private static final String TIMER = "timer";
        private BlastDeathBomb bomb;
        private int timer = 3;
        private final ArrayList<Emitter> smokeEmitters = new ArrayList<>();

        {
            actPriority = BLOB_PRIO + 1;
        }

        private Fuse ignite(BlastDeathBomb item) {
            bomb = item;
            return this;
        }

        @Override
        protected boolean act() {
            Heap heap = null;
            for (Heap candidate : Dungeon.level.heaps.valueList()) {
                if (candidate.items.contains(bomb)) {
                    heap = candidate;
                    break;
                }
            }
            if (heap == null) {
                finish();
                return true;
            }

            if (timer > 0) {
                warnAt(heap.pos);
                spend(TICK);
                return true;
            }

            int cell = heap.pos;
            heap.remove(bomb);
            BlastBombWarnings.refresh(Dungeon.level);
            boolean[] footprint = bomb.blastWarningCells(cell);
            ArrayList<Char> affectedChars = new ArrayList<>();
            for (int affected = 0; affected < footprint.length; affected++) {
                if (!footprint[affected]) continue;
                Char ch = Actor.findChar(affected);
                if (ch != null) affectedChars.add(ch);
            }
            // Char.damage can trigger deaths and alter shared PathFinder state.
            for (Char ch : affectedChars) {
                if (!ch.isAlive()) continue;
                int damage = Random.NormalIntRange(5 + Dungeon.scalingDepth(),
                        10 + Dungeon.scalingDepth() * 2) - ch.drRoll();
                if (damage <= 0) continue;
                ch.damage(damage, Bomb.class);
                if (ch == Dungeon.hero && !ch.isAlive()) {
                    Dungeon.fail(BlastDeathBomb.class);
                    GLog.n(BlastMessages.get("death_bomb.ondeath"));
                }
            }
            Sample.INSTANCE.play(Assets.Sounds.BLAST);
            finish();
            return true;
        }

        private void warnAt(int cell) {
            if (timer <= 0) throw new IllegalStateException("Counterbomb countdown expired");
            if (smokeEmitters.isEmpty()) {
                boolean[] footprint = bomb.blastWarningCells(cell);
                for (int affected = 0; affected < footprint.length; affected++) {
                    if (!footprint[affected]) continue;
                    Emitter emitter = CellEmitter.get(affected);
                    emitter.pour(SmokeParticle.FACTORY, 0.25f);
                    smokeEmitters.add(emitter);
                }
            }
            PointF location = DungeonTilemap.raisedTileCenterToWorld(cell);
            FloatingText.show(location.x, location.y, cell, timer + "...", CharSprite.WARNING);
            timer--;
        }

        private void finish() {
            for (Emitter emitter : smokeEmitters) {
                emitter.burst(BlastParticle.FACTORY, 2);
            }
            smokeEmitters.clear();
            Actor.remove(this);
        }

        @Override
        public void storeInBundle(Bundle bundle) {
            super.storeInBundle(bundle);
            bundle.put(TIMER, timer);
        }

        @Override
        public void restoreFromBundle(Bundle bundle) {
            super.restoreFromBundle(bundle);
            timer = bundle.getInt(TIMER);
        }
    }
}
