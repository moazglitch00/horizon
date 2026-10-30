# Fabric compatibility: architecture

Status: proposal, with Phase 1 (jar metadata and dependency resolution) implemented in
`crates/horizon-fabric`. **No Java code runs inside the game yet.** Nothing in this document is a
compatibility claim; claims live in [COMPATIBILITY.md](COMPATIBILITY.md) and only after a test.

The goal is stated by the issue that started this work: a player puts ordinary Fabric mods in a
folder and plays them in Horizon. This page explains what that costs, which part of it the current
architecture can deliver, and which part it cannot. Read the risks in
[§4](#4-the-impedance-mismatch-and-why-it-is-not-a-bug) before the design.

## 1. What a Fabric mod actually is

A Fabric mod jar holds:

| Part | File | What it needs from the game |
|---|---|---|
| Metadata | `fabric.mod.json` | Nothing; parsed by Fabric Loader |
| Code | `*.class` | **Minecraft's own Java classes**: `net/minecraft/client/MinecraftClient`, `net/minecraft/world/level/Level`, `net/minecraft/world/item/ItemStack`, … |
| Hooks | `*.mixins.json`, `*.accesswidener` | The **bytecode** of those classes: target method names, descriptors, ordinals, local variable slots |
| Libraries | nested jars in `META-INF/jars/` | Nothing; more mods, or libraries (Kotlin stdlib, GeckoLib, Cloth Config) |
| Data and assets | `data/`, `assets/` | A resource/data pack pipeline (Horizon has one) |

Three properties decide everything below.

1. **Mods are compiled against a namespace, not against a spec.** Production Fabric mods are
   compiled against *intermediary* names (`net/minecraft/class_310`); since 26.1 the game ships
   unobfuscated, so named and intermediary coincide. Either way every reference is to a concrete
   class and member that must exist at runtime with the same descriptor.
2. **Mixin is bytecode surgery, not an API.** `@Inject(at = @At("HEAD"), method = "setBlock")` is
   valid only if the target class has a method with that name and descriptor, and it *does
   something* only if the game actually calls that method. A shim with the right signature but a
   different body satisfies the first condition and not the second.
3. **Fabric Loader is a launch pipeline.** It resolves metadata, then builds a `Knot` classloader,
   remaps between namespaces and applies Mixin/ASM transformations while classes load. It is not a
   plugin registry that can be pointed at a different engine.

Fabric API is a mod like any other: it declares `depends` on the loader and the game, ships its own
mixin configs, and mixins into both vanilla internals and the loader. It cannot be used unmodified
against anything but real Minecraft classes unless it is forked.

## 2. What Horizon is today

A Rust (wgpu) reimplementation of vanilla 26.3's behaviour. No JVM in the game process, no
Minecraft classes, no bytecode. The state a mod would touch lives in Rust:

| State | Where | A Java-side mirror would need |
|---|---|---|
| Block states, chunks, light | `horizon-client-render/src/{chunk,chunk_map,light,streaming}.rs`, `horizon-world/src/region.rs` | ids + generation, batched reads |
| Block behaviour, ticks, redstone | `horizon-world/src/level/` | the same call graph in Java, or a command channel |
| Items, components, enchantments | `horizon-items/` | registry snapshot + mutation log |
| Entities, mob AI, physics | `horizon-client/src/{entities,mobs}`, `horizon-world/src/entity_move.rs` | entity ids + per-tick deltas |
| World generation | `horizon-world/src/worldgen/` | nothing (a mod that generates is Tier 3, §4) |
| Rendering | `horizon-client-render/src/gfx.rs` | nothing a mixin can patch |
| Networking | `horizon-protocol` | payload channel pass-through |
| Menus, screens, HUD | `horizon-client-render/src/{containers,hud}.rs`, `game/` | a widget/host API, not MC's `Screen` |

It already has the parts a JVM integration needs to stand on:

- **JDK discovery and child-JVM execution**: `crates/horizon-prepare/src/java.rs` (finds a JDK,
  `javac`, `java` with a classpath, timeouts, no console window on Windows) and its tests.
- **A pinned Fabric Loader 0.19.5 stack** for the vanilla benchmark: `tools/benchmark-deps/
  fabric.lock.json` (loader, ASM 9.10.1, Mixin, intermediary) and `tools/runtime.py`, which
  downloads and hashes it. Horizon already starts a real Fabric client for benchmarks.
- **Jar and class-file reading without running anything**: `horizon-port-pack` inspects Java jars
  and their class files; `horizon-mods` owns `.hmod` metadata, discovery and the registry.
- **Mod lifecycle surfaces**: `game/mods.rs`, `mod_content.rs`, Options > Mods, the launcher's
  Mods page with per-mod verdicts (`docs/mod-browser.md`).
- **Named native hook points**: `native-hooks.json`, `game/native_hooks.rs` — the model for a
  stable cross-boundary ABI.

## 3. The gap, in one sentence

A Fabric mod's value is the code it injects into Minecraft's simulation; Horizon's simulation is
Rust, so a mod's injection points have nothing to inject into unless that simulation is either
mirrored in Java or run in Java.

## 4. The impedance mismatch, and why it is not a bug

Compatibility is not one target. It is four tiers, decided by *where a mod hooks*:

| Tier | What the mod does | Example | Feasible with a Java facade over Rust? |
|---|---|---|---|
| **0** | Ships data/assets only; its code just registers them | Macaw's Fences (blocks, models, recipes) | Yes — already done by the port pipeline today |
| **1** | Calls public game/Fabric API: registers blocks, items, commands; listens to events | a mod adding a sword and `PlayerBlockBreakEvents` | Yes, if the facade is honest and the event bridge is driven by Rust |
| **2** | Mixins into *state* that Rust owns, to observe or adjust it | a mod that clamps fall damage in `LivingEntity.hurt` | Partly: only for methods we deliberately mirror, and the mirror must be the real path |
| **3** | Replaces or rewrites a subsystem | Sodium/Iris (renderer), Lithium (world tick), C2ME (chunk pipeline) | **No.** The code they patch is Rust; a facade has no equivalent bytecode |

Tier 3 is where most of the ecosystem's popularity sits (performance, shaders, minimap, big tech
mods), and it is precisely the tier a facade cannot reach. This is the honest core of the risk: a
"full compatibility layer" inside the Rust engine is not a matter of enough work; it is a category
error for Tier 3. Any plan that promises it is wrong.

