# Fabric Runtime: Current Status

## Verified against the source tree (2026-11-06)

### Working

- **HMOD native content mods**: `crates/horizon-mods` (manifest, content, modpack, index). Data/assets only.
- **Fabric jar intake (Phase 1)**: `crates/horizon-fabric` reads `.jar` files, parses `fabric.mod.json`, resolves dependencies, produces a load plan. 5 pinned jars → 58 mods, 49 loading, 0 errors.
- **Java host (Phase 2)**: `crates/horizon-jvm` + `java/horizon-host/`. A supervised child JVM runs Fabric Loader's discovery path. Framed pipe protocol (version 1). 53 mods reported by Loader, sets agree with Phase 1.
- **Registry bridge (Phase 3, started)**: `registry-snapshot` and `event` frames. `horizon-registry-dump` binary reads `facts.json` via `BlockRegistry`. End-to-end verified (103-byte snapshot received by host).
- **Dual mod discovery**: `horizon-mods::dual::scan` finds both `.hmod` and `.jar` in one directory.
- **Cross-format validation**: `horizon-mods::validate` checks duplicate IDs and conflicts.
- **Entrypoint invocation**: `invoke_entrypoint` loads a class out of its own jar, instantiates it and calls `onInitialize()`. Verified for our own fixture only.
- **`net.minecraft` compatibility layer**: 63 shims, every path exists in a real `26.1.2.jar`. The core value classes (`Mth`, `Vec2`, `Vec3`, `Vec3i`, `BlockPos`, `ChunkPos`, `Identifier`, `Direction`, `Rotation`), the RNG cluster (`RandomSource`, `BitRandomSource`, the three legacy sources, `MarsagliaPolarGaussian`, `PositionalRandomFactory`, `RandomSupport`) and `AABB` with its hit types (`HitResult`, `BlockHitResult`) are **bit-exact** against it: `tools/fabric_shim_parity.py` reports 5,849/5,849 identical values.
- **Member coverage tooling**: `tools/fabric_shim_coverage.py` measures declared public/protected members against a real jar. Currently **16.8%** of methods (620/3685) and **10.4%** of fields (67/646).

### Not working

- **Real Java mod execution**: no real mod's `ModInitializer` has run. The Java host is a child process; only our fixtures have been invoked.
- **Mixin application**: no bytecode transformation is performed.
- **Fabric API events**: registration stores nothing and no invoker fires.
- **Gameplay bridge**: no `net.minecraft` call reaches the Rust engine's world state.
- **Member completeness**: ~83% of public methods called by real mods are absent, so most mods would fail to link with `NoSuchMethodError`.
- **In-process JNI**: the architecture uses a supervised child-process JVM, not JNI, so a mod crash cannot kill the game. This is a deliberate design decision.

### Key files

| File | Purpose |
|---|---|
| `crates/horizon-fabric/src/*.rs` | Jar intake, metadata, version predicates, resolution |
| `crates/horizon-jvm/src/protocol.rs` | Wire format (Rust side) |
| `crates/horizon-jvm/src/host.rs` | Process supervision, handshake, ping, discover, shutdown, entrypoints |
| `crates/horizon-jvm/src/discovery.rs` | Typed Loader report, probe orchestration |
| `crates/horizon-jvm/src/libs.rs` | Pinned library verification (SHA-256) |
| `java/horizon-host/src/horizon/host/*.java` | Java host: Wire, Main, Discovery, Entrypoints, Launcher |
| `crates/horizon-mods/src/dual.rs` | Unified .hmod + .jar discovery |
| `crates/horizon-mods/src/validate.rs` | Cross-format validation |
| `crates/horizon-world/src/registry.rs` | BlockRegistry (used by registry dump) |
| `tools/fabric_shim_parity.py` | Differential oracle: real jar vs shims, bit-for-bit |
| `tools/fabric_shim_coverage.py` | Public/protected member coverage against a real jar |

### Missing components for Java mod execution

1. The remaining ~83% of `net.minecraft` members, and the Fabric API surface real mods call (T-241/T-242).
2. A Mixin engine (`@Inject`/`@Shadow`/`@Redirect`), Phase 5.
3. Fabric API event dispatch wired to game events.
4. A gameplay bridge (Java `Level`/`Entity` calls serviced by Rust), Phase 6.
5. A Minecraft Java **26.3** runtime: this machine has `26.1.2.jar` (used as ground truth) but no 26.3 classes.
6. Multiplayer mod handshake.

### Honest assessment

The foundation (discovery, version resolution, the framed host, entrypoints, and a
bit-exact core shim set) is solid and tested. A real Java mod still does **not**
run in Horizon: member coverage is 16.8%, Mixin is 0%, Fabric API dispatch is 0%,
and the gameplay bridge is 0%. The largest remaining phases are T-241 (members),
Phase 5 (Mixin) and Phase 6 (gameplay bridge), and they need the 26.3 runtime for
final verification.
