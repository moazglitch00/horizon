# Fabric Runtime: Integration Log

Newest first. Each entry is a verified integration checkpoint: what changed,
what proved it, and what it did **not** prove.

---

## 2026-11-06 — T-241 batch 2: the RNG cluster, bit-exact

### Change
- Added 8 shims, all transcribed from the real bytecode:
  - `net/minecraft/util/RandomSource.java` (the interface and the default
    `nextIntBetweenInclusive` / `triangle` / `consumeCount` / `nextInt(origin,
    bound)` bodies, plus `create`/`createThreadSafe`/`createThreadLocalInstance`),
  - `net/minecraft/world/level/levelgen/BitRandomSource.java` (48-bit
    primitives, including the rejection loop in `nextInt(int)`),
  - `LegacyRandomSource.java` (atomic LCG, `setSeed` resets the Gaussian),
  - `SingleThreadedRandomSource.java` (plain-long LCG),
  - `ThreadSafeLegacyRandomSource.java` (CAS-retry `next`, and `setSeed` does
    **not** reset the Gaussian),
  - `MarsagliaPolarGaussian.java`, `PositionalRandomFactory.java`,
    `RandomSupport.java` (Stafford mix, 128-bit seed upgrade,
    `seedFromHashOf`, `generateUniqueSeed`, nested `Seed128bit` record).
- Filled the 8 `Mth` helpers that were previously documented as blocked:
  `nextInt`, `nextFloat`, `nextDouble`, `createInsecureUUID`, `wobble`,
  `randomBetweenInclusive`, `randomBetween`, `normal`.
- `SOURCES` and the `VERIFIED` path list grew to 77 entries (1:1 with the
  files on disk).
- Extended `ShimParity.java` with 647 RNG values: draw sequences from 8 seeds
  across every `next*` method and 11 bounds; `fork`/`forkPositional`
  (`at`, `fromHashOf`, `fromSeed`, `parityConfigString`); the
  `SingleThreadedRandomSource`/`ThreadSafeLegacyRandomSource` constructors;
  Gaussian-cache behaviour across `setSeed`; `RandomSupport` mix/upgrade/hash;
  and `Mth`'s random helpers.

### Proof
```
python -u tools/fabric_shim_parity.py --report 40
real lines: 5730   shim lines: 5730
PARITY OK: 5730 values identical between the real jar and the shims

cargo test -p horizon-jvm                    # 27 passed, 0 failed
cargo clippy -p horizon-jvm --all-targets -- -D warnings   # clean
cargo fmt -p horizon-jvm --check             # clean

python tools/fabric_shim_coverage.py --jar .../26.1.2.jar
PUBLIC/PROTECTED METHODS: 555/3618 = 15.3%
PUBLIC/PROTECTED FIELDS:    60/639 = 9.4%

python tools/check_public_tree.py            # passed
python -m unittest tests.test_lab            # 16 passed
```

### Difference the oracle found (vanilla is not the obvious formula)
- `RandomSource.triangle(min, max)` is `min + max * (nextDouble() - nextDouble())`
  — it multiplies by `max`, not by `max - min`. The shim first used the
  textbook interpolation and produced 16 mismatches; the real bytecode says
  otherwise, so the shim now reproduces vanilla's arithmetic rather than
  "fixing" it. The float overload has the same shape.

### Deliberate, documented deviations
- `RandomSource.createThreadLocalInstance()` seeds from the JDK's
  `ThreadLocalRandom` instead of netty's `ThreadLocalRandom`. Both are
  thread-local and the seed is never observable, so no sequence can depend on
  it.
- `LegacyRandomSource` reports concurrent use with a named
  `IllegalStateException` instead of vanilla's
  `ThreadingDetector.makeThreadingException` (that class is not ported yet).
  Only reachable by misusing one instance from several threads.
- `RandomSupport.seedFromHashOf` uses the JDK's MD5 over the UTF-8 bytes,
  reading them as two big-endian longs, instead of Guava's `Hashing.md5` +
  `Longs.fromBytes`. Same 16 bytes, same longs; the oracle confirms it.

Did **not** prove: that a mod links or runs. This batch is still value-level
parity. Known next gaps: `AABB` + `HitResult`/`BlockHitResult` (which also
unblock `Mth.rayIntersectsAABB`), `Component`/`Style`, `BlockState`.

