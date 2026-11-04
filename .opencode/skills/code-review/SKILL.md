---
name: code-review
description: Review Horizon changes for correctness, regressions and policy before integration. Use after an agent reports a task complete, or before a commit/PR.
---

# Code Review

## When to activate
- A specialist agent reports a task complete
- Before committing a multi-file change
- Before opening a PR

## Workflow
1. Read the diff, not the description: `git diff`, `git status --short`.
2. Check in this order:
   - **Correctness** — does it do what the task says, including edge cases?
   - **Regressions** — does it change behaviour outside its scope?
   - **Fake work** — empty methods, `TODO`, `UnsupportedOperationException`
     without a phase name, hard-coded returns that only satisfy a test.
   - **API fidelity** — Java signatures match real Minecraft/Fabric classes.
   - **Tests** — is there a test that would fail if the logic were wrong?
   - **Policy** — no Mojang files, no secrets, no `git push`, no writes outside
     the repo, `.hmod` loader untouched.
3. Run the commands the author claims to have run. Do not trust the report.
4. Report findings in severity order: blocker / major / minor / nit.
5. A task is **rejected** if any blocker exists; the owner fixes and resubmits.

## Common mistakes
- Reviewing only the summary instead of the diff
- Accepting "tests pass" without seeing the test names or output
- Letting a compatibility class pass with wrong package or wrong return type
- Missing that a change touched the deprecated in-frame path

## Quality requirements
- Every finding cites file and line
- Every claim about tests is backed by pasted output
- No approval without at least one compile + one test run

## Integration requirements
- Findings are recorded in `docs/fabric-runtime/INTEGRATION_LOG.md`
- Rejected work returns to the task board as `blocked` with the reason
