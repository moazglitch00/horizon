#!/usr/bin/env python3
"""Differential parity check for the Java shim layer against a real Minecraft jar.

The shim sources under `java/horizon-host/src/net/minecraft` are only useful if
they behave like vanilla, not merely if they compile. This tool proves behaviour
bit-for-bit:

1. compile the shims (`javac --release 21`),
2. compile `crates/horizon-jvm/tests/fixtures/ShimParity.java` against the *real*
   jar, so the fixture can only use members the real game has,
3. run the fixture twice - once on the real classes, once with the shim classes
   first on the classpath - and
4. diff the two transcripts byte for byte.

Every value is printed as raw float/double bits, so a difference in the last
place is a failure. If a shim is missing a member the fixture uses, the shim run
dies with `NoSuchMethodError`; that is also a failure, and the message names the
member.

Nothing is downloaded: pass a jar that is already on disk (default: the newest
`%APPDATA%/.minecraft/versions/*/*.jar` whose name looks like a 26.x version).

Usage:
    python3 tools/fabric_shim_parity.py
    python3 tools/fabric_shim_parity.py --jar path/to/26.1.2.jar
    python3 tools/fabric_shim_parity.py --keep --report 40
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import time

REPO = pathlib.Path(__file__).resolve().parent.parent
SRC_ROOT = REPO / "java" / "horizon-host" / "src"
SHIM_ROOT = SRC_ROOT / "net" / "minecraft"
FIXTURE = REPO / "crates" / "horizon-jvm" / "tests" / "fixtures" / "ShimParity.java"
FIXTURE_CLASS = "ShimParity"

# JDKs known on the development machines; a jar built for Java 25 cannot be read
# by javac 21 (class file 69 vs 65), and the fixture has to link against it.
CANDIDATE_JDKS = [
    r"C:\Program Files\Microsoft\jdk-25.0.5.21-hotspot",
    r"C:\Program Files\Java\jdk-25",
    r"C:\Program Files\Java\jdk-21",
    r"C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot",
]

SEP = ";" if os.name == "nt" else ":"


def exe(jdk: pathlib.Path, name: str) -> pathlib.Path:
    for candidate in (jdk / "bin" / f"{name}.exe", jdk / "bin" / name):
        if candidate.is_file():
            return candidate
    raise SystemExit(f"no {name} under {jdk}")


def jdk_version(jdk: pathlib.Path) -> int:
    """Major version of a JDK home, parsed from `release`, or 0 if unknown."""
    release = jdk / "release"
    if release.is_file():
        match = re.search(r'JAVA_VERSION="(\d+)', release.read_text(errors="replace"))
        if match:
            return int(match.group(1))
    return 0


def pick_jdk(explicit: str | None, minimum: int) -> pathlib.Path:
    if explicit:
        jdk = pathlib.Path(explicit)
        if jdk_version(jdk) < minimum:
            raise SystemExit(f"{jdk} is Java {jdk_version(jdk)}; need {minimum}+")
        return jdk
    for raw in CANDIDATE_JDKS:
        jdk = pathlib.Path(raw)
        if jdk.is_dir() and jdk_version(jdk) >= minimum:
            return jdk
    on_path = shutil.which("javac")
    if on_path is not None:
        return pathlib.Path(on_path).resolve().parent.parent
    raise SystemExit(f"no JDK {minimum}+ found; pass --jdk")


def pick_jar(explicit: str | None) -> pathlib.Path:
    if explicit:
        jar = pathlib.Path(explicit)
        if not jar.is_file():
            raise SystemExit(f"jar not found: {jar}")
        return jar
    minecraft = pathlib.Path(os.environ.get("APPDATA", "")) / ".minecraft"
    versions = minecraft / "versions"
    if not versions.is_dir():
        raise SystemExit(f"no Minecraft versions under {versions}; pass --jar")
    found = [
        (version_key(entry.name), entry)
        for entry in versions.iterdir()
        if (entry / f"{entry.name}.jar").is_file()
    ]
    if not found:
        raise SystemExit(f"no version jar under {versions}; pass --jar")
    return max(found)[1] / f"{max(found)[1].name}.jar"


def version_key(name: str) -> tuple:
    numbers = [int(part) for part in re.findall(r"\d+", name)[:3]]
    return (numbers, not re.search(r"(snapshot|pre|rc)", name, re.I))


def library_classpath(jar: pathlib.Path) -> list[pathlib.Path]:
    """The real game classpath recorded next to the jar, in its own version json."""
    minecraft = jar.parent.parent.parent
    manifest = jar.with_suffix(".json")
    if not manifest.is_file():
        return []
    data = json.loads(manifest.read_text(encoding="utf-8"))
    classpath = []
    for library in data.get("libraries", []):
        coordinates = library.get("name", "").split(":")
        if len(coordinates) < 3:
            continue
        group, artifact, version = coordinates[:3]
        classifier = coordinates[3] if len(coordinates) > 3 else None
        relative = pathlib.Path(group.replace(".", "/")) / artifact / version
        stem = f"{artifact}-{version}" + (f"-{classifier}" if classifier else "")
        candidate = minecraft / "libraries" / relative / f"{stem}.jar"
        if candidate.is_file():
            classpath.append(candidate)
    return classpath


def compile_shims(jdk: pathlib.Path, out: pathlib.Path) -> None:
    sources = sorted(SHIM_ROOT.rglob("*.java"))
    if not sources:
        raise SystemExit(f"no shim sources under {SHIM_ROOT}")
    argfile = out.parent / "shim-sources.txt"
    # javac's argument file needs one path per line, quoted on Windows.
    argfile.write_text("\n".join(f'"{s.as_posix()}"' for s in sources), encoding="ascii")
    run(
        [
            exe(jdk, "javac"),
            "--release",
            "21",
            "-encoding",
            "UTF-8",
            "-proc:none",
            "-nowarn",
            "-d",
            str(out),
            f"@{argfile}",
        ],
        "compile shims",
    )


def compile_fixture(jdk: pathlib.Path, classpath: list[pathlib.Path], out: pathlib.Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    run(
        [
            exe(jdk, "javac"),
            "-encoding",
            "UTF-8",
            "-proc:none",
            "-nowarn",
            "-cp",
            SEP.join(str(p) for p in classpath),
            "-d",
            str(out),
            str(FIXTURE),
        ],
        "compile parity fixture against the real jar",
    )


def tail(text: str, limit: int = 25) -> str:
    lines = text.strip().splitlines()
    return "\n".join(lines[-limit:])


def run(command: list[pathlib.Path | str], label: str) -> subprocess.CompletedProcess:
    argv = [str(part) for part in command]
    # Windows occasionally refuses to spawn a freshly written executable
    # (WinError 5) while the virus scanner has it open; retry briefly.
    last: OSError | None = None
    for attempt in range(5):
        try:
            result = subprocess.run(argv, capture_output=True, text=True, errors="replace")
            break
        except PermissionError as error:
            last = error
            if attempt == 4:
                raise SystemExit(f"{label} could not start: {error}") from error
            time.sleep(0.5 * (attempt + 1))
    else:  # pragma: no cover - the loop either breaks or raises
        raise SystemExit(f"{label} could not start: {last}")
    if result.returncode != 0:
        raise SystemExit(
            f"{label} failed (exit {result.returncode})\n"
            f"--- stderr ---\n{tail(result.stderr, 40)}\n"
            f"--- stdout (last 25 lines) ---\n{tail(result.stdout)}"
        )
    return result


def execute_fixture(
    jdk: pathlib.Path, classpath: list[pathlib.Path], label: str, arguments: list[str]
) -> list[str]:
    output = run(
        [
            exe(jdk, "java"),
            "-XX:+UseSerialGC",
            "-cp",
            SEP.join(str(p) for p in classpath),
            FIXTURE_CLASS,
            *arguments,
        ],
        f"run fixture ({label})",
    )
    return output.stdout.splitlines()


def diff(real: list[str], shim: list[str]) -> list[str]:
    """Compare by `key=value` line, preserving order so a lost line is visible."""
    problems: list[str] = []
    for index in range(max(len(real), len(shim))):
        left = real[index] if index < len(real) else "<missing>"
        right = shim[index] if index < len(shim) else "<missing>"
        if left != right:
            key = left.split("=", 1)[0]
            problems.append(f"line {index + 1}  {key}\n    real: {left}\n    shim: {right}")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", help="real Minecraft jar (default: newest installed 26.x)")
    parser.add_argument("--jdk", help="JDK used to compile/run the fixture (needs to read the jar)")
    parser.add_argument("--shim-jdk", help="JDK used to compile the shims with --release 21")
    parser.add_argument("--report", type=int, default=15, help="mismatches to print (default 15)")
    parser.add_argument("--keep", action="store_true", help="keep the work directory and print it")
    args = parser.parse_args()

    jar = pick_jar(args.jar)
    fixture_jdk = pick_jdk(args.jdk, minimum=25)
    shim_jdk = pick_jdk(args.shim_jdk or None, minimum=21)
    real_classpath = [jar, *library_classpath(jar)]
    if len(real_classpath) < 2:
        raise SystemExit(f"no libraries next to {jar}; is this a full version jar?")

    work = pathlib.Path(tempfile.mkdtemp(prefix="horizon-shim-parity-"))
    shims = work / "shimclasses"
    fixture = work / "fixture"
    print(f"jar:   {jar}")
    print(f"jdk:   {fixture_jdk} (fixture), {shim_jdk} (shims)")
    print(f"libs:  {len(real_classpath) - 1}")
    print(f"work:  {work}")
    compile_shims(shim_jdk, shims)
    compile_fixture(fixture_jdk, real_classpath, fixture)

    real_lines = execute_fixture(fixture_jdk, [*real_classpath, fixture], "real classes", [])
    shim_lines = execute_fixture(
        fixture_jdk, [shims, *real_classpath, fixture], "shim classes", ["shim"]
    )

    problems = diff(real_lines, shim_lines)
    print(f"\nreal lines: {len(real_lines)}   shim lines: {len(shim_lines)}")
    if not problems:
        print(f"PARITY OK: {len(real_lines)} values identical between the real jar and the shims")
        if not args.keep:
            shutil.rmtree(work, ignore_errors=True)
        return 0

    print(f"MISMATCHES: {len(problems)}")
    for problem in problems[: args.report]:
        print("\n" + problem)
    if len(problems) > args.report:
        print(f"\n... {len(problems) - args.report} more (use --report N)")
    print(f"\nwork directory kept: {work}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