## 2026-11-06 — T-241 batch 1: bit-exact parity for the core value classes

### Change
- Added `tools/fabric_shim_parity.py`: a differential oracle. It compiles the
  shims (`javac --release 21`) and one fixture against the real `26.1.2.jar`
  (JDK 25), then runs the fixture twice — once on the real classes, once with
  the shim class directory first on the classpath — and diffs stdout
  **bit-for-bit**. Flags: `--jar`, `--jdk`, `--shim-jdk`, `--report N`, `--keep`.
- Added `crates/horizon-jvm/tests/fixtures/ShimParity.java`: 5,083 values across
  `Mth`, `Vec2`, `Vec3`, `Vec3i`, `BlockPos`, `ChunkPos`, `Identifier`,
  `Direction` and `Rotation`. Floats/doubles are printed as raw bits, and the
  fixture writes only through a stdout `PrintStream` captured **before**
  Bootstrap swaps `System.out`, so log4j timestamps cannot mask a difference.
- Rewrote/grew against real bytecode: `net/minecraft/core/{Vec3i,BlockPos,
  Direction}.java`, `net/minecraft/world/{level/ChunkPos,level/block/Rotation,
  phys/Vec2,phys/Vec3}.java`, `net/minecraft/resources/Identifier.java`,
  `net/minecraft/IdentifierException.java` (new).
- `SOURCES` and the `VERIFIED` path list now include `IdentifierException`
  (69 entries, still 1:1 with the files on disk).

### Proof
```
python -u tools/fabric_shim_parity.py --report 20
real lines: 5083   shim lines: 5083
PARITY OK: 5083 values identical between the real jar and the shims

cargo test -p horizon-jvm
test result: ok. 6 passed   (unit)
test result: ok. 10 passed  (tests/host.rs)
test result: ok. 10 passed  (tests/protocol.rs)
test result: ok. 1 passed   (registry dump)          # 27 total, 0 failed
cargo clippy -p horizon-jvm --all-targets -- -D warnings   # clean

python tools/fabric_shim_coverage.py --jar .../26.1.2.jar
PUBLIC/PROTECTED METHODS: 487/3558 = 13.7%
PUBLIC/PROTECTED FIELDS:    54/633 = 8.5%
```

Differences the oracle found and fixed (each read from `javap` bytecode, not
guessed):
- `Vec3i.toString()` is `BlockPos{x=…, y=…, z=…}` (Guava
  `MoreObjects.toStringHelper(this)`), not `[x, y, z]`.
- `Vec3.toString()` uses parentheses; `directionFromRotation(Vec2)` passes
  `rotation.x` then `rotation.y`.
- `Rotation.CLOCKWISE_180.getSerializedName()` is `"180"`.
- `Direction`: 2D data values are SOUTH=0, WEST=1, NORTH=2, EAST=3; `BY_2D_DATA`
  follows that order; `getClockWise`/`getCounterClockWise` map WEST↔NORTH and
  EAST↔SOUTH; `AxisDirection` names are `Towards positive`/`Towards negative`.
- `Identifier`: `bySeparator` validates and throws `IdentifierException`;
  `tryParse` delegates to `tryBySeparator`; `isValidPath("")` and
  `isValidNamespace("")` are **true**, `isValidNamespace("..")` is false;
  `hashCode = 31 * namespace.hashCode() + path.hashCode()`;
  `toShortString()` drops the `minecraft` namespace; `resolveAgainst` appends
  no `.json`.

Did **not** prove: that a mod links or runs. The fixture may only call members
that already exist in the real jar; a real mod also needs the other ~86% of
members, Fabric API, Mixin and the gameplay bridge.

## 2026-11-05 — T-240: all 45 shim paths match a real 26.x jar

### Change
- Renamed 8 shims that did not exist in `26.1.2.jar` to their real paths:
  `client.MinecraftClient`→`client.Minecraft`,
  `util.Identifier` + `resources.ResourceLocation`→`resources.Identifier`,
  `world.entity.player.PlayerEntity`→`world.entity.player.Player`,
  `world.level.ClientWorld`→`client.multiplayer.ClientLevel`,
  `world.level.ServerWorld`→`server.level.ServerLevel`,
  `attributes.Attribute`→`world.entity.ai.attributes.Attribute`.
