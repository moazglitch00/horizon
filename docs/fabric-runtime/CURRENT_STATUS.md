# Fabric Runtime: Current Status

## Verified against the source tree (2026-11-02)

### Working

- **HMOD native content mods**: `crates/horizon-mods` (manifest, content, modpack, index). Data/assets only.
- **Fabric jar intake (Phase 1)**: `crates/horizon-fabric` reads `.jar` files, parses `fabric.mod.json`, resolves dependencies, produces a load plan. 5 pinned jars → 58 mods, 49 loading, 0 errors.
- **Java host (Phase 2)**: `crates/horizon-jvm` + `java/horizon-host/`. A supervised child JVM runs Fabric Loader's discovery path. Framed pipe protocol (version 1). 53 mods reported by Loader, sets agree with Phase 1.
- **Registry bridge (Phase 3, started)**: `registry-snapshot` and `event` frames. `horizon-registry-dump` binary reads `facts.json` via `BlockRegistry`. End-to-end verified (103-byte snapshot received by host).
- **Dual mod discovery**: `horizon-mods::dual::scan` finds both `.hmod` and `.jar` in one directory.
- **Cross-format validation**: `horizon-mods::validate` checks duplicate IDs and conflicts.

### Not working

- **Java mod execution**: No JVM is embedded in the Rust process. The Java host is a child process that only runs discovery. No Java code from mods is ever invoked.
- **Mixin application**: No bytecode transformation is performed.
- **Fabric API events**: No event bridge is wired to game events.
- **Minecraft Java compatibility layer**: No Java-facing Minecraft classes exist. Mods that reference `net.minecraft.*` classes would fail to load.
- **In-process JNI**: The architecture uses a child-process JVM, not JNI. The spec requests JNI; this is a design decision that may need revisiting for Phase 6.

### Key files

| File | Purpose |
|---|---|
| `crates/horizon-fabric/src/*.rs` | Jar intake, metadata, version predicates, resolution |
| `crates/horizon-jvm/src/protocol.rs` | Wire format (Rust side) |
| `crates/horizon-jvm/src/host.rs` | Process supervision, handshake, ping, discover, shutdown |
| `crates/horizon-jvm/src/discovery.rs` | Typed Loader report, probe orchestration |
| `crates/horizon-jvm/src/libs.rs` | Pinned library verification (SHA-256) |
| `java/horizon-host/src/horizon/host/*.java` | Java host: Wire, Main, Discovery, GameProvider, Launcher |
| `crates/horizon-mods/src/dual.rs` | Unified .hmod + .jar discovery |
| `crates/horizon-mods/src/validate.rs` | Cross-format validation |
| `crates/horizon-world/src/registry.rs` | BlockRegistry (used by registry dump) |

### Missing components for Java mod execution

1. A Minecraft Java runtime (game jar) — not present on this machine (only up to 26.1 snapshots)
2. In-process JVM or JNI bridge
3. Minecraft Java compatibility classes (net.minecraft.*)
4. Mixin engine
5. Fabric API implementation
6. Event bridge wired to game events
7. Classpath unlocking for mods
8. Entrypoint invocation
9. Multiplayer mod handshake

### Honest assessment

The project is at **Phase 3** of a 6-phase plan. Java mods cannot execute. The foundation (discovery, verification, bridge protocol) is solid and tested. The remaining work (Phases 4-6) is the bulk of the effort and requires a real Minecraft Java runtime that is not available on this machine.