## 5. Options considered

### A. Java facade over the Rust engine ("Fabric Compatibility Layer")

Write Java classes in package `net.minecraft.*` whose bodies forward to Rust over an FFI, run the
real Fabric Loader and Mixin against them, and fork Fabric API to compile against them.

- **Keeps**: Rust as the only simulation; native rendering, worldgen, physics; no Mojang code in
  the tree; one game, one world format.
- **Costs**: every class, field and method a mod touches must exist with the correct descriptor.
  Tier 2 works only for methods that are genuinely the mutation path, which means routing hot
  loops through Java → the performance model inverts unless every path is batched.
  Fabric API must be maintained as a fork, per game version, forever.
- **Ceiling**: Tier 2 partial, Tier 3 never.

### B. JVM-hosted compatibility mode

Run the **real** client classes (from the player's own `jars/client.jar`, which `horizon-prepare`
already imports) under Fabric Loader in a JVM, and let Rust own the window, GPU, input and audio.
Two sub-variants:

- **B1, renderer substitution**: a Horizon Fabric mod replaces vanilla's `LevelRenderer`/
  `RenderSystem` draw path and streams draw work to the Rust renderer. Mods that draw (Sodium, Iris)
  then draw through a Java pipeline that no longer exists.
- **B2, protocol bridge**: run a Fabric *server* with the mods and connect Horizon's Rust client to
  it over a mod-aware protocol. Server-side mods work natively; client-only mods (minimap, HUD,
  Sodium) do not exist in a server jar, so they cannot run.