- Deleted `net/minecraft/network/chat/Text.java`: no such class exists in the
  real jar (26.x uses `net.minecraft.network.chat.Component`).
- Updated every import/reference in the remaining shims, the behaviour fixture
  and `SOURCES` (62 entries, still 1:1 with the files on disk).
- Added `tools/fabric_shim_coverage.py`: compiles the shims and compares every
  public/protected method and field (name **and** descriptor) with a real jar.

### Proof
```
45/45 shim paths exist in 26.1.2.jar
cargo test -p horizon-jvm
test result: ok. 6 passed  (unit)
test result: ok. 10 passed (tests/host.rs, incl. shim_paths_match_the_verified_26x_set)
test result: ok. 10 passed (protocol)
test result: ok. 1 passed  (registry dump)
cargo clippy -p horizon-jvm --all-targets -- -D warnings   # clean
```

### Measurement this exposed
```
python tools/fabric_shim_coverage.py --jar .../26.1.2.jar --jdk .../jdk-21
PUBLIC/PROTECTED METHODS: 120/3338 = 3.6%
PUBLIC/PROTECTED FIELDS:    9/589 = 1.5%
```

Did **not** prove: that a mod links or runs. Path conformance is necessary, not
sufficient — a mod calls members, and 96.4% of them are missing (T-241).

## 2026-11-04 — T-214: the audited silent fakes are gone

### Change
- `Block.setId` now stores its argument (`id` is no longer `final`), so
  `getId()` can return the registry id.
- `FriendlyByteBuf.readUtf(maxLength)` now enforces the cap (and rejects a
  negative cap).
- `FriendlyByteBuf(int capacity)` no longer marks unwritten capacity as
  readable — `new FriendlyByteBuf(256).readableBytes()` was 256, so every
  write/read round-trip read 256 garbage bytes first. Found by the new fixture.
- `MinecraftServer`: `isRunning()` reads a real field (false until
  `setRunning(true)`), and `tick()` throws
  `UnsupportedOperationException("server-tick: …")` instead of silently doing
  nothing.
- `RecipeManager`: real `LinkedHashMap` store (`addRecipe`), `getRecipeFor`
  scans and calls `Recipe.matches`, `byType(null)` returns the store, and
  `byType(type)` throws a named error until a type registry exists.
- `AbstractContainerMenu.clicked(...)` throws a named `container-click` error
  instead of silently ignoring clicks.
- `BlockEntityType.create()` throws a named `block-entity-type.factory` error
  instead of returning `null`.

### Proof
`crates/horizon-jvm/tests/fixtures/ShimBehaviourTest.java` asserts every
behaviour above, and `shim_behaviour_is_not_silent` compiles all 58
`net/minecraft` sources with it, runs it on a plain JVM and requires `SHIM-OK`.

```
cargo test -p horizon-jvm
test result: ok. 6 passed  (unit)
test result: ok. 9 passed  (tests/host.rs, incl. shim_behaviour_is_not_silent)
test result: ok. 10 passed (protocol)
test result: ok. 1 passed  (registry dump)
cargo clippy -p horizon-jvm --all-targets -- -D warnings   # clean
```

Did **not** prove: that the shims match real 26.x signatures (next finding), or
that any real mod links.

## 2026-11-04 — T-230 corrected: 26.x ships named classes

T-201 measured a **1.21.5** jar and concluded real mods use intermediary names.
That is true for 1.21.x jars, but **not for 26.x**: `26.1.2.jar` has
`net/minecraft/world/level/block/Block.class` and **zero**
`net/minecraft/class_*.class` entries, so mods built for 26.x reference
official names. The repo's `docs/fabric-compatibility/ARCHITECTURE.md` already
said this ("since 26.1 the game ships unobfuscated").

No 26.3 mappings or Loader 0.19.5 exist on disk, so building a remapper first
would be speculative; it stays as legacy ≤1.21 support.

What we measured instead: 39 of 47 shims exist at the same path in
`26.1.2.jar`; 8 use Yarn/older names (`MinecraftClient`, `util.Identifier`,
`PlayerEntity`, `ClientWorld`, `ServerWorld`, `ResourceLocation`,
`attributes.Attribute`, `network.chat.Text`). That is T-240, and it is the real
next blocker.

