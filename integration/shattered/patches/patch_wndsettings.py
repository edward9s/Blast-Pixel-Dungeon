#!/usr/bin/env python3
from pathlib import Path
import sys



MARKER = "// BLAST_SETTINGS_TAB"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wndsettings.py <WndSettings.java>")

    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")

    if MARKER in text:
        raise SystemExit(f"Blast settings tab already exists in {path}")

    field_anchor = "\tprivate LangsTab    langs;\n"
    field_replacement = (
        field_anchor
        + "\tprivate com.spd.blast.BlastSettingsTab blast; "
        + MARKER
        + "\n"
        + "\tprivate com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane blastScroll;\n"
    )
    text = replace_once(text, field_anchor, field_replacement, "settings field")

    tab_anchor = "\t\tadd( langsTab );\n\n\t\tresize(width, (int)Math.ceil(height));\n"
    tab_replacement = """\t\tadd( langsTab );

\t\tfinal float standardHeight = height;
\t\tif (com.spd.blast.BlastSettings.SHOW_BLAST_TAB) {
\t\t\t// Pointer listeners run newest-first. Create the scroll controller before
\t\t\t// the settings buttons so they receive clicks before the scroll area.
\t\t\tblastScroll = new com.shatteredpixel.shatteredpixeldungeon.ui.ScrollPane(
\t\t\t\t\tnew com.watabou.noosa.ui.Component());
\t\t\tblast = new com.spd.blast.BlastSettingsTab();
\t\t\tblast.setSize(width, 0);
\t\t\tblastScroll.content().add(blast);
\t\t\tblastScroll.content().setSize(width, blast.height());
\t\t\theight = PixelScene.landscape() ? standardHeight
\t\t\t\t\t: Math.max(standardHeight, blast.height());
\t\t\tblast.setHeightChanged(() -> {
\t\t\t\tblastScroll.content().setSize(width, blast.height());
\t\t\t\tint windowHeight = (int)Math.ceil(PixelScene.landscape() ? standardHeight
\t\t\t\t\t\t: Math.max(standardHeight, blast.height()));
\t\t\t\tresize(width, windowHeight);
\t\t\t\tlayoutTabs();
\t\t\t\tblastScroll.setRect(0, 0, width, windowHeight);
\t\t\t\tblastScroll.scrollTo(0, 0);
\t\t\t});
\t\t\tadd(blastScroll);

\t\t\tadd(new IconTab(Icons.get(Icons.TALENT)) {
\t\t\t\t@Override
\t\t\t\tprotected void select(boolean value) {
\t\t\t\t\tsuper.select(value);
\t\t\t\t\tblastScroll.visible = blastScroll.active = value;
\t\t\t\t\tif (value) last_index = 6;
\t\t\t\t}
\t\t\t});
\t\t}

\t\tresize(width, (int)Math.ceil(height));
\t\tif (blastScroll != null) {
\t\t\tblastScroll.setRect(0, 0, width, (int)Math.ceil(height));
\t\t}
"""
    text = replace_once(text, tab_anchor, tab_replacement, "settings tab")

    hidden_input_anchor = "\t\tif (tabs.size() == 5 && last_index >= 3){\n"
    hidden_input_replacement = "\t\tif (!DeviceCompat.hasHardKeyboard() && !ControllerHandler.isControllerConnected() && last_index >= 3){\n"
    text = replace_once(
        text,
        hidden_input_anchor,
        hidden_input_replacement,
        "hidden input tab selection",
    )

    path.write_text(text, encoding="utf-8")
    print(f"patched {path}: Blast settings tab")


if __name__ == "__main__":
    main()