- **Gets**: real Mixin semantics, unmodified Fabric API, Tier 1–3 as vanilla defines them.
- **Costs**: simulation, worldgen and AI run in the JVM — the exact performance advantage Horizon
  exists to have. Version coupling is total (a jar per Minecraft version). B2 needs a client-side
  equivalent of every server mod's payloads, which is another compat layer.

### C. Hybrid (recommended)

1. The **native Rust game stays the default** and the main engine.
2. A separate instance kind, *Fabric compatibility mode*, runs option **B1/B2** through the same
   launcher, so players who need a mod that cannot be facaded have a real answer.
3. The native path gains **Tier 1, then curated Tier 2** through option **A**, measured by the
   compatibility database.
4. Mods that are data-only keep using the existing port pipeline (Tier 0) — no JVM at all.

This is the only plan where every sentence we tell a player is true: which mods run natively, which
run in compatibility mode, and which do not run.

## 6. Design of the native path (option A + C)

### 6.1 Process model: the JVM is a child process, not a library

A `java` child process ("Horizon Java Host") per game instance:

- The JVM cannot be unloaded, owns process-global signal handlers and threads, and allows one
  `JNI_CreateJavaVM` per process; embedding it in a wgpu game with user-provided JDKs across three
  OSes is fragile. `horizon-prepare` already runs Java this way, with a timeout and clean failure.
- A mod that calls `System.exit`, deadlocks or crashes the JVM must not take the game down; the
  host reports the crash and the game offers to restart without that mod.
- Turning mods off and on again (mod management, §8 of the issue) is only possible across a process
  boundary.

**Transport**: a duplex pipe (Windows named pipe, Unix `socketpair`), length-prefixed frames, plus a
shared-memory ring for bulk data (chunk sections, entity snapshots). No TCP port, no network
exposure. Protocol version is negotiated at handshake; a mismatch is a startup error naming both.

**Threads**: the JVM's main thread owns all Minecraft API objects (that is load-time and runtime
truth, not a choice). Rust has a dedicated bridge thread; the render and tick threads never block
on the JVM. Requests carry a deadline; a missed deadline drops the request and logs it once.

**Data**: Rust is authoritative. Java holds *handles* (`{kind, id, generation}`), never pointers;
stale handles are a defined error, not a crash. Rust sends deltas at tick boundaries.

**Cost**: one frame of a 20 TPS tick has 50 ms; a bridge frame budget of ≤ 1 ms at p99 keeps the
cost invisible (a Phase 2 target, not a measurement — see [PERFORMANCE.md](PERFORMANCE.md)).

**Loader version is part of the contract**: `tools/benchmark-deps/fabric.lock.json` pins the loader
that runs the game (0.19.5). That jar ships MixinExtras 0.5.5 at
`META-INF/jars/mixinextras-fabric-0.5.5.jar`, so `mixinextras` is a loader-provided dependency, never
something the player installs. Rust's Phase 1 report models exactly the pinned loader's bundled mods
and warns instead of guessing for any other version; the host must report the same set, and a
mismatch between the two reports is a bug in one of them.

### 6.2 Event bridge

Rust already has the events; they become a canonical, versioned stream
(`horizon-fabric/events-v1`): tick start/end, block placed/broken/used, item use, entity
spawned/died/damaged, player join/leave, chat, command, resource reload, world open/close. Java
dispatches them to Fabric API callbacks and returns a command list (`set_block`, `give`, `spawn`,
`play_sound`, `send_message`) applied by Rust at a defined point in the next tick — never reentrant.

Ordering rule: a Java-side mutation requested during event *E* is applied after *E*'s tick phase
completes, so the simulation never sees a half-applied mod action. This is stricter than vanilla and
must be documented for mod authors.

### 6.3 Registries: the Phase 3 gate

