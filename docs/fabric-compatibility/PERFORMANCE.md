# Fabric compatibility: performance

This page is the only place compatibility work may claim a cost. A number here has a machine, a
build, a command and a date. Targets are marked **target** and are not measurements; when the
measurement exists, the target row is replaced rather than kept beside it.

Status: **Phase 1 measured; Phases 2+ not measured yet.** The JVM host, the loader probe and the
event bridge do not exist, so this page currently answers one question — what does reading a real
mod folder cost — and leaves the rest explicitly empty. That is deliberate: an invented IPC budget
would become a design constraint nobody verified.

## Machine and method

| Item | Value |
|---|---|
| CPU | AMD Ryzen 5 7535HS (6 cores / 12 threads, 3.3 GHz) |
| RAM | 7.3 GiB |
| OS | Windows 11 Home 10.0.26200 |
| Rust | rustc 1.99.1, `x86_64-pc-windows-gnu`, release profile |
| JVM | Oracle JDK 21.0.5 (`C:\Program Files\Java\jdk-21`) |
| Date | 2026-10-30 |

Method: each run is a fresh process, launched and timed with .NET `Stopwatch` around
process creation plus exit. Timings are reported as median with min/max over *n* runs; a single run
is never quoted. Peak working set is sampled every 20 ms while the process lives and reported as the
maximum sample, which is a lower bound on the true peak. Nothing else runs during a measurement
batch except the measuring shell.

## Measured: reading five real mod jars

Input: the five mods pinned in `tools/benchmark-deps/mods.lock.json` (SHA-512 verified), copied to a
single `mods/` folder — Sodium 0.9.2+mc26.3, Lithium 0.26.2+mc26.3, FerriteCore 9.0.0,
Fabric API 0.161.0+26.3, ImmediatelyFast 1.17.1+26.3.

```text
horizon-fabric check <mods-folder> --loader-version 0.19.5 --json
```

| Measurement | Result |
|---|---|
| Wall time, 5 jars / 58 mods, n = 7 | median **305 ms**, min 283 ms, max 688 ms |
| Same command on an empty folder, n = 5 | median **134 ms** (process start, no jars) |
| Peak working set, n = 1 | **6.9 MiB** (sampled, so a lower bound) |
| Data read | 5 jars, 5.6 MB, 58 mods, 80 mixin configs, 43 access wideners |
| Class references extracted | 2441 distinct `net/minecraft/*`, 505 graphics |

So the census itself costs roughly 170 ms above process start for this folder, and 6.9 MiB says the
census structures, not the archives, dominate memory. Both are small enough that a launcher can run
the check on the Mods page without a spinner.

Caveats, stated so nobody over-reads the table:

- **One machine, one folder.** 58 mods from 5 jars is not a large folder: Fabric API nests 44 mods,
  so "58" is mostly metadata, and a 300-mod folder has far more classes to census. Phase 8's modpack
  work must measure that case before quoting these numbers to players.
- The 688 ms maximum is a real outlier (2.2× the median) seen once in seven runs; it is kept in the
  table rather than trimmed. Cause not investigated: filesystem or antivirus interference is the
  usual suspect on this machine.
- The debug build of the same binary takes about 1.1 s for this input, so the numbers above are not
  achievable in `cargo run`; only the release binary is representative.
- Nothing here includes the cost the JVM will add. That is the next section.

## Measured: JVM start on this machine

Same JDK, trivial class (`Hello.class`, prints one byte), n = 7:

| Measurement | Result |
|---|---|
| `java -version` | min **179 ms**, median **185 ms**, max 215 ms |
| `java -cp <dir> Hello` | min **201 ms**, median **215 ms**, max 235 ms |

This is the floor for Phase 2's child process: ~0.2 s before any loader code runs. Two related
measurements are **not** in this table:

- Starting the JVM with the five mod jars on its classpath was measured **once at 314 ms**. It is
  not in the table because a repeated batch could not be completed: the runs stalled inside the
  measuring shell rather than in the JVM (the child process had already exited; no `java.exe`
  remained). Each jar individually starts in ~0.2 s. Phase 2 must measure this from Rust, where the
  host's stdio belongs to the game rather than to a scripting shell — and must treat "the host
  process exited but the parent never noticed" as a bug class, because that is exactly what the
  stall looked like.
- Fabric Loader startup is not measured. Loader needs a game provider and would fail before doing
  useful work; measuring it without Phase 2's host would measure the failure path, not the loader.

## Budgets for Phase 2 (targets)

These are constraints the host must satisfy, agreed before implementation so the protocol is not
designed after the fact. They are **targets**, backed by the two measured facts above (0.2 s JVM
floor, 1.1 s for a full jar census) and by the roadmap's tick budget (20 TPS = 50 ms).

| Target | Value | Why | How it will be measured |
|---|---|---|---|
| Host spawn → handshake complete | ≤ 1.5 s p95 | one-time at launch; a player notices 3 s | stopwatch from `Command::spawn` to the first acknowledged frame, 20 runs |
| Discovery report for 50 mods | ≤ 2.0 s after handshake | loader re-reads every jar; must not double the 1.1 s Rust census | same folder as above, loader's own report timed in `horizon-fabric`'s harness |
| Control round-trip p99 | ≤ 1 ms | 50 ms tick; a bridge call must not be visible in frame time | ping frame over the pipe, 10 000 iterations, p50/p99 reported |
| Max control frame | 4 KiB | anything larger is a payload, not a control message | enforced in code, tested by a rejected oversized frame |
| Registry snapshot throughput | ≥ 16 MiB/s | world load already blocks; the bridge must not add seconds | 100 MiB synthetic delta over the pipe, timed |
| Heartbeat / crash detection | ≤ 2 s | a frozen host must not freeze the game | SIGKILL the host mid-run, time to the Rust-side error |
| Clean shutdown | ≤ 500 ms, forced kill at 5 s | quitting must not hang on a mod's shutdown hook | 20 runs with a mod that registers a 2 s shutdown hook |
| Host idle RSS | ≤ 512 MiB | loader + Fabric API + 50 small mods, before any world | sampled as in this page, at idle and after a world tick loop |

When Phase 2 lands, each row gains a measured column with date and machine; the target text is
deleted, not kept alongside. If a target turns out wrong, the change is a documented decision in
this file, not a silent edit.

## Rules for numbers on this page

- A number without machine, build profile, command, `n` and date is not allowed here.
- Medians with spread. A single run never becomes a row.
- Jar intake is measured on the pinned mod set; tick/registry numbers use the existing benchmark
  harness with a fixed seed, 600 ticks, first 100 discarded, and the same run must pass the
  correctness harness (ROADMAP phase 9).
- The native engine's own performance work lives in `docs/performance/` and `docs/prepare-performance.md`.
  This page only covers cost added *by* Fabric compatibility; it never re-states engine numbers.
- A target that is missed is recorded as missed. "Close enough" is not a status.

## What is not measured yet, and will not be guessed

- Fabric Loader discovery cost (Phase 2).
- Registry snapshot and delta size for a real world (Phase 3).
- Per-tick cost of the event bridge with 50 mods (Phase 3/9).
- Mixin apply cost at class load (Phase 6, compatibility mode).
- Compatibility mode's own frame time, which is Minecraft's, not Horizon's (Phase 6).
