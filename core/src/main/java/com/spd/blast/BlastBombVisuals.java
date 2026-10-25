package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.BlastParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.SmokeParticle;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.Group;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.tweeners.PosTweener;
import com.watabou.utils.PointF;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.PriorityQueue;
import java.util.WeakHashMap;

/** Render-only timing for explosions; all gameplay effects remain synchronous. */
public final class BlastBombVisuals extends Group {
    private static final long INTERVAL_NANOS = 90_000_000L;
    private static volatile BlastBombVisuals active;
    private static final ThreadLocal<Chain> currentChain = new ThreadLocal<>();

    private static final class Event {
        final long time;
        final long order;
        final Runnable effect;

        Event(long time, long order, Runnable effect) {
            this.time = time;
            this.order = order;
            this.effect = effect;
        }
    }

    private static final class Explosion {
        final Bomb bomb;
        final int cell;
        final Runnable blastEffect;
        final ArrayList<Runnable> extraEffects = new ArrayList<>();

        Explosion(Bomb bomb, int cell, Runnable blastEffect) {
            this.bomb = bomb;
            this.cell = cell;
            this.blastEffect = blastEffect;
        }
    }

    private static final class Chain {
        final BlastBombVisuals stage;
        final int origin;
        final ArrayList<Explosion> explosions = new ArrayList<>();
        final IdentityHashMap<Bomb, Explosion> byBomb = new IdentityHashMap<>();
        final ArrayList<Runnable> afterChain = new ArrayList<>();
        int depth = 1;

        Chain(BlastBombVisuals stage, int origin) {
            this.stage = stage;
            this.origin = origin;
        }
    }

    private final Level level;
    private final PriorityQueue<Event> events = new PriorityQueue<>(
            Comparator.<Event>comparingLong(e -> e.time).thenComparingLong(e -> e.order));
    private final WeakHashMap<Bomb, Long> explosionTimes = new WeakHashMap<>();
    private long nextOrder;
    private long lastExplosion;

    public BlastBombVisuals() {
        level = Dungeon.level;
        active = this;
    }

    /** Track nested explosions as one chain, without changing when they deal damage. */
    public static void begin(int cell) {
        BlastBombVisuals stage = active;
        if (stage == null || stage.level != Dungeon.level) return;

        Chain chain = currentChain.get();
        if (chain == null) {
            currentChain.set(new Chain(stage, cell));
        } else {
            if (chain.stage != stage) throw new IllegalStateException("Explosion crossed game scenes");
            chain.depth++;
        }
    }

    /** Publish the complete chain at once so recursive map traversal cannot dictate visual order. */
    public static void end() {
        Chain chain = currentChain.get();
        if (chain == null) return;
        if (--chain.depth > 0) return;
        currentChain.remove();

        BlastBombVisuals stage = chain.stage;
        if (active != stage || Dungeon.level != stage.level || chain.explosions.isEmpty()) return;

        int width = stage.level.width();
        chain.explosions.sort(Comparator.comparingLong(explosion ->
                distanceSquared(chain.origin, explosion.cell, width)));

        synchronized (stage) {
            long now = System.nanoTime();
            long time = Math.max(now, stage.lastExplosion + INTERVAL_NANOS);
            long previousDistance = -1;

            for (Explosion explosion : chain.explosions) {
                long distance = distanceSquared(chain.origin, explosion.cell, width);
                // Bombs on the same radius appear together, like an outward-moving shockwave.
                if (previousDistance >= 0 && distance != previousDistance) {
                    time += INTERVAL_NANOS;
                }
                previousDistance = distance;

                stage.explosionTimes.put(explosion.bomb, time);
                stage.events.add(new Event(time, stage.nextOrder++, explosion.blastEffect));
                for (Runnable extra : explosion.extraEffects) {
                    stage.events.add(new Event(time, stage.nextOrder++, extra));
                }
            }
            stage.lastExplosion = time;
        }
        // Gameplay terrain changes resolve synchronously, after every linked blast.
        for (Runnable change : chain.afterChain) change.run();
    }

    /** Queue gameplay effects until the whole chain, including subtype effects, is finished. */
    public static void afterChain(Runnable action) {
        Chain chain = currentChain.get();
        if (chain != null) {
            chain.afterChain.add(action);
        } else if (active == null || active.level != Dungeon.level) {
            action.run();
        } else {
            throw new IllegalStateException("Collapse without explosion chain");
        }
    }

