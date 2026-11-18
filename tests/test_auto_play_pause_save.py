#!/usr/bin/env python3
"""Guard the per-save auto-play pause persistence hooks."""
from pathlib import Path
import runpy
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_dungeon_auto_pause.py"
APPLY = ROOT / "integration/shattered/apply.sh"
AUTO_PLAY = ROOT / "core/src/main/java/com/spd/blast/BlastAutoPlay.java"

DUNGEON_FIXTURE = """public class Dungeon {
\tpublic static void init() {

\t\tinitialVersion = version = Game.versionCode;
\t}

\tpublic static void saveGame(int save) {
\t\t\tbundle.put( HERO, hero );
\t}

\tpublic static void loadGame(int save) {
\t\thero = (Hero)bundle.get( HERO );
\t}
}
"""


class AutoPlayPauseSaveTests(unittest.TestCase):

    def patch_fixture(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Dungeon.java"
            path.write_text(DUNGEON_FIXTURE, encoding="utf-8")
            patch = runpy.run_path(str(PATCH))["patch"]
            patch(path)
            return path.read_text(encoding="utf-8")

    def test_new_game_save_and_load(self):
        source = self.patch_fixture()
        self.assertIn("BlastAutoPlay.resetPause();", source)
        self.assertLess(source.index("BlastAutoPlay.resetPause();"),
                        source.index("initialVersion"))
        self.assertIn(
            'bundle.put( "blast_auto_play_paused", com.spd.blast.BlastAutoPlay.isPaused() );',
            source,
        )
        self.assertIn(
            'BlastAutoPlay.restorePause(hero, bundle.getBoolean("blast_auto_play_paused"));',
            source,
        )
        self.assertLess(source.index("hero = (Hero)bundle.get( HERO );"),
                        source.index("BlastAutoPlay.restorePause("))
        self.assertEqual(source.count("blast_auto_play_paused"), 2)

    def test_patch_fails_fast_on_missing_or_duplicate_hooks(self):
        patch = runpy.run_path(str(PATCH))["patch"]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Dungeon.java"
            path.write_text(DUNGEON_FIXTURE.replace(
                "\t\thero = (Hero)bundle.get( HERO );",
                "\t\t\thero = (Hero)bundle.get( OTHER_HERO );",
            ), encoding="utf-8")
            with self.assertRaises(SystemExit):
                patch(path)

            path.write_text(DUNGEON_FIXTURE, encoding="utf-8")
            patch(path)
            with self.assertRaises(SystemExit):
                patch(path)

    def test_pause_lifecycle_remains_hero_scoped(self):
        source = AUTO_PLAY.read_text(encoding="utf-8")
        self.assertIn("private static volatile Hero pausedHero;", source)
        self.assertIn("public static void resetPause()", source)
        self.assertIn("public static void restorePause(Hero hero, boolean paused)", source)
        self.assertIn("pausedHero = paused ? hero : null;", source)
        self.assertIn("pausedHero == Dungeon.hero", source)
        self.assertIn("if (!BlastSettings.testAutoPlay()) pausedHero = null;", source)
        self.assertIn("pausedHero = resume ? null : hero;", source)
        self.assertIn('patch_dungeon_auto_pause.py" "$root/Dungeon.java"',
                      APPLY.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
