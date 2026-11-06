# Fabric Runtime: Compatibility Matrix

**Verified 2026-11-06** against the working tree. See `PROJECT_STATUS.md` for
the numbers this file is built on.

## Status legend

| Status | Meaning |
|---|---|
| **VERIFIED** | Exercised by a test that would fail if the behaviour were wrong |
| **SHIM** | Compiles into the host, holds local state; never loaded by a real mod |
| **SHELL** | Exists for linkage only; no semantics yet (must be named as such, never sold as done) |
| **PLANNED** | Not implemented |
| **BLOCKED** | Needs the 26.3 runtime, which is absent on this machine |

A class count is not progress. Anything below marked SHIM has **never** been
exercised by a mod, so its fidelity is unproven.

## Mod format

| Format | Status | Notes |
|---|---|---|
| `.hmod` (native content) | VERIFIED | manifest, content fence, assets, recipes |
| `.jar` (Fabric) | SHIM | discovered, parsed, resolved; code not executed |

## Fabric Loader

| Feature | Status | Notes |
|---|---|---|
| Jar scan + nested jars | VERIFIED | 5 pinned jars → 58 mods |
| `fabric.mod.json` parse | VERIFIED | |
| Version predicates | VERIFIED | |
| Dependency resolution + ordering | VERIFIED | 49 ordered, 0 errors |
| Loader child process | VERIFIED | spawn → hello ≈ 0.55 s |
| Loader discovery report | VERIFIED | 53 mods, sets agree with intake |
| **Entrypoint invocation** | VERIFIED for own fixtures | `invoke_entrypoint` loads a class from its jar, instantiates it, calls `onInitialize`; real mods still blocked by intermediary naming |
| Main / client / server init | PARTIAL | dispatcher supports all three interface methods; `client`/`server` only differ by which interface is cast |
| Mod conflict detection | VERIFIED | `horizon-mods::validate` |

## Fabric API modules

| Module | Status vs real jar (T-201) | What exists |
|---|---|---|
| fabric-api-base (`Event`) | MISMATCH | shim is an interface + `create()`/`SimpleEvent`; real is an abstract class with `volatile T invoker` and phase-aware `register`, no `create()` |
| fabric-lifecycle-events-v1 | MISMATCH | `ServerLifecycleEvents`/`ClientLifecycleEvents` are interfaces with `Object` params; real are final classes with `MinecraftServer`/`class_310` params and more events |
| fabric-client-lifecycle (tick) | MISMATCH | interface, 2 events; real is a final class with 4 events incl. world ticks |
| fabric-item-api-v1 | ABSENT | `FabricItemSettings` does not exist; real is `FabricItem.Settings` |
| fabric-block-api-v1 | ABSENT | `FabricBlockSettings` does not exist; real settings are vanilla `BlockBehaviour.Properties` |
| fabric-key-binding-api-v1 | ABSENT | real API is `FabricKeyBinding` + `KeyBindingHelper` |
| fabric-rendering-v1 | ABSENT | `RenderLayer` enum is vanilla `RenderType`; real Fabric API has `BlendMode`/`BlockRenderLayerMap` |
| fabric-registry-sync-v0 | PLANNED | |
| fabric-entity-events-v1 | PLANNED | |
| fabric-events-interaction-v0 | PLANNED | |
| fabric-command-api-v2 | PLANNED | |
| fabric-resource-loader-v0 | PLANNED | |
| fabric-networking-api-v1 | PLANNED | |
| fabric-screen-api-v1 | PLANNED | |
| fabric-biome-api-v1 | PLANNED | |

## Minecraft Java API — 63 shims

All rows are **SHIM** unless noted: they compile with `javac --release 21` into
the host and have a smoke check, but no mod has loaded them. The T-202 audit
measured depth: **25 REAL, 18 PARTIAL, 4 SHELL, 3 blocker-level fakes**.
T-214 removed the silent fakes.

