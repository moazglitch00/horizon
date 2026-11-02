# Fabric Runtime: Compatibility Matrix

## Status legend

- **SUPPORTED**: Tested in-game, works.
- **PARTIAL**: Some functionality works, gaps remain.
- **PLANNED**: Not implemented yet.
- **N/A**: Not applicable to Horizon's architecture.

## Mod format

| Format | Status | Notes |
|---|---|---|
| `.hmod` (native) | SUPPORTED | Content mods (data, assets, recipes). Code mods are native WASM. |
| `.jar` (Fabric) | PLANNED | Discovery and metadata reading work. Execution not implemented. |

## Fabric Loader

| Feature | Status |
|---|---|
| Mod discovery | SUPPORTED (Phase 2) |
| fabric.mod.json parsing | SUPPORTED (Phase 1) |
| Dependency resolution | SUPPORTED (Phase 1) |
| Nested JARs | SUPPORTED (Phase 1) |
| Entrypoints | PLANNED (Phase 3) |
| Main init | PLANNED (Phase 3) |
| Client init | PLANNED (Phase 3) |
| Server init | PLANNED (Phase 3) |
| Mod conflicts | PLANNED (Phase 3) |
| Version checks | SUPPORTED (Phase 1) |
| Class loading | PLANNED (Phase 1 of runtime) |

## Fabric API modules

| Module | Status |
|---|---|
| fabric-api-base | PLANNED |
| fabric-lifecycle-events-v1 | PLANNED |
| fabric-item-api-v1 | PLANNED |
| fabric-block-api-v1 | PLANNED |
| fabric-registry-sync-v0 | PLANNED |
| fabric-entity-events-v1 | PLANNED |
| fabric-events-interaction-v0 | PLANNED |
| fabric-command-api-v2 | PLANNED |
| fabric-resource-loader-v0 | PLANNED |
| fabric-networking-api-v1 | PLANNED |
| fabric-screen-api-v1 | PLANNED |
| fabric-key-binding-api-v1 | PLANNED |
| fabric-rendering-v1 | PLANNED |
| fabric-biome-api-v1 | PLANNED |

## Minecraft Java API

| Class | Status |
|---|---|
| Identifier | PLANNED |
| Registry | PLANNED |
| Block | PLANNED |
| BlockState | PLANNED |
| Item | PLANNED |
| ItemStack | PLANNED |
| Entity | PLANNED |
| LivingEntity | PLANNED |
| PlayerEntity | PLANNED |
| World | PLANNED |
| ClientWorld | PLANNED |
| ServerWorld | PLANNED |
| Chunk | PLANNED |
| Biome | PLANNED |
| Screen | PLANNED |
| Inventory | PLANNED |
| MinecraftClient | PLANNED |
| MinecraftServer | PLANNED |

## Mixin

| Feature | Status |
|---|---|
| @Mixin | PLANNED |
| @Inject | PLANNED |
| @Redirect | PLANNED |
| @ModifyArg | PLANNED |
| @ModifyVariable | PLANNED |
| @Shadow | PLANNED |
| @Overwrite | PLANNED |
| @Unique | PLANNED |
| @Accessor | PLANNED |
| @Invoker | PLANNED |
| Access wideners | PLANNED |
| CallbackInfo | PLANNED |

## Bridge

| Feature | Status |
|---|---|
| Child-process JVM | SUPPORTED (Phase 2) |
| Framed pipe protocol | SUPPORTED (Phase 2) |
| Registry snapshot | SUPPORTED (Phase 3) |
| Event frames | SUPPORTED (Phase 3) |
| JNI in-process | NOT IMPLEMENTED |
| Java-to-Rust method calls | PLANNED |
| Rust-to-Java callbacks | PLANNED |

## Known mod compatibility (tested)

No Java mods have been tested for actual execution. The following have been *read* by the intake system:

| Mod | Version | Intake | Execution |
|---|---|---|---|
| Fabric API | 0.161.0+26.3 | ✅ Read | ❌ Not tested |
| Sodium | 0.9.2+mc26.3 | ✅ Read | ❌ Not tested |
| Lithium | 0.26.2+mc26.3 | ✅ Read | ❌ Not tested |
| FerriteCore | 9.0.0 | ✅ Read | ❌ Not tested |
| ImmediatelyFast | 1.17.1+26.3 | ✅ Read | ❌ Not tested |