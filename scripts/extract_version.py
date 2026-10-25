#!/usr/bin/env python3
"""Extract the explicit Blast version for artifact names."""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: extract_version.py <BlastVersion.java>")

content = Path(sys.argv[1]).read_text(encoding="utf-8")
found = re.findall(r'public\s+static\s+final\s+String\s+VERSION\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"\s*;', content)
if len(found) != 1:
    raise SystemExit("Exactly one Blast VERSION constant is required")
print(found[0])
