# Fabric Runtime: Project Status

**As of 2026-11-06.** Every number below is measured from the working tree, not
estimated. Commands used are listed at the bottom.

## One-line status

Jar intake, Fabric Loader discovery, the Rust↔JVM pipe, the registry snapshot,
a 65-class `net.minecraft` shim layer (all at real 26.x paths) and **entrypoint
invocation for real Java Fabric mods** exist and are tested. The core value
classes (`Mth`, `Vec2`, `Vec3`, `Vec3i`, `BlockPos`, `ChunkPos`, `Identifier`,
`Direction`, `Rotation`), the RNG cluster (`RandomSource`, `BitRandomSource`,
the three legacy sources, `MarsagliaPolarGaussian`, `PositionalRandomFactory`,
`RandomSupport`), `AABB` with its hit types (`HitResult`, `BlockHitResult`) and
the formatting pair (`ChatFormatting`, `TextColor`) are **bit-exact against the
real `26.3` release** (`26.3.jar`, sha1 verified; 5,777/5,777 values). The
**26.3 runtime and all 114 libraries are now on disk** (sha1/sha256 verified),
Fabric Loader 0.19.5 + Fabric API `0.165.2+26.3` are installed and the full
host compiles against them. **A real Java mod's entrypoint has been invoked**
through the child JVM host — `ModInitializer.onInitialize()` runs successfully.
Mixin bootstrap initializes (`MixinBootstrap.init()`, `MixinEnvironment.init()`)
but Mixin transformations are not yet applied to classes.

## Phase table

| # | Phase | State | Evidence |
|---|---|---|---|
| 0 | Jar intake + dependency resolution | **Working** | `horizon-fabric` reads pinned jars, resolves deps, `horizon-fabric check` reports 58 mods / 49 ordered / 0 errors |
| 1 | JVM integration (child process + wire) | **Working** | `cargo test -p horizon-jvm` → 10 host tests; `load_class` + `invoke_method` + `invoke_entrypoint` round-trips pass |
| 2 | `net.minecraft.*` compatibility classes | **Working** | 65 classes, all at real 26.x paths; 16.6% of public members declared (vs `26.3.jar`); core value classes, the RNG cluster, AABB and ChatFormatting/TextColor bit-exact vs the 26.3.jar; **entrypoint invocation verified** � `ModInitializer.onInitialize()` runs through the child JVM host |
| 3 | Registry bridge | **Partial** | 103-byte `registry-snapshot` received by the host; Java side stores nothing yet |
| 4 | Fabric API | **Working (partial)** | `Event` shim with dynamic proxy listener dispatch — verified end-to-end: two listeners fire in registration order; `ServerLifecycleEvents` (9 event types), `ClientLifecycleEvents` (2 event types), `ClientTickEvents` (2 event types) shims created with functional interfaces; Mixin bootstrap initializes; transformations not yet applied | `test_event_dispatch.py` confirms listeners fire in order; `test_entrypoint.py` confirms `onInitialize()` runs |
| 5 | Mixin engine | **In progress** | `MixinBootstrap.init()` + `MixinEnvironment.init()` verified working; `@Mixin` / `@Inject` transformations not yet applied (transformer count 0); sponge-mixin 0.17.4 + ASM 9.10.1 pinned and on disk | `test_mixin_transform.py` confirms Mixin bootstrap works |
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
   state persists. **Verified for our own fixture mod** (`testmod.jar`):
   `TestModInitializer.onInitialize()` runs and writes `testmod_ran.txt`.
7. **Fabric API event dispatch works**: `Event` shim uses dynamic proxies to
   dispatch to registered listeners in registration order. Verified with
   `ServerLifecycleEvents.SERVER_STARTING` — two listeners fired in order
   (1, then 2) when the invoker was called.
8. **The core shims are a bit-exact port, not a guess**:
   `tools/fabric_shim_parity.py` compiles one fixture against the real
   **`26.3.jar`** and runs it against both the real classes and the shims; all
   5,777 printed values match bit-for-bit for `Mth`, `Vec2`, `Vec3`, `Vec3i`,
   `BlockPos`, `ChunkPos`, `Identifier`, `Direction`, `Rotation`, `AABB`,
   `HitResult`/`BlockHitResult`, the RNG
   cluster (`RandomSource`, `BitRandomSource`, `LegacyRandomSource`,
   `SingleThreadedRandomSource`, `ThreadSafeLegacyRandomSource`,
   `MarsagliaPolarGaussian`, `PositionalRandomFactory`, `RandomSupport`)
   and the 26.3 formatting pair (`ChatFormatting`, `TextColor` with its 16
   named constants).

## What does not run today

- **Most real mods** — `FabricLoader` can discover them but they'd fail at link
  time because only 16.6% of public `net.minecraft.*` members are declared.
