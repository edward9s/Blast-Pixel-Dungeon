"""Source-level regression checks for toxin-room catalyst purity crossing."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
TOXIC = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoToxicRoom.java").read_text()
AUTO = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java").read_text()
ROOMS = (ROOT / "core/src/main/java/com/spd/blast/BlastAutoSpecialRooms.java").read_text()


class ToxicCatalystPurityTest(unittest.TestCase):

    def test_only_catalyst_treasure_triggers_unidentified_purity(self):
        self.assertIn("item instanceof TrinketCatalyst", TOXIC)
        self.assertIn("heap.type != Heap.Type.CHEST && heap.type != Heap.Type.HEAP", TOXIC)
        self.assertIn("PotionOfPurity potion = hero.belongings.getItem(PotionOfPurity.class);", TOXIC)
        self.assertNotIn("!potion.isKnown()", TOXIC)
        self.assertNotIn("potion.isKnown() &&", TOXIC)
        self.assertIn("potion.execute(hero, Potion.AC_DRINK);", TOXIC)
        self.assertIn("if (!collectibleCatalyst(hero, heap))", TOXIC)
        self.assertIn("if (safe[treasure] && distances[treasure] != Integer.MAX_VALUE)", TOXIC)

    def test_crossing_preserves_risk_and_duration_guards(self):
        self.assertIn("item instanceof TrinketCatalyst", TOXIC)
        self.assertIn("hazard.cur[cell] > 0 && !(hazard instanceof ToxicGas)", TOXIC)
        self.assertIn("hero.buff(BlobImmunity.class)", TOXIC)
        self.assertIn("!BlastAutoPlay.isTrapSafe(level.traps.get(cell))", TOXIC)
        self.assertIn("AutoPlayPlantSafety.isHarmful(level.plants.get(cell))", TOXIC)
        self.assertIn("Actor.findChar(cell)", TOXIC)
        self.assertIn("enoughPurity(immunity.cooldown()", TOXIC)
        self.assertIn("int stand = purityStand(level, room, door, safe, distances);", TOXIC)
        self.assertIn("new HeroAction.OpenChest(purityTreasure)", TOXIC)
        self.assertIn("new HeroAction.PickUp(purityTreasure)", TOXIC)
        self.assertIn("attemptedPurityTreasure = treasure;", TOXIC)

    def test_retreat_order_and_reset(self):
        runner = AUTO.index("BlastAutoToxicRoom.continuePurityRun(")
        escape = AUTO.index("BlastAutoTrapClearer.gasRetreatStep(")
        self.assertLess(runner, escape)
        self.assertIn("BlastAutoToxicRoom.reset();", ROOMS)
        self.assertIn("BlastAutoToxicRoom.chooseAction(", AUTO)
        self.assertIn("purityEntrance = -1;", TOXIC)
        self.assertIn("purityTreasure = -1;", TOXIC)


if __name__ == "__main__":
    unittest.main()