    /**
     * Queue a visible projectile after the entire chain's explosion effects.
     * The gameplay bomb already exists: only the flying sprite is delayed.
     */
    public static void throwDeathBomb(BlastDeathBomb bomb, int from, int to) {
        BlastBombVisuals stage = active;
        if (stage == null || stage.level != Dungeon.level) return;
        synchronized (stage) {
            long start = Math.max(System.nanoTime(), stage.lastExplosion + INTERVAL_NANOS);
            stage.events.add(new Event(start, stage.nextOrder++,
                    () -> stage.showDeathBombThrow(bomb, from, to)));
        }
    }

    /** Runs on the render thread, above characters and fog, at a readable speed. */
    private void showDeathBombThrow(BlastDeathBomb bomb, int from, int to) {
        if (active != this || Dungeon.level != level) return;

        ItemSprite projectile = new ItemSprite(bomb);
        PointF origin = DungeonTilemap.raisedTileCenterToWorld(from);
        PointF destination = DungeonTilemap.raisedTileCenterToWorld(to);
        projectile.point(new PointF(origin.x - projectile.width() / 2f,
                origin.y - projectile.height() / 2f));
        add(projectile);

        PointF finish = new PointF(destination.x - projectile.width() / 2f,
                destination.y - projectile.height() / 2f);
        // Native MissileSprite uses 240 px/s, almost invisible at melee range.
        // Keep short throws readable without delaying combat.
        float pixels = PointF.distance(origin, destination);
        PosTweener flight = new PosTweener(projectile, finish,
                Math.max(0.35f, pixels / 190f));
        flight.listener = tween -> projectile.killAndErase();
        add(flight);
    }

    private static long distanceSquared(int from, int to, int width) {
        long dx = from % width - to % width;
        long dy = from / width - to / width;
        return dx * dx + dy * dy;
    }

    /** Called once per Bomb.explode, before it recursively triggers nearby bombs. */
    public static void blast(Bomb bomb, int cell, ArrayList<Integer> affectedCells) {
        Level level = Dungeon.level;
        boolean visibleCenter = affectedCells != null && level.heroFOV[cell];
        ArrayList<Integer> visibleSmoke = new ArrayList<>();
        if (affectedCells != null) {
            for (int affected : affectedCells) {
                if (level.heroFOV[affected]) visibleSmoke.add(affected);
            }
        }

        Runnable effect = () -> {
            Sample.INSTANCE.play(Assets.Sounds.BLAST);
            if (visibleCenter) {
                CellEmitter.center(cell).burst(BlastParticle.FACTORY, 30);
            }
            for (int affected : visibleSmoke) {
                CellEmitter.get(affected).burst(SmokeParticle.FACTORY, 4);
            }
        };

        BlastBombVisuals stage = active;
        if (stage == null || stage.level != level) {
            effect.run();
            return;
        }

        Chain chain = currentChain.get();
        if (chain == null || chain.stage != stage) {
            throw new IllegalStateException("Explosion visual missing chain context");
        }
        Explosion explosion = new Explosion(bomb, cell, effect);
        if (chain.byBomb.put(bomb, explosion) != null) {
            throw new IllegalStateException("Bomb exploded twice in the same chain");
        }
        chain.explosions.add(explosion);
    }

    /** Subclass particles and sounds run at the exact timestamp of their own blast. */
    public static void effect(Bomb bomb, Runnable visual) {
        BlastBombVisuals stage = active;
        if (stage == null || stage.level != Dungeon.level) {
            visual.run();
            return;
        }

        Chain chain = currentChain.get();
        if (chain != null && chain.stage == stage) {
            Explosion explosion = chain.byBomb.get(bomb);
            if (explosion == null) throw new IllegalStateException("Bomb effect without blast");
            explosion.extraEffects.add(visual);
            return;
        }

        synchronized (stage) {
            Long time = stage.explosionTimes.get(bomb);
            if (time == null) throw new IllegalStateException("Bomb effect without blast");
            stage.events.add(new Event(time, stage.nextOrder++, visual));
        }
    }

    public static void reset() {
        active = null;
    }

    @Override
    public void update() {
        ArrayList<Runnable> due = new ArrayList<>();
        synchronized (this) {
            long now = System.nanoTime();
            while (!events.isEmpty() && events.peek().time <= now) {
                due.add(events.remove().effect);
            }
        }
        if (active == this && Dungeon.level == level) {
            for (Runnable effect : due) effect.run();
        }
        super.update();
    }
}
