package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Burning;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Wraith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Sheep;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.PathFinder;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/** Autonomous testing, one ordinary HeroAction per actor turn. */
public final class BlastAutoPlay {

    private static final int ATTACKER_MEMORY_TURNS = 8;
    private static final int LOST_TARGET_TURNS = 8;
    private static final int BLOCKED_TRAVEL_TURNS = 8;

    private static Hero currentHero;
    private static Level currentLevel;
    private static final BlastAutoRoaming roaming = new BlastAutoRoaming();
    private static final BlastAutoProgressGuard progress = new BlastAutoProgressGuard();
    private static final BlastAutoHarvest harvesting = new BlastAutoHarvest();
    private static Char lastAttacker;
    private static int attackerCell = -1;
    private static int attackerTurns;
    private static Mob combatTarget;
    private static int combatTargetCell = -1;
    private static int lostTargetTurns;
    private static Mob suspendedTarget;
    private static int suspendedTargetCell = -1;
    private static int suspendedTargetTurns;
    private static Mob ignoredTarget;
    private static int ignoredTargetCell = -1;
    private static int lastRetryCell = -1;
    private static volatile boolean manualAction;
    private static volatile int interruptedDestination = -1;
    private static int blockedTravelTurns;
    private static volatile Hero manualConfirmationHero;
    private static final AtomicReference<ManualInput> pendingManualInput = new AtomicReference<>();
    private static volatile boolean waitingForInput;
    private static volatile Hero pausedHero;
    // Timed throws stay on the Hero's turn; free commands can preempt mob turns.
    private static final ConcurrentLinkedQueue<BombInput> pendingBombInputs = new ConcurrentLinkedQueue<>();
    private static final Object freeBombLock = new Object();
    private static final ArrayDeque<BombInput> pendingFreeBombInputs = new ArrayDeque<>();
    private static FreeBombActor freeBombActor;

    private abstract static class ManualInput {
        final Hero hero;
        final Level level;

        ManualInput(Hero hero, Level level) {
            this.hero = hero;
            this.level = level;
        }
    }

    private static final class ManualCell extends ManualInput {
        final int destination;

        ManualCell(Hero hero, Level level, int destination) {
            super(hero, level);
            this.destination = destination;
        }
    }

    private static final class ManualRest extends ManualInput {
        final boolean fullRest;

        ManualRest(Hero hero, Level level, boolean fullRest) {
            super(hero, level);
            this.fullRest = fullRest;
        }
    }

    private enum BombAction { PLACE, THROW, DETONATE, TOGGLE }

    private static final class BombInput {
        final BombAction action;
        final int destination;

        BombInput(BombAction action, int destination) {
            this.action = action;
            this.destination = destination;
        }
    }

    /** A single high-priority actor preserves the click order of free bomb commands. */
    private static final class FreeBombActor extends Actor {
        private final Hero hero;
        private final Level level;

        private FreeBombActor(Hero hero, Level level) {
            this.hero = hero;
            this.level = level;
            actPriority = VFX_PRIO;
        }

        @Override
        protected boolean act() {
            BombInput input;
            synchronized (freeBombLock) {
                if (freeBombActor != this) {
                    Actor.remove(this);
                    return true;
                }
                if (!BlastSettings.testAutoPlay()
                        || hero != Dungeon.hero || level != Dungeon.level) {
                    clearFreeBombInputs();
                    return true;
                }
                input = pendingFreeBombInputs.pollFirst();
                if (input == null) {
                    clearFreeBombInputs();
                    return true;
                }
            }

            switch (input.action) {
                case PLACE:
                    BlastBombManiac maniac = BlastBombManiac.current();
                    if (maniac != null) maniac.place();
                    break;
                case DETONATE:
                    BlastRemoteBomb.detonateNext();
                    break;
                case TOGGLE:
                    BlastRemoteBomb.toggleMode();
                    break;
                default:
                    throw new IllegalStateException("Unexpected free bomb action: " + input.action);
            }

            synchronized (freeBombLock) {
                if (freeBombActor == this && pendingFreeBombInputs.isEmpty()) {
                    Actor.remove(this);
                    freeBombActor = null;
                }
            }
            return true;
        }
    }

    private static void clearFreeBombInputs() {
        synchronized (freeBombLock) {
            pendingFreeBombInputs.clear();
            if (freeBombActor != null) {
                Actor.remove(freeBombActor);
                freeBombActor = null;
            }
        }
    }

    private BlastAutoPlay() {}

    public static void onSettingChanged() {
        currentHero = null;
        currentLevel = null;
        roaming.reset();
        progress.reset();
        harvesting.reset();
        lastAttacker = null;
        attackerCell = -1;
        attackerTurns = 0;
        resetPursuitMemory();
        ignoredTarget = null;
        ignoredTargetCell = -1;
        lastRetryCell = -1;
        manualAction = false;
        manualConfirmationHero = null;
        clearInterruptedTravel();
        pendingManualInput.set(null);
        waitingForInput = false;
        if (!BlastSettings.testAutoPlay()) pausedHero = null;
        pendingBombInputs.clear();
        clearFreeBombInputs();
        BlastAutoPickup.reset();
        BlastAutoSacrifice.reset();

        Hero hero = Dungeon.hero;
        if (hero == null || Dungeon.level == null) return;
        if (BlastSettings.testAutoPlay()) {
            if (hero.ready) hero.next();
        } else {
            hero.curAction = null;
            hero.lastAction = null;
        }
    }

