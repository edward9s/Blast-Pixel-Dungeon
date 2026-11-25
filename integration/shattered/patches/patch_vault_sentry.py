import sys
from pathlib import Path


def patch(path: Path) -> None:
    source = path.read_text(encoding="utf-8")
    field = "\tprivate int scansMade = 0;\n"
    if source.count(field) != 1:
        raise ValueError("Expected one vault sentry scan counter")
    source = source.replace(
        field,
        field + "\n\tpublic int blastScansMade() {\n\t\treturn scansMade;\n\t}\n",
    )
    path.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_vault_sentry.py <VaultSentry.java>")
    patch(Path(sys.argv[1]))
