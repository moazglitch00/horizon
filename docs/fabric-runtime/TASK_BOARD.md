# Fabric Runtime: Task Board

Updated 2026-11-06. One owner per file per wave. `done` requires pasted
command output in `INTEGRATION_LOG.md`.

## Wave 1 — complete

| ID | Task | Owner | Result | Status |
|---|---|---|---|---|
| T-201 | Audit 8 Fabric API shims against the real `fabric-api` jar with `javap` | research-specialist | 4 MISMATCH, 4 ABSENT; intermediary-name blocker found | **done** |
| T-202 | Audit 47 `net.minecraft` shims for fake/empty methods | testing-specialist | 25 REAL / 18 PARTIAL / 4 SHELL / 3 blocker fakes | **done** |
| T-203 | `TestModInitializer` fixture that can be invoked | java-specialist | `javac` + `java` output `INIT:fixture-mod` | **done** |
| T-204 | Dedupe and register `SOURCES` | project-manager | 64 entries → 82 entries; host tests green | **done** |
| T-210 | Entrypoint invocation over the pipe | java-specialist + project-manager | `a_mod_entrypoint_runs_from_its_jar` passes; **real `testmod.jar` `onInitialize()` runs** | **done** |
| T-215 | `SOURCES` ↔ disk coverage guard | project-manager | catches the `HorizonGameProvider` regression | **done** |

## Wave 2 — unblocked now

| ID | Task | Owner | Files (exclusive) | Acceptance |
|---|---|---|---|---|
| T-211 | Fix T-201 MISMATCHes: `Event` as abstract class with real `invoker`; lifecycle events as final classes with static fields; drop dead `create()`/`SimpleEvent` | fabric-specialist | `net/fabricmc/fabric/api/**` | `javap` re-audit shows MATCH for the 4 present types | **done** — `Event` rewritten with dynamic proxy listener dispatch; lifecycle events as final classes |
| T-212 | `Event.invoker()` dispatches listeners in registration order | fabric-specialist | `event/Event.java` | test: two listeners fire order 1,2 | **done** — `test_event_dispatch.py` confirms both listeners fire in order |
| T-213 | Java `RegistryStore` for `registry-snapshot` | registry-specialist | `horizon/host/RegistryStore.java` (new) + `host.rs` registration by PM | round-trip: id sent == id resolved |
| T-214 | De-fake the 3 blockers + 2 majors from T-202 | java-specialist | `MinecraftServer`, `RecipeManager`, `AbstractContainerMenu`, `Block`, `FriendlyByteBuf` (+`BlockEntityType`) | **done** — `shim_behaviour_is_not_silent` passes |
| T-216 | Replace the 4 ABSENT Fabric API types (keybinding, render layer, item/block settings) with real counterparts or delete them | fabric-specialist | those 4 files | audit shows the shim either matches or is gone |
| T-242 | Seed the `Event` + lifecycle classes from T-211 with real `invoker()` dispatch | fabric-specialist | `ServerLifecycleEvents`, `ClientLifecycleEvents`, `ClientTickEvents` | **done** — lifecycle events have static `Event` fields with typed event classes; dispatch verified end-to-end |

## Wave 3 — namespace and real linking (highest priority)

| ID | Task | Owner | Why |
|---|---|---|---|
| T-240 | Rename the 8 shims that do not exist in a 26.x jar to their real paths (`MinecraftClient`→`Minecraft`, `util.Identifier`+`ResourceLocation`→`resources.Identifier`, `PlayerEntity`→`Player`, `ClientWorld`→`client.multiplayer.ClientLevel`, `ServerWorld`→`server.level.ServerLevel`, `attributes.Attribute`→`world.entity.ai.attributes.Attribute`, delete `network.chat.Text`) and update every reference + `SOURCES` | java-specialist + PM | **done** — 45/45 paths exist in `26.1.2.jar`; `shim_paths_match_the_verified_26x_set` passes |
| T-241 | Grow public/protected member conformance against a real 26.x jar, worst classes first, re-measuring per batch | java-specialist + PM | **in progress** — batches 1–3 (core value classes; the RNG cluster; `AABB`/`HitResult`/`BlockHitResult`; formatting pair) are bit-exact against `26.3.jar` (`tools/fabric_shim_parity.py`: 5,777/5,777 values); coverage **16.6%** methods (624/3758) / **20.8%** fields (105/504). Next: `Component`/`Style`, `BlockState`/`BlockEntity` |
| T-220 | Mixin `@Inject`/`@Shadow` on real Minecraft classes | mixin-specialist | Mixin bootstrap initializes (`MixinBootstrap.init()` + `MixinEnvironment.init()` verified) but transformations not applied yet | sponge-mixin 0.17.4 + ASM 9.10.1 on disk |

## Definition of done for any task

1. Compiles (`javac --release 21` or `cargo build -p <crate>`).
2. A test exists that fails without the change.
3. The exact command + `test result:` lines are pasted in the report.
4. The diff touches only the owned files.
5. `PROJECT_STATUS.md` / `COMPATIBILITY_MATRIX.md` updated if a status changed.