**Naming:** since 26.1 the game ships unobfuscated (`26.1.2.jar` has zero
`class_*` entries), so 26.x mods reference these official names directly.
Intermediary remapping only applies to ≤1.21 mods (T-230, deferred).

**Path conformance (measured against `26.1.2.jar`):** **63/63**. T-240 renamed
the 8 Yarn/older paths (`client.MinecraftClient`→`client.Minecraft`,
`util.Identifier`+`resources.ResourceLocation`→`resources.Identifier`,
`world.entity.player.PlayerEntity`→`...Player`,
`world.level.ClientWorld`→`client.multiplayer.ClientLevel`,
`world.level.ServerWorld`→`server.level.ServerLevel`,
`attributes.Attribute`→`world.entity.ai.attributes.Attribute`; `network.chat.Text`
was deleted). T-241 added the RNG cluster (`RandomSource`,
`world.level.levelgen.*`) and the hit-test cluster (`AABB`, `HitResult`,
`BlockHitResult`). `shim_paths_match_the_verified_26x_set` guards the set.

**Member conformance (measured with `tools/fabric_shim_coverage.py`):**
**16.8%** of public/protected methods (620/3685) and **10.4%** of fields (67/646).
This, not the path count, is the linking gap: mods call members, and a missing
one is `NoSuchMethodError`.

**Differential parity (measured with `tools/fabric_shim_parity.py`):** the
fixture runs the same source against the real `26.1.2.jar` and against the
shims; **5,849/5,849 values are bit-identical** for `Mth`, `Vec2`, `Vec3`,
`Vec3i`, `BlockPos`, `ChunkPos`, `Identifier`, `Direction`, `Rotation`,
`AABB`, `HitResult`, `BlockHitResult` and the
RNG cluster (`RandomSource`, `BitRandomSource`, `LegacyRandomSource`,
`SingleThreadedRandomSource`, `ThreadSafeLegacyRandomSource`,
`MarsagliaPolarGaussian`, `PositionalRandomFactory`, `RandomSupport`). This
proves *those members* behave like vanilla; it does not prove the other ~83%
of members exist or behave. The oracle caught one vanilla quirk worth
naming: `RandomSource.triangle` multiplies the random difference by `max`,
not by `max - min`; the Iterable clip path also translates block-local shapes
by the `BlockPos` before testing them.

| Package | Classes |
|---|---|
| `net.minecraft` | `IdentifierException` |
| `net.minecraft.util` | `Mth`, `RandomSource` |
| `net.minecraft.world.level.levelgen` | `BitRandomSource`, `LegacyRandomSource`, `SingleThreadedRandomSource`, `ThreadSafeLegacyRandomSource`, `MarsagliaPolarGaussian`, `PositionalRandomFactory`, `RandomSupport` |
| `net.minecraft.resources` | `Identifier` |
| `net.minecraft.core` | `BlockPos`, `Direction`, `Position`, `Registry`, `Vec3i`, `particles.ParticleType`, `particles.SimpleParticleType` |
| `net.minecraft.sounds` | `SoundEvent` |
| `net.minecraft.world.item` | `Item`, `ItemStack`, `CreativeModeTab` |
| `net.minecraft.world.item.crafting` | `Recipe`, `RecipeManager` |
| `net.minecraft.world.item.enchantment` | `Enchantment` |
| `net.minecraft.world.level` | `Level`, `ChunkPos` |
| `net.minecraft.world.level.block` | `Block`, `Rotation` |
| `net.minecraft.world.level.block.state` | `BlockState` |
| `net.minecraft.world.level.block.entity` | `BlockEntity`, `BlockEntityType` |
| `net.minecraft.world.level.chunk` | `ChunkAccess`, `LevelChunk` |
| `net.minecraft.world.level.storage.loot` | `LootTable`, `LootContext` |
| `net.minecraft.world.entity` | `Entity`, `EntityType`, `LivingEntity`, `Mob` |
| `net.minecraft.world.entity.player` | `Player` |
| `net.minecraft.world.entity.ai.goal` | `Goal` |
| `net.minecraft.world.entity.ai.attributes` | `Attribute`, `AttributeSupplier` |
| `net.minecraft.world.phys` | `Vec2`, `Vec3`, `AABB`, `HitResult`, `BlockHitResult` |
| `net.minecraft.world.inventory` | `AbstractContainerMenu` |
| `net.minecraft.world` | `Container` |
| `net.minecraft.world.damagesource` | `DamageSource` |
| `net.minecraft.world.effect` | `MobEffect` |
| `net.minecraft.network` | `FriendlyByteBuf` |
| `net.minecraft.network.chat` | `Component` |
| `net.minecraft.network.protocol` | `Packet` |
| `net.minecraft.network.syncher` | `EntityDataAccessor` |
| `net.minecraft.server` | `MinecraftServer` |
| `net.minecraft.server.level` | `ServerLevel` |
| `net.minecraft.client` | `Minecraft` |
| `net.minecraft.client.gui.screens` | `Screen` |
| `net.minecraft.client.multiplayer` | `ClientLevel` |
| `net.minecraft.commands` | `CommandSourceStack` |

