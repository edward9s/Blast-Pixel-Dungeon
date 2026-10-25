#!/usr/bin/env python3
"""Install Blast launcher artwork into the checked-out SPD Android and iOS projects."""
import json
from decimal import Decimal
from pathlib import Path
import sys

from PIL import Image


LEGACY_SIZES = {
    "ldpi": 36,
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
ADAPTIVE_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}
SOURCE_SIZES = {
    "ic_launcher.png": 192,
    "ic_launcher_foreground.png": 432,
    "ic_launcher_monochrome.png": 432,
}
IOS_BACKGROUND = "#10351b"  # iOS icons must be opaque, including the marketing icon.


def load_sources(directory: Path) -> dict[str, Image.Image]:
    sources = {}
    for name, size in SOURCE_SIZES.items():
        path = directory / name
        if not path.is_file():
            raise ValueError(f"missing Blast icon: {path}")
        with Image.open(path) as image:
            if image.format != "PNG" or image.size != (size, size):
                raise ValueError(f"expected {size}x{size} PNG: {path}")
            sources[name] = image.convert("RGBA")
    return sources


def ios_sizes(directory: Path) -> dict[str, int]:
    data = json.loads((directory / "Contents.json").read_text(encoding="utf-8"))
    sizes = {}
    for item in data["images"]:
        if "filename" not in item:
            continue  # SPD's macOS icon slots are deliberately empty.
        name = item["filename"]
        width, height = item["size"].split("x")
        if width != height or not item["scale"].endswith("x"):
            raise ValueError(f"unexpected iOS icon entry: {item}")
        pixels = Decimal(width) * Decimal(item["scale"][:-1])
        if pixels != int(pixels) or (name in sizes and sizes[name] != int(pixels)):
            raise ValueError(f"invalid iOS icon size for {name}: {pixels}")
        sizes[name] = int(pixels)

    actual = {file.name for file in directory.glob("*.png")}
    if len(sizes) != 15 or actual != sizes.keys():
        raise ValueError(f"unexpected SPD iOS icon set: manifest={sorted(sizes)}, files={sorted(actual)}")
    return sizes


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_icons.py <spd-root> <blast-icon-source-directory>")
    upstream, source_dir = map(Path, sys.argv[1:])
    try:
        sources = load_sources(source_dir)
        outputs = {}
        for variant in ("main", "debug"):
            root = upstream / "android" / "src" / variant / "res"
            for density, size in LEGACY_SIZES.items():
                target = root / f"mipmap-{density}" / "ic_launcher.png"
                outputs[target] = ("ic_launcher.png", size, False)
            for density, size in ADAPTIVE_SIZES.items():
                for name in ("ic_launcher_foreground.png", "ic_launcher_monochrome.png"):
                    target = root / f"mipmap-{density}" / name
                    outputs[target] = (name, size, False)

        ios = upstream / "ios" / "assets" / "Assets.xcassets" / "AppIcon.appiconset"
        for name, size in ios_sizes(ios).items():
            outputs[ios / name] = ("ic_launcher.png", size, True)

        missing = [str(path) for path in outputs if not path.is_file()]
        if missing:
            raise ValueError("missing upstream icon files:\n" + "\n".join(missing))

        cache = {}
        for path, (name, size, opaque) in outputs.items():
            key = (name, size, opaque)
            if key not in cache:
                result = sources[name].resize((size, size), Image.Resampling.NEAREST)
                if opaque:
                    background = Image.new("RGBA", (size, size), IOS_BACKGROUND)
                    background.alpha_composite(result)
                    result = background.convert("RGB")
                cache[key] = result
            cache[key].save(path, format="PNG")

    except (OSError, KeyError, TypeError, ValueError) as error:
        raise SystemExit(f"Blast icon patch failed: {error}") from error
    print("Replaced 32 Android and 15 iOS icons (47 PNG files); kept upstream Android backgrounds.")


if __name__ == "__main__":
    main()
