# Fabric Runtime: Project Status

**As of 2026-11-04.** Every number below is measured from the working tree, not
estimated. Commands used are listed at the bottom.

## One-line status

Jar intake, Fabric Loader discovery, the Rust↔JVM pipe, the registry snapshot,
a 47-class `net.minecraft` shim layer and **entrypoint invocation for our own
fixture mods** exist and are tested. **A real Java mod still does not run**:
8 shims use names absent from a 26.x jar, Mixin is absent, and Fabric API event
dispatch is not implemented.

## Phase table

| # | Phase | State | Evidence |
|---|---|---|---|
| 0 | Jar intake + dependency resolution | **Working** | `horizon-fabric` reads pinned jars, resolves deps, `horizon-fabric check` reports 58 mods / 49 ordered / 0 errors |
| 1 | JVM integration (child process + wire) | **Working** | `cargo test -p horizon-jvm` → 9 host tests; `load_class` + `invoke_method` + `invoke_entrypoint` round-trips pass |
| 2 | `net.minecraft.*` compatibility classes | **In progress, unverified** | 47 classes compile into the host; T-202 audit: 25 REAL / 18 PARTIAL / 4 SHELL, then T-214 de-faked the 3 blockers + 3 minors; 0 loaded by a real mod |
| 3 | Registry bridge | **Partial** | 103-byte `registry-snapshot` received by the host; Java side stores nothing yet |
| 4 | Fabric API | **Started, wrong signatures** | 8 types; T-201 against the real jar: 4 MISMATCH, 4 ABSENT; no listener has been fired |
| 5 | Mixin engine | **Not started** | 0 transformations; sponge-mixin 0.17.4 + ASM 9.10.1 pinned and on disk |
| 6 | Gameplay compatibility | **Not started** | 0 Java calls reach the Rust engine |
| 7–11 | UI, multiplayer, perf, tests, `.hmod` removal | **Not started** | — |

## What actually runs today

1. `horizon-fabric` scans a `mods/` folder, reads `fabric.mod.json`, resolves
   versions and dependencies, orders the load.
2. `horizon-jvm` spawns a supervised child JVM running Fabric Loader's discovery
   path, with a framed pipe (8 MiB data cap, 4 KiB control cap).
3. Frames implemented on both sides: `HELLO`, `HELLO_ACK`, `PING`, `PONG`,
   `DISCOVER`, `DISCOVER_RESULT`, `LOG`, `FATAL`, `SHUTDOWN`,
   `REGISTRY_SNAPSHOT`, `EVENT`, `LOAD_CLASS`, `CLASS_LOADED`, `INVOKE_METHOD`,
   `INVOKE_RESULT`, `ENTRYPOINT`.
4. `horizon-registry-dump` reads `facts.json` through
   `horizon_world::registry::BlockRegistry` and sends a snapshot over the pipe.
5. The host can define and call a class that ships inside the host itself
   (`tests/fixtures/HorizonTest.java`).
6. **A mod entrypoint runs from a jar**: `host.invoke_entrypoint(jar, class,
   "main", id)` loads the class out of its own jar, instantiates it and calls
   `ModInitializer.onInitialize()`, with the loader cached per jar so static
   state persists. Verified for our own fixture only.

## What does not run today

- A **real** mod's `ModInitializer` — the 8 shims that use names absent from a
  26.x jar (T-240) plus the incomplete API mean most real mods would fail to
  link even before Mixin runs.
- Any Mixin — no bytecode is transformed.
- Any Fabric API event — registration stores nothing, no invoker exists.
- Any `net.minecraft` call that would need real world state — T-202 found 4 SHIM
  classes carry no semantics and 18 are partial. T-214 made the remaining
  blockers loud or real (`MinecraftServer.tick`, `RecipeManager`,
  `AbstractContainerMenu.clicked`, `Block.setId`, `readUtf(maxLength)`), but
  nothing is bridged to Rust.
- Real mod jars at runtime — `fabric-api-0.127.1+1.21.5.jar` is read but targets
  1.21.5, not 26.3, so its classes are not usable as-is.

## Measured inventory (2026-11-04)

| Item | Count |
|---|---|
| Java files in `java/horizon-host/src` | 64 |
| `net.minecraft.*` shims | 47 |
| `net.fabricmc.*` shims (API + entrypoint interfaces) | 11 |
| Host runtime files (`horizon/host/*`) | 6 |
| Sources embedded via `SOURCES` in `host.rs` | 64 (1:1 with files on disk, guarded by a test) |
| `horizon-jvm` tests passing | 26 (6 unit, 9 host, 10 protocol, 1 registry dump) |

