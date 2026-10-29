#!/usr/bin/env python3
"""Offline regression checks for the scheduled build decision."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from decide_release import decide_mode

SHA = "a" * 40
SMM = "b" * 40
SMM_TAG = "SPD-v4.0.2-m0.3.10"
SPD = "v4.0.2"
VERSION = "0.1.0"


def official(version, draft=False, prerelease=False):
    return {
        "tag_name": f"Blast-v{version}-SPD-{SPD}",
        "draft": draft,
        "prerelease": prerelease,
    }


def test_release():
    base = f"Blast-v{VERSION}-SPD-{SPD}"
    return {
        "draft": False,
        "prerelease": True,
        "body": "\n".join([
            f"Blast commit: {SHA}",
            f"SPD: {SPD}",
            f"SMM main commit: {SMM}",
            f"SMM release: {SMM_TAG}",
        ]),
        "assets": [
            {"name": base + ".apk"},
            {"name": base + ".jar"},
            {"name": base + "-SMM-m0.3.10.apk"},
            {"name": base + "-SMM-m0.3.10.jar"},
        ],
    }


class DecideReleaseTests(unittest.TestCase):
    def decide(self, version=VERSION, sha=SHA, spd=SPD, smm=SMM,
               smm_tag=SMM_TAG, releases=None, test=None):
        return decide_mode(
            version, sha, spd, smm, smm_tag,
            [official(VERSION)] if releases is None else releases,
            test_release() if test is None else test,
        )

    def test_unchanged_skips(self):
        self.assertEqual("skip", self.decide())

    def test_source_change_builds_test(self):
        self.assertEqual("test", self.decide(sha="c" * 40))

    def test_spd_change_builds_test(self):
        self.assertEqual("test", self.decide(spd="v4.0.3"))

    def test_smm_change_builds_test(self):
        self.assertEqual("test", self.decide(smm="c" * 40))

    def test_smm_release_change_builds_test(self):
        self.assertEqual("test", self.decide(smm_tag="SPD-v4.0.2-m0.3.11"))

    def test_manual_version_increase_creates_official(self):
        self.assertEqual("formal", self.decide(version="0.1.1"))

    def test_initial_official_release(self):
        self.assertEqual("formal", self.decide(releases=[]))

    def test_missing_test_assets_builds_test(self):
        test = test_release()
        test["assets"].pop()
        self.assertEqual("test", self.decide(test=test))

    def test_draft_and_prerelease_do_not_count(self):
        self.assertEqual("skip", self.decide(releases=[
            official(VERSION), official("0.1.1", draft=True),
            official("0.1.2", prerelease=True),
        ]))

    def test_lower_version_errors(self):
        with self.assertRaisesRegex(ValueError, "older"):
            self.decide(releases=[official("0.1.1")])

    def test_official_assets_are_not_replaced_after_spd_update(self):
        self.assertEqual("test", self.decide(spd="v4.0.3"))

    def test_build_test_must_stay_prerelease(self):
        test = test_release()
        test["prerelease"] = False
        with self.assertRaisesRegex(ValueError, "prerelease"):
            self.decide(test=test)


if __name__ == "__main__":
    unittest.main()
