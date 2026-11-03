---
name: java-compatibility
description: Implement Minecraft Java compatibility classes in the Horizon JVM host. Use when adding new net.minecraft.* classes, fixing API signatures, or wiring Java objects to Rust.
---

# Java Compatibility

## When to activate
- Adding a new Minecraft Java class to `java/horizon-host/src/net/minecraft/`
- Fixing a method signature mismatch reported by a mod
- Wiring a Java compatibility method to a Rust bridge call

## Workflow
1. Find the vanilla class in `.local/minecraft/26.3/sources/` (decompiled) or use `rea` tools.
2. Identify the methods/fields mods actually use (check Fabric API source, mod source).
3. Create the Java shim in `java/horizon-host/src/net/minecraft/...` following existing patterns.
4. Add the file to `SOURCES` in `crates/horizon-jvm/src/host.rs`.
5. Build: `cargo build -p horizon-jvm`
6. Test: `cargo test -p horizon-jvm`
7. Commit.

## Common mistakes
- Forgetting to add to SOURCES (class won't be compiled into the host)
- Using `java.net.URLClassLoader` without the JAR on the classpath
- Not handling null in equals/hashCode
- Using `Object` instead of specific types

## Quality requirements
- Every class must compile with `javac --release 21`
- Every class must have at least a constructor and toString
- No empty methods that silently do nothing — either implement or throw UnsupportedOperationException naming the phase
- Follow existing naming conventions (getters, setters, bean-style)

## Integration requirements
- Class must be on the host's classpath
- Rust side must be able to load it via `JavaHost::load_class`
- If the class wraps a Rust object, add a bridge method