---

## 2026-11-04 — T-202 shim audit + SOURCES regression fixed

### Regression fixed
Commit `74207ba89` had silently dropped `HorizonGameProvider.java` from
`SOURCES`. `Discovery.java` references it, so the embedded host could not
compile — and no test noticed because none compiled `SOURCES`. Restored it
(63 → 64 entries, now 1:1 with the files on disk).

### New guards
- `every_java_source_is_embedded_in_the_host`: walks
  `java/horizon-host/src` and asserts `SOURCES` and the disk agree both ways.
  This would have caught the regression.
- `host_sources_compile_into_a_class_directory`: runs the real `compile()` when
  pinned Fabric libs are available (`HORIZON_FABRIC_LIBS` or `.local/fabric-libs`),
  otherwise reports why it skipped. Not run locally (network blocked, jars
  absent); runs in CI.

### T-202 result (read-only audit of 47 shims)
25 REAL, 18 PARTIAL, 4 SHELL, 3 blocker-level fakes:
`MinecraftServer.tick`/`isRunning`, `RecipeManager` lookups,
`AbstractContainerMenu.clicked`, plus `Block.setId` discarding its argument and
`FriendlyByteBuf.readUtf(maxLength)` ignoring the cap. De-faking is T-214.

Proof:
```
cargo test -p horizon-jvm
test result: ok. 6 passed  (unit)
test result: ok. 8 passed  (tests/host.rs)
test result: ok. 10 passed (protocol)
test result: ok. 1 passed  (registry dump)
```

Did **not** prove: behaviour against a real mod; the compile test skipped here.

---
## 2026-11-04 — T-210: a mod entrypoint runs from its own jar

### Change
- `java/horizon-host/src/net/fabricmc/api/{ModInitializer,ClientModInitializer,DedicatedServerModInitializer}.java`
  — the real Fabric Loader entrypoint interfaces.
- `java/horizon-host/src/horizon/host/Entrypoints.java` — caches a
  `URLClassLoader` per mod jar, instantiates the entrypoint class and calls the
  interface method (`main` / `client` / `server`). Errors are returned as
  `error:<throwable>:<message>`, never thrown into the host loop.
- `Wire.java` + `protocol.rs` — new `ENTRYPOINT` frame (tag 16) carrying
  `jar_path`, `class_name`, `entrypoint`, `mod_id`.
- `Main.java` + `tests/fixtures/EchoHost.java` — dispatch the frame.
- `host.rs::invoke_entrypoint` + `crates/horizon-jvm/tests/host.rs::a_mod_entrypoint_runs_from_its_jar`.

Proof:
```
cargo test -p horizon-jvm
test result: ok. 6 passed  (unit)
test result: ok. 6 passed  (tests/host.rs, including a_mod_entrypoint_runs_from_its_jar)
test result: ok. 10 passed (protocol)
test result: ok. 1 passed  (registry dump)
```
The test compiles the Fabric interfaces onto the host classpath, compiles the
mod **against** them, packages *only* the mod class into a jar (as a real mod
does), then over the pipe asserts: the body ran (`initialized=true`,
`calls=1`), the cached loader kept static state across a second call
(`calls=2`), a wrong entrypoint yields `ClassCastException`, and a missing
class yields `ClassNotFoundException`.

Did **not** prove: that a real mod's jar works. Real mods reference Minecraft
and Fabric API under **intermediary** names (see T-201 below); this fixture
references only our own interfaces.

## 2026-11-04 — T-201: Fabric API shim signature audit (read-only)

Compared our 8 `net.fabricmc.fabric.api.*` shims with
`fabric-api-0.127.1+1.21.5.jar` using `javap -p`.

