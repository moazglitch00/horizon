# Fabric Runtime: Task Board

Updated 2026-11-04. One owner per file per wave. `done` requires pasted
command output in `INTEGRATION_LOG.md`.

## Wave 1 — complete

| ID | Task | Owner | Result | Status |
|---|---|---|---|---|
| T-201 | Audit 8 Fabric API shims against the real `fabric-api` jar with `javap` | research-specialist | 4 MISMATCH, 4 ABSENT; intermediary-name blocker found | **done** |
| T-202 | Audit 47 `net.minecraft` shims for fake/empty methods | testing-specialist | 25 REAL / 18 PARTIAL / 4 SHELL / 3 blocker fakes | **done** |
| T-203 | `TestModInitializer` fixture that can be invoked | java-specialist | `javac` + `java` output `INIT:fixture-mod` | **done** |
| T-204 | Dedupe and register `SOURCES` | project-manager | 64 entries; host tests green | **done** |
| T-210 | Entrypoint invocation over the pipe | java-specialist + project-manager | `a_mod_entrypoint_runs_from_its_jar` passes | **done** |
| T-215 | `SOURCES` ↔ disk coverage guard | project-manager | catches the `HorizonGameProvider` regression | **done** |

## Wave 2 — unblocked now

| ID | Task | Owner | Files (exclusive) | Acceptance |
|---|---|---|---|---|
| T-211 | Fix T-201 MISMATCHes: `Event` as abstract class with real `invoker`; lifecycle events as final classes with static fields; drop dead `create()`/`SimpleEvent` | fabric-specialist | `net/fabricmc/fabric/api/**` | `javap` re-audit shows MATCH for the 4 present types |
| T-212 | `Event.invoker()` dispatches listeners in registration order | fabric-specialist | `event/Event.java` | test: two listeners fire order 1,2 |
| T-213 | Java `RegistryStore` for `registry-snapshot` | registry-specialist | `horizon/host/RegistryStore.java` (new) + `host.rs` registration by PM | round-trip: id sent == id resolved |
| T-214 | De-fake the 3 blockers + 2 majors from T-202 | java-specialist | `MinecraftServer`, `RecipeManager`, `AbstractContainerMenu`, `Block`, `FriendlyByteBuf` | no silent no-op remains; each either real or `UnsupportedOperationException("<phase>")` |
| T-216 | Replace the 4 ABSENT Fabric API types (keybinding, render layer, item/block settings) with real counterparts or delete them | fabric-specialist | those 4 files | audit shows the shim either matches or is gone |

## Wave 3 — architecture decision (unblocked, high priority)

| ID | Task | Owner | Why |
|---|---|---|---|
| T-230 | Decide and prototype the intermediary→official remap | project-manager + research-specialist | Real mods reference `class_XXX`; without remapping no real mod links. Prototype: remap a fixture class's reference and load it against an official-named shim. |
| T-231 | If remapping is chosen, wire it into `Entrypoints.loaderFor` before `Class.forName` | java-specialist | first real-mod link path |

## Wave 4 — needs the 26.3 runtime (blocked, not skipped)

| ID | Task | Why blocked |
|---|---|---|
| T-220 | Mixin `@Inject`/`@Shadow` on real Minecraft classes | no 26.3 jar/classes on this machine |
| T-221 | Verify a real mod's entrypoints (Sodium/Lithium) | needs 26.3 + matching mod builds |
| T-222 | Block/item registration reaching the Rust registry | needs a mod that registers content |
| T-223 | Gameplay bridge (`Level.getBlockState` → Rust) | needs world state access from the JVM |

## Definition of done for any task

1. Compiles (`javac --release 21` or `cargo build -p <crate>`).
2. A test exists that fails without the change.
3. The exact command + `test result:` lines are pasted in the report.
4. The diff touches only the owned files.
5. `PROJECT_STATUS.md` / `COMPATIBILITY_MATRIX.md` updated if a status changed.
