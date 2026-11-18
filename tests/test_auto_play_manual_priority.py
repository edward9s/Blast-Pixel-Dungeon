#!/usr/bin/env python3
"""Guard manual input priority and modal confirmation while auto-play is enabled."""
import ast
from pathlib import Path
import runpy
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
AUTO = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"
HERO = ROOT / "integration/shattered/patches/patch_hero.py"
SCENE = ROOT / "integration/shattered/patches/patch_game_scene.py"
CHASM = ROOT / "integration/shattered/patches/patch_chasm.py"
APPLY = ROOT / "integration/shattered/apply.sh"


def injection(path, label):
    tree = ast.parse(path.read_text(encoding="utf-8"))
    matches = [
        node for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id in ("replace_once", "replace")
        and len(node.args) == 4
        and isinstance(node.args[3], ast.Constant)
        and node.args[3].value == label
    ]
    if len(matches) != 1:
        raise AssertionError(f"Expected one {label} patch")
    return ast.literal_eval(matches[0].args[2])


def method(source, signature):
    return source.split(signature, 1)[1].split("\n    }", 1)[0]


def native_chasm_fixture():
    tab = lambda n: "\t" * n
    return (
        tab(7) + "protected void onSelect( int index ) {\n"
        + tab(8) + "if (index == 0 && elapsed > 0.2f) {\n"
        + tab(9) + "if (Dungeon.hero.pos == heroPos) {\n"
        + tab(10) + "jumpConfirmed = true;\n"
        + tab(10) + "hero.resume();\n"
        + tab(9) + "}\n"
        + tab(8) + "}\n"
        + tab(7) + "}\n"
        + tab(6) + "}\n"
        + tab(4) + ");\n"
    )


class ManualPriorityTests(unittest.TestCase):
    def test_click_is_queued_on_render_thread(self):
        code = injection(SCENE, "actor-thread manual cell input")
        self.assertIn("BlastAutoPlay.requestManualCell(cell)", code)
        self.assertIn("else if (Dungeon.hero.handle( cell ))", code)

    def test_map_listener_exists_on_auto_play_start(self):
        code = injection(SCENE, "initial auto-play map listener")
        self.assertIn("cellSelector.listener = defaultCellListener", code)

    def test_input_precedes_ai_and_bombs(self):
        code = injection(HERO, "Hero.act automatic actions")
        manual = code.index("BlastAutoPlay.performManualInput(this)")
        target = code.index("BlastAutoPlay.pauseForManualInput(this)")
        bomb = code.index("BlastAutoPlay.performBombInput(this)")
        ai = code.index("BlastAutoPlay.chooseAction(this)")
        self.assertLess(manual, target)
        self.assertLess(target, bomb)
        self.assertLess(bomb, ai)
        self.assertIn("&& !com.spd.blast.BlastAutoPlay.manualActionActive(this)\n"
                      "\t\t\t\t&& com.spd.blast.BlastAutoPlay.performBombInput(this)", code)

    def test_manual_completion_returns_to_actor_loop(self):
        code = injection(HERO, "Hero.act wait for door unlock animation")
        self.assertIn("boolean blastManualFinished", code)
        self.assertIn("return (blastManualFinished || actResult)", code)
        self.assertNotIn("finishManualAction(this)) next()", code)

    def test_latest_click_consumed_atomically(self):
        source = AUTO.read_text(encoding="utf-8")
        request = method(source, "public static void requestManualCell(int cell) {")
        execute = method(source, "public static boolean performManualInput(Hero hero) {")
        self.assertIn("pendingManualInput.set(new ManualCell(hero, level, cell))", request)
        self.assertIn("if (hero.ready) hero.next();", request)
        self.assertIn("pendingManualInput.getAndSet(null)", execute)
        self.assertIn("input.hero != hero || input.level != Dungeon.level", execute)
        self.assertNotIn("hero.lastAction = null", execute)
        self.assertIn("hero.handle(cell.destination)", execute)
        self.assertIn("new AtomicReference<>()", source)
        self.assertGreaterEqual(source.count("pendingManualInput.set(null)"), 3)

    def test_prompt_blocks_manual_action_completion(self):
        code = injection(HERO, "Hero.getCloser chasm confirmation")
        self.assertLess(code.index("beginManualConfirmation(this)"),
                        code.index("Chasm.heroJump(this)"))
        source = AUTO.read_text(encoding="utf-8")
        completion = method(source, "public static boolean finishManualAction(Hero hero) {")
        self.assertIn("manualConfirmationHero == hero", completion)
        decline = method(source, "public static void cancelManualConfirmation(Hero hero) {")
        self.assertIn("manualAction = false", decline)
        self.assertLess(decline.index("hero.lastAction = null"),
                        decline.index("hero.next()"))
        accept = method(source, "public static void acceptManualConfirmation(Hero hero) {")
        self.assertNotIn("hero.lastAction = null", accept)

    def test_native_chasm_yes_no_and_back(self):
        patch = runpy.run_path(str(CHASM))["patch"]
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "Chasm.java"
            path.write_text(native_chasm_fixture(), encoding="utf-8")
            patch(path)
            modified = path.read_text(encoding="utf-8")
            self.assertIn("jumpConfirmed = true;", modified)
            self.assertIn("hero.resume();", modified)
            self.assertEqual(modified.count("acceptManualConfirmation(hero)"), 1)
            self.assertEqual(modified.count("cancelManualConfirmation(hero)"), 2)
            self.assertIn("public void onBackPressed()", modified)
            with self.assertRaises(SystemExit):
                patch(path)

    def test_chasm_patch_is_applied(self):
        self.assertIn('patch_chasm.py" "$root/levels/features/Chasm.java"',
                      APPLY.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
