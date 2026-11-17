#!/usr/bin/env python3
"""Guard free bomb tag command dispatch under heavy actor loads."""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
AUTO = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"
MANIAC_TAG = ROOT / "core/src/main/java/com/spd/blast/BlastBombManiacTag.java"
REMOTE_TAG = ROOT / "core/src/main/java/com/spd/blast/BlastRemoteBombTag.java"


class BombTagDispatchTests(unittest.TestCase):
    def setUp(self):
        self.source = AUTO.read_text(encoding="utf-8")

    def test_free_tags_use_actor_dispatch_instead_of_hero_turn(self):
        source = self.source
        request = source.split("private static void requestBombInput(", 1)[1]
        request = request.split("public static void requestBombPlacement()", 1)[0]
        self.assertIn("action == BombAction.THROW || GameScene.manualInputPending()", request)
        self.assertIn("pendingBombInputs.add(new BombInput(action, destination))", request)
        self.assertIn("pendingFreeBombInputs.addLast(new BombInput(action, destination))", request)
        self.assertIn("Actor.add(freeBombActor)", request)
        self.assertIn("hero.ready && !GameScene.manualInputPending()", request)
        self.assertLess(request.index("pendingFreeBombInputs.addLast"),
                        request.index("Actor.add(freeBombActor)"))

    def test_free_commands_are_serialized_and_preempt_mobs(self):
        source = self.source
        actor = source.split("private static final class FreeBombActor extends Actor {", 1)[1]
        actor = actor.split("private static void clearFreeBombInputs()", 1)[0]
        self.assertIn("actPriority = VFX_PRIO", actor)
        self.assertIn("synchronized (freeBombLock)", actor)
        self.assertIn("pendingFreeBombInputs.pollFirst()", actor)
        self.assertIn("case PLACE:", actor)
        self.assertIn("case DETONATE:", actor)
        self.assertIn("case TOGGLE:", actor)
        self.assertIn("BlastBombManiac.current()", actor)
        self.assertIn("BlastRemoteBomb.detonateNext()", actor)
        self.assertIn("BlastRemoteBomb.toggleMode()", actor)
        self.assertIn("Actor.remove(this)", actor)
        self.assertIn("freeBombActor = null", actor)
        self.assertIn("hero != Dungeon.hero || level != Dungeon.level", actor)

    def test_hero_turn_retains_throws_and_modal_fallback(self):
        source = self.source
        method = source.split("public static boolean performBombInput(Hero hero) {", 1)[1]
        method = method.split("/** Retry a blocked action", 1)[0]
        self.assertIn("pendingBombInputs.poll()", method)
        self.assertIn("blastAutoThrow(input.destination)", method)
        self.assertIn("BlastRemoteBomb.detonateNext()", method)
        self.assertIn("maniac.place()", method)
        resume = source.split("public static void resumeIfInputFinished() {", 1)[1]
        resume = resume.split("/** Queue tag commands", 1)[0]
        self.assertIn("!isPaused() || !pendingBombInputs.isEmpty()", resume)

    def test_settings_and_level_changes_cancel_pending_commands(self):
        source = self.source
        self.assertEqual(source.count("        clearFreeBombInputs();\n        BlastAutoPickup.reset();"), 2)
        clear = source.split("private static void clearFreeBombInputs() {", 1)[1]
        clear = clear.split("private BlastAutoPlay()", 1)[0]
        self.assertIn("pendingFreeBombInputs.clear()", clear)
        self.assertIn("Actor.remove(freeBombActor)", clear)
        self.assertIn("freeBombActor = null", clear)

    def test_tag_handlers_still_dispatch_through_auto_play(self):
        maniac = MANIAC_TAG.read_text(encoding="utf-8")
        remote = REMOTE_TAG.read_text(encoding="utf-8")
        self.assertIn("BlastAutoPlay.requestBombPlacement()", maniac)
        self.assertIn("BlastAutoPlay.requestBombDetonation()", remote)
        self.assertIn("BlastAutoPlay.requestBombModeToggle()", remote)


if __name__ == "__main__":
    unittest.main()