    public static void onDamage(Hero hero, Object source) {
        if (!BlastSettings.testAutoPlay() || !(source instanceof Char)) return;
        Char attacker = (Char) source;
        if (attacker == hero || attacker.alignment == Char.Alignment.ALLY
                || attacker instanceof NPC
                || (attacker instanceof Mob && avoidEvasiveNeutral(hero, (Mob) attacker))) return;
        resetForLevel(hero);
        if (ignoredTarget == attacker) {
            ignoredTarget = null;
            ignoredTargetCell = -1;
        }
        lastAttacker = attacker;
        attackerCell = attacker.pos;
        attackerTurns = ATTACKER_MEMORY_TURNS;
    }

    /** Preserve player control until a complete multi-turn HeroAction finishes. */
    public static void beginManualAction(Hero hero) {
        if (!BlastSettings.testAutoPlay()) return;
        resetForLevel(hero);
        // Only a new movement order or explicit resume supersedes retained AI travel.
        // Native Hero.handle() keeps lastAction for attacks and interactions.
        if (hero.curAction instanceof HeroAction.Move
                || hero.lastAction instanceof HeroAction.Move) clearInterruptedTravel();
        manualAction = true;
        manualConfirmationHero = null;
        resetPursuitMemory();
        roaming.reset();
        progress.reset();
        lastRetryCell = -1;
    }

    /** A newly seen enemy interrupts a manual route; let combat AI run first. */
    public static void interruptManualTravelForCombat(Hero hero) {
        if (!BlastSettings.testAutoPlay() || isPaused() || !manualActionActive(hero)
                || hero.curAction != null || !(hero.lastAction instanceof HeroAction.Move)
                || hero.visibleEnemies() == 0) return;

        int destination = ((HeroAction.Move) hero.lastAction).dst;
        if (!Dungeon.level.insideMap(destination)) {
            throw new IllegalStateException("Interrupted travel destination outside level: " + destination);
        }
        interruptedDestination = destination;
        blockedTravelTurns = 0;
        manualAction = false;
        hero.lastAction = null;
    }

    private static void clearInterruptedTravel() {
        interruptedDestination = -1;
        blockedTravelTurns = 0;
    }

    /** Reuse the auto-play safe path map, never the now-stale native move route. */
    private static boolean resumeInterruptedTravel(Hero hero, boolean[] safe, int[] distances) {
        if (interruptedDestination < 0) return false;
        if (!Dungeon.level.insideMap(interruptedDestination)) {
            throw new IllegalStateException("Retained destination outside level: " + interruptedDestination);
        }
        if (hero.pos == interruptedDestination) {
            clearInterruptedTravel();
            return false;
        }
        if (safe[interruptedDestination] && distances[interruptedDestination] != Integer.MAX_VALUE) {
            blockedTravelTurns = 0;
            hero.curAction = new HeroAction.Move(firstStep(interruptedDestination, safe, distances));
            return true;
        }
        // Allow transient enemies, doors or hazards to clear; never wait indefinitely.
        if (++blockedTravelTurns >= BLOCKED_TRAVEL_TURNS) clearInterruptedTravel();
        return false;
    }

    public static boolean manualActionActive(Hero hero) {
        return currentHero == hero && manualAction;
    }

    public static boolean finishManualAction(Hero hero) {
        if (!manualActionActive(hero) || hero.curAction != null
                || manualConfirmationHero == hero) return false;
        manualAction = false;
        return true;
    }

    /** Retain an interrupted manual action until a confirmation window finishes. */
    public static void beginManualConfirmation(Hero hero) {
        if (manualActionActive(hero)) manualConfirmationHero = hero;
    }

    /** Accepting a confirmation lets the native callback resume the saved action. */
    public static void acceptManualConfirmation(Hero hero) {
        if (manualConfirmationHero == hero) manualConfirmationHero = null;
    }

    /** Cancelling the prompt discards the saved action and wakes the hero. */
    public static void cancelManualConfirmation(Hero hero) {
        if (manualConfirmationHero != hero) return;
        manualConfirmationHero = null;
        manualAction = false;
        hero.lastAction = null;
        if (hero == Dungeon.hero && BlastSettings.testAutoPlay()) hero.next();
    }

    /**
     * Map clicks arrive on the render thread. The latest click wins and is
     * interpreted on the actor thread before another automated action.
     */
    public static void requestManualCell(int cell) {
        Hero hero = Dungeon.hero;
        Level level = Dungeon.level;
        if (!BlastSettings.testAutoPlay() || hero == null || level == null) {
            throw new IllegalStateException("Cannot request manual movement without auto-play");
        }
        if (!level.insideMap(cell)) {
            throw new IllegalArgumentException("Invalid manual destination: " + cell);
        }
        pendingManualInput.set(new ManualCell(hero, level, cell));
        if (hero.ready) hero.next();
    }

