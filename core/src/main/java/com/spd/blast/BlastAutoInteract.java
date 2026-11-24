package com.spd.blast;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Blacksmith;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultTokenDoor;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.VaultMirror;
import com.shatteredpixel.shatteredpixeldungeon.items.quest.DwarfToken;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.levels.RegularLevel;
import com.shatteredpixel.shatteredpixeldungeon.levels.rooms.special.CrystalVaultRoom;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.CrystalKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.GoldenKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.IronKey;
import com.shatteredpixel.shatteredpixeldungeon.items.keys.WornKey;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.watabou.utils.PathFinder;

/** Automated native interactions with discovered containers and keyed doors. */
public final class BlastAutoInteract {
    private BlastAutoInteract() {}

    static boolean canOpen(Heap.Type type, int goldenKeys, int crystalKeys) {
        switch (type) {
            case CHEST:
            case SKELETON:
            case REMAINS:
                return true;
            case LOCKED_CHEST:
                return goldenKeys > 0;
            case CRYSTAL_CHEST:
                return crystalKeys > 0;
            default:
                return false;
        }
    }

    static boolean canUnlock(int terrain, int ironKeys, int crystalKeys, int wornKeys) {
        switch (terrain) {
            case Terrain.LOCKED_DOOR:
                return ironKeys > 0;
            case Terrain.CRYSTAL_DOOR:
                return crystalKeys > 0;
            case Terrain.LOCKED_EXIT:
                return wornKeys > 0;
            default:
                return false;
        }
    }

    /** Chooses one safe native action using auto-play's existing distance map. */
    static boolean chooseAction(Hero hero, boolean[] safe, int[] distances) {
        Level level = Dungeon.level;
        if (safe.length != level.length() || distances.length != level.length()) {
            throw new IllegalArgumentException("Invalid auto-interaction distance map");
        }

        if (!Blacksmith.Quest.given()) {
            for (Mob mob : level.mobs) {
                if (!(mob instanceof Blacksmith) || !mob.isAlive()
                        || !hero.fieldOfView[mob.pos]) continue;
                int candidate = nearestStand(level, mob.pos, false, safe, distances);
                if (candidate < 0) continue;
                hero.curAction = hero.pos == candidate
                        ? new HeroAction.Interact(mob)
                        : new HeroAction.Move(BlastAutoPlay.firstStep(candidate, safe, distances));
                return true;
            }
        }

        // Vault NPC objects are opened through their native interaction dialogs.
        // Approach only when the token threshold or an unclaimed reward is present.
        for (Mob mob : level.mobs) {
            boolean ready = mob instanceof VaultTokenDoor
                    && hero.belongings.getItem(DwarfToken.class) != null
                    && hero.belongings.getItem(DwarfToken.class).quantity() >= 10;
            ready |= mob instanceof VaultMirror && ((VaultMirror) mob).reward != null;
            if (!ready || !mob.isAlive() || !hero.fieldOfView[mob.pos]
) continue;
            int candidate = nearestStand(level, mob.pos, false, safe, distances);
            if (candidate < 0) continue;
            if (hero.pos == candidate) {
                if (mob instanceof VaultTokenDoor) {
                    if (!((VaultTokenDoor) mob).autoUnlock(hero))
                        throw new IllegalStateException("Vault token door became unavailable");
                } else {
                    if (!((VaultMirror) mob).autoCollect(hero))
                        throw new IllegalStateException("Vault mirror reward became unavailable");
                }
                hero.spendAndNext(Actor.TICK);
            } else {
                hero.curAction = new HeroAction.Move(
                        BlastAutoPlay.firstStep(candidate, safe, distances));
            }
            return true;
        }

        // Journal keys are valid on the main branch only, like native SPD.
        int goldenKeys = 0;
        int crystalKeys = 0;
        int ironKeys = 0;
        int wornKeys = 0;
        if (Dungeon.branch == 0) {
            goldenKeys = Notes.keyCount(new GoldenKey(Dungeon.depth));
            crystalKeys = Notes.keyCount(new CrystalKey(Dungeon.depth));
            ironKeys = Notes.keyCount(new IronKey(Dungeon.depth));
            wornKeys = Notes.keyCount(new WornKey(Dungeon.depth));
        }

        if (crystalKeys == 0 && demolishLastCrystalChest(hero, safe, distances)) return true;

        int target = -1;
        int stand = -1;
        int steps = Integer.MAX_VALUE;
        boolean chest = false;
        int bestPriority = Integer.MIN_VALUE;

        for (Heap heap : level.heaps.valueList()) {
            if (!heap.seen || !canOpen(heap.type, goldenKeys, crystalKeys)
                    || !BlastAutoPickup.hasCollectibleLoot(hero, heap)) continue;
            int candidate = nearestStand(level, heap.pos, true, safe, distances);
            int priority = BlastAutoCrystalRooms.priority(level, hero, heap.pos, true);
            if (candidate >= 0 && BlastAutoCrystalRooms.replace(
                    priority, bestPriority, distances[candidate], steps)) {
                target = heap.pos;
                stand = candidate;
                steps = distances[candidate];
                chest = true;
                bestPriority = priority;
            }
        }

        if (ironKeys > 0 || crystalKeys > 0 || wornKeys > 0) {
            for (int cell = 0; cell < level.length(); cell++) {
                if (!(level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell])
                        || !canUnlock(level.map[cell], ironKeys, crystalKeys, wornKeys)) {
                    continue;
                }
                int candidate = nearestStand(level, cell, false, safe, distances);
                int priority = BlastAutoCrystalRooms.priority(level, hero, cell, false);
                if (candidate >= 0 && BlastAutoCrystalRooms.replace(
                        priority, bestPriority, distances[candidate], steps)) {
                    target = cell;
                    stand = candidate;
                    steps = distances[candidate];
                    chest = false;
                    bestPriority = priority;
                }
            }
        }

