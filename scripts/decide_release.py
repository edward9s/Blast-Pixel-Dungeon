#!/usr/bin/env python3
"""Choose whether the scheduled job publishes a new release, a test build, or nothing."""
import json
import os
import re
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen

OFFICIAL_TAG = re.compile(r"Blast-v(\d+\.\d+\.\d+)-SPD-v\d+(?:\.\d+)+")
VERSION = re.compile(r"\d+\.\d+\.\d+")


def version_tuple(version):
    if not VERSION.fullmatch(version):
        raise ValueError("Invalid Blast version: " + version)
    return tuple(map(int, version.split(".")))


def decide_mode(version, blast_sha, spd_tag, smm_sha, smm_tag, releases, test_release, manual=False):
    installed = version_tuple(version)
    published = []
    for release in releases:
        if release["draft"] or release["prerelease"]:
            continue
        match = OFFICIAL_TAG.fullmatch(release["tag_name"])
        if match:
            published.append(version_tuple(match.group(1)))

    latest = max(published, default=None)
    if latest is not None and installed < latest:
        raise ValueError("Blast version is older than the newest official release")
    if manual:
        return "manual"
    if latest is None or installed > latest:
        return "formal"

    if test_release is None:
        return "test"
    if not test_release["prerelease"] or test_release["draft"]:
        raise ValueError("build-test must be a published prerelease")

    notes = set((test_release.get("body") or "").splitlines())
    inputs = {
        f"Blast commit: {blast_sha}",
        f"SPD: {spd_tag}",
        f"SMM main commit: {smm_sha}",
        f"SMM release: {smm_tag}",
    }
    base = f"Blast-v{version}-SPD-{spd_tag}"
    names = [asset["name"] for asset in test_release["assets"]]
    smm = [name for name in names if name.startswith(base + "-SMM-m")]
    complete = (
        len(names) == 4 and len(set(names)) == 4
        and base + ".apk" in names and base + ".jar" in names
        and len(smm) == 2
        and len({name.rsplit(".", 1)[0] for name in smm}) == 1
        and {name.rsplit(".", 1)[-1] for name in smm} == {"apk", "jar"}
        and all(re.fullmatch(
            re.escape(base) + r"-SMM-m\d+(?:\.\d+)*\.(?:apk|jar)", name
        ) for name in smm)
    )
    return "skip" if inputs.issubset(notes) and complete else "test"


def github_json(path, allow_missing=False):
    url = "https://api.github.com/repos/" + os.environ["GITHUB_REPOSITORY"] + "/" + path
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Blast-Pixel-Dungeon",
    })
    try:
        with urlopen(request, timeout=30) as response:
            return json.load(response)
    except HTTPError as error:
        if allow_missing and error.code == 404:
            return None
        raise


def main():
    if len(sys.argv) not in (6, 7) or (len(sys.argv) == 7 and sys.argv[6] != "--manual"):
        raise SystemExit(
            "usage: decide_release.py VERSION BLAST_SHA SPD_TAG SMM_MAIN_SHA "
            "SMM_RELEASE_TAG [--manual]"
        )
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
    test_release = github_json("releases/tags/build-test", allow_missing=True)
    print(decide_mode(*sys.argv[1:6], releases, test_release, manual=len(sys.argv) == 7))


if __name__ == "__main__":
    main()