    /** Rest and map clicks share the same latest-input slot. */
    public static void requestManualRest(boolean fullRest) {
        Hero hero = Dungeon.hero;
        Level level = Dungeon.level;
        if (!BlastSettings.testAutoPlay() || hero == null || level == null) {
            throw new IllegalStateException("Cannot request manual rest without auto-play");
        }
        pendingManualInput.set(new ManualRest(hero, level, fullRest));
        if (hero.ready) hero.next();
    }

    /** Actor-thread dispatch; returns true when native rest has spent this turn. */
    public static boolean performManualInput(Hero hero) {
        if (!BlastSettings.testAutoPlay()) return false;
        ManualInput input = pendingManualInput.getAndSet(null);
        resetForLevel(hero);
        if (input == null || input.hero != hero || input.level != Dungeon.level) return false;
        progress.reset();
        // Hero.handle() alone decides when a native lastAction is superseded.
        hero.resting = false;
        if (input instanceof ManualRest) {
            hero.curAction = null;
            manualAction = false;
            manualConfirmationHero = null;
            resetPursuitMemory();
            roaming.reset();
            lastRetryCell = -1;
            hero.rest(((ManualRest) input).fullRest);
            return true;
        }
        ManualCell cell = (ManualCell) input;
        if (!hero.handle(cell.destination)) {
            throw new IllegalStateException("Queued manual destination was rejected: " + cell.destination);
        }
        return false;
    }

    /** An explicit item action supersedes the last AI movement order. */
    public static void beginItemAction(Hero hero) {
        if (!BlastSettings.testAutoPlay()) return;
        resetForLevel(hero);
        progress.reset();
        manualAction = false;
        manualConfirmationHero = null;
        pendingManualInput.set(null);
        hero.curAction = null;
        // Item use may interrupt a current action but must retain saved movement.
        hero.resting = false;
        resetPursuitMemory();
        roaming.reset();
    }

    /** A new run starts unpaused, independent of the previous hero. */
    public static void resetPause() {
        pausedHero = null;
    }

    /** Restore a saved run's pause state after its hero has been loaded. */
    public static void restorePause(Hero hero, boolean paused) {
        if (paused && hero == null) {
            throw new IllegalArgumentException("Cannot restore pause without a hero");
        }
        pausedHero = paused ? hero : null;
    }

    /** Pause is scoped to the current hero and does not change the enabled setting. */
    public static boolean isPaused() {
        return BlastSettings.testAutoPlay() && pausedHero != null && pausedHero == Dungeon.hero;
    }

    public static void togglePause() {
        if (!BlastSettings.testAutoPlay() || Dungeon.hero == null) {
            throw new IllegalStateException("Cannot toggle disabled auto play");
        }
        boolean resume = isPaused();
        Hero hero = Dungeon.hero;
        if (resume) {
            // A native interrupted manual route becomes the autonomous destination.
            if (hero.lastAction instanceof HeroAction.Move) {
                interruptedDestination = ((HeroAction.Move) hero.lastAction).dst;
                blockedTravelTurns = 0;
                hero.lastAction = null;
            }
        } else if (interruptedDestination >= 0) {
            // Pausing restores the native resume tag for a saved autonomous route.
            hero.lastAction = new HeroAction.Move(interruptedDestination);
            clearInterruptedTravel();
        }
        pausedHero = resume ? null : hero;
        if (resume && hero.ready && !GameScene.manualInputPending()) {
            hero.next();
        }
    }

    /** The Hero.act caller enters native ready() so its map listener is restored. */
    public static boolean pauseForUser(Hero hero) {
        return isPaused() && !manualActionActive(hero) && pendingBombInputs.isEmpty();
    }

    /** Manual targeting pauses auto play; the game menu no longer does. */
    public static boolean pauseForManualInput(Hero hero) {
        if (!GameScene.manualInputPending()) return false;
        waitingForInput = true;
        hero.ready = true;
        return true;
    }

    /** The render loop wakes the actor after a selection ends or is cancelled. */
    public static void resumeIfInputFinished() {
        if (!waitingForInput || GameScene.manualInputPending()) return;
        waitingForInput = false;
        Hero hero = Dungeon.hero;
        if (BlastSettings.testAutoPlay() && hero != null && hero.ready
                && (!isPaused() || !pendingBombInputs.isEmpty()
                || hero.resting || pendingManualInput.get() != null)) hero.next();
    }

    /** Queue tag commands on the actor thread; free actions need not wait for Hero.act. */
    private static void requestBombInput(BombAction action, int destination) {
        Hero hero = Dungeon.hero;
        Level level = Dungeon.level;
        if (hero == null || level == null || !BlastSettings.testAutoPlay()) return;
        resetForLevel(hero);

        // A targeting window must finish before its queued commands can run.
        if (action == BombAction.THROW || GameScene.manualInputPending()) {
            pendingBombInputs.add(new BombInput(action, destination));
        } else {
            synchronized (freeBombLock) {
                pendingFreeBombInputs.addLast(new BombInput(action, destination));
                if (freeBombActor == null) {
                    freeBombActor = new FreeBombActor(hero, level);
                    Actor.add(freeBombActor);
                }
            }
        }
        if (hero.ready && !GameScene.manualInputPending()) hero.next();
    }

