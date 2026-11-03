---
name: fabric-api-implementation
description: Implement Fabric API modules as Java compatibility classes. Use when adding Fabric API support (events, resources, screens, etc.).
---

# Fabric API Implementation

## When to activate
- Implementing a Fabric API module (fabric-api-base, fabric-lifecycle-events-v1, etc.)
- Adding event registration or callback infrastructure
- Wiring Fabric API classes to Rust

## Workflow
1. Read the Fabric API source for the target module
2. Identify the classes, interfaces, and methods mods use
3. Create Java shim classes in java/horizon-host/src/net/fabricmc/
4. Add to SOURCES in host.rs
5. Build and test
6. Commit

## Priority order
1. fabric-api-base (Event system)
2. fabric-lifecycle-events-v1
3. fabric-item-api-v1
4. fabric-block-api-v1
5. fabric-registry-sync-v0

## Common mistakes
- Implementing the full API at once (do one module at a time)
- Not checking the actual Fabric API source for exact signatures
- Forgetting that events need both registration AND dispatch

## Quality requirements
- Each module must have at least one test
- Event registration must store callbacks
- Methods must not silently do nothing (throw or implement)