Minecraft's registries are static Java tables filled at class initialization; Horizon's tables are
built from data at startup. A mod that registers a block must see it in Java *and* have it exist in
Rust before any world loads. Sequence:

1. Rust loads vanilla data and produces a registry snapshot (ids, components, block facts).
2. Host starts; Fabric Loader discovers mods; the snapshot is installed into the shim registries.
3. Mods' `onInitialize` runs; `Registry.register(...)` lands in the shim.
4. Host returns the delta (`new blocks/items/entities`, frozen ids).
5. Rust materializes the delta into its own tables; a world may now load.
6. Any step's failure aborts with a per-mod report; a partially registered registry is never used.

This is why mod loading cannot be "lazy": the world cannot exist before both sides agree.

### 6.4 Mixin against shims

Loader + Mixin transform shim classes. Rules:

- A mixin whose target class is missing, or whose target method/descriptor is absent, is a
  **startup error** naming mod, target and the exact missing member. No silent skip.
- `@Overwrite` and `@Redirect` on a forwarder are refused with the reason ("this member is
  implemented in Rust; the mod would replace the bridge, not the behaviour").
- Access wideners apply to shims; a widened member that does not exist is refused.
- Diagnostics are written to `logs/fabric/<mod>.log` and shown in Options > Mods and the launcher.

### 6.5 Clean-room rule

No Mojang class files, decompiled sources or mappings go in the tree or the shipped game
(`AGENTS.md`, `tools/check_public_tree.py`). Shim classes are written from the documented/public
API surface; the game runs only what the player's own installation licenses them to run. This also
means the native path cannot ship a ready-made Minecraft runtime: shim coverage is our work.

## 7. Integration points in this repository

| New / changed | Purpose | Phase |
|---|---|---|
| `crates/horizon-fabric` | jar reading, `fabric.mod.json`, version predicates, resolution, class census | **1, this change** |
| `crates/horizon-jvm` | JDK discovery (reuse `horizon-prepare::java`), host spawn, framing, restart, crash report | 2 |
| `java/horizon-host/` | loader plugin, event dispatch, shim root package, diagnostics | 2–4 |
| `java/horizon-shim/` | `net.minecraft.*` facade, generated where mechanical, hand-written where semantic | 3–5 |
| `crates/horizon-client/src/game/mods.rs` | new mod kind `java`, load plan, enable/disable | 3 |
| `crates/horizon-protocol` | `minecraft:register`/custom payload pass-through | 6 |
| `launcher/src/` | `mods/` install, per-mod status, compatibility mode instances | 3, 7 |
| `tools/fabric_compat.py` | the harness matrix that produces COMPATIBILITY.md rows | 2 onward |

`mods/` at the game-folder root is the Fabric convention; Horizon's content mods stay in
`horizon-mods/`, so the two never collide. A jar in `mods/` is never executed by the port pipeline
and a `.hmod` is never handed to the JVM.

## 8. Non-goals

- Running Tier 3 mods inside the native Rust engine. They run only in compatibility mode, if at all.
- Byte-for-byte vanilla behaviour with mods loaded (mod order, event timing and world state are
  Horizon's, and are documented differences).
- Sandboxing Java mods. They run with the player's rights, like native mods: opt-in, with the same
  warning, review and signing rules.
- Shipping Mojang code, or a Fabric Loader fork that pretends to be one.

## 9. Decisions still open

1. Compatibility mode (option B) as a first-class instance kind, or a separate launcher profile?
   Recommendation: instance kind, so the launcher's mod list and worlds stay in one place.
2. Whether Fabric API is forked (option A) or only supported in compatibility mode.
   Recommendation: compatibility mode only, until Tier 1 shim coverage is measured.
3. Shim generation from the player's own jar (reflection at startup, never shipped) versus
   hand-written classes. Recommendation: hand-written for semantics, reflection only to *verify*
   descriptors and to produce diagnostics.
