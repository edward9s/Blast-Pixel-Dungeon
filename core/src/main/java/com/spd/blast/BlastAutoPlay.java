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
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MagesStaff;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfPrismaticLight;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfTransfusion;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.PathFinder;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/** Autonomous testing, one ordinary HeroAction per actor turn. */
public final class BlastAutoPlay {

    private static final int ATTACKER_MEMORY_TURNS = 8;
    private static final int LOST_TARGET_TURNS = 8;

    private static Hero currentHero;
    private static Level currentLevel;
    private static final BlastAutoRoaming roaming = new BlastAutoRoaming();
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
    private static volatile Hero manualConfirmationHero;
    private static final AtomicReference<ManualCell> pendingManualCell = new AtomicReference<>();
    private static volatile boolean waitingForInput;
    private static volatile Hero pausedHero;
    private static final ConcurrentLinkedQueue<BombInput> pendingBombInputs = new ConcurrentLinkedQueue<>();

    private static final class ManualCell {
        final Hero hero;
        final Level level;
        final int destination;

        ManualCell(Hero hero, Level level, int destination) {
            this.hero = hero;
            this.level = level;
            this.destination = destination;
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

    private BlastAutoPlay() {}

    public static void onSettingChanged() {
        currentHero = null;
        currentLevel = null;
        roaming.reset();
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
        pendingManualCell.set(null);
        waitingForInput = false;
        if (!BlastSettings.testAutoPlay()) pausedHero = null;
        pendingBombInputs.clear();
        BlastAutoPickup.reset();
        BlastAutoFarming.reset();
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
        manualAction = true;
        manualConfirmationHero = null;
        resetPursuitMemory();
        roaming.reset();
        lastRetryCell = -1;
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
        if (!BlastSettings.testAutoPlay() || hero == null || Dungeon.level == null) {
            throw new IllegalStateException("Cannot request manual movement without auto-play");
        }
        if (!Dungeon.level.insideMap(cell)) {
            throw new IllegalArgumentException("Invalid manual destination: " + cell);
        }
        pendingManualCell.set(new ManualCell(hero, Dungeon.level, cell));
        if (hero.ready) hero.next();
    }

    /** This is called by Hero.act, not the render callback. */
    public static void performManualCell(Hero hero) {
        if (!BlastSettings.testAutoPlay()) return;
        ManualCell input = pendingManualCell.getAndSet(null);
        resetForLevel(hero);
        if (input == null || input.hero != hero || input.level != Dungeon.level) return;
        hero.lastAction = null;
        hero.resting = false;
        if (!hero.handle(input.destination)) {
            throw new IllegalStateException("Queued manual destination was rejected: " + input.destination);
        }
    }

    /** An explicit item action supersedes the last AI movement order. */
    public static void beginItemAction(Hero hero) {
        if (!BlastSettings.testAutoPlay()) return;
        resetForLevel(hero);
        manualAction = false;
        manualConfirmationHero = null;
        pendingManualCell.set(null);
        hero.curAction = null;
        hero.lastAction = null;
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
        pausedHero = resume ? null : Dungeon.hero;
        if (resume && Dungeon.hero.ready && !GameScene.manualInputPending()) {
            Dungeon.hero.next();
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
        if (BlastSettings.testAutoPlay() && !isPaused() && hero != null && hero.ready) hero.next();
    }

    /** Tag commands are applied on the actor thread, never in a render callback. */
    private static void requestBombInput(BombAction action, int destination) {
        Hero hero = Dungeon.hero;
        if (hero == null || !BlastSettings.testAutoPlay()) return;
        resetForLevel(hero);
        pendingBombInputs.add(new BombInput(action, destination));
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
        hero.lastAction = null;
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
        hero.curAction = null;
        hero.lastAction = null;
        hero.resting = false;

        Blob[] hazards = BlastAutoEnvironment.activeHazards(Dungeon.level);
        boolean[] sentryFloors = BlastAutoEnvironment.activeSentryFloors(Dungeon.level);
        boolean[] safe = safeCells(hero, hazards, sentryFloors);
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
                    || sentryFloors[hero.pos]) {
                for (int offset : PathFinder.NEIGHBOURS8) {
                    int cell = hero.pos + offset;
                    if (Dungeon.level.insideMap(cell) && safe[cell] && distances[cell] == 1) {
                        hero.curAction = new HeroAction.Move(cell);
                        return false;
                    }
                }
            }
        }

        // Lure pursuing enemies into Sacrificial Fire before using direct attacks.
        BlastAutoSacrifice.Action sacrifice = BlastAutoSacrifice.chooseAction(hero, safe, distances);
        if (sacrifice == BlastAutoSacrifice.Action.WAIT) return true;
        if (sacrifice == BlastAutoSacrifice.Action.QUEUED) return false;

        // Blast-specific tactics are isolated from the portable AI policy.
        if (BlastAutoBombAI.act(hero)) return true;

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

            boolean ranged = BlastAutoSacrifice.isRangedEnemy(mob.getClass());
            boolean adjacent = Dungeon.level.adjacent(hero.pos, mob.pos);
            // Ranged enemies must be approached into melee range, even if
            // the hero can attack from farther away with an extended weapon.
            boolean canMelee = canMeleeTarget(ranged, adjacent, hero.canAttack(mob));
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

        // Rooted heroes cannot chase a target, but may attack adjacent enemies
        // or use the existing high-evasion wand fallback on non-ranged mobs.
        if (hero.rooted) {
            if (selected != null) {
                rememberCombatTarget(selected);
                if (useWand(hero, selected)) return true;
                if (selectedCanMelee) {
                    hero.curAction = new HeroAction.Attack(selected);
                    return false;
                }
            }
            for (Mob mob : Dungeon.level.mobs) {
                if (mob.isAlive() && mob.alignment != Char.Alignment.ALLY
                        && !isProtectedNpc(mob)
                        && mob.invisible == 0 && hero.fieldOfView[mob.pos]
                        && isAwake(mob) && !ignored(mob)
                        && !mob.isInvulnerable(Hero.class) && !hero.isCharmedBy(mob)
                        && !avoidEvasiveNeutral(hero, mob) && useWand(hero, mob)) {
                    rememberCombatTarget(mob);
                    return true;
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
            if (useWand(hero, selected)) return true;
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

        // Collect reachable floor loot after visible enemies have been handled.
        if (BlastAutoPickup.chooseAction(hero, safe, distances,
                BlastAutoPickupPolicy::isInstantPickup)) return false;

        // Use native actions to open visible containers and keyed doors.
        if (BlastAutoInteract.chooseAction(hero, safe, distances)) return false;

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
        if (harvest(hero, safe, distances)) return false;

        // Only planting new grass depends on the separate farming setting.
        BlastAutoFarming.Action farmingAction =
                BlastAutoFarming.chooseAction(hero, safe, distances);
        if (farmingAction != BlastAutoFarming.Action.NONE) {
            // Farming takes priority and cancels the former wandering target.
            roaming.clearDestination();
            return farmingAction == BlastAutoFarming.Action.BOMB;
        }

        int step = roamStep(hero, safe, distances);
        if (step >= 0) hero.curAction = new HeroAction.Move(step);
        return false;
    }

    /** Wraiths and targets with at least twice the hero's accuracy qualify. */
    static boolean highEvasion(boolean wraith, int defenseSkill, int attackSkill) {
        return wraith || defenseSkill >= 2L * attackSkill;
    }

    private static boolean highEvasion(Hero hero, Mob mob) {
        return highEvasion(mob instanceof Wraith, mob.defenseSkill(hero),
                hero.attackSkill(mob));
    }

    static boolean avoidEvasiveNeutral(Char.Alignment alignment, boolean evasive) {
        return alignment == Char.Alignment.NEUTRAL && evasive;
    }

    private static boolean avoidEvasiveNeutral(Hero hero, Mob mob) {
        return avoidEvasiveNeutral(mob.alignment, highEvasion(hero, mob));
    }

    private static boolean useWand(Hero hero, Mob target) {
        if (isProtectedNpc(target)) return false;
        // Ranged opponents are always approached for melee, except for bomb attacks.
        if (BlastAutoSacrifice.isRangedEnemy(target.getClass())) return false;
        // High-evasion enemies retain their existing wand fallback.
        if (!highEvasion(hero, target)) return false;

        // The mage's equipped staff can contain a charged wand.
        if (hero.belongings.weapon() instanceof MagesStaff) {
            MagesStaff staff = (MagesStaff) hero.belongings.weapon();
            if (castWand(hero, target, staff.blastAutoPlayWand())) return true;
        }
        for (Item item : hero.belongings) {
            if (item instanceof Wand && castWand(hero, target, (Wand) item)) return true;
        }
        return false;
    }

    private static boolean castWand(Hero hero, Mob target, Wand wand) {
        if (wand == null || !(wand instanceof WandOfMagicMissile
                || wand instanceof WandOfFrost
                || wand instanceof WandOfPrismaticLight
                || (wand instanceof WandOfTransfusion
                    && target.properties().contains(Char.Property.UNDEAD)))) {
            return false;
        }
        return wand.blastAutoZap(hero, target.pos);
    }

    private static void resetForLevel(Hero hero) {
        if (currentHero == hero && currentLevel == Dungeon.level) return;
        currentHero = hero;
        currentLevel = Dungeon.level;
        roaming.reset();
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
        pendingManualCell.set(null);
        waitingForInput = false;
        pendingBombInputs.clear();
        BlastAutoPickup.reset();
        BlastAutoFarming.reset();
        BlastAutoSacrifice.reset();
    }

    /** A ranged enemy must be adjacent before an ordinary melee attack is issued. */
    static boolean canMeleeTarget(boolean rangedEnemy, boolean adjacent, boolean canAttack) {
        return canAttack && (!rangedEnemy || adjacent);
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

    private static boolean ignored(Mob mob) {
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

    private static void rememberCombatTarget(Mob mob) {
        // Keep one hidden target in reserve when combat is interrupted.
        if (combatTarget != null && combatTarget != mob && suspendedTarget == null
                && combatTarget.isAlive() && Dungeon.hero != null
                && !Dungeon.hero.fieldOfView[combatTarget.pos]) {
            suspendedTarget = combatTarget;
            suspendedTargetCell = combatTargetCell;
            suspendedTargetTurns = lostTargetTurns;
        }
        combatTarget = mob;
        combatTargetCell = mob.pos;
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

    private static boolean[] safeCells(Hero hero, Blob[] hazards, boolean[] sentryFloors) {
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
                    && !sentryFloors[cell];
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
                    && !sentryFloors[cell]) {
                safe[cell] = true;
            } else {
                safe[cell] = false;
            }
        }
        safe[hero.pos] = true;
        return safe;
    }

    /** Find a shortest reachable destination at or beside an occupied target. */
    private static int nearCell(int origin, int target, boolean[] safe, int[] distances) {
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
    private static int retreatStep(Hero hero, int threat, boolean[] safe, int[] distances) {
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
        BlastAutoFarming.recordHarvest(next);
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