### Not present (blocks real mods)

`BlockBehaviour`, `Item$Settings`, `Block$Settings`, `TagKey`, `Tags`,
`BuiltInRegistries`, `Registries`, `Holder`, `HolderLookup`, `DataComponents`,
`InteractionResult`, `BlockBox`, `EntityType$Builder`,
`SpawnGroup`, `Structure`, `BiomeSource`, `ChunkGenerator`, `PacketByteBuf`,
`CustomPayload`, `ResourceManager`, `ClientPlayerEntity`, `WorldRenderEvents`
types. Each needs its real signature before implementation — this list is the
queue, not a promise. (`ServerLevel` now exists as a shim; it was on this list
by mistake.)

## Mixin

| Feature | Status |
|---|---|
| sponge-mixin 0.17.4 + ASM 9.10.1 pinned + SHA-256 verified | VERIFIED (on disk) |
| `MixinService` integrated with classloading | PLANNED |
| `@Mixin`, `@Inject`, `@Shadow`, `@Redirect`, `@Overwrite` | BLOCKED (needs 26.3 targets; fixture-only work possible) |
| Access wideners | PLANNED |
| `CallbackInfo` | PLANNED |

## Bridge

| Feature | Status |
|---|---|
| Supervised child JVM (crash cannot kill the game) | VERIFIED |
| Framed pipe, 8 MiB / 4 KiB caps | VERIFIED |
| `HELLO/PING/DISCOVER/SHUTDOWN` | VERIFIED |
| `REGISTRY_SNAPSHOT` | VERIFIED (send/receive) |
| `EVENT` frame | VERIFIED (transport only; nothing fires) |
| `LOAD_CLASS` + `INVOKE_METHOD` | VERIFIED (host's own fixture class) |
| `ENTRYPOINT` — run a mod's `onInitialize` from its jar | VERIFIED (own fixture; loader cached per jar) |
| Java → Rust calls | PLANNED |
| Rust → Java callbacks during tick | PLANNED |

## Real mods

No real Java mod has been executed. `fabric-api-0.127.1+1.21.5.jar` is present in
the test `mods/` folder and is *readable* by intake, but it targets 1.21.5 and its
classes use intermediary names, so they are not usable against our official-named
shims.

| Mod | Intake | Execution |
|---|---|---|
| Fabric API (1.21.5 jar on disk) | ✅ read | ❌ never run |
| Sodium / Lithium / FerriteCore / ImmediatelyFast | ✅ metadata read | ❌ never run |
| Our `TestModInitializer` fixture | ✅ compiled to a jar | ✅ `onInitialize` ran |
