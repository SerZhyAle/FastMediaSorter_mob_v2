---
name: process-audit-2026-07
description: The 2026-07-31 agent-process audit - its findings, its open owner questions, and what it forbids re-proposing
metadata:
  type: project
---

`dev/AGENT_PROCESS_AUDIT_2026-07-31.md` holds a measured audit of how the owner and the agents work together (347 main + 869 nested transcripts, 40-agent workflow, 143 findings, 26 adversarially verified).

The core economics: cost is `accumulated context x turns`. Cache reads are **72.4%** of spend, output only 11.9%; ~**271k cached tokens ride on every request**; the fixed preamble floor is ~64k = 23.3% of everything billed; and **6.7% of sessions carry 50% of all cache_read**, nearly all of them `/spec-next` / `/spec-all` loops that compact at a 389k median instead of resetting.

**Why:** the owner asked what to optimise. The answer turned out to be session boundaries and mechanical enforcement, not prose, re-reads, subagents or MCP - all four were measured and refuted as non-problems. Do not re-propose them; section 5 of the report lists each with its counter-measurement.

**How to apply:**
- Before proposing any cost optimisation, check section 5 - the idea may already be a measured dead end.
- The report's `[U]` items were never adversarially verified. Treat them as leads and re-measure before acting.
- The single strongest structural finding: **gated rules hold at ~99%, ungated ones at 1-8%**. Prefer a hook or an `assert-*` gate over a sentence in CLAUDE.md, always - the same advice already ships in the Read tool schema every turn and gets 22% compliance.
- The owner answered the blocking questions on 2026-07-31 and the work is specced as **S1338** (umbrella) with **S1339** bounded rounds, **S1340** rules gate-or-compress, **S1341** model routing, **S1342** canon propagation - all `Approved`, release package 30. His rulings: reset the loop at a **300k context threshold** (he first said 400k, then accepted that it sat at the existing 389k compaction median); **gate only what reached him, compress the rest**; **Opus = judgement, Sonnet = mechanics, Fable and Haiku by hand only**.
- An agent cannot execute `/clear` or `/compact` - they are harness built-ins. Any "reset the context" design has to be a self-halt with a resume handle, and the threshold check reads the live transcript's newest `cache_read_input_tokens`.

Measurement method and its traps: [[transcript-cost-mining]].