        if (target < 0) return false;
        if (hero.pos == stand) {
            hero.curAction = chest ? new HeroAction.OpenChest(target)
                    : new HeroAction.Unlock(target);
        } else {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(stand, safe, distances));
        }
        return true;
    }

    /** Recover the unchosen crystal vault chest after all crystal keys are spent. */
    private static boolean demolishLastCrystalChest(Hero hero, boolean[] safe,
                                                    int[] distances) {
        Level level = Dungeon.level;
        if (!(level instanceof RegularLevel) || Dungeon.branch != 0
                || Notes.keyCount(new CrystalKey(Dungeon.depth)) != 0
                || !BlastSettings.testAutoPlay() || BlastRemoteBomb.isTimedMode()
                || hero.buff(BlastRemoteBomb.class) == null) return false;
        CrystalVaultRoom room = (CrystalVaultRoom)
                ((RegularLevel) level).room(CrystalVaultRoom.class);
        if (room == null) return false;
        Heap chest = null;
        int remaining = 0;
        for (Heap heap : level.heaps.valueList()) {
            if (heap.type == Heap.Type.CRYSTAL_CHEST
                    && room.inside(level.cellToPoint(heap.pos))) {
                remaining++;
                chest = heap;
            }
        }
        if (remaining != 1 || !BlastAutoPickup.hasCollectibleLoot(hero, chest)
                || !(chest.seen || hero.fieldOfView[chest.pos]
                        || level.visited[chest.pos] || level.mapped[chest.pos])) return false;
        BlastBombManiac maniac = BlastBombManiac.current();
        if (maniac == null || maniac.remaining() == 0) return false;
        Bomb bomb = maniac.bombAt(0);
        boolean remote = BlastSettings.testRemoteThrow();
        int best = -1;
        int shortest = Integer.MAX_VALUE;
        for (int cell = 0; cell < level.length(); cell++) {
            if (!safe[cell] || distances[cell] == Integer.MAX_VALUE
                    || distances[cell] >= shortest) continue;
            int center = remote ? BlastBombTrajectory.throwPos(cell, chest.pos) : cell;
            if (!level.insideMap(center) || !bomb.blastWarningCells(center)[chest.pos])
                continue;
            if (remote && center != chest.pos) continue;
            best = cell;
            shortest = distances[cell];
        }
        if (best < 0 || hero.rooted && best != hero.pos) return false;
        if (best != hero.pos) {
            hero.curAction = new HeroAction.Move(
                    BlastAutoPlay.firstStep(best, safe, distances));
            return true;
        }
        boolean started = remote ? maniac.blastAutoDemolishChest(chest.pos)
                : maniac.blastAutoDemolishChestNearby();
        if (started) {
            BlastLog.debug("auto-crystal action=demolish chest=" + chest.pos);
            if (!remote) hero.spendAndNext(1f);
        }
        return started;
    }

    /** Containers allow standing on their cell; doors require an adjacent cell. */
    private static int nearestStand(Level level, int target, boolean includeTarget,
                                    boolean[] safe, int[] distances) {
        int stand = -1;
        int steps = Integer.MAX_VALUE;
        if (includeTarget && level.insideMap(target) && safe[target]
                && distances[target] < steps) {
            stand = target;
            steps = distances[target];
        }
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = target + offset;
            if (!level.insideMap(cell) || !safe[cell]) continue;
            if (distances[cell] < steps) {
                stand = cell;
                steps = distances[cell];
            }
        }
        return stand;
    }
}
