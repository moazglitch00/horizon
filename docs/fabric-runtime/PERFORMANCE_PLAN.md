# Fabric Runtime: Performance Plan

## Measured so far

See `docs/fabric-compatibility/PERFORMANCE.md` for Phase 1 and Phase 2 measurements.

| Measurement | Result |
|---|---|
| Jar intake (5 jars, 58 mods) | median 305 ms |
| Empty folder | median 134 ms |
| Peak working set | 6.9 MiB |
| JVM startup (`java -version`) | median 185 ms |
| JVM startup (`java -cp Hello`) | median 215 ms |
| Host spawn → hello | ~0.55 s |
| Discovery report | ~0.30 s |
| Control round-trip (ping) | ~1 ms |
| Clean shutdown | ~0.04 s |

## Performance targets for the runtime

| Target | Value | Measurement method |
|---|---|---|
| JVM startup overhead | ≤ 1.5 s p95 | Stopwatch from spawn to hello, 20 runs |
| Registry snapshot transfer (50 mods) | ≤ 2.0 s | Timed in probe |
| Control round-trip p99 | ≤ 1 ms | 10,000 ping iterations |
| Java mod event callback | ≤ 0.5 ms | Timed from Rust trigger to Java return |
| World tick with 50 mods | ≤ 50 ms (20 TPS) | Game tick profiling |
| Host idle RSS | ≤ 512 MiB | Sampled at idle |
| World load time increase | ≤ 2x vanilla | Timed world load with/without mods |

## Performance rules

- World simulation stays in Rust.
- Chunk processing stays in Rust.
- Physics stays in Rust.
- Rendering stays in Rust where possible.
- Java is used only where compatibility requires it.
- Batch large operations across the bridge.
- Avoid copying large world structures.
- Avoid excessive JNI/IPC calls.

## What's not measured yet

- JVM with Minecraft Java classes on classpath
- Mixin application cost
- Fabric API event dispatch overhead
- Multi-mod world tick cost
- Multiplayer with mods
- Garbage collection impact during gameplay