#!/usr/bin/env python3
"""Source checks for safe trap clearing across corridors and open layouts."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CLEAR = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoTrapClearer.java").read_text(encoding="utf-8")
PLAY = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text(encoding="utf-8")
PICKUP = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPickup.java").read_text(encoding="utf-8")


class AutoTrapClearanceTest(unittest.TestCase):
    def test_only_after_combat_loot_and_idle_tasks(self):
        self.assertLess(PLAY.index('debugDecision = "pickup";'), PLAY.index('debugDecision = "trap";'))
        self.assertLess(PLAY.index('debugDecision = "trap";'), PLAY.index('debugDecision = "farm";'))
        self.assertLess(PLAY.index('debugDecision = "aimed-trap";'),
                        PLAY.index('debugDecision = "combat";'))
        self.assertIn("BlastAutoTrapClearer.chooseAimedAction(hero, safe, distances)", PLAY)
        self.assertLess(PLAY.index('debugDecision = "trap";'), PLAY.index('debugDecision = "roam";'))
        # Farming may use its own restricted navigation grid; roaming must restore ROAM.
        roam_section = PLAY.split('debugDecision = "roam";', 1)[0]
        self.assertIn("BlastAutoProgressGuard.Intent.ROAM", roam_section.split(
            'debugDecision = "farm";', 1)[1])
        self.assertIn("BlastAutoTrapClearer.chooseAction(hero, idleSafe, idleDistances)", PLAY)
        self.assertIn("trapAction == BlastAutoTrapClearer.Action.THROW", PLAY)

    def test_hidden_and_revealed_disarming_corridor_traps(self):
        self.assertIn("trap.active", CLEAR)
        self.assertNotIn("|| !trap.visible", CLEAR)
        self.assertIn("trap.disarmedByActivation", CLEAR)
        self.assertIn("terrain == Terrain.TRAP || terrain == Terrain.SECRET_TRAP", CLEAR)
        self.assertIn("level.visited[cell] || level.mapped[cell] || hero.fieldOfView[cell]", CLEAR)
        self.assertIn("visited[first] || mapped[first] || visible[first]", CLEAR)
        self.assertIn("visited[second] || mapped[second] || visible[second]", CLEAR)
        self.assertIn("Actor.findChar(cell) != null", CLEAR)
        self.assertIn("separatedSide(", CLEAR)
        self.assertIn("firstReached == secondReached", CLEAR)
        self.assertIn("passable[exit]", CLEAR)
        self.assertIn("pit[exit]", CLEAR)
        self.assertIn("exitKnown && !safe[exit]", CLEAR)
        self.assertIn("distances[stand] == Integer.MAX_VALUE", CLEAR)

    def test_only_safe_known_effects(self):
        allowed = CLEAR.split("static int minimumThrowDistance(", 1)[1].split(
            "static int reachableSide(", 1
        )[0]
        for name in ("CursingTrap", "WeakeningTrap", "DisarmingTrap",
                     "OozeTrap", "ChillingTrap", "FrostTrap", "BurningTrap",
                     "BlazingTrap", "ToxicTrap", "ConfusionTrap", "CorrosionTrap",
                     "GeyserTrap", "ShockingTrap", "FlockTrap",
                     "TeleportationTrap", "WarpingTrap"):
            self.assertIn("type == " + name + ".class", allowed)
        for name in ("WornDartTrap", "PoisonDartTrap",
                     "DisintegrationTrap", "GrimTrap"):
            self.assertIn("type == " + name + ".class", allowed)
        for name in ("ExplosiveTrap", "FlashingTrap", "GrippingTrap",
                     "TenguDartTrap"):
            self.assertNotIn("type == " + name + ".class", allowed)
        self.assertIn("return -1;", allowed)
        self.assertIn("item.throwPos(hero, bestTarget) != bestTarget", CLEAR)
        self.assertIn("Ballistica.PROJECTILE", CLEAR)

    def test_freezing_trap_standoff(self):
        # Chilling seeds adjacent cells; Frost reaches two path steps.
        self.assertIn("type == OozeTrap.class || type == ChillingTrap.class) return 2", CLEAR)
        self.assertIn("type == FrostTrap.class) return 3", CLEAR)
        self.assertIn("terrain == Terrain.TRAP || terrain == Terrain.SECRET_TRAP", CLEAR)
        self.assertIn("new Ballistica(stand, trapCell, Ballistica.PROJECTILE)", CLEAR)
        self.assertIn("int minimum = minimumThrowDistance(trap);", CLEAR)

    def test_corner_fire_and_frost_potion(self):
        self.assertIn("Exactly two walkable cardinal openings also includes L-shaped corridors", CLEAR)
        self.assertIn("return second < 0 ? 0 : separatedSide(first, second, cell", CLEAR)
        self.assertIn("nearbyStand(level, trapCell, trapCell, minimum, gas, trap, safe, distances,", CLEAR)
        self.assertIn("nearbyStand(level, trapCell, door, minimum, false, null, safe, distances,", CLEAR)
        self.assertIn("type == BurningTrap.class) return 2", CLEAR)
        self.assertIn("type == BlazingTrap.class) return 3", CLEAR)
        self.assertIn("hero.belongings.getItem(PotionOfFrost.class)", CLEAR)
        self.assertIn("potion.quantity() > 0 && potion.isKnown()", CLEAR)
        self.assertIn("throwingStand(level, cell, side, 1, false, trap,", CLEAR)
        self.assertIn('item instanceof PotionOfFrost ? "frost" : "throw"', CLEAR)
        self.assertIn("item == null && bestTarget == chosen.pos && fireTrap(chosen)", CLEAR)
        self.assertIn("item.throwPos(hero, bestTarget) != bestTarget", CLEAR)
        self.assertIn("item.cast(hero, bestTarget)", CLEAR)

    def test_rejection_diagnostic_is_bounded_and_specific(self):
        self.assertIn('status=" + reason', CLEAR)
        self.assertIn('" hidden=" + !trap.visible', CLEAR)
        for reason in ("unsupported", "chain-reaction", "no-safe-stand",
                       "lost-inventory", "no-throwable-item", "blocked-trajectory",
                       "unsafe-target", "not-in-range"):
            self.assertIn('"' + reason + '"', CLEAR)
        self.assertIn("LOG_REPEAT_INTERVAL = 10", CLEAR)
        self.assertIn("Dungeon.level.distance(hero.pos, trap.pos) > MAX_THROW_DISTANCE", CLEAR)
        self.assertIn("BlastAutoTrapClearer.reset();", PLAY)

    def test_door_throw_and_toxic_escape(self):
        self.assertIn("level.map[cell] == Terrain.DOOR", CLEAR)
        self.assertIn("stand = doorStand(", CLEAR)
        self.assertIn("target = closedDoor(", CLEAR)
        self.assertIn('openDoor ? "open-door" : item instanceof PotionOfFrost ? "frost" : "throw"', CLEAR)
        self.assertIn("gasEscapeStep(trapCell, stand, level.width(), safe", CLEAR)
        self.assertIn("Math.max(GAS_ESCAPE_DISTANCE, distance + 1)", CLEAR)
        self.assertIn("nearbyStand(level, trapCell, trapCell, minimum, gas, trap, safe, distances,", CLEAR)
        self.assertIn("gasEscapeStep(gasOrigin, hero.pos, level.width(), safe", CLEAR)
        self.assertIn("if (stand < 0 && gas)", CLEAR)
        self.assertIn("int fallback = trap.getClass() == CorrosionTrap.class ? 2 : 1;", CLEAR)
        self.assertIn("throwingStand(level, cell, side, fallback, true, trap,", CLEAR)
        self.assertIn("MAX_ESCAPE_STEPS = 8", CLEAR)
        self.assertIn("hero.rooted", CLEAR)
        self.assertNotIn("hasGasRetreat(level, trapCell, stand, side, safe)", CLEAR)
        self.assertIn("gasOrigin = chosen.pos", CLEAR)
        self.assertIn("gasType = spreadingGas(chosen)", CLEAR)
        self.assertIn("level.blobs.get(gasType)", CLEAR)
        self.assertIn("spreadingGas(trap)", CLEAR)
        self.assertIn("standSafeForEffect(level, trap, stand)", CLEAR)
        self.assertIn("safeToActivate(level, trap)", CLEAR)
        self.assertIn("side == 0", CLEAR)
        self.assertNotIn('reason = "not-chokepoint"', CLEAR)
        self.assertIn("BlastAutoTrapClearer.gasRetreatStep(hero, safe)", PLAY)
        self.assertIn("BlastAutoTrapClearer.avoidGasReturn(hero, safe)", PLAY)
        self.assertLess(PLAY.index("BlastAutoTrapClearer.gasRetreatStep(hero, safe)"),
                        PLAY.index('debugDecision = "combat";'))
        self.assertIn("gas.cur[adjacent] > 0", CLEAR)
        self.assertIn("heldDoor = bestTarget", CLEAR)
        self.assertIn("heldDoorTrap = chosen.pos", CLEAR)
        self.assertIn("holdingDoorOpen(int cell)", CLEAR)
        self.assertIn("BlastAutoTrapClearer.holdingDoorOpen(cell)", PICKUP)
        self.assertIn('status = "door-held"', PICKUP)

    def test_aimed_traps_check_hostile_targets_at_launch(self):
        for name in ("WornDartTrap", "PoisonDartTrap",
                     "DisintegrationTrap", "GrimTrap"):
            self.assertIn("type == " + name + ".class", CLEAR)
        self.assertIn("Math.max(6, level.viewDistance) + 0.5f", CLEAR)
        self.assertIn("level.trueDistance(trapCell, character.pos)", CLEAR)
        self.assertIn("character.invisible > 0", CLEAR)
        self.assertIn("new Ballistica(trapCell, character.pos, Ballistica.PROJECTILE)", CLEAR)
        self.assertIn("character.alignment == Char.Alignment.ENEMY", CLEAR)
        self.assertIn("BlastAutoPlay.isAwake(mob)", CLEAR)
        self.assertIn("BlastAutoPlay.isAutoAttackExcluded(mob)", CLEAR)
        self.assertIn("hero.isCharmedBy(mob)", CLEAR)
        self.assertIn("hero.fieldOfView[mob.pos]", CLEAR)
        self.assertIn("mob.isInvulnerable(Hero.class)", CLEAR)
        self.assertIn("else if (distance == closest && !hostile)", CLEAR)
        self.assertIn("heroDistance > closest", CLEAR)
        self.assertIn("safeAimedStand(hero, level, trapCell, stand, aimed)", CLEAR)
        self.assertIn("aimedOnly && (stand != hero.pos || target != cell)", CLEAR)
        self.assertIn("predictAimedTargets(hero, level, chosen.pos)", CLEAR)
        self.assertIn("BlastAutoTrapClearer.Action.THROW", PLAY)

    def test_aqua_brew_is_a_nonactivating_fallback(self):
        self.assertIn("import com.shatteredpixel.shatteredpixeldungeon.items.potions.brews.AquaBrew;", CLEAR)
        self.assertIn("static boolean canDouse(Trap trap, int terrain)", CLEAR)
        self.assertIn("return trap.active && (terrain == Terrain.TRAP || terrain == Terrain.SECRET_TRAP)", CLEAR)
        self.assertIn("AquaBrew aqua = hero.belongings.getItem(AquaBrew.class)", CLEAR)
        self.assertIn("aqua.quantity() <= 0", CLEAR)
        self.assertIn("!canFlood(level, cell)", CLEAR)
        self.assertIn("tile.allowWater(tileX, tileY)", CLEAR)
        self.assertIn("!douseSafeForCharacters(level, cell, hero)", CLEAR)
        self.assertIn("character.alignment != Char.Alignment.ENEMY", CLEAR)
        self.assertIn("BlastAutoPlay.isAutoAttackExcluded((Mob) character)", CLEAR)
        self.assertIn("nearbyStand(level, cell, cell, 2, false, null, safe, distances,", CLEAR)
        self.assertIn("if (aqua.throwPos(hero, chosen.pos) != chosen.pos)", CLEAR)
        self.assertIn("aqua.cast(hero, chosen.pos)", CLEAR)
        self.assertIn("Action aqua = douseWithAqua(hero, safe, distances)", CLEAR)
        self.assertIn("if (!aimedOnly)", CLEAR)
        self.assertNotIn("aqua.shatter(", CLEAR)
        self.assertNotIn("level.pressCell(", CLEAR)

    def test_reusable_missiles_trigger_traps_before_consuming_gear(self):
        self.assertIn("static boolean safeTrapMissile(MissileWeapon missile)", CLEAR)
        self.assertIn("candidate.getClass() == Dart.class", CLEAR)
        self.assertIn("missile != null ? missile", CLEAR)
        self.assertIn("missile.trueLevel() == 0", CLEAR)
        self.assertIn("!(missile instanceof ForceCube)", CLEAR)
        self.assertIn("!(missile instanceof HeavyBoomerang)", CLEAR)
        self.assertIn("!(missile instanceof TippedDart)", CLEAR)
        self.assertIn("!missile.hasGoodEnchant()", CLEAR)
        self.assertIn("item.cast(hero, bestTarget)", CLEAR)

    def test_inventory_and_native_throw(self):
        self.assertIn("item instanceof Weapon || item instanceof Armor", CLEAR)
        self.assertIn("item instanceof Food", CLEAR)
        self.assertIn("BlastAutoFood.mostAbundant(foods)", CLEAR)
        self.assertIn("!(item instanceof MeatPie)", CLEAR)
        self.assertIn("candidate.isEquipped(hero)", CLEAR)
        self.assertIn("item.setCurrent(hero);", CLEAR)
        self.assertIn("item.cast(hero, bestTarget);", CLEAR)
        self.assertNotIn("trap.activate()", CLEAR)


if __name__ == "__main__":
    unittest.main()
