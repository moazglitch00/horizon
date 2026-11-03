---
name: parallel-execution
description: Run independent build/test/implementation tasks concurrently. Use when multiple tasks have no dependencies.
---

# Parallel Execution

## When to activate
- Multiple independent files need to be created
- Multiple independent tests can run simultaneously
- Research and implementation can overlap

## Workflow
1. Identify independent tasks
2. Spawn sub-agents with `background=true` for each
3. Continue with other work while they run
4. Collect results when notified
5. Integrate and test

## Rules
- Set concurrency limit based on system resources (default: 4)
- Each agent works on distinct files
- Never parallelize tasks that share mutable state
- Always run full test suite after integration