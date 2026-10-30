# Fabric compatibility: roadmap

Each phase has an **entry gate** (what must already be true), a **deliverable** (what a player or a
maintainer can see), and an **exit gate** (the check that must pass before the next phase starts).
A phase that misses its exit gate does not move on; the roadmap's dates are deliberately absent
because the honest unknown is Phase 5, not the calendar.

Rule for every phase: the game keeps compiling, `python3 tools/pr_ready.py` stays green for the
crates touched, and nothing already shipped regresses.

## Phase 1 — Mod jar intake (done)

**Entry**: nothing. Today Horizon cannot even answer "what is in this mods folder".

**Deliverable**: `crates/horizon-fabric` and its `horizon-fabric` binary.

- Read `.jar` (and `META-INF/jars/*.jar` nested jars) without running Java.
- Parse `fabric.mod.json` (schema 1 and 2) strictly, with file and mod id in every error.
- Fabric version predicates: `*`, exact, `>=`, `>`, `<=`, `<`, `=`, `~`, `^`, `x` wildcards, AND by
  space, OR by JSON array.
- Resolve a load order: hard dependencies missing / version-mismatched, `breaks`, duplicate ids,
  environment filtering, nested-jar flattening.
- Class census per jar (constant-pool references, mixin configs, access wideners, Kotlin markers),
  the raw material for COMPATIBILITY.md.
- `horizon-fabric check <dir> [--game-version V] [--side client|server] [--json]`.

**Exit**: `cargo test -p horizon-fabric` green; a directory of hand-built jars in
`tests/` produces a stable plan and stable diagnostics.

**Built and checked** (2026-10-30): 26 unit + 3 end-to-end tests pass; the five pinned real mods
produce 58 mods, 49 loading, 0 errors, 0 warnings (`COMPATIBILITY.md` §Phase 1). The real-jar run
also turned three Loader rules from assumptions into code:

- A duplicate id is **selected**, not refused. Root jar first, then newer version, then less nested,
  then file name, mirroring `ModPrioSorter` in Loader 0.19.5. Sodium's eight embedded Fabric API
  modules collide with the Fabric API download in a normal folder, so the old behaviour made a
  correct folder look broken.
- `provides` is honoured: a dependency may name an alias, and a mod with the real id wins.
- A `Class::method` entrypoint is split into class and method; the class is what must exist, and the
  check covers nested jars because they share the loader's class path.

**Explicitly not**: no JVM, no Fabric Loader, no claim that any mod runs.

## Phase 2 — The Java host (no Minecraft classes yet)

**Entry**: Phase 1.

**Deliverable**: `crates/horizon-jvm` + `java/horizon-host/`.

- JDK discovery through `horizon-prepare::java::find_runtime` (Java 25 for 26.3, never the
  player's random `java`), version checked.
- Spawn a host process, handshake with a versioned frame protocol over a pipe, heartbeat, timeout,
  clean shutdown, restart after a crash, crash log with the host's stderr.
- Host loads Fabric Loader's **discovery** path only (no game provider): it reports the loader's own
  view of the mod set. This is a cross-check of Phase 1, not a second implementation.
- Fabric Loader and libraries come from the existing pinned lock
  (`tools/benchmark-deps/fabric.lock.json`) with SHA-256 verification; nothing is vendored.
- `horizon-client --fabric-probe <dir>` prints the loader's report; the launcher shows it on the
  Mods page under a "Check with Fabric Loader" row.

**Exit**: a test starts a real JVM, gets a report for a fixture mods folder, kills the host mid-run
and observes a clean error; startup cost measured and recorded in PERFORMANCE.md.

**Explicitly not**: Fabric API, mixins applied to anything, a world.

## Phase 3 — Registry bridge and the first running mod

**Entry**: Phase 2; registry snapshot and delta format agreed with `horizon-client`'s data load.

**Deliverable**: one real, popular Tier 1 mod running natively, named in COMPATIBILITY.md as
tested.

- Registry snapshot Rust → Java; delta Java → Rust; frozen ids; failure aborts world load.
- `net.minecraft` shim root: `Identifier`, `Registry`, `Item`, `ItemStack`, `Block`, `BlockState`,
  `ItemGroup`, `CreativeModeTab` and what registering them needs.
- Event bridge v1: at minimum `ServerLifecycleEvents`, `PlayerBlockBreakEvents`,
  `UseBlockCallback`, `ServerTickEvents`, `CommandRegistrationCallback`.
- Options > Mods and the launcher list Java mods with the same trust/status language as native
  mods; a mod that failed shows the shim diagnostic, not a stack trace.
- Crafting a world: a Java-added block places, breaks, drops and crafts; the item exists in Rust.

**Exit**: `tools/fabric_compat.py --mod <slug> --tier 1` passes end to end (install → world →
place → break → craft → save/load), with the mod's own jar from the player's download. If no Tier 1
mod exists that can pass, the shim's event coverage is the gap — fix it, do not lower the bar.

## Phase 4 — Fabric API base, resources and screens

**Entry**: Phase 3.

**Deliverable**: shim coverage for the API mods use in practice: Base, Lifecycle (all), Interaction
(both), Command, Registry, Block/Item/Entity events, Resource Loader, Key Binding. Resource packs
and data packs added by a mod reload through Horizon's pack pipeline. Key bindings appear in
Options > Controls.

**Exit**: the Fabric API submodules in scope pass their own registration paths; three Tier 1 mods
(one of them a library consumer such as Cloth Config) run concurrently without id collisions; the
event bridge's ordering rules are tested (a mutation in an event is applied at the documented
point, not mid-tick).

