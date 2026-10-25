#!/usr/bin/env python3
"""Register Blast's native, configurable keyboard actions for its two Tag buttons."""
from pathlib import Path
import sys


def replace_exact(source: str, before: str, after: str, label: str) -> str:
    count = source.count(before)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    return source.replace(before, after, 1)


def patch_actions(source: str) -> str:
    if "TAG_BLAST_MANIAC" in source or "TAG_BLAST_REMOTE" in source:
        raise SystemExit("Blast key actions are already registered")
    old = '\tpublic static final GameAction TAG_RESUME   = new SPDAction("tag_resume");'
    source = replace_exact(
        source, old,
        old + '\n\tpublic static final GameAction TAG_BLAST_MANIAC = new SPDAction("tag_blast_maniac");'
            + '\n\tpublic static final GameAction TAG_BLAST_REMOTE = new SPDAction("tag_blast_remote");',
        "Blast key actions",
    )
    old = '\t\tdefaultBindings.put( Input.Keys.R,              SPDAction.TAG_RESUME );'
    source = replace_exact(
        source, old,
        old + '\n\t\tdefaultBindings.put( Input.Keys.B,              SPDAction.TAG_BLAST_MANIAC );'
            + '\n\t\tdefaultBindings.put( Input.Keys.V,              SPDAction.TAG_BLAST_REMOTE );',
        "Blast keyboard defaults",
    )
    return source


def patch_binding_window(source: str) -> str:
    if "SPDAction.TAG_BLAST_MANIAC" in source or "SPDAction.TAG_BLAST_REMOTE" in source:
        raise SystemExit("Blast key labels are already registered")
    old = '\t\t\tactionName = PixelScene.renderTextBlock(Messages.get(WndKeyBindings.class, action.name()), 6 );'
    new = """\t\t\tString label;
\t\t\tif (action == SPDAction.TAG_BLAST_MANIAC) {
\t\t\t\tlabel = com.spd.blast.BlastMessages.get("bomb_maniac.buff_name");
\t\t\t} else if (action == SPDAction.TAG_BLAST_REMOTE) {
\t\t\t\tlabel = com.spd.blast.BlastMessages.get("remote_bomb.buff_name");
\t\t\t} else {
\t\t\t\tlabel = Messages.get(WndKeyBindings.class, action.name());
\t\t\t}
\t\t\tactionName = PixelScene.renderTextBlock(label, 6 );"""
    source = replace_exact(source, old, new, "Blast keybinding labels")
    return source


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: patch_tag_keybindings.py <SPDAction.java> <WndKeyBindings.java>")
    action_path = Path(sys.argv[1])
    window_path = Path(sys.argv[2])
    actions = patch_actions(action_path.read_text(encoding="utf-8"))
    window = patch_binding_window(window_path.read_text(encoding="utf-8"))
    action_path.write_text(actions, encoding="utf-8")
    window_path.write_text(window, encoding="utf-8")
