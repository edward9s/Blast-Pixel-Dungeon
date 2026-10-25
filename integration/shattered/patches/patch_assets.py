#!/usr/bin/env python3
"""Install Blast banner and hero artwork, replacing the original hero splashes."""
from pathlib import Path
import shutil
import struct
import sys


HEROES = ("warrior", "mage", "rogue", "huntress", "duelist", "cleric")
IMAGE_SIZES = {"interfaces/banners.png": (512, 256), "interfaces/blast_credit.png": (16, 16)}
IMAGE_SIZES.update({f"splashes/{hero}.png": (800, 450) for hero in HEROES})


def check_png(path: Path, expected_size: tuple[int, int]) -> None:
    data = path.read_bytes()
    if len(data) < 24 or data[:16] != b"\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR":
        raise SystemExit(f"expected PNG image: {path}")
    size = struct.unpack(">II", data[16:24])
    if size != expected_size:
        raise SystemExit(f"unexpected image size for {path}: {size}, expected {expected_size}")


def main() -> None:
    if len(sys.argv) != 4:
        raise SystemExit("usage: patch_assets.py <Assets.java> <target-assets> <blast-assets>")

    path, target, blast = map(Path, sys.argv[1:])
    java = path.read_text(encoding="utf-8")

    # Validate all input before writing files.
    old_icon = 'public static final String ICONS    = "interfaces/icons.png";'
    new_icon = old_icon + '\n\t\tpublic static final String BLAST_CREDIT = "interfaces/blast_credit.png";'
    if java.count(old_icon) != 1:
        raise SystemExit(f"expected exactly one Assets icon anchor, found {java.count(old_icon)}")
    for relative, size in IMAGE_SIZES.items():
        source = blast / relative
        if not source.is_file():
            raise SystemExit(f"missing Blast artwork: {source}")
        check_png(source, size)
        if not (target / relative).parent.is_dir():
            raise SystemExit(f"missing upstream asset directory: {(target / relative).parent}")

    for hero in HEROES:
        jpg = f"splashes/{hero}.jpg"
        old = f'"{jpg}"'
        count = java.count(old)
        if count != 1:
            raise SystemExit(f"expected exactly one {old} in {path}, found {count}")
        if not (target / jpg).is_file():
            raise SystemExit(f"missing native splash: {target / jpg}")
        java = java.replace(old, f'"splashes/{hero}.png"', 1)

    if not (target / "interfaces/banners.png").is_file():
        raise SystemExit("missing native banner")

    for relative in IMAGE_SIZES:
        shutil.copyfile(blast / relative, target / relative)
    for hero in HEROES:
        (target / f"splashes/{hero}.jpg").unlink()

    java = java.replace(old_icon, new_icon, 1)
    path.write_text(java, encoding="utf-8")
    print(f"patched {path}: Blast banner, credit icon, and six PNG hero splashes")


if __name__ == "__main__":
    main()
