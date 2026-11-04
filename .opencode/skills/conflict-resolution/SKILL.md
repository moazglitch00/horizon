---
name: conflict-resolution
description: Detect and resolve overlapping edits between parallel agents before they corrupt the tree. Use when two tasks touch the same file, or an integration build fails.
---

# Conflict Resolution

## When to activate
- Two task assignments list the same file
- `git status` shows a file changed in a way no agent claims
- An integration build fails after merging agent output

## Workflow
1. **Prevent:** before dispatch, build a file-ownership map. One owner per file
   per wave. Shared files (`host.rs`, `Cargo.toml`, `mod.rs`) get a single
   integrator, never a worker.
2. **Detect:** after a wave, `git status --short` + `git diff --stat`. Any file
   not in exactly one report is suspect.
3. **Isolate:** move the contested work to a worktree
   (`opencode.worktree_create`) or revert the unowned hunk.
4. **Resolve by intent, not by text:** reread both tasks; keep the version that
   satisfies both, or sequence them.
5. **Re-run:** compile + the targeted tests + the harnesses that cover the file.
6. Record the resolution and the rule that would have prevented it.

## Common mistakes
- Resolving by taking "the newer" edit
- Letting two agents append to the same `SOURCES` list in `host.rs`
- Merging a shared-file change without rebuilding the whole crate
- Hiding a conflict by deleting one side

## Quality requirements
- Every shared file has one owner per wave
- Post-merge: `cargo test -p <crate>` passes for every touched crate
- No file loses functionality without an explicit decision recorded

## Integration requirements
- `docs/fabric-runtime/INTEGRATION_LOG.md` records the conflict + resolution
- Ownership map lives in `docs/fabric-runtime/TASK_BOARD.md`
