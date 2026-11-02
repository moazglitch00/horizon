# Fabric Runtime: Implementation Plan

## Phase 1 — Real JVM (NOT STARTED)

**Goal**: Integrate a real JVM into Horizon.

**Approach**: The existing architecture uses a child-process JVM. Phase 1 of this plan asks for in-process JNI. Decision needed: whether to switch from child-process to JNI, or to enhance the child-process bridge to support class loading and method invocation.

**Recommendation**: Keep the child-process model (already built and tested) and extend the pipe protocol with class-loading and method-invocation frames. This avoids JNI complexity while achieving the same goal. If in-process is required, use the `jni` crate.

**Milestones**:
1. Add `load-class`, `invoke-method`, `get-field` frames to the protocol.
2. Java host: implement class loading from arbitrary JARs via URLClassLoader.
3. Java host: implement method invocation with argument/return serialization.
4. Rust side: `JavaHost::load_class`, `JavaHost::invoke_method` methods.
5. Test: load a simple Java JAR, call a method, return result to Rust.

**Acceptance**: A simple Java test JAR loads, its entrypoint executes, a method returns a value to Rust, and the result is logged.

## Phase 2 — Minecraft Java Compatibility Layer (NOT STARTED)

**Goal**: Expose Minecraft Java API classes to mods.

**Approach**: Create Java classes in `horizon-host` that mirror `net.minecraft.*` signatures. Each method delegates to Rust via the bridge. This is the largest phase.

**Milestones**:
1. `Identifier`, `Registry`, `Block`, `BlockState`, `Item`, `ItemStack`
2. `World`, `ClientWorld`, `ServerWorld`, `Chunk`, `Biome`
3. `Entity`, `LivingEntity`, `PlayerEntity`
4. `Screen`, `Inventory`, `Menu`
5. `MinecraftClient`, `MinecraftServer`
6. `ResourceManager`, `EntityType`, `BlockEntityType`

**Acceptance**: A Java mod that references `net.minecraft.world.level.Level` compiles and loads without `ClassNotFoundException`.

## Phase 3 — Fabric Loader (PARTIALLY DONE)

**Goal**: Integrate real Fabric Loader.

**Done**: Discovery and resolution (Phase 2 of the original plan).
**Remaining**: Entrypoint invocation, client/server init, mod lifecycle.

**Milestones**:
1. Invoke mod entrypoints (`onInitialize`, `onInitializeClient`, `onInitializeServer`).
2. Wire entrypoints to game lifecycle (on startup, on world load).
3. Handle mod conflicts and version checks at runtime.

## Phase 4 — Fabric API (NOT STARTED)

**Goal**: Implement Fabric API compatibility.

**Priority order**: fabric-api-base → lifecycle-events → item-api → block-api → registry-sync → entity-events → events-interaction → command-api → resource-loader → networking → screen → key-binding → rendering → biome-api.

**Approach**: For each module, create Java classes that mirror Fabric's API, with methods that delegate to Rust. Test each module before moving to the next.

## Phase 5 — Mixin Engine (NOT STARTED)

**Goal**: Real bytecode transformation.

**Approach**: Use sponge-mixin (already on the classpath) to transform classes before they're defined. Requires a custom `MixinService` that integrates with the JVM's class loading.

**Milestones**:
1. `@Mixin` + `@Inject` at `HEAD`/`RETURN`
2. `@Shadow`, `@Unique`, `@Accessor`, `@Invoker`
3. `@Redirect`, `@ModifyArg`, `@ModifyVariable`
4. Access wideners
5. Transformation ordering and conflict detection

## Phase 6 — Gameplay Compatibility (NOT STARTED)

**Goal**: Connect Java mods to real Horizon functionality.

**Approach**: Each Minecraft compatibility class's methods delegate to Rust via the bridge. Rust performs the actual operation (place block, spawn entity, etc.) and returns the result.

## Phase 7 — Mod Management UI (NOT STARTED)

**Goal**: Unified mod manager showing both .hmod and .jar mods.

## Phase 8 — Multiplayer (NOT STARTED)

## Phase 9 — Performance (NOT STARTED)

## Phase 10 — Testing (NOT STARTED)

## Phase 11 — Replace HMOD with JAR (NOT STARTED)

## Estimated effort

This is a **multi-month to multi-year** project. Phase 1 alone (JVM integration) is weeks. Phase 2 (Minecraft compat layer) is months. Phase 6 (gameplay) is the largest. The honest timeline is 6-12+ months for a solo developer.