#!/usr/bin/env python3
"""Rewrite protoc's Python imports so they resolve inside the package.

protoc derives imports from proto paths (`from ai import x_pb2`), which only
resolve when the output directory itself is on sys.path. Rewriting the imports
afterwards leaves descriptor file names such as `ai/x.proto` unchanged, so they
keep matching the other languages. Safe to run repeatedly.

    python scripts/fix-python-imports.py python/nacos_sdk_proto
"""
import re
import sys
from pathlib import Path


def rewrite(source, package, subpackages):
    names = "|".join(re.escape(name) for name in sorted(subpackages))
    if names:
        source = re.sub(
            rf"^from ((?:{names})(?:\.\w+)*) import ",
            rf"from {package}.\1 import ",
            source,
            flags=re.MULTILINE,
        )
    return re.sub(
        r"^import (\w+_pb2)\b",
        rf"from {package} import \1",
        source,
        flags=re.MULTILINE,
    )


def main():
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <package_dir>", file=sys.stderr)
        return 2

    package_dir = Path(sys.argv[1])
    package = package_dir.resolve().name
    subpackages = [
        path.name
        for path in package_dir.iterdir()
        if path.is_dir() and path.name != "__pycache__"
    ]

    changed = 0
    for path in package_dir.rglob("*_pb2*.py"):
        source = path.read_text(encoding="utf-8")
        updated = rewrite(source, package, subpackages)
        if updated != source:
            path.write_text(updated, encoding="utf-8")
            changed += 1
    print(f"Rewrote imports in {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