    public static void requestBombPlacement() {
        requestBombInput(BombAction.PLACE, -1);
    }

    public static void requestBombThrow(int destination) {
        if (Dungeon.level == null || !Dungeon.level.insideMap(destination)) {
            throw new IllegalArgumentException("Invalid bomb destination: " + destination);
        }
        requestBombInput(BombAction.THROW, destination);
    }

    public static void requestBombDetonation() {
        requestBombInput(BombAction.DETONATE, -1);
    }

    public static void requestBombModeToggle() {
        requestBombInput(BombAction.TOGGLE, -1);
    }

    /** Returns true only for an asynchronous throw that owns the actor turn. */
    public static boolean performBombInput(Hero hero) {
        if (!BlastSettings.testAutoPlay()) return false;
        resetForLevel(hero);
        BombInput input = pendingBombInputs.poll();
        if (input == null) return false;
        manualAction = false;
        manualConfirmationHero = null;
        hero.curAction = null;
        // Bomb actions do not supersede a native interrupted travel destination.
        hero.resting = false;
        switch (input.action) {
            case PLACE:
                BlastBombManiac maniac = BlastBombManiac.current();
                if (maniac != null) maniac.place();
                return false;
            case THROW:
                BlastBombManiac thrower = BlastBombManiac.current();
                return thrower != null && thrower.blastAutoThrow(input.destination);
            case DETONATE:
                BlastRemoteBomb.detonateNext();
                return false;
            case TOGGLE:
                BlastRemoteBomb.toggleMode();
                return false;
            default:
                throw new IllegalStateException("Unknown bomb input: " + input.action);
        }
    }

    /** Retry a blocked action once at the same location, then stop idle looping. */
    public static boolean retryBlockedAction(Hero hero) {
        if (hero.pos == lastRetryCell) return false;
        lastRetryCell = hero.pos;
        return true;
    }

    /** Returns true only when a wand cast already started its own asynchronous turn. */
    public static boolean chooseAction(Hero hero) {
        resetForLevel(hero);
        progress.observe(hero.pos);
        boolean started = chooseActionOnce(hero);
        HeroAction action = hero.curAction;
        progress.record(hero.pos, action instanceof HeroAction.Move ? action.dst : -1);
        return started;
    }

