# Fabric Runtime: Architecture

## Design principle

The Rust engine remains the main game engine. Java provides compatibility for existing mods.
Java never corrupts the Rust engine: a mod crash takes down the Java host (a child process),
never the game.

## Process model

```
┌─────────────────────────────────────────────┐
│  Horizon Rust Engine (main process)         │
│  - World simulation, physics, rendering     │
│  - Chunk processing, lighting               │
│  - Owns the authoritative game state        │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │  horizon-jvm (library)              │    │
│  │  - Spawns/supervises Java host      │    │
│  │  - Framed pipe protocol (v1)        │    │
│  │  - Registry snapshot bridge         │    │
│  │  - Event bridge (planned)           │    │
│  └──────────────┬──────────────────────┘    │
└─────────────────┼───────────────────────────┘
                  │ pipe (stdin/stdout)
                  │ u32 LE length + u8 tag + fields
                  │ 8 MiB max frame, 4 KiB max control
                  v
┌─────────────────────────────────────────────┐
│  Horizon Java Host (child JVM process)      │
│  - Fabric Loader discovery + resolution     │
│  - Minecraft Java compatibility layer       │
│    (planned: net.minecraft.* classes)       │
│  - Fabric API implementation (planned)      │
│  - Mixin engine (planned)                   │
│  - Mod entrypoint invocation (planned)      │
│                                             │
│  Runs on JDK 25 (Fabric API requirement)    │
│  Host bytecode compiled with --release 21   │
└─────────────────────────────────────────────┘
```

## Why a child process, not JNI

1. **Safety**: a mod calling `System.exit()`, deadlocking, or crashing the JVM cannot take down the game. The engine sees a closed pipe or missed heartbeat.
2. **Isolation**: the JVM's garbage collector, memory, and threads are separate from Rust's.
3. **Restartability**: the host can be killed and restarted without restarting the game.
4. **No JNI complexity**: no in-process class loading, no unsafe pointer sharing, no JNI local/global ref management.

The tradeoff is IPC overhead (~1 ms per control round-trip measured), which is acceptable for a game tick budget of 50 ms.

## Wire protocol

Tags: HELLO(1), HELLO_ACK(2), PING(3), PONG(4), DISCOVER(5), DISCOVER_RESULT(6), LOG(7), FATAL(8), SHUTDOWN(9), REGISTRY_SNAPSHOT(10), EVENT(11).

Strings: u32 length + UTF-8. Integers: little-endian. Control frames capped at 4 KiB; payload frames at 8 MiB.

The Java half (`Wire.java`) and Rust half (`protocol.rs`) must stay in sync. A change to a tag, field, or limit means changing both and bumping `PROTOCOL_VERSION`.

## Library verification

Horizon never downloads anything. Fabric Loader and its 6 libraries (ASM, sponge-mixin) are located on disk and verified by SHA-256 against `tools/benchmark-deps/fabric.lock.json`. A wrong jar is reported, not silently used.

## Mod discovery

- `.hmod` (native): `horizon-mods::manifest::find` — `.hmod` files or folders with `horizon-mod.toml`
- `.jar` (Fabric): `horizon-fabric::check` — reads `fabric.mod.json`, resolves deps
- Unified: `horizon-mods::dual::scan` — finds both, partitions by format

## What's NOT in this architecture yet

- In-process JVM (JNI) — deferred; child process is sufficient for Phase 1-3
- Minecraft Java compatibility classes — Phase 5
- Mixin engine — Phase 6
- Fabric API implementation — Phase 4
- Entrypoint invocation — Phase 6
- Event bridge wired to game events — Phase 4/6