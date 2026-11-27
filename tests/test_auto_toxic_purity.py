"""Source checks for the simple toxic-room loot and item-use priority."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
TOXIC = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoToxicRoom.java").read_text()
AUTO = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text()
PICKUP = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPickup.java").read_text()


class AutoToxicLootTest(unittest.TestCase):

    def test_all_pending_containers_not_only_catalyst(self):
        self.assertIn("type == Heap.Type.CHEST", TOXIC)
        self.assertIn("type == Heap.Type.SKELETON", TOXIC)
        self.assertIn("type == Heap.Type.REMAINS", TOXIC)
        self.assertIn("(type == Heap.Type.HEAP && collectible)", TOXIC)
        self.assertIn("private static boolean hasRoomLoot(", TOXIC)
        self.assertNotIn("TrinketCatalyst", TOXIC)

    def test_aqua_purity_then_direct_entry(self):
        aqua = TOXIC.index("BlastAutoAqua.chooseAction(")
        purity = TOXIC.index("purity.execute(hero, Potion.AC_DRINK);")
        entry = TOXIC.index("BlastAutoSpecialRooms.Action collect = collectRoom(")
        self.assertLess(aqua, purity)
        self.assertLess(purity, entry)
        self.assertNotIn("purity.isKnown() &&", TOXIC)
        self.assertNotIn("!purity.isKnown()", TOXIC)
        self.assertIn("hero.buff(BlobImmunity.class) == null", TOXIC)
        self.assertIn("closestRoomLoot(hero, level, room, PathFinder.distance) == null", TOXIC)
        self.assertIn("if (gas &&", TOXIC)
        self.assertIn("if (pending)", TOXIC)

    def test_safe_route_ignores_only_toxic_gas(self):
        self.assertIn("private static boolean[] lootRoute(", TOXIC)
        self.assertIn("hazard.cur[cell] > 0 && !(hazard instanceof ToxicGas)", TOXIC)
        self.assertIn("!BlastAutoPlay.isTrapSafe(level.traps.get(cell))", TOXIC)
        self.assertIn("AutoPlayPlantSafety.isHarmful(level.plants.get(cell))", TOXIC)
        self.assertIn("barriers[cell]", TOXIC)
        self.assertIn("Actor.findChar(cell)", TOXIC)
        self.assertIn("new HeroAction.OpenChest(target)", TOXIC)
        self.assertIn("BlastAutoPickup.preparePickup(hero, closest, item)", TOXIC)
        self.assertIn("new HeroAction.Move(BlastAutoPlay.firstStep(target, route, distances))", TOXIC)
        self.assertNotIn("enoughPurity(", TOXIC)
        self.assertNotIn("shortestOrder(", TOXIC)
        self.assertNotIn("purityTargets", TOXIC)

    def test_entering_gas_does_not_trigger_immediate_retreat(self):
        self.assertLess(AUTO.index("BlastAutoToxicRoom.continueLoot("),
                        AUTO.index("BlastAutoTrapClearer.gasRetreatStep("))
        self.assertIn("room.inside(level.cellToPoint(hero.pos))", TOXIC)

    def test_room_pickup_preserves_native_callbacks(self):
        self.assertIn("BlastAutoPickup.preparePickup(hero, closest, item)", TOXIC)
        self.assertIn("static void preparePickup(Hero hero, Heap heap, Item item)", PICKUP)
        self.assertIn("attemptedItem = item;", PICKUP)
        self.assertIn("attemptedAction = new HeroAction.PickUp(heap.pos);", PICKUP)
        self.assertIn("hero.curAction = attemptedAction;", PICKUP)


if __name__ == "__main__":
    unittest.main()