    private static boolean chooseActionOnce(Hero hero) {
        hero.curAction = null;
        hero.lastAction = null;
        hero.resting = false;

        Blob[] hazards = BlastAutoEnvironment.activeHazards(Dungeon.level);
        boolean[] specialBarriers = BlastAutoEnvironment.activeSpecialBarriers(Dungeon.level);
        boolean[] safe = safeCells(hero, hazards, specialBarriers);
        progress.refreshHazards(Dungeon.level.width(), Dungeon.level.length(),
                cell -> BlastAutoEnvironment.hazardousAt(cell, hazards)
                        || specialBarriers[cell]);
        // Compute reachability once, rather than doing up to nine searches per mob.
        PathFinder.buildDistanceMap(hero.pos, safe);
        int[] distances = PathFinder.distance.clone();
        if (lastRetryCell != hero.pos) lastRetryCell = -1;
        // A suspended pursuit expires while the hero handles another threat.
        if (suspendedTarget != null && ++suspendedTargetTurns > LOST_TARGET_TURNS) {
            suspendedTarget = null;
            suspendedTargetCell = -1;
            suspendedTargetTurns = 0;
        }

        // Extinguish burning in reachable water, but never detour more than four steps.
        // Also leave an active environmental hazard before fighting or collecting loot.
        if (!hero.rooted) {
            if (hero.buff(Burning.class) != null && !hero.flying) {
                int waterCell = BlastAutoEnvironment.nearbyWater(
                        hero.pos, Dungeon.level.water, safe, distances);
                if (waterCell >= 0) {
                    hero.curAction = new HeroAction.Move(firstStep(waterCell, safe, distances));
                    return false;
                }
            }
            if (BlastAutoEnvironment.hazardousAt(hero.pos, hazards)
                    || specialBarriers[hero.pos]) {
                for (int offset : PathFinder.NEIGHBOURS8) {
                    int cell = hero.pos + offset;
                    if (Dungeon.level.insideMap(cell) && safe[cell] && distances[cell] == 1) {
                        progress.hazardousRetreat(hero.pos);
                        progress.intent(BlastAutoProgressGuard.Intent.RETREAT);
                        hero.curAction = new HeroAction.Move(cell);
                        return false;
                    }
                }
            }
        }

        // Bombard ranged enemies before sacrificial-fire luring or melee pursuit.
        // The trajectory and blast radius determine whether unseen targets can be hit.
        if (BlastAutoBombAI.actAgainstRanged(hero)) return true;

        // Keep sacrificial-fire luring ahead of bomb attacks on other enemies.
        BlastAutoSacrifice.Action sacrifice = BlastAutoSacrifice.chooseAction(hero, safe, distances);
        if (sacrifice == BlastAutoSacrifice.Action.WAIT) return true;
        if (sacrifice == BlastAutoSacrifice.Action.QUEUED) return false;

        // Blast-specific tactics are isolated from the portable AI policy.
        if (BlastAutoBombAI.actAgainstOtherEnemies(hero)) return true;

        BlastAutoCombat.Action combat = BlastAutoCombat.chooseAction(hero, safe, distances);
        if (combat != BlastAutoCombat.Action.NONE) {
            return combat == BlastAutoCombat.Action.STARTED;
        }

        Mob invulnerable = null;
        Mob hiddenNecromancer = null;
        int hiddenNecromancerGoal = -1;
        int hiddenNecromancerSteps = Integer.MAX_VALUE;
        Mob selected = null;
        int selectedGoal = -1;
        boolean selectedCanMelee = false;
        int selectedPriority = Integer.MAX_VALUE;
        int selectedSteps = Integer.MAX_VALUE;
        int selectedDistance = Integer.MAX_VALUE;

        for (Mob mob : Dungeon.level.mobs) {
            if (!mob.isAlive() || mob.alignment == Char.Alignment.ALLY
                    || isProtectedNpc(mob)
                    || mob.invisible > 0 || !isAwake(mob) || ignored(mob)
                    || hero.isCharmedBy(mob)) continue;

            boolean visible = hero.fieldOfView[mob.pos];
            if (mob.isInvulnerable(Hero.class)) {
                if (visible && (invulnerable == null
                        || Dungeon.level.distance(hero.pos, mob.pos)
                                < Dungeon.level.distance(hero.pos, invulnerable.pos))) {
                    invulnerable = mob;
                }
                continue;
            }
            if (avoidEvasiveNeutral(hero, mob)) continue;

            // Preserve the pre-existing hidden-necromancer fallback without
            // letting an unseen target override any visible threat.
            if (!visible) {
                if (mob instanceof Necromancer && !hero.rooted) {
                    int goal = nearCell(hero.pos, mob.pos, safe, distances);
                    if (goal >= 0 && distances[goal] < hiddenNecromancerSteps) {
                        hiddenNecromancer = mob;
                        hiddenNecromancerGoal = goal;
                        hiddenNecromancerSteps = distances[goal];
                    }
                }
                continue;
            }

            // Visible hostile combat belongs exclusively to BlastAutoCombat.
            // Ordinary neutral creatures retain their previous melee behavior.
            if (mob.alignment == Char.Alignment.ENEMY) continue;
            boolean ranged = BlastAutoSacrifice.isRangedEnemy(mob.getClass());
            boolean adjacent = Dungeon.level.adjacent(hero.pos, mob.pos);
            boolean canMelee = hero.canAttack(mob) && (!ranged || adjacent);
            int goal = canMelee ? -1 : nearCell(hero.pos, mob.pos, safe, distances);
            if (!canMelee && (goal < 0 || hero.rooted)) continue;

            int priority = combatPriority(mob,
                    !ranged && adjacent && mob.state == mob.HUNTING
                            && mob.isTargeting(hero));
            int steps = canMelee ? 0 : distances[goal];
            int distance = Dungeon.level.distance(hero.pos, mob.pos);
            if (betterTarget(priority, steps, distance,
                    selectedPriority, selectedSteps, selectedDistance)) {
                selected = mob;
                selectedGoal = goal;
                selectedCanMelee = canMelee;
                selectedPriority = priority;
                selectedSteps = steps;
                selectedDistance = distance;
            }
        }

        // Rooted heroes cannot chase. Hostile ranged attacks were tried above.
        if (hero.rooted) {
            if (selected != null) {
                rememberCombatTarget(selected);
                if (selectedCanMelee) {
                    hero.curAction = new HeroAction.Attack(selected);
                    return false;
                }
            }
            hero.spendAndNext(Actor.TICK);
            return true;
        }

        if (attackerTurns > 0 && lastAttacker != null
                && lastAttacker.isAlive() && lastAttacker.isInvulnerable(Hero.class)
                && !(lastAttacker instanceof NPC)
                && (!(lastAttacker instanceof Mob) || isAwake((Mob) lastAttacker))) {
            int step = retreatStep(hero, lastAttacker.pos, safe, distances);
            if (step >= 0) hero.curAction = new HeroAction.Move(step);
            return false;
        }

        // Avoid an adjacent invulnerable foe; a distant one cannot pre-empt combat.
        if (invulnerable != null && Dungeon.level.adjacent(hero.pos, invulnerable.pos)) {
            int step = retreatStep(hero, invulnerable.pos, safe, distances);
            if (step >= 0) hero.curAction = new HeroAction.Move(step);
            return false;
        }

        if (selected != null) {
            rememberCombatTarget(selected);
            if (selectedCanMelee) {
                hero.curAction = new HeroAction.Attack(selected);
            } else {
                hero.curAction = new HeroAction.Move(firstStep(selectedGoal, safe, distances));
            }
            return false;
        }

        // Keep pursuing the last observed position before switching to floor loot.
        if (pursueLastSeenTarget(hero, safe, distances)) return false;

        if (hiddenNecromancer != null) {
            hero.curAction = new HeroAction.Move(
                    firstStep(hiddenNecromancerGoal, safe, distances));
            return false;
        }

        // Suppress failed idle approaches without constraining combat or emergency escape.
        boolean[] idleSafe = progress.idleSafe(safe, hero.pos);
        int[] idleDistances = distances;
        if (idleSafe != safe) {
            PathFinder.buildDistanceMap(hero.pos, idleSafe);
            idleDistances = PathFinder.distance.clone();
        }

        // Resume interrupted player travel after tactical combat and before idle tasks.
        if (resumeInterruptedTravel(hero, idleSafe, idleDistances)) {
            progress.intent(BlastAutoProgressGuard.Intent.ROAM);
            return false;
        }

        // Collect reachable floor loot after visible enemies have been handled.
        if (BlastAutoPickup.chooseAction(hero, idleSafe, idleDistances,
                BlastAutoPickupPolicy::isInstantPickup)) {
            progress.intent(BlastAutoProgressGuard.Intent.PICKUP);
            return false;
        }

        // Use native actions to open visible containers and keyed doors.
        if (BlastAutoInteract.chooseAction(hero, idleSafe, idleDistances)) {
            progress.intent(BlastAutoProgressGuard.Intent.INTERACT);
            return false;
        }

        if (attackerTurns > 0) {
            attackerTurns--;
            if (lastAttacker != null && lastAttacker.isAlive()
                    && lastAttacker.alignment != Char.Alignment.ALLY
                    && !(lastAttacker instanceof NPC)
                    && (!(lastAttacker instanceof Mob)
                        || (isAwake((Mob) lastAttacker)
                            && !avoidEvasiveNeutral(hero, (Mob) lastAttacker)))) {
                int goal = nearCell(hero.pos, attackerCell, safe, distances);
                if (goal >= 0) {
                    hero.curAction = new HeroAction.Move(firstStep(goal, safe, distances));
                    return false;
                }
            }
        }

        // Harvesting belongs to ordinary auto-play even when auto-farming is disabled.
        if (harvest(hero, idleSafe, idleDistances)) {
            progress.intent(BlastAutoProgressGuard.Intent.HARVEST);
            return false;
        }

        // Only planting new grass depends on the separate farming setting.
        BlastAutoFarming.Action farmingAction =
                BlastAutoFarming.chooseAction(hero, idleSafe, idleDistances);
        if (farmingAction != BlastAutoFarming.Action.NONE) {
            // Farming takes priority and cancels the former wandering target.
            roaming.clearDestination();
            if (farmingAction == BlastAutoFarming.Action.MOVE) {
                progress.intent(BlastAutoProgressGuard.Intent.FARM);
            }
            return farmingAction == BlastAutoFarming.Action.BOMB;
        }

        int step = roamStep(hero, idleSafe, idleDistances);
        if (step >= 0) {
            progress.intent(BlastAutoProgressGuard.Intent.ROAM);
            hero.curAction = new HeroAction.Move(step);
        }
        return false;
    }

