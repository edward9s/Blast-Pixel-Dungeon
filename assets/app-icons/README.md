# Blast app icon sources

Upload exactly these three original PNG images to this directory:

| Filename | Dimensions | Use |
|---|---|---|
| `ic_launcher.png` | 192×192 | Legacy Android icon and all iOS app icons |
| `ic_launcher_foreground.png` | 432×432 | Adaptive Android foreground |
| `ic_launcher_monochrome.png` | 432×432 | Android themed/monochrome icon |

Do not upload resized copies: `integration/shattered/patches/patch_icons.py` writes the 47 native icons during the Blast integration step. It uses nearest-neighbor resizing for pixel art.

Android's `ic_launcher_background.png` stays **unchanged** in both `main` and `debug` resources. The iOS icons use the legacy icon, composited onto an opaque dark-green background where its PNG is transparent, to satisfy iOS icon requirements.

The build and release workflows use the same script, for both standalone Blast and Blast + SMM. These workflows currently build Android APKs and Desktop JARs, **not iOS binaries**. An iOS build from the patched SPD checkout will use the replaced icons.

Manual integration (after `python -m pip install Pillow==11.3.0`):

```bash
bash integration/shattered/apply.sh /path/to/spd .
```

All three original PNGs must exist with the exact sizes above. Missing artwork, unexpected SPD icon resources, or a missing Pillow dependency fails the integration instead of silently retaining native graphics.
