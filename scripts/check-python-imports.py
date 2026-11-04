#!/usr/bin/env python3
"""Import every module of the generated Python package.

Catches generated code that compiles but cannot be imported once installed.
Modules are enumerated from the file tree rather than pkgutil, which skips a
subpackage silently when the subpackage itself fails to import.

    python scripts/check-python-imports.py python
"""
import importlib
import sys
from pathlib import Path

PACKAGE = "nacos_sdk_proto"


def module_names(package_dir):
    for path in sorted(package_dir.rglob("*.py")):
        parts = path.relative_to(package_dir.parent).with_suffix("").parts
        if parts[-1] == "__init__":
            parts = parts[:-1]
        yield ".".join(parts)


def main():
    if len(sys.argv) != 2:
        print(f"usage: {sys.argv[0]} <python_root>", file=sys.stderr)
        return 2

    root = Path(sys.argv[1]).resolve()
    names = list(module_names(root / PACKAGE))
    if not names:
        print(f"error: no modules found under {root / PACKAGE}", file=sys.stderr)
        return 2

    # The release workflow stages with `git add -A`; never leave .pyc files behind.
    sys.dont_write_bytecode = True
    sys.path.insert(0, str(root))
    failures = []
    for name in names:
        try:
            importlib.import_module(name)
        except Exception as exc:
            failures.append(f"{name}: {type(exc).__name__}: {exc}")

    for failure in failures:
        print(failure, file=sys.stderr)
    print(f"Imported {len(names) - len(failures)}/{len(names)} modules")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