    /** Wraiths and targets with at least twice the hero's accuracy qualify. */
    static boolean highEvasion(boolean wraith, int defenseSkill, int attackSkill) {
        return wraith || defenseSkill >= 2L * attackSkill;
    }

    static boolean highEvasion(Hero hero, Mob mob) {
        return highEvasion(mob instanceof Wraith, mob.defenseSkill(hero),
                Math.max(1, Math.round(BlastAutoCombat.estimatedAccuracy(hero, mob,
                        hero.belongings.attackingWeapon(), false))));
    }

    static boolean avoidEvasiveNeutral(Char.Alignment alignment, boolean evasive) {
        return alignment == Char.Alignment.NEUTRAL && evasive;
    }

    private static boolean avoidEvasiveNeutral(Hero hero, Mob mob) {
        return avoidEvasiveNeutral(mob.alignment, highEvasion(hero, mob));
    }

    private static void resetForLevel(Hero hero) {
        if (currentHero == hero && currentLevel == Dungeon.level) return;
        currentHero = hero;
        currentLevel = Dungeon.level;
        roaming.reset();
        progress.reset();
        harvesting.reset();
        lastAttacker = null;
        attackerCell = -1;
        attackerTurns = 0;
        resetPursuitMemory();
        ignoredTarget = null;
        ignoredTargetCell = -1;
        lastRetryCell = -1;
        manualAction = false;
        manualConfirmationHero = null;
        clearInterruptedTravel();
        pendingManualInput.set(null);
        waitingForInput = false;
        pendingBombInputs.clear();
        clearFreeBombInputs();
        BlastAutoPickup.reset();
        BlastAutoSacrifice.reset();
    }

