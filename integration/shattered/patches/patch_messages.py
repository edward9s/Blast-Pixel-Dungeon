#!/usr/bin/env python3
"""Merge Blast's translated messages into every upstream SPD locale."""
from pathlib import Path
import sys

PREFIX = "blast."


def read_keys(path: Path) -> dict[str, str]:
    items: dict[str, str] = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        if "=" not in line:
            raise SystemExit(f"{path}:{number}: expected key=value")
        key, value = line.split("=", 1)
        key = key.strip()
        if not key.startswith(PREFIX) or key in items:
            raise SystemExit(f"{path}:{number}: invalid or duplicate message key: {key}")
        items[key] = value
    if not items:
        raise SystemExit(f"Empty message bundle: {path}")
    return items


def apply(target_dir: Path, translations: Path) -> None:
    base = read_keys(translations / "misc.properties")
    target_files = sorted(target_dir.glob("misc*.properties"))
    if not target_files:
        raise SystemExit(f"No SPD locale bundles: {target_dir}")

    target_names = {path.name for path in target_files}
    translation_names = {path.name for path in translations.glob("misc*.properties")}
    if target_names != translation_names:
        missing = sorted(target_names - translation_names)
        extra = sorted(translation_names - target_names)
        raise SystemExit(f"Blast locale mismatch: missing={missing}, extra={extra}")

    for target in target_files:
        candidate = translations / target.name
        selected = read_keys(candidate)
        if set(selected) != set(base):
            raise SystemExit(f"Locale key mismatch: {candidate}")
        for key, value in base.items():
            translated = selected[key]
            if value.count("%s") != translated.count("%s") or value.count("%") != translated.count("%"):
                raise SystemExit(f"{candidate}: format placeholder mismatch: {key}")

        contents = target.read_text(encoding="utf-8")
        present = {key for key in base if any(line.strip().startswith(key + "=")
                for line in contents.splitlines())}
        if present:
            raise SystemExit(f"{target}: existing Blast keys: {sorted(present)}")
        added = "\n".join(f"{key}={selected[key]}" for key in base)
        target.write_text(contents.rstrip() + "\n\n# Blast Pixel Dungeon\n" + added + "\n",
                          encoding="utf-8")
        print(f"patched {target}: Blast translations")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_messages.py <upstream-misc-directory> <blast-messages-directory>")
    apply(Path(sys.argv[1]), Path(sys.argv[2]))
