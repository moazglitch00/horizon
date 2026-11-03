---
name: minecraft-internals
description: Understand and implement Minecraft Java internals. Use when working with world, chunks, entities, blocks, items, or rendering.
---

# Minecraft Internals

## When to activate
- Implementing a Minecraft Java class that wraps Rust functionality
- Understanding how a vanilla system works
- Mapping Java API to Rust engine

## Key resources
- Decompiled sources: `.local/minecraft/26.3/sources/`
- Port ledger: `tools/port_ledger.py`
- Vanilla bytecode: `tools/rea_vanilla.py`

## Workflow
1. Find the vanilla class in decompiled sources
2. Identify methods mods actually call (check Fabric API, mod source)
3. Create compatibility shim with matching signatures
4. Wire methods to Rust via bridge if needed
5. Test with a real mod's usage pattern

## Common mistakes
- Implementing the full vanilla class (only implement what mods use)
- Wrong method signatures (must match exactly)
- Missing null checks
- Not handling client/server thread requirements