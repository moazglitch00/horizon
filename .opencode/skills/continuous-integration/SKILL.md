---
name: continuous-integration
description: Keep Horizon green. Use when adding a check, fixing a red build, or deciding what CI must run for a change.
---

# Continuous Integration

## When to activate
- A build or test is red on the branch
- A new crate, binary or harness is added
- Deciding the minimum set of checks for a PR

## Local gate (fast order, cheapest first)
```sh
cargo fmt --all -- --check
cargo clippy -p <touched crates> --all-targets -- -D warnings
cargo test -p <touched crates>
cargo test -p horizon-jvm            # Java host compile + wire tests
python3 tools/parity.py --changed origin/main   # only when it applies
```
`python3 tools/pr_ready.py --quick` bundles most of this.

## Workflow when red
1. Read the first error, not the last.
2. Reproduce with the narrowest command (`--test <name>`, `-p <crate>`).
3. Fix the cause; do not `#[ignore]` or delete the test to go green.
4. Re-run the narrow command, then the crate, then the gate.
5. Paste the final output into the task report.

## Common mistakes
- Declaring "all tests pass" from a partial run
- Treating a linker error on Windows (`collect2.exe`) as a code failure —
  fix `PATH` (WinLibs `bin` + `libexec/gcc/...`) first
- Skipping clippy because it is noisy; `-D warnings` is the gate
- Letting the Java host compile cache hide a broken source file
  (touch `crates/horizon-jvm/src/lib.rs` to force a rebuild)

## Quality requirements
- Green means: fmt + clippy + crate tests + Java host tests
- Every new binary/test target is reachable by the gate
- CI runtime stays under the shard budget; use `--quick` while iterating

## Integration requirements
- The command block that proved green goes into
  `docs/fabric-runtime/INTEGRATION_LOG.md`
- `tools/benchmark-deps/fabric.lock.json` stays the only pin source
