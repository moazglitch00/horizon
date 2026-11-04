# Fabric Runtime: Integration Log

Newest first. Each entry is a verified integration checkpoint: what changed,
what proved it, and what it did **not** prove.

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