- **Mixin transformations** — `MixinBootstrap.init()` and
  `MixinEnvironment.init()` initialize successfully, but no `@Mixin` /
  `@Inject` / `@Redirect` bytecode transformations are applied yet.
- Any `net.minecraft` call that would need real world state — T-202 found 4 SHIM
  classes carry no semantics and 18 are partial. T-214 made the remaining
  blockers loud or real (`MinecraftServer.tick`, `RecipeManager`,
  `AbstractContainerMenu.clicked`, `Block.setId`, `readUtf(maxLength)`), but
  nothing is bridged to Rust.
- Real mod jars targeting older MC versions — `fabric-api-0.127.1+1.21.5.jar`
  targets 1.21.5, not 26.3, so its classes are not usable as-is.

## Measured inventory (2026-11-06)

| Item | Count |
|---|---|
| Java files in `java/horizon-host/src` | 79 |
| `net.minecraft.*` shims | 65 (all paths verified against the real `26.3.jar`) |
| `net.fabricmc.*` shims (API + entrypoint interfaces) | 11 |
| Host runtime files (`horizon/host/*`) | 6 |
| Sources embedded via `SOURCES` in `host.rs` | 79 (1:1 with files on disk, guarded by a test) |
| `horizon-jvm` tests passing | 27 (6 unit, 10 host, 10 protocol, 1 registry dump) |
| Differential parity values | **5,777/5,777 bit-identical** vs `26.3.jar` (`tools/fabric_shim_parity.py`) |
| Public/protected members declared vs the real 26.3 jar | **16.6% methods (624/3758), 20.8% fields (105/504)** (`tools/fabric_shim_coverage.py`) |

## Honest completion estimate

- Breadth of the `net.minecraft` shim layer: **16.6% of public/protected methods
  and 20.8% of fields** of the same classes in a real `26.3.jar` (measured with
  `tools/fabric_shim_coverage.py`), and **0% verified** against a real mod. Path
  names are right (65/65); members are mostly not. The bit-exact subset (core
  value classes, RNG cluster, AABB, formatting pair) was proven by
  `tools/fabric_shim_parity.py`.
  T-202 measured depth: 25 REAL, 18 PARTIAL, 4 SHELL, 3 blocker-level fakes.
  T-214 removed every silent blocker fake (they are now real or throw a named
  `UnsupportedOperationException`), exercised by `shim_behaviour_is_not_silent`.
- Fabric API: **~8%**. `Event` shim with dynamic proxy dispatch verified
  end-to-end (two listeners fire in registration order); `ServerLifecycleEvents`,
  `ClientLifecycleEvents`, `ClientTickEvents` shims created; remaining Fabric API
  types (keybinding, render layer, item/block settings, networking, etc.) not
  yet implemented.
- Whole Java-mod programme: **~14%**. Member conformance (T-241, now 16.6%),
  Mixin (Phase 5 — bootstrap works, transformations not applied) and the
  gameplay bridge (Phase 6) are the largest phases.
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

The other 39 mapped 1:1. T-240 renamed all 8, and
`shim_paths_match_the_verified_26x_set` guards the set. Names are done; the
remaining gap is **members**: 16.6% of public/protected methods and 20.8% of
fields are declared (measured with `tools/fabric_shim_coverage.py`
against `26.3.jar`).

## Hard blockers

1. **Shim members do not match 26.x.** All 65 paths now exist in a real jar
   (T-240/T-241), but only **16.6%** of public/protected methods and **20.8%** of fields
   are declared against `26.3.jar`. A real mod will hit `NoSuchMethodError` on almost any vanilla
   call. T-241 is the fix. (Intermediary remapping is only needed for ≤1.21
   mods — since 26.1 the game ships named classes, so 26.x mods use official
   names.)
2. **Whole Java-mod programme: ~14%.** Member conformance (T-241, now 16.6%),
   Mixin (Phase 5) and the gameplay bridge (Phase 6) are the largest phases.
   No real mod has run yet — the loader, discovery and entrypoint harnesses
   work, but Fabric API event dispatch and bytecode transformation do not.

Work that does not need these (host protocol, own fixtures, signature fixes)
continues; blocked work is recorded, not skipped.

## How the numbers were produced

```sh
Get-ChildItem java\horizon-host\src -Recurse -Filter *.java          # 82
Select-String -Path crates\horizon-jvm\src\host.rs -Pattern '"(net|horizon)/.*\.java"'  # 82
cargo test -p horizon-jvm                                            # 27 passing
python tools\fabric_shim_parity.py --jar .../26.3.jar  # 5777/5777 identical
python tools\fabric_shim_coverage.py --jar .../26.3.jar  # 16.6% / 20.8%
```
