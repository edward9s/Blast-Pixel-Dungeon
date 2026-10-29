#!/usr/bin/env python3
"""Decide whether to publish an official Blast release or skip the daily build."""
import json
import os
import re
import sys
from urllib.request import Request, urlopen

OFFICIAL_TAG = re.compile(r"Blast-v(\d+\.\d+\.\d+)-SPD-(v\d+(?:\.\d+)+)")
SPD_TAG = re.compile(r"v\d+(?:\.\d+)+")
VERSION = re.compile(r"\d+\.\d+\.\d+")


def version_tuple(version):
    if not VERSION.fullmatch(version):
        raise ValueError("Invalid Blast version: " + version)
    return tuple(map(int, version.split(".")))


def decide_mode(version, spd_tag, releases, manual=False):
    installed = version_tuple(version)
    if not SPD_TAG.fullmatch(spd_tag):
        raise ValueError("Invalid SPD release tag: " + spd_tag)
    published = set()
    for release in releases:
        if release["draft"] or release["prerelease"]:
            continue
        match = OFFICIAL_TAG.fullmatch(release["tag_name"])
        if match:
            published.add((version_tuple(match.group(1)), match.group(2)))

    latest_blast = max((item[0] for item in published), default=None)
    if latest_blast is not None and installed < latest_blast:
        raise ValueError("Blast version is older than the newest official release")
    if manual:
        return "manual"
    # New combinations must publish even if only SPD changed.
    return "skip" if (installed, spd_tag) in published else "formal"


def github_json(path):
    url = "https://api.github.com/repos/" + os.environ["GITHUB_REPOSITORY"] + "/" + path
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Blast-Pixel-Dungeon",
    })
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    if len(sys.argv) not in (3, 4) or (len(sys.argv) == 4 and sys.argv[3] != "--manual"):
        raise SystemExit("usage: decide_release.py VERSION SPD_TAG [--manual]")

    releases = []
    for page in range(1, 101):
        batch = github_json(f"releases?per_page=100&page={page}")
        if not isinstance(batch, list):
            raise ValueError("Invalid GitHub Releases response")
        releases.extend(batch)
        if len(batch) < 100:
            break
    else:
        raise ValueError("Too many releases to inspect safely")

    print(decide_mode(sys.argv[1], sys.argv[2], releases, manual=len(sys.argv) == 4))


if __name__ == "__main__":
    main()
