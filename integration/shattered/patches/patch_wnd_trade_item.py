import sys
from pathlib import Path


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    buy = "\tprivate void buy( Heap heap ) {"
    if source.count(buy) != 1:
        raise ValueError("Expected one native shop purchase method")
    path.write_text(source.replace(buy, "\tpublic static void buy( Heap heap ) {"), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wnd_trade_item.py <WndTradeItem.java>")
    patch(Path(sys.argv[1]))