    /** Priority tiers are independent of distance; route length breaks ties. */
    static int combatPriority(Mob mob, boolean immediateMeleeThreat) {
        if (immediateMeleeThreat) return 0;
        return mob instanceof Thief || BlastAutoSacrifice.isRangedEnemy(mob.getClass())
                ? 1 : 2;
    }

    static boolean betterTarget(int priority, int steps, int distance,
                                int otherPriority, int otherSteps, int otherDistance) {
        return priority < otherPriority
                || (priority == otherPriority && (steps < otherSteps
                || (steps == otherSteps && distance < otherDistance)));
    }

    /** Quest and service NPCs are never auto-combat targets, regardless of alignment. */
    static boolean isProtectedNpc(Mob mob) {
        return mob instanceof NPC;
    }

    /** Sleeping enemies are deliberately left alone by automatic combat. */
    static boolean isAwake(Mob mob) {
        return mob.state != mob.SLEEPING;
    }

    static boolean ignored(Mob mob) {
        if (mob != ignoredTarget) return false;
        // A stationary target becomes urgent again when it actively threatens
        // the hero at melee range, even if an earlier chase was abandoned.
        if (mob.pos == ignoredTargetCell) {
            Hero hero = Dungeon.hero;
            if (hero == null || mob.state != mob.HUNTING || !mob.isTargeting(hero)
                    || !Dungeon.level.adjacent(hero.pos, mob.pos)) return true;
        }
        ignoredTarget = null;
        ignoredTargetCell = -1;
        return false;
    }

    static void rememberCombatTarget(Mob mob) {
        rememberCombatTarget(mob, mob.pos);
    }

    static void rememberCombatTarget(Mob mob, int observedCell) {
        if (observedCell < 0 || !Dungeon.level.insideMap(observedCell)) {
            throw new IllegalArgumentException("Invalid last-seen combat cell: " + observedCell);
        }
        // Keep one hidden target in reserve when combat is interrupted.
        if (combatTarget != null && combatTarget != mob && suspendedTarget == null
                && combatTarget.isAlive() && Dungeon.hero != null
                && !Dungeon.hero.fieldOfView[combatTarget.pos]) {
            suspendedTarget = combatTarget;
            suspendedTargetCell = combatTargetCell;
            suspendedTargetTurns = lostTargetTurns;
        }
        combatTarget = mob;
        combatTargetCell = observedCell;
        lostTargetTurns = 0;
    }

    private static void forgetCombatTarget() {
        combatTarget = null;
        combatTargetCell = -1;
        lostTargetTurns = 0;
    }

    private static void resetPursuitMemory() {
        forgetCombatTarget();
        suspendedTarget = null;
        suspendedTargetCell = -1;
        suspendedTargetTurns = 0;
    }

    private static void abandonCombatTarget() {
        ignoredTarget = combatTarget;
        ignoredTargetCell = combatTargetCell;
        forgetCombatTarget();
    }

    /** Short, bounded pursuit of the last *visible* position, with no hidden-position lookup. */
    private static boolean pursueLastSeenTarget(Hero hero, boolean[] safe, int[] distances) {
        if (combatTarget == null && suspendedTarget != null) {
            combatTarget = suspendedTarget;
            combatTargetCell = suspendedTargetCell;
            lostTargetTurns = suspendedTargetTurns;
            suspendedTarget = null;
            suspendedTargetCell = -1;
            suspendedTargetTurns = 0;
        }
        if (combatTarget == null) return false;
        if (!combatTarget.isAlive() || combatTarget.alignment == Char.Alignment.ALLY
                || isProtectedNpc(combatTarget)
                || combatTarget.invisible > 0 || !isAwake(combatTarget)
                || hero.isCharmedBy(combatTarget)
                || combatTarget.isInvulnerable(Hero.class)
                || avoidEvasiveNeutral(hero, combatTarget)) {
            forgetCombatTarget();
            return false;
        }
        if (hero.fieldOfView[combatTarget.pos]) {
            // A visible but unapproachable enemy is handled by normal targeting.
            forgetCombatTarget();
            return false;
        }

        int goal = nearCell(hero.pos, combatTargetCell, safe, distances);
        lostTargetTurns++;
        if (!canPursue(lostTargetTurns,
                Dungeon.level.distance(hero.pos, combatTargetCell), goal >= 0)) {
            // Avoid a repeated enemy/loot oscillation until this enemy moves or hits us.
            abandonCombatTarget();
            return false;
        }

        hero.curAction = new HeroAction.Move(firstStep(goal, safe, distances));
        return true;
    }

    static boolean canPursue(int unseenTurns, int distance, boolean reachable) {
        return reachable && distance > 1 && unseenTurns <= LOST_TARGET_TURNS;
    }

    static boolean isTrapSafe(Trap trap) {
        return trap == null || !trap.active;
    }

