---
name: automated-testing
description: Run and create tests for Horizon. Use when verifying changes, adding test coverage, or debugging.
---

# Automated Testing

## When to activate
- After any code change
- Before committing
- When adding new compatibility classes

## Commands
- `cargo test -p horizon-jvm` — JVM host tests
- `cargo test -p horizon-fabric` — Fabric jar intake tests
- `cargo test -p horizon-mods` — Mod system tests
- `cargo clippy -p <crate> --all-targets` — lint
- `cargo fmt -p <crate>` — format

## Workflow
1. Make changes
2. `cargo fmt -p <crate>`
3. `cargo clippy -p <crate> --all-targets`
4. `cargo test -p <crate>`
5. Fix any errors
6. Commit

## Rules
- Every new class must compile with `javac --release 21`
- Every new Rust module must pass clippy
- Never commit without tests passing
- Use `--test <name>` for targeted testing