## Phase 5 — The honest verdict on tiers 2 and 3

**Entry**: Phase 4; a written list of the top 100 mods by downloads and the layer each hooks.

**Deliverable**: a decision, made with measurements rather than hope:

- For each Tier 2 candidate: an experiment that routes the method through Java and measures the
  tick cost. If the cost is within budget and the semantics hold, mirror it; otherwise mark it
  unsupported natively.
- For Tier 3: report that the native path cannot host it, and specify compatibility mode
  (below). COMPATIBILITY.md's `native` column says "not by design", with the layer that makes it so.

**Exit**: the top-100 table is filled with `native` and `compat-mode` verdicts backed by a test or a
measurement. No row says "should work".

## Phase 6 — Compatibility mode (the real Minecraft runtime)

**Entry**: Phase 5 (so we know what it is for), plus a licence review.

**Deliverable**: a second instance kind that runs the player's own client jar with Fabric Loader
and Fabric API, either with renderer substitution (B1) or as a Fabric server the Rust client joins
(B2), as decided in Phase 5.

- Worlds, mods, options and logs live in the instance, exactly like a native instance.
- The launcher shows which mode an instance is, and warns that it does not have native performance.
- Horizon's client mods, resource packs and shaders that can work in that mode are listed.

**Exit**: `tools/fabric_compat.py --mode compat --mod sodium` (and three others across categories)
runs a world; the result is a COMPATIBILITY.md row. Multiplayer handshake works (§ Phase 7).

## Phase 7 — Multiplayer

**Entry**: Phase 3 (server-side events) and Phase 6 (if compatibility mode is the only route).

- Mod handshake: `minecraft:register`-style channel advertisement, mod list and versions exchanged
  at login, refusal with a readable reason (vanilla/Fabric clients and Horizon clients both).
- Registry sync for Java-side content; a client whose shim lacks a mod's payload gets a specific
  error, never a kick with no message.
- Custom payload pass-through in `horizon-protocol`, bounded sizes, per-channel rate limits.
- Dedicated server: host process runs headless beside `horizon-server`.

**Exit**: `tools/lan_e2e.py` gains a modded case: two Horizon clients, one server, one Java mod's
block and one payload; a vanilla client joining a modded Horizon server is refused with the reason.

## Phase 8 — Mod management and the compatibility database

**Entry**: Phases 1–4 running.

- `mods/` is the Java folder; nested jars resolved; enable/disable; per-mod logs.
- Dependency resolution errors surface in the launcher with the mod that needs what.
- `COMPATIBILITY.md` generated from `tools/fabric_compat.py` runs (never hand-edited rows).
- Registry (`horizon-mods`) may list a Java mod only with a measured verdict and a pinned hash.
- Crash diagnostics: a JVM crash report names the mod whose stack frame is topmost non-game code.

**Exit**: 30 real mods across the issue's categories have a row, each with a harness command and a
date; the table is regenerated by CI (nightly, not per PR).

## Phase 9 — Performance

**Entry**: Phase 3 (first mod) and Phase 6 (if shipped).

- Bridge budget enforcement: p99 ≤ 1 ms per tick at 20 TPS with 50 mods' worth of events.
- Registry snapshot size and world-load delta measured against vanilla's own load.
- `--benchmark` variants: no mods, Java runtime idle, Fabric API only, N mods.
- No benchmark published without the correctness harness passing in the same run.

**Exit**: PERFORMANCE.md carries measured tables from at least two machines, with the method that
produced them.

## Phase 10 — Scale and long-term maintenance

- Version port: the shim must be regenerated/verified per Minecraft version; the process is
  documented and timed, because Horizon's value is that it tracks vanilla quickly.
- Kotlin mods: language adapters (Fabric's `languageAdapter`) plus a pinned Kotlin runtime, tested
  (Phase 4 if it lands early).
- Modpacks: a pack's Java mods get a load plan, blocked mods listed, no silent omission.
- Removal policy: a mod that no longer works is marked in the DB with the version that broke it.

## Size, honestly

| Phase | Nature | Rough effort |
|---|---|---|
| 1 | Rust, no external system | days (this change) |
| 2 | process + protocol + loader discovery | 1–2 weeks |
| 3 | shim root + registry bridge + events | 4–8 weeks, and it is the real gate |
| 4 | Fabric API coverage | 6–12 weeks, ongoing |
| 5 | measurement + decision | 1–2 weeks |
| 6 | second runtime | 8–16 weeks, high uncertainty |
| 7 | protocol + handshake | 3–5 weeks |
| 8 | harness matrix + CI | ongoing |

Nothing above depends on the others being perfect, but Phase 3 cannot be skipped: it is where the
architecture either proves it can host a mod or does not.

## What this roadmap refuses to promise

- That popular performance mods will run natively. They will not (Tier 3).
- A date for "Fabric API works". Phase 4 is the long pole and its size depends on Phase 3's shape.
- That a mod's behaviour is identical to vanilla with other mods loaded. Horizon defines its own
  event order, and this is documented rather than hidden.
