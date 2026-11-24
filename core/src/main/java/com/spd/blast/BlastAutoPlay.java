package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.SacrificialFire;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Burning;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.DemonSpawner;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Necromancer;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Thief;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Wraith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.NPC;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Sheep;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
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
    private static String debugDecision;
    private static String nearbyDebugLoot;
    private static String lastLootTrace;
    private static int repeatedLootTrace;
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
        lastLootTrace = null;
        repeatedLootTrace = 0;
        manualAction = false;
        manualConfirmationHero = null;
        clearInterruptedTravel();
        pendingManualInput.set(null);
        waitingForInput = false;
        if (!BlastSettings.testAutoPlay()) pausedHero = null;
        pendingBombInputs.clear();
        clearFreeBombInputs();
        BlastAutoPickup.reset();
        BlastAutoEquipment.reset();
        BlastAutoTrapClearer.reset();
        BlastAutoSpecialRooms.reset();
        BlastAutoSacrifice.reset();
        BlastAutoFood.stopFeeding();

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
        BlastAutoFood.stopFeeding();
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
        // A periodic flame corridor needs a turn to change phase. Keep the clicked
        // destination while its next step is temporarily unsafe.
        if (BlastAutoVault.waitForFlames(hero, Dungeon.level, safe)) {
            hero.spendAndNext(Actor.TICK);
            return true;
        }
        // Allow transient enemies, doors or hazards to clear; never wait indefinitely.
        if (++blockedTravelTurns >= BLOCKED_TRAVEL_TURNS) clearInterruptedTravel();
        return false;
    }

    public static boolean manualActionActive(Hero hero) {
        return currentHero == hero && manualAction;
    }

    /** Keep native suspended manual travel when control returns to auto-play. */
    private static void retainNativeManualTravel(Hero hero) {
        if (!manualActionActive(hero) || isPaused()
                || !(hero.lastAction instanceof HeroAction.Move)) return;
        int destination = ((HeroAction.Move) hero.lastAction).dst;
        if (!Dungeon.level.insideMap(destination)) {
            throw new IllegalStateException("Suspended manual destination outside level: " + destination);
        }
        interruptedDestination = destination;
        blockedTravelTurns = 0;
        hero.lastAction = null;
    }

    public static boolean finishManualAction(Hero hero) {
        if (!manualActionActive(hero) || hero.curAction != null
                || manualConfirmationHero == hero) return false;
        retainNativeManualTravel(hero);
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
        // Map taps and directional controls may select the unplayable border.
        // Ignore those inputs before they reach the actor thread.
        if (!level.insideMap(cell)) return;
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
            retainNativeManualTravel(hero);
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
        // Route map-click travel through the same one-step hazard checks as auto-play.
        // Native Hero.getCloser() otherwise caches an unsafe path across flame cycles.
        if (Dungeon.level instanceof com.shatteredpixel.shatteredpixeldungeon.levels.VaultLevel
                && cell.destination != hero.pos
                && com.shatteredpixel.shatteredpixeldungeon.actors.Actor.findChar(cell.destination) == null
                && Dungeon.level.passable[cell.destination]) {
            interruptedDestination = cell.destination;
            blockedTravelTurns = 0;
            manualAction = false;
            manualConfirmationHero = null;
            hero.curAction = null;
            hero.lastAction = null;
            return false;
        }
        if (!hero.handle(cell.destination)) {
            throw new IllegalStateException("Queued manual destination was rejected: " + cell.destination);
        }
        return false;
    }

    /** An explicit item action supersedes the last AI movement order. */
    public static void beginItemAction(Hero hero) {
        if (!BlastSettings.testAutoPlay() || BlastAutoFood.isEatingAutomatically()
                || BlastAutoEquipment.isUsingAutomatically()) return;
        resetForLevel(hero);
        BlastAutoFood.stopFeeding();
        retainNativeManualTravel(hero);
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
            // Transfer the manual route before autonomous turns may start again.
            if (hero.lastAction instanceof HeroAction.Move) {
                interruptedDestination = ((HeroAction.Move) hero.lastAction).dst;
                blockedTravelTurns = 0;
                hero.lastAction = null;
            }
            pausedHero = null;
        } else {
            // Stop autonomous turns before exposing their destination to the UI.
            BlastAutoFood.stopFeeding();
            pausedHero = hero;
            if (interruptedDestination >= 0) {
                hero.lastAction = new HeroAction.Move(interruptedDestination);
                clearInterruptedTravel();
            }
        }
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
        logNearbyLoot(hero, action, started);
        return started;
    }

    private static boolean chooseActionOnce(Hero hero) {
        debugDecision = "hazard";
        nearbyDebugLoot = null;
        hero.curAction = null;
        hero.lastAction = null;
        hero.resting = false;

        Blob[] hazards = BlastAutoEnvironment.activeHazards(Dungeon.level);
        boolean[] specialBarriers = BlastAutoEnvironment.activeSpecialBarriers(Dungeon.level);
        boolean[] safe = safeCells(hero, hazards, specialBarriers);
        progress.refreshHazards(Dungeon.level.length(),
                cell -> BlastAutoEnvironment.hazardousAt(cell, hazards)
                        || specialBarriers[cell]);
        int toxicRetreat = BlastAutoTrapClearer.gasRetreatStep(hero, safe);

        // Persistent test invulnerability allows attacking before leaving gas.
        // Only attack from the current tile; failed attacks retain normal retreat.
        // Temporary bomb protection is insufficient for this exception.
        if (hero.buff(BlastTestBuffs.Indestructible.class) != null
                && hero.buff(Burning.class) == null
                && !specialBarriers[hero.pos]
                && (!BlastAutoEnvironment.hazardousAt(hero.pos, hazards)
                        || BlastAutoEnvironment.gasOnlyAt(hero.pos, hazards))
                && (toxicRetreat >= 0 || BlastAutoEnvironment.gasOnlyAt(hero.pos, hazards))) {
            debugDecision = "gas-ranged-bomb";
            if (BlastAutoBombAI.actAgainstRanged(hero)) return true;

            // Do not bypass the sacrificial-fire requirement for melee targets.
            Blob fire = Dungeon.level.blobs.get(SacrificialFire.class);
            boolean sacrificeActive = fire != null && fire.volume > 0
                    && BlastAutoSacrifice.knownFireCell(fire.cur, Dungeon.level.visited,
                            Dungeon.level.mapped, hero.fieldOfView) >= 0;
            if (sacrificeActive) {
                debugDecision = "gas-sacrifice";
                if (BlastAutoBombAI.actAgainstSacrifice(hero, fire.cur)) return true;
            } else {
                debugDecision = "gas-bomb";
                if (BlastAutoBombAI.actAgainstOtherEnemies(hero)) return true;
                debugDecision = "gas-combat";
                BlastAutoCombat.Action attack = BlastAutoCombat.attackIfPossible(hero);
                if (attack != BlastAutoCombat.Action.NONE) {
                    return attack == BlastAutoCombat.Action.STARTED;
                }
            }
        }
        if (toxicRetreat >= 0) {
            debugDecision = "trap-retreat";
            progress.intent(BlastAutoProgressGuard.Intent.RETREAT);
            hero.curAction = new HeroAction.Move(toxicRetreat);
            return false;
        }
        BlastAutoTrapClearer.avoidGasReturn(hero, safe);
        // Compute reachability once, rather than doing up to nine searches per mob.
        PathFinder.buildDistanceMap(hero.pos, safe);
        int[] distances = PathFinder.distance.clone();
        if (BlastSettings.debugLog()) {
            nearbyDebugLoot = BlastAutoPickup.nearbyDiagnostic(hero, safe, distances,
                    progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.PICKUP),
                    BlastAutoPickupPolicy::isInstantPickup);
        }
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
                // With no clean adjacent tile, walk through gas to the nearest
                // reachable clean tile. Other hazards and terrain rules stay blocked.
                if (BlastAutoEnvironment.gasOnlyAt(hero.pos, hazards)) {
                    boolean[] gasEscape = safeCells(hero, hazards, specialBarriers, true);
                    PathFinder.buildDistanceMap(hero.pos, gasEscape);
                    int exit = BlastAutoEnvironment.nearestSafeExit(
                            hero.pos, safe, PathFinder.distance);
                    if (exit >= 0) {
                        int step = firstStep(exit, gasEscape, PathFinder.distance);
                        progress.hazardousRetreat(hero.pos);
                        progress.intent(BlastAutoProgressGuard.Intent.RETREAT);
                        hero.curAction = new HeroAction.Move(step);
                        return false;
                    }
                }
            }
        }

        // Cleanse the rare red wraith before any bomb or weapon attack.
        debugDecision = "grave-spirit";
        BlastAutoSpecialRooms.Action spirit = BlastAutoGraves.cleanse(hero, safe, distances);
        if (spirit != BlastAutoSpecialRooms.Action.NONE)
            return spirit == BlastAutoSpecialRooms.Action.USE;

        // Finish a sprint before unrelated actions consume the haste window.
        debugDecision = "sentry-run";
        BlastAutoSpecialRooms.Action sentryRun = BlastAutoSentryRoom.continueRun(
                hero, safe, distances, hazards, specialBarriers);
        if (sentryRun != BlastAutoSpecialRooms.Action.NONE)
            return sentryRun == BlastAutoSpecialRooms.Action.USE;

        // Wait for delayed summons, then let existing indestructible combat handle them.
        debugDecision = "summoning-wait";
        if (BlastAutoSummoningRoom.awaitWave(hero)) return true;

        // Bombard ranged enemies before sacrificial-fire luring or melee pursuit.
        // The trajectory and blast radius determine whether unseen targets can be hit.
        debugDecision = "ranged-bomb";
        if (BlastAutoBombAI.actAgainstRanged(hero)) return true;

        // Keep sacrificial-fire luring ahead of bomb attacks on other enemies.
        debugDecision = "sacrifice";
        BlastAutoSacrifice.Action sacrifice = BlastAutoSacrifice.chooseAction(hero, safe, distances);
        if (sacrifice == BlastAutoSacrifice.Action.WAIT) return true;
        if (sacrifice == BlastAutoSacrifice.Action.QUEUED) return false;

        // Blast-specific tactics are isolated from the portable AI policy.
        debugDecision = "bomb";
        if (BlastAutoBombAI.actAgainstOtherEnemies(hero)) return true;

        // A safe, immediately throwable ranged trap can hit a hostile target
        // before ordinary combat consumes the opportunity. Never detour for it.
        debugDecision = "aimed-trap";
        if (BlastAutoTrapClearer.chooseAimedAction(hero, safe, distances)
                == BlastAutoTrapClearer.Action.THROW) return true;

        debugDecision = "combat";
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
                    || isAutoAttackExcluded(mob)
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
        debugDecision = "rooted";
        if (hero.rooted) {
            if (selected != null) {
                rememberCombatTarget(selected);
                if (selectedCanMelee && BlastAutoCombat.meleeCollateralSafe(hero, selected)) {
                    hero.curAction = new HeroAction.Attack(selected);
                    return false;
                }
            }
            // Normal demolition is an idle task; rooted heroes cannot reach it.
            if (BlastAutoBarricade.clearAdjacentWeb(hero)
                    == BlastAutoBarricade.Action.DETONATE) {
                debugDecision = "web";
                return true;
            }
            hero.spendAndNext(Actor.TICK);
            return true;
        }

        debugDecision = "retreat";
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

        debugDecision = "neutral";
        if (selected != null) {
            rememberCombatTarget(selected);
            if (selectedCanMelee && BlastAutoCombat.meleeCollateralSafe(hero, selected)) {
                hero.curAction = new HeroAction.Attack(selected);
            } else {
                hero.curAction = new HeroAction.Move(firstStep(selectedGoal, safe, distances));
            }
            return false;
        }

        // Native intentional search reveals a visible adjacent secret door in one turn.
        debugDecision = "secret-search";
        if (searchNearbySecretDoor(hero)) return true;

        // Keep pursuing the last observed position before switching to floor loot.
        debugDecision = "pursuit";
        if (pursueLastSeenTarget(hero, safe, distances)) return false;

        debugDecision = "necromancer";
        if (hiddenNecromancer != null) {
            hero.curAction = new HeroAction.Move(
                    firstStep(hiddenNecromancerGoal, safe, distances));
            return false;
        }

        // Each idle task sees only its own failed steps; hazardous retreats affect all.
        boolean[] idleSafe = progress.safeFor(safe, hero.pos,
                BlastAutoProgressGuard.Intent.TRAVEL);
        int[] idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);

        // Resume interrupted player travel after tactical combat and before idle tasks.
        debugDecision = "manual-route";
        if (resumeInterruptedTravel(hero, idleSafe, idleDistances)) {
            progress.intent(BlastAutoProgressGuard.Intent.TRAVEL);
            return false;
        }

        // Finish a levitating crossing before ordinary idle tasks consume its duration.
        debugDecision = "special-flight";
        BlastAutoSpecialRooms.Action flightAction =
                BlastAutoSpecialRooms.continueFlight(
                        hero, safe, distances, hazards, specialBarriers);
        if (flightAction != BlastAutoSpecialRooms.Action.NONE) {
            roaming.clearDestination();
            return flightAction == BlastAutoSpecialRooms.Action.USE;
        }

        // Synchronized vault flames require blob immunity before entering.
        debugDecision = "vault-purity";
        if (BlastAutoVault.drinkPurity(hero, Dungeon.level)) return true;

        // Strength potions, equipment upgrades, and ordinary gear cleanup run only while idle.
        // Cursed gear is sold immediately from the native pickup-success callback.
        debugDecision = "equipment";
        if (BlastAutoEquipment.manage(hero)) return true;

        // Feed only during idle auto-play, after combat and interrupted manual travel.
        // Food.execute owns the animated eating turn and its native time cost.
        debugDecision = "food";
        if (BlastAutoFood.eatIfNeeded(hero)) return true;

        // Use a known pot or a ready equipped toolkit; never navigate to an alchemy site.
        debugDecision = "alchemy";
        if (BlastAutoAlchemy.brewIfPossible(hero, safe[hero.pos])) return true;

        // Finish quenching toxic vents before unrelated loot or crystal-door
        // interactions can draw the hero away from the gas room.
        debugDecision = "toxic-vents";
        BlastAutoSpecialRooms.Action toxicVents = BlastAutoToxicRoom.chooseAction(
                hero, safe, distances, hazards, specialBarriers);
        if (toxicVents != BlastAutoSpecialRooms.Action.NONE) {
            roaming.clearDestination();
            progress.intent(BlastAutoProgressGuard.Intent.INTERACT);
            return toxicVents == BlastAutoSpecialRooms.Action.USE;
        }

        // Collect reachable floor loot after visible enemies have been handled.
        idleSafe = progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.PICKUP);
        idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);
        debugDecision = "pickup";
        if (BlastAutoPickup.chooseAction(hero, idleSafe, idleDistances,
                BlastAutoPickupPolicy::isInstantPickup)) {
            progress.intent(BlastAutoProgressGuard.Intent.PICKUP);
            return false;
        }

        // Use native actions to open visible containers and keyed doors.
        idleSafe = progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.INTERACT);
        idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);
        debugDecision = "interact";
        if (BlastAutoInteract.chooseAction(hero, idleSafe, idleDistances)) {
            progress.intent(BlastAutoProgressGuard.Intent.INTERACT);
            return hero.curAction == null;
        }

        // Open prepared tombs; spawned wraiths go to the existing combat AI.
        debugDecision = "grave";
        BlastAutoSpecialRooms.Action grave =
                BlastAutoGraves.openTomb(hero, idleSafe, idleDistances);
        if (grave != BlastAutoSpecialRooms.Action.NONE) {
            roaming.clearDestination();
            progress.intent(BlastAutoProgressGuard.Intent.INTERACT);
            return grave == BlastAutoSpecialRooms.Action.USE;
        }

        // Previously seen hidden doors remain investigation targets even after
        // a short corridor stops qualifying as an unexplored roaming frontier.
        // The native introduction forbids finding secrets, so do not approach them.
        if (!SPDSettings.intro()) {
            idleSafe = progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.ROAM);
            idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);
            int secretStand = BlastAutoSecretDoors.nearestStand(hero.pos, Dungeon.level.width(),
                    Dungeon.level.map, Dungeon.level.visited, Dungeon.level.mapped,
                    hero.fieldOfView, idleSafe, idleDistances);
            if (secretStand >= 0) {
                debugDecision = "secret-approach";
                progress.intent(BlastAutoProgressGuard.Intent.ROAM);
                roaming.clearDestination();
                hero.curAction = new HeroAction.Move(firstStep(secretStand, idleSafe, idleDistances));
                return false;
            }
        }

        // Sacrifice patrol is only an idle task. Picking up known loot and
        // opening reachable containers must not be starved by its roaming.
        if (sacrifice == BlastAutoSacrifice.Action.PATROL) {
            debugDecision = "sacrifice-patrol";
            BlastAutoSacrifice.Action patrol = BlastAutoSacrifice.patrol(hero, safe, distances);
            if (patrol == BlastAutoSacrifice.Action.WAIT) return true;
            if (patrol == BlastAutoSacrifice.Action.QUEUED) return false;
        }

        debugDecision = "attacker";
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
        idleSafe = progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.HARVEST);
        idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);
        debugDecision = "harvest";
        if (harvest(hero, idleSafe, idleDistances)) {
            progress.intent(BlastAutoProgressGuard.Intent.HARVEST);
            return false;
        }

        // Approach solvable fire and trap rooms before ordinary trap clearing.
        idleSafe = progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.ROAM);
        idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);
        debugDecision = "special-room";
        BlastAutoSpecialRooms.Action roomAction =
                BlastAutoSpecialRooms.chooseAction(hero, idleSafe, idleDistances,
                        hazards, specialBarriers);
        if (roomAction != BlastAutoSpecialRooms.Action.NONE) {
            roaming.clearDestination();
            if (roomAction == BlastAutoSpecialRooms.Action.MOVE) {
                progress.intent(BlastAutoProgressGuard.Intent.ROAM);
            }
            return roomAction == BlastAutoSpecialRooms.Action.USE;
        }

        // Clear reachable traps before repeatedly planting new grass.
        idleSafe = progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.ROAM);
        idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);
        debugDecision = "trap";
        BlastAutoTrapClearer.Action trapAction =
                BlastAutoTrapClearer.chooseAction(hero, idleSafe, idleDistances);
        if (trapAction != BlastAutoTrapClearer.Action.NONE) {
            roaming.clearDestination();
            if (trapAction == BlastAutoTrapClearer.Action.MOVE) {
                progress.intent(BlastAutoProgressGuard.Intent.ROAM);
            }
            return trapAction == BlastAutoTrapClearer.Action.THROW;
        }

        // Barricades and webs block navigation but can be destroyed by ordinary bombs.
        // Keep trap clearance and manual travel above optional demolition.
        debugDecision = "demolition";
        BlastAutoBarricade.Action barricadeAction =
                BlastAutoBarricade.chooseAction(hero, idleSafe, idleDistances);
        if (barricadeAction != BlastAutoBarricade.Action.NONE) {
            roaming.clearDestination();
            if (barricadeAction == BlastAutoBarricade.Action.MOVE) {
                progress.intent(BlastAutoProgressGuard.Intent.ROAM);
            }
            return barricadeAction == BlastAutoBarricade.Action.DETONATE;
        }

        // Only planting new grass depends on the separate farming setting.
        debugDecision = "farm";
        if (BlastSettings.autoFarming()) {
            idleSafe = progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.FARM);
            idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);
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
        }

        idleSafe = progress.safeFor(safe, hero.pos, BlastAutoProgressGuard.Intent.ROAM);
        idleDistances = navigationDistances(hero.pos, safe, distances, idleSafe);
        debugDecision = "roam";
        int step = roamStep(hero, idleSafe, idleDistances);
        if (step >= 0) {
            progress.intent(BlastAutoProgressGuard.Intent.ROAM);
            hero.curAction = new HeroAction.Move(step);
        } else {
            if (BlastAutoVault.waitForLaser(hero, Dungeon.level, safe, distances)) {
                debugDecision = "vault-laser-wait";
                roaming.clearDestination();
                hero.spendAndNext(Actor.TICK);
                return true;
            }
            int staging = BlastAutoVault.stagingCell(hero, Dungeon.level, safe, distances);
            if (staging >= 0 && !hero.rooted) {
                roaming.clearDestination();
                if (staging == hero.pos) {
                    debugDecision = "vault-wait";
                    hero.spendAndNext(Actor.TICK);
                    return true;
                }
                debugDecision = "vault-stage";
                hero.curAction = new HeroAction.Move(firstStep(staging, safe, distances));
                return false;
            }
            debugDecision = "idle";
        }
        return false;
    }

    /** Long-press magnifier behavior, only when an adjacent secret door is in view. */
    private static boolean searchNearbySecretDoor(Hero hero) {
        // Native intentional searches cannot reveal secrets during the introduction.
        if (SPDSettings.intro()) return false;
        Level level = Dungeon.level;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = hero.pos + offset;
            if (level.insideMap(cell) && hero.fieldOfView[cell]
                    && level.map[cell] == Terrain.SECRET_DOOR) {
                // search() reports discovery, not whether it consumed a turn.
                hero.search(true);
                return true;
            }
        }
        return false;
    }

    /** Only the existing Debug Log switch enables nearby-loot decision traces. */
    private static void logNearbyLoot(Hero hero, HeroAction action, boolean started) {
        if (!BlastSettings.debugLog() || nearbyDebugLoot == null) {
            lastLootTrace = null;
            repeatedLootTrace = 0;
            return;
        }
        String command;
        if (action instanceof HeroAction.Move) {
            command = "move@" + ((HeroAction.Move) action).dst;
        } else if (action instanceof HeroAction.PickUp) {
            command = "pickup@" + ((HeroAction.PickUp) action).dst;
        } else {
            command = action == null ? (started ? "turn" : "wait")
                    : action.getClass().getSimpleName();
        }
        String trace = "auto-loot pos=" + hero.pos + " "
                + nearbyDebugLoot + " chosen=" + debugDecision + " action=" + command;
        if (trace.equals(lastLootTrace)) {
            if (++repeatedLootTrace < 10) return;
            repeatedLootTrace = 0;
        } else {
            lastLootTrace = trace;
            repeatedLootTrace = 0;
        }
        BlastLog.debug(trace);
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
        lastLootTrace = null;
        repeatedLootTrace = 0;
        manualAction = false;
        manualConfirmationHero = null;
        clearInterruptedTravel();
        pendingManualInput.set(null);
        waitingForInput = false;
        pendingBombInputs.clear();
        clearFreeBombInputs();
        BlastAutoPickup.reset();
        BlastAutoEquipment.reset();
        BlastAutoTrapClearer.reset();
        BlastAutoSpecialRooms.reset();
        BlastAutoSacrifice.reset();
        BlastAutoFood.stopFeeding();
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

    /** Exclude service NPCs and demon spawners from all automatic attacks. */
    static boolean isAutoAttackExcluded(Mob mob) {
        return mob instanceof NPC || mob instanceof DemonSpawner;
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
                || isAutoAttackExcluded(combatTarget)
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
        return safeCells(hero, hazards, specialBarriers, false);
    }

    private static boolean[] safeCells(Hero hero, Blob[] hazards, boolean[] specialBarriers,
                                       boolean allowGas) {
        Level level = Dungeon.level;
        boolean[] safe = Dungeon.findPassable(hero, level.passable, hero.fieldOfView, true).clone();
        boolean phaseEnabled = BlastMovement.enabled();
        com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.TrapsRoom flightRoom =
                BlastAutoSpecialRooms.flightRoom(level, hero);
        com.shatteredpixel.shatteredpixeldungeon.levels.rooms.secret.SecretChestChasmRoom chasmRoom =
                BlastAutoChasmRoom.flightRoom(level, hero);

        boolean[] vaultDanger = BlastAutoVault.imminent(hero, level);
        for (int cell = 0; cell < safe.length; cell++) {
            boolean known = level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell];
            boolean airborne = known && flightRoom != null
                    && BlastAutoSpecialRooms.flightTile(level, flightRoom, cell);
            airborne |= chasmRoom != null && BlastAutoChasmRoom.flightTile(level, chasmRoom, cell);
            boolean traversable = safe[cell]
                    || (phaseEnabled && known && BlastMovement.canPhase(cell))
                    || airborne;
            safe[cell] = (known || airborne) && level.insideMap(cell) && traversable
                    && (!level.pit[cell] || airborne)
                    && (isTrapSafe(level.traps.get(cell)) || airborne)
                    && !AutoPlayPlantSafety.isHarmful(level.plants.get(cell))
                    && (!BlastAutoEnvironment.hazardousAt(cell, hazards)
                        || (allowGas && BlastAutoEnvironment.gasOnlyAt(cell, hazards)))
                    && !specialBarriers[cell]
                    && !vaultDanger[cell];
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
                    && (!BlastAutoEnvironment.hazardousAt(cell, hazards)
                        || (allowGas && BlastAutoEnvironment.gasOnlyAt(cell, hazards)))
                    && !specialBarriers[cell] && !vaultDanger[cell]) {
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

    /** Recompute reachability only for tasks with an active step exclusion. */
    private static int[] navigationDistances(int origin, boolean[] safe, int[] distances,
                                             boolean[] taskSafe) {
        if (taskSafe == safe) return distances;
        PathFinder.buildDistanceMap(origin, taskSafe);
        return PathFinder.distance.clone();
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
                    && isAwake(mob) && !isAutoAttackExcluded(mob)) return true;
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
