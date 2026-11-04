---
name: performance-optimization
description: Measure and improve Horizon's performance without changing behaviour. Use for slow builds, frame-time regressions, JVM startup cost, or memory growth.
---

# Performance Optimization

## When to activate
- A build/test/render path got measurably slower
- JVM host startup or mod discovery exceeds its budget
- A compatibility shim allocates on a hot path
- Someone asks "why is this slow" with numbers attached

## Workflow
1. **Measure first.** Never optimize from intuition. Capture a baseline:
   - `cargo build --profile fast -p horizon-client` wall time
   - `cargo test -p horizon-jvm` wall time
   - `cargo run -p horizon-fabric -- check <mods dir>` (median of 5)
   - JVM: spawn → hello, discover, ping (already reported by the probe)
2. Record the baseline in `docs/fabric-runtime/PERFORMANCE_PLAN.md`.
3. Find the actual cost: `cargo build --timings`, `--release` flamegraph, or
   instrument the phase with `std::time::Instant` behind a feature flag.
4. Change one thing. Re-measure with the same command, same machine, same load.
5. Keep the change only if the delta exceeds noise (>= 2x run-to-run stddev).
6. Add a regression guard when the number is stable enough to assert.

## Common mistakes
- Optimizing debug builds (meaningless for shipped profiles)
- Caching a mutable result (correctness bug wearing a performance hat)
- Caching across a content/asset change without invalidating
- Measuring once and declaring victory
- Trading startup cost for steady-state cost without saying which one matters

## Quality requirements
- Every optimisation has before/after numbers in the PR or doc
- No behaviour change unless the task asked for one
- Cache keys include every input that can change the output
- Release builds still pass the full test suite

## Integration requirements
- Update `docs/fabric-runtime/PERFORMANCE_PLAN.md` with the measurement
- No new dependency without a size/startup note
