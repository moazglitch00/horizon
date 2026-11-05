#!/usr/bin/env python3
"""Measure how much of Minecraft's real Java API the shim layer declares.

For every `net/minecraft` shim under `java/horizon-host/src`, this compares the
public/protected members the shim declares with the members a real Minecraft jar
declares for the same class, and prints coverage per class plus a total.

Names alone do not make a mod link: the method/field name *and* descriptor must
exist. A class can have the right path and 0% of its members.

Usage:
    python3 tools/fabric_shim_coverage.py --jar path/to/26.x.jar
    python3 tools/fabric_shim_coverage.py --jar ... --classes out/ --jdk "C:/Program Files/Java/jdk-21"

If `--classes` is omitted the shims are compiled into a temporary directory with
`javac --release 21` first. Nothing is downloaded; the jar must already be on
disk. `javap` comes from the same JDK.
"""

from __future__ import annotations

import argparse
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile

REPO = pathlib.Path(__file__).resolve().parent.parent
SHIM_ROOT = REPO / "java" / "horizon-host" / "src" / "net" / "minecraft"


def find_javac(jdk: str | None) -> pathlib.Path:
    if jdk:
        for name in ("javac.exe", "javac"):
            candidate = pathlib.Path(jdk) / "bin" / name
            if candidate.is_file():
                return candidate
        raise SystemExit(f"no javac under {jdk}")
    found = shutil.which("javac")
    if found is None:
        raise SystemExit("javac not found; pass --jdk")
    return pathlib.Path(found)


def compile_shims(javac: pathlib.Path) -> pathlib.Path:
    out = pathlib.Path(tempfile.mkdtemp(prefix="horizon-shim-coverage-"))
    sources = sorted(SHIM_ROOT.rglob("*.java"))
    if not sources:
        raise SystemExit(f"no shim sources under {SHIM_ROOT}")
    command = [
        str(javac),
        "--release",
        "21",
        "-encoding",
        "UTF-8",
        "-proc:none",
        "-d",
        str(out),
        *(str(s) for s in sources),
    ]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        raise SystemExit("javac failed:\n" + result.stdout + result.stderr)
    return out


def members(javap: pathlib.Path, classpath: pathlib.Path, cls: str):
    result = subprocess.run(
        [str(javap), "-p", "-s", "-classpath", str(classpath), cls],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return None
    methods: set[tuple[str, str]] = set()
    fields: set[tuple[str, str]] = set()
    lines = result.stdout.splitlines()
    for index, line in enumerate(lines):
        if not line.startswith("  "):
            continue
        decl = line.strip()
        desc = None
        if index + 1 < len(lines) and lines[index + 1].strip().startswith("descriptor:"):
            desc = lines[index + 1].split("descriptor:", 1)[1].strip()
        if desc is None:
            continue
        head = decl.split("(", 1)[0]
        if not (head.startswith("public ") or head.startswith("protected ")):
            continue
        if "(" in decl:
            match = re.search(r"([\w$<>]+)\((.*)\);", decl)
            if match:
                methods.add((match.group(1), desc))
        else:
            match = re.search(r"([\w$.\[\]<>]+)\s+([\w$]+);", decl)
            if match:
                fields.add((match.group(2), desc))
    return methods, fields


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", required=True, type=pathlib.Path, help="real Minecraft jar")
    parser.add_argument("--classes", type=pathlib.Path, help="precompiled shim classes")
    parser.add_argument("--jdk", help="JDK home (for javac/javap)")
    parser.add_argument("--top", type=int, default=12, help="worst N classes to show")
    args = parser.parse_args()

    if not args.jar.is_file():
        raise SystemExit(f"jar not found: {args.jar}")
    javac = find_javac(args.jdk)
    javap = javac.with_name("javap.exe" if javac.suffix == ".exe" else "javap")
    classes = args.classes or compile_shims(javac)

    rows = []
    for source in sorted(SHIM_ROOT.rglob("*.java")):
        name = source.relative_to(REPO / "java" / "horizon-host" / "src").with_suffix("")
        cls = name.as_posix().replace("/", ".")
        real = members(javap, args.jar, cls)
        ours = members(javap, classes, cls)
        if real is None or ours is None:
            rows.append((cls, None))
            continue
        real_methods, real_fields = real
        our_methods, our_fields = ours
        rows.append(
            (
                cls,
                (
                    len(real_methods),
                    len(real_methods & our_methods),
                    len(real_fields),
                    len(real_fields & our_fields),
                ),
            )
        )

    print(f"{'class':68} methods  ok  fields  ok")
    total = covered = field_total = field_covered = 0
    ratios = []
    for cls, data in rows:
        if data is None:
            print(f"{cls:68} javap failed")
            continue
        methods, matched, fields, field_matched = data
        total += methods
        covered += matched
        field_total += fields
        field_covered += field_matched
        ratios.append(((matched / methods) if methods else 1.0, cls, matched, methods))
        print(f"{cls:68} {methods:7d} {matched:4d} {fields:7d} {field_matched:4d}")

    if total:
        print(f"\nPUBLIC/PROTECTED METHODS: {covered}/{total} = {100.0 * covered / total:.1f}%")
    if field_total:
        print(
            f"PUBLIC/PROTECTED FIELDS:  {field_covered}/{field_total} = "
            f"{100.0 * field_covered / field_total:.1f}%"
        )
    print(f"\nWorst {args.top} (classes with at least 10 public members):")
    shown = 0
    for ratio, cls, matched, methods in sorted(ratios):
        if methods >= 10 and shown < args.top:
            print(f"  {ratio * 100:5.1f}%  {cls} ({matched}/{methods})")
            shown += 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