## Honest completion estimate

- Breadth of the `net.minecraft` shim layer: **~12%** (47 of an estimated 300+
  classes a typical Fabric mod touches), and **0% verified** against a real mod.
  T-202 measured depth: 25 REAL, 18 PARTIAL, 4 SHELL, 3 blocker-level fakes.
  T-214 removed every silent blocker fake (they are now real or throw a named
  `UnsupportedOperationException`), exercised by `shim_behaviour_is_not_silent`.
- Fabric API: **~5%**. All 8 existing types need signature work; dispatch is 0%.
- Whole Java-mod programme: **~9%**. Remapping (T-230/T-231), Mixin (Phase 5) and
  the gameplay bridge (Phase 6) are the largest phases and are at 0%.
- Do not read the class count as progress toward "mods work". A shim that has
  never been loaded by a mod is untested, not done.

## Namespace finding (2026-11-04, corrected)

T-201 audited a **1.21.5** Fabric API jar, which is from the obfuscated era, so
its bytecode uses **intermediary** names (`net/minecraft/class_2960`). That is
true of that jar, but it is **not the situation for 26.x mods**:

- The repo's own `docs/fabric-compatibility/ARCHITECTURE.md` §1 says that since
  26.1 the game ships unobfuscated.
- Measured: `26.1.2.jar` (30,675 entries) contains
  `net/minecraft/world/level/block/Block.class` and **zero** `net/minecraft/class_*.class`
  entries. Mods built for 26.x therefore reference official names directly.
- Fabric Loader's runtime remapper and 26.3 mappings are **absent** from this
  machine anyway (intermediary stops at 1.21.6, no `.tiny` files), so building
  the remapper first would be speculative work.

**Consequence:** remapping (T-230) is *legacy support for ≤1.21 mods*, not the
critical path. The real blocker is that 8 of our 47 shims use Yarn/older names
that do not exist in a 26.x jar. Measured against `26.1.2.jar`:

| Our shim | Real 26.x path |
|---|---|
| `net/minecraft/attributes/Attribute` | `net/minecraft/world/entity/ai/attributes/Attribute` |
| `net/minecraft/client/MinecraftClient` | `net/minecraft/client/Minecraft` |
| `net/minecraft/network/chat/Text` | absent (no such class) |
| `net/minecraft/resources/ResourceLocation` | `net/minecraft/resources/Identifier` |
| `net/minecraft/util/Identifier` | `net/minecraft/resources/Identifier` |
| `net/minecraft/world/entity/player/PlayerEntity` | `net/minecraft/world/entity/player/Player` |
| `net/minecraft/world/level/ClientWorld` | `net/minecraft/client/multiplayer/ClientLevel` |
| `net/minecraft/world/level/ServerWorld` | `net/minecraft/server/level/ServerLevel` |

The other 39 map 1:1. Fixing the 8 is T-240 and comes before Mixin.

## Hard blockers

1. **No Minecraft Java 26.3 runtime on this machine**
   (`%APPDATA%\.minecraft\versions` stops at 26.1 snapshots). Mixin targets and
   gameplay bridging need the real 26.3 class files.
2. **Shim names/signatures do not all match 26.x.** 8 of 47 shims use
   Yarn/older names that do not exist in `26.1.2.jar`
   (`MinecraftClient`, `util.Identifier`, `PlayerEntity`, …). Measured, and
   T-240 is the fix. (Intermediary remapping is only needed for ≤1.21 mods —
   since 26.1 the game ships named classes, so 26.x mods reference official
   names.)
3. **Pinned Fabric libs are not on disk** and the network is blocked here, so
   the full-host `compile()` test skips locally; it runs in CI with
   `.local/fabric-libs`.

Work that does not need these (host protocol, own fixtures, signature fixes)
continues; blocked work is recorded, not skipped.

## How the numbers were produced

```sh
Get-ChildItem java\horizon-host\src -Recurse -Filter *.java          # 64
Select-String -Path crates\horizon-jvm\src\host.rs -Pattern '"(net|horizon)/.*\.java"'  # 64
cargo test -p horizon-jvm                                            # 26 passing
```
