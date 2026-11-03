---
name: multi-agent-orchestration
description: Split large tasks across parallel sub-agents with clean role boundaries. Use when a task has independent workstreams.
---

# Multi-Agent Orchestration

## When to activate
- A task has 3+ independent subtasks
- Multiple files need to be created that don't depend on each other
- Speed is important

## Workflow
1. Analyze the task and identify independent workstreams
2. Create a task board (TASK_BOARD.md) with task IDs, dependencies, and assignments
3. Spawn sub-agents for independent tasks using the subagent tool
4. Collect results from all agents
5. Review and integrate changes
6. Run tests
7. Commit

## Rules
- Never let two agents edit the same file simultaneously
- Each agent gets a specific, narrow scope
- Agents report: files modified, tests run, results, blockers
- The orchestrator (you) merges and validates
- Use smaller models for simple tasks, stronger models for architecture

## Common mistakes
- Spawning agents that depend on each other's output
- Not specifying file ownership boundaries
- Forgetting to integrate and test after agents complete