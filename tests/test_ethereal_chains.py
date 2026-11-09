#!/usr/bin/env python3
"""Smoke-test reachability patching for Ethereal Chains."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PATCH = ROOT / "integration/shattered/patches/patch_ethereal_chains.py"
SOURCE = """public class EtherealChains {
    void cast() {
\t\t\t\t//chains cannot be used to go where it is impossible to walk to
\t\t\t\tPathFinder.buildDistanceMap(target, BArray.or(Dungeon.level.passable, Dungeon.level.avoid, null));
\t\t\t\tif (!(Dungeon.level instanceof MiningLevel) && PathFinder.distance[curUser.pos] == Integer.MAX_VALUE){
\t\t\t\t\tGLog.w( Messages.get(EtherealChains.class, "cant_reach") );
\t\t\t\t\treturn;
\t\t\t\t}
    }
}
"""


class EtherealChainsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("patch_ethereal_chains", PATCH)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def patch(self, source=SOURCE):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "EtherealChains.java"
            path.write_text(source, encoding="utf-8")
            self.module.patch(path)
            return path.read_text(encoding="utf-8")

    def test_blocked_start_uses_pathfinder_find(self):
        source = self.patch()
        self.assertIn("PathFinder.find(curUser.pos, target,", source)
        self.assertIn("&& curUser.pos != target", source)
        self.assertIn("!(Dungeon.level instanceof MiningLevel)", source)
        self.assertIn('Messages.get(EtherealChains.class, "cant_reach")', source)
        self.assertNotIn("PathFinder.buildDistanceMap(target,", source)

    def test_rejects_missing_or_repeated_anchor(self):
        with self.assertRaises(SystemExit):
            self.patch("public class EtherealChains {}")
        with self.assertRaises(SystemExit):
            self.patch(self.patch())


if __name__ == "__main__":
    unittest.main()
