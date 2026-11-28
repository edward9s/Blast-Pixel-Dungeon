import sys
from pathlib import Path


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    buy = "\tprivate void buy( Heap heap ) {"
    if source.count(buy) != 1:
        raise ValueError("Expected one native shop purchase method")
    before, method = source.split(buy)
    empty = "if (item == null) return;"
    collect = """\t\tif (!item.doPickUp( Dungeon.hero )) {
\t\t\tDungeon.level.drop( item, heap.pos ).sprite.drop();
\t\t}"""
    if method.count(empty) != 1 or method.count(collect) != 1:
        raise ValueError("Expected native shop pickup and fallback")
    method = method.replace(empty, "if (item == null) return false;")
    method = method.replace(collect, """\t\tif (item.doPickUp( Dungeon.hero )) return true;
\t\tDungeon.level.drop( item, heap.pos ).sprite.drop();
\t\treturn false;""")
    path.write_text(before + "\tpublic static boolean buy( Heap heap ) {" + method, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wnd_trade_item.py <WndTradeItem.java>")
    patch(Path(sys.argv[1]))
