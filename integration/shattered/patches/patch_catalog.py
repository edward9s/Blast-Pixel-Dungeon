#!/usr/bin/env python3
"""Register Blast-only item types in SPD's existing journal categories."""
from pathlib import Path
import sys


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    registrations = {
        "\t\tPOTIONS.addItems(Generator.Category.POTION.classes);":
            "\t\tPOTIONS.addItems(com.spd.blast.BlastPhasePotion.class);",
        "\t\tSCROLLS.addItems(Generator.Category.SCROLL.classes);":
            "\t\tSCROLLS.addItems(com.spd.blast.BlastBombproofScroll.class);",
        "\t\t\t\tSealShard.class, BrokenStaff.class, CloakScrap.class, BowFragment.class, BrokenHilt.class, TornPage.class);":
            "\t\tMISC_CONSUMABLES.addItems(com.spd.blast.BlastRadiusPickup.class,\n"
            "\t\t\t\tcom.spd.blast.BlastHastePickup.class);"
    }
    for anchor, addition in registrations.items():
        if source.count(anchor) != 1:
            raise SystemExit(f"{path}: expected exactly one Catalog registration: {anchor}")
        source = source.replace(anchor, anchor + "\n" + addition, 1)
    path.write_text(source, encoding="utf-8")
    print(f"patched {path}: Blast journal entries")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_catalog.py <Catalog.java>")
    patch(Path(sys.argv[1]))
