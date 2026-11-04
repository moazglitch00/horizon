---
name: technical-research
description: Answer a design question using the repo's own sources before the web. Use for "how does vanilla do X", "which Fabric module owns Y", or "is this feasible".
---

# Technical Research

## When to activate
- A task needs a decision that cannot be made from the code in front of us
- A class signature must match real Minecraft/Fabric
- Someone asks whether an approach is feasible

## Order of sources (stop at the first that answers)
1. This repo: `docs/`, `AGENTS.md`, `crates/*/README`-style comments, `git log`.
2. Port ledger: `python3 tools/port_ledger.py where Class.method` /
   `what path:line`.
3. Decompiled vanilla: `.local/minecraft/26.3/sources/{client,server}/`.
   If missing, `python3 tools/lab.py decompile` (needs the vanilla jar).
4. Bytecode: `python3 tools/rea_vanilla.py method <class> <method>`.
5. Pinned dependency sources (Fabric Loader/API, Mixin) from
   `tools/benchmark-deps/fabric.lock.json` — the exact version we ship.
6. The web, only for things none of the above cover. Cite the URL and date.

## Output contract
- **Question** as asked.
- **Answer** in <= 10 lines.
- **Evidence**: file:line or URL for every factual claim.
- **Confidence**: high / medium / low, with what would raise it.
- **Recommendation** for the implementation task, including what NOT to build.

## Common mistakes
- Recalling vanilla from memory instead of reading 26.3
- Quoting a newer/older Fabric version than the pinned one
- Confusing Yarn names with Mojang/official names in this repo
- Presenting a guess as a finding

## Quality requirements
- No claim without a source
- If the source is unavailable, say so and state the blocker
- Research is written down (doc or report), not just spoken

## Integration requirements
- Durable findings go into `docs/fabric-runtime/COMPATIBILITY_MATRIX.md`
  or `ARCHITECTURE.md`, not only into a chat message
