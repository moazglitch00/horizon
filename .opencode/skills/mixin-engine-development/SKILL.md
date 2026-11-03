---
name: mixin-engine-development
description: Implement Mixin bytecode transformation support. Use when adding @Mixin, @Inject, @Shadow, etc.
---

# Mixin Engine Development

## When to activate
- Implementing Mixin annotations (@Mixin, @Inject, @Redirect, etc.)
- Setting up bytecode transformation pipeline
- Adding access widener support

## Workflow
1. Study sponge-mixin API (already on classpath)
2. Create Mixin service that integrates with JVM class loading
3. Implement annotation processing
4. Implement injection points (HEAD, RETURN, etc.)
5. Test transformations against real classes

## Key components
- MixinService: integrates with JVM
- MixinConfig: parses mixin JSON configs
- Transformer: applies bytecode changes
- CallbackInfo: carries injection context

## Common mistakes
- Assuming Mixins can patch Rust code (they can only patch Java classes)
- Not handling transformation ordering
- Ignoring access widener requirements

## Quality requirements
- Each injection type must have a test
- Transformation must happen before class definition
- Conflicts must be detected and reported