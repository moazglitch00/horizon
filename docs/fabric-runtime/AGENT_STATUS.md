# Fabric Runtime: Agent Status

**As of 2026-11-06.** Agents are OpenCode subagents (`.opencode/agents/*.md`).
A row is only `idle` when a task is finished *and verified*; otherwise it says
what it is doing.

## Roster

| Agent | Model | Mode | Scope | State |
|---|---|---|---|---|
| `project-manager` | `opencode/big-pickle` | primary | decomposition, integration, gate | active |
| `java-specialist` | `opencode/deepseek-v4.1-flash-free` | subagent | `java/horizon-host/src/**` shims | idle |
| `minecraft-specialist` | `opencode/glm-5.3-flash-free` | subagent | vanilla behaviour → shim/Rust boundary | idle |
| `fabric-specialist` | `opencode/mimo-v2.6-flash-free` | subagent | `net.fabricmc.fabric.api.*` | idle |
| `mixin-specialist` | `opencode/crouton-alpha-free` | subagent | bytecode transformation | idle |
| `registry-specialist` | `opencode/pulse-alpha-free` | subagent | Rust ↔ Java registry ids | idle |
| `testing-specialist` | `opencode/flint-40b-free` | subagent | tests + gate evidence | idle |
| `performance-specialist` | `opencode/beam-free` | subagent | measured speed work | idle |
| `research-specialist` | `opencode/nemotron-3.5-super-vl-free` | subagent | evidence-backed answers | idle |
| `security-reviewer` | `opencode/big-pickle` | subagent | read-only risk review | idle |

## Model routing

- Architecture, integration and security review: `big-pickle` (strongest
  available), used sparingly.
- Java and Rust implementation: `deepseek-v4.1-flash-free` / `glm-5.3-flash-free`.
- Read-only audits and inventory: `flint-40b-free` / `nemotron-3.5-super-vl-free`.
- Measurement loops: `beam-free`.
- Fabric/event plumbing: `mimo-v2.6-flash-free`; Mixin: `crouton-alpha-free`.

Routing rule: if a task needs judgement about Minecraft semantics, use a strong
model; if it needs enumeration or formatting, use a fast one. Never route a
correctness-critical decision to a fast model and then skip review.

## Concurrency

Default wave size: **3**. Rationale: Cargo holds a lock on `target/`, so more
than one building agent at a time only queues; 3 keeps the machine responsive
and lets read-only agents overlap with one builder.

Guaranteed by dispatch: one owner per file per wave. Shared files
(`crates/horizon-jvm/src/host.rs`, `protocol.rs`, `Cargo.toml`, `mod.rs`) are
edited only by the project-manager.

## Current wave (2026-11-04)

| Task | Agent | Model | Files owned | State |
|---|---|---|---|---|
| T-201 shim signature audit vs real Fabric jar | research | nemotron | none (read-only) | **done** — 4 MISMATCH, 4 ABSENT |
| T-202 fake/empty shim audit | testing | flint | none (read-only) | **done** — 25 REAL / 18 PARTIAL / 4 SHELL |
| T-203 `ModInitializer` fixture | java | deepseek | `tests/fixtures/TestModInitializer.java` | **done** |
| T-210 entrypoint invocation | java + PM | deepseek | `host.rs`, `Main.java`, `tests/host.rs` | **done** — test passes |
| T-215 `SOURCES` coverage guard | PM | big-pickle | `host.rs`, `tests/host.rs` | **done** — guards the regression |
| T-214 de-fake blockers | java | (direct) | 6 `net.minecraft` files + `ShimBehaviourTest.java` | **done** — `shim_behaviour_is_not_silent` passes |
| T-230 remap feasibility | research | nemotron | none (read-only) | **done** — conclusion superseded: 26.x ships named classes, remap is legacy-only |

## Next wave assignment

| Task | Agent | Model | Files owned |
|---|---|---|---|
| T-240 rename 8 shims to real 26.x paths | java-specialist + PM | deepseek-v4.1-flash-free | **done** — 45/45 paths verified in `26.1.2.jar`; `shim_paths_match_the_verified_26x_set` |
| T-241 grow public/protected member conformance | java-specialist + PM | deepseek-v4.1-flash-free | **in progress** — batches 1–3 proven bit-exact by `tools/fabric_shim_parity.py` (5,777/5,777 vs `26.3.jar`); coverage now **16.6%** methods / **20.8%** fields against `26.3.jar`. Next: `Component`/`Style`, `BlockState`/`BlockEntity` |

## Retry policy

- Transient spawn error (`EPERM ... git.exe`) → retry once with the same model.
- Free-model quota error → re-dispatch the task to a different free model;
  never silently drop the task.
- Agent exceeds its scope or edits an unowned file → discard its diff, task
  returns to the board as `blocked`.