| Our type | Real counterpart | Verdict |
|---|---|---|
| `event.Event` (interface + `create()` + `SimpleEvent`) | **abstract class** `Event<T>`, `volatile T invoker`, phase-aware `register`, `addPhaseOrdering`, no `create()` | MISMATCH |
| `client.event.lifecycle.v1.ClientTickEvents` (interface) | **final class**, 4 static `Event` fields incl. world ticks, params `class_310`/`class_638` | MISMATCH |
| `event.lifecycle.v1.ClientLifecycleEvents` (interface) | **final class**, only `CLIENT_STARTED`/`CLIENT_STOPPING`, param `class_310` | MISMATCH |
| `event.lifecycle.v1.ServerLifecycleEvents` (interface) | **final class**, 10 events, param `MinecraftServer` | MISMATCH |
| `client.keybinding.v1.KeyBinding` | absent; real API is `FabricKeyBinding extends class_304` + `KeyBindingHelper` | ABSENT |
| `client.rendering.v1.RenderLayer` | absent; the enum is vanilla `RenderType` | ABSENT |
| `item.v1.FabricItemSettings` | absent; real is `FabricItem.Settings` (interface) | ABSENT |
| `object.builder.v1.block.FabricBlockSettings` | absent; real is vanilla `BlockBehaviour.Properties` | ABSENT |

**Consequence (blocker for real mods):** a real Fabric mod is compiled against
**intermediary** names (`net.minecraft.class_310`, `class_2960`, …), while our
47 `net.minecraft` shims use **official** names (`net.minecraft.client.MinecraftClient`,
`net.minecraft.resources.ResourceLocation`, …). Nothing links the two yet. To run
unedited mods we must either remap mod bytecode intermediary→official before
defining it (what Fabric Loader does), or ship intermediary-named shims. This is
the next architectural decision (T-230) and is why "Java mods run" is still false.

Did **not** prove: behaviour; this was a signature diff only.

---

## 2026-11-04 — Wave 1 setup

### Commit: register entity shims, de-duplicate `SOURCES`
- `crates/horizon-jvm/src/host.rs`: removed 19 duplicate entries (70 → 51),
  appended `LivingEntity`, `Mob`, `Goal`, `AttributeSupplier` → 55 entries.
- New shims: `net/minecraft/world/entity/{LivingEntity,Mob}.java`,
  `.../entity/ai/goal/Goal.java`, `.../entity/ai/attributes/AttributeSupplier.java`.

Proof:
```
cargo test -p horizon-jvm
test result: ok. 6 passed  (unit)
test result: ok. 5 passed  (tests/host.rs)
test result: ok. 10 passed (protocol)
test result: ok. 1 passed  (registry dump)
```

Did **not** prove: that any of these classes matches the real 26.3 signature, or
that a mod can use them. They compile into the host; nothing loads them.

### Agent infrastructure
- `.opencode/skills/`: 12 skills (orchestration, parallel execution, Java compat,
  Minecraft internals, Fabric API, Mixin, testing, performance, code review,
  conflict resolution, research, CI).
- `.opencode/agents/`: 10 agents with distinct models; project-manager is the
  primary, the other nine are scoped subagents.

Proof: skills appear in the live skill catalog after the write; agent files
follow the documented frontmatter (`description`, `mode`, `model`,
`permissions`, `steps`, `color`).

### Environment note
- Workspace-wide `cargo check --workspace --all-targets` still fails at the link
  step with `collect2.exe: ... CreateProcess: No such file or directory`. This is
  a `PATH` problem, not a code problem: prepend WinLibs
  `mingw64\bin` and `mingw64\libexec\gcc\x86_64-w64-mingw32\16.2.0`.
- OmniRoute MCP is registered but its gateway is not serving: every data tool
  returns `fetch failed`. It is not used for routing yet; models come from the
  built-in `opencode` provider.

---

## Earlier checkpoints (from the commit history)

| Commit | What | Verified by |
|---|---|---|
| `d93995542` | `ServerWorld` shim | `cargo test -p horizon-jvm` |
| `48e1f4eee` | event frames | host receives `EVENT` |
| `dddeec49d` | `horizon-registry-dump` | 103-byte snapshot received |
| `830d1df2f` | `registry-snapshot` frame | probe → host round-trip |
| `fd4fdcb5d` | `load_class` / `invoke_method` | `tests/host.rs` |
| `e3050a31f` | JVM host process + pipe | 5 host tests |
| `3e62ad8a5` | Fabric jar intake | 58 mods, 49 ordered, 0 errors |

---

## Template for new entries

```
## <date> — <wave / task id>

### <change>
- <files>

Proof:
```
<exact command>
<verbatim result lines>
```

Did not prove: <explicit limits>
```
