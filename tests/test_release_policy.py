#!/usr/bin/env python3
"""Offline regression checks for scheduled and manual official releases."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from decide_release import decide_mode

VERSION = "0.1.0"


def official(version, draft=False, prerelease=False, spd="v4.0.2"):
    return {
        "tag_name": f"Blast-v{version}-SPD-{spd}",
        "draft": draft,
        "prerelease": prerelease,
    }


class DecideReleaseTests(unittest.TestCase):
    def decide(self, version=VERSION, releases=None, manual=False):
        return decide_mode(
            version, [official(VERSION)] if releases is None else releases, manual=manual
        )

    def test_unchanged_daily_version_skips(self):
        self.assertEqual("skip", self.decide())

    def test_same_blast_version_with_new_spd_release_skips(self):
        self.assertEqual("skip", self.decide(releases=[official(VERSION, spd="v4.0.1")]))

    def test_new_blast_version_scheduled_official_build(self):
        self.assertEqual("formal", self.decide(version="0.1.1"))

    def test_first_official_release(self):
        self.assertEqual("formal", self.decide(releases=[]))

    def test_manual_release_forces_build_with_unchanged_version(self):
        self.assertEqual("manual", self.decide(manual=True))

    def test_manual_release_creates_new_official_release(self):
        self.assertEqual("manual", self.decide(version="0.1.1", manual=True))

    def test_manual_release_without_existing_official_release(self):
        self.assertEqual("manual", self.decide(releases=[], manual=True))

    def test_draft_and_prerelease_do_not_count(self):
        self.assertEqual("skip", self.decide(releases=[
            official(VERSION), official("0.1.1", draft=True),
            official("0.1.2", prerelease=True),
        ]))

    def test_prerelease_only_does_not_block_initial_official_release(self):
        self.assertEqual("formal", self.decide(releases=[
            official("0.1.2", prerelease=True),
        ]))

    def test_downgrade_rejected_for_schedule(self):
        with self.assertRaisesRegex(ValueError, "older"):
            self.decide(releases=[official("0.1.1")])

    def test_downgrade_rejected_for_manual_release(self):
        with self.assertRaisesRegex(ValueError, "older"):
            self.decide(manual=True, releases=[official("0.1.1")])

    def test_malformed_version_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid Blast version"):
            self.decide(version="v0.1.0")

    def test_unrelated_tags_ignored(self):
        self.assertEqual("formal", self.decide(releases=[{
            "tag_name": "build-test", "draft": False, "prerelease": True,
        }]))


if __name__ == "__main__":
    unittest.main()