    private static boolean[] safeCells(Hero hero, Blob[] hazards, boolean[] specialBarriers) {
        Level level = Dungeon.level;
        boolean[] safe = Dungeon.findPassable(hero, level.passable, hero.fieldOfView, true).clone();
        boolean phaseEnabled = BlastMovement.enabled();

        for (int cell = 0; cell < safe.length; cell++) {
            boolean known = level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell];
            boolean traversable = safe[cell]
                    || (phaseEnabled && known && BlastMovement.canPhase(cell));
            safe[cell] = known && level.insideMap(cell) && traversable
                    && !level.pit[cell]
                    && isTrapSafe(level.traps.get(cell))
                    && !AutoPlayPlantSafety.isHarmful(level.plants.get(cell))
                    && !BlastAutoEnvironment.hazardousAt(cell, hazards)
                    && !specialBarriers[cell];
        }

        // Inspect occupied cells only; per-cell Actor.findChar() is unnecessary.
        for (Char character : Actor.chars()) {
            if (character == hero) continue;
            int cell = character.pos;
            if (phaseEnabled && character instanceof Sheep && level.passable[cell]
                    && level.insideMap(cell)
                    && (level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell])
                    && !level.pit[cell] && isTrapSafe(level.traps.get(cell))
                    && !AutoPlayPlantSafety.isHarmful(level.plants.get(cell))
                    && !BlastAutoEnvironment.hazardousAt(cell, hazards)
                    && !specialBarriers[cell]) {
                safe[cell] = true;
            } else {
                safe[cell] = false;
            }
        }
        safe[hero.pos] = true;
        return safe;
    }

    /** Find a shortest reachable destination at or beside an occupied target. */
    static int nearCell(int origin, int target, boolean[] safe, int[] distances) {
        int goal = -1;
        int shortest = Integer.MAX_VALUE;
        if (target != origin && safe[target] && distances[target] < shortest) {
            goal = target;
            shortest = distances[target];
        }
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = target + offset;
            if (!Dungeon.level.insideMap(cell) || cell == origin || !safe[cell]) continue;
            if (distances[cell] < shortest) {
                goal = cell;
                shortest = distances[cell];
            }
        }
        return goal;
    }

    /** Follow the existing distance map backwards instead of running another search. */
    static int firstStep(int goal, boolean[] safe, int[] distances) {
        int cell = goal;
        if (distances[cell] == Integer.MAX_VALUE || distances[cell] == 0) {
            throw new IllegalArgumentException("Unreachable or current auto-play destination: " + goal);
        }
        while (distances[cell] > 1) {
            int previous = -1;
            for (int offset : PathFinder.NEIGHBOURS8) {
                int adjacent = cell + offset;
                if (Dungeon.level.insideMap(adjacent) && safe[adjacent]
                        && distances[adjacent] == distances[cell] - 1) {
                    previous = adjacent;
                    break;
                }
            }
            if (previous < 0) {
                throw new IllegalStateException("Inconsistent auto-play distance map at " + cell);
            }
            cell = previous;
        }
        return cell;
    }

    /** Retreat without entering traps, chasms or non-grass plants. */
    static int retreatStep(Hero hero, int threat, boolean[] safe, int[] distances) {
        int distance = Dungeon.level.distance(hero.pos, threat);
        int step = -1;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (!Dungeon.level.insideMap(cell) || !safe[cell]) continue;
            int nextDistance = Dungeon.level.distance(cell, threat);
            if (nextDistance > distance) {
                distance = nextDistance;
                step = cell;
            }
        }
        if (step >= 0) return step;

        // If the first escape step must be sideways, use the same distance map.
        int goal = -1;
        int farthest = Dungeon.level.distance(hero.pos, threat);
        for (int cell = 0; cell < safe.length; cell++) {
            if (!safe[cell] || distances[cell] == Integer.MAX_VALUE) continue;
            int nextDistance = Dungeon.level.distance(cell, threat);
            if (nextDistance > farthest) {
                farthest = nextDistance;
                goal = cell;
            }
        }
        return goal < 0 ? -1 : firstStep(goal, safe, distances);
    }

    /** Target safe high grass and beneficial plants before farming or roaming. */
    private static boolean harvest(Hero hero, boolean[] safe, int[] distances) {
        if (hero.flying || hero.rooted || hasVisibleEnemy(hero)) return false;

        Level level = Dungeon.level;
        int crop = harvesting.chooseDestination(hero.pos, safe, distances,
                hero.fieldOfView, hero.canSelfTrample(),
                cell -> BlastAutoHarvest.harvestable(level.map[cell], level.plants.get(cell)));
        if (crop < 0) return false;

        int next = crop == hero.pos ? hero.pos : firstStep(crop, safe, distances);
        hero.curAction = new HeroAction.Move(next);
        roaming.clearDestination();
        return true;
    }

    static boolean hasVisibleEnemy(Hero hero) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                    && mob.invisible <= 0 && hero.fieldOfView[mob.pos]
                    && isAwake(mob)) return true;
        }
        return false;
    }

    private static int roamStep(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        int goal = roaming.chooseDestination(hero.pos, level.width(), safe, distances,
                level.visited, level.mapped, hero.fieldOfView, level.solid);
        return goal < 0 ? -1 : firstStep(goal, safe, distances);
    }
}
