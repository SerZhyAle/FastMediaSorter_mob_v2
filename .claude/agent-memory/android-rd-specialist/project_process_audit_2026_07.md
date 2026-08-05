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

**Retrospective run 2026-08-05 (5 days post-change, adversarially verified - 4 of 5 findings refuted or corrected):**
- Cost effect is **null within noise**. Per-turn context is flat *within each tier* (main 282.4k -> 288.3k, nested 116.4k -> 112.6k); the aggregate rise is a tier-mix shift - subagent delegation halved, 35.9% -> 17.8% of turns. At PRE's tier mix POST is +1%. Every "-30% for bounded work" style split is a selection artifact: the same procedure on the untouched PRE window yields -24.6% with nothing changed. Always demand a placebo split on PRE before believing a POST split.
- What did move, verifiably: `/compact` 79 -> 1 and `/clear` 4 -> 20, compactions 5.3/day -> ~0, longest block 1473 -> 358 turns, assert-* gates 34 -> 44.
- The always-on preamble floor moved -2.46% (85,822 -> 83,707 B) = ~0.23% of the bill - two orders of magnitude below the corpus's own daily variance, so undetectable by construction. ~40k of the ~64k floor is harness-owned; any repo-side cut is capped at ~37% of it.
- **The owner's two goals are decoupled and this is the headline.** Output (all prose, spec text, every written artifact) is 11.9% of the bill, so culling artifacts is a speed/sanity lever, not a token lever. Do not sell one as the other.
- **RETRACTED same day: "46.5% of specs are never re-read" and "42% of tactical plans are never opened" are both instrument artifacts.** Counting consumption by the **Read tool alone is invalid** in this repo. Read.file_path lands inside a tactical dir 740 times against Edit.file_path 2378 - and `/spec-dev` *executes* a step by Edit-ing its `[ ]` -> `[x]` checkbox, so the act of consumption is an Edit, not a Read. Shell reads (`head`, `sed`, `Get-Content`, `Select-String`) carry no `file_path` field at all and are invisible to a tool-name scan. Corrected never-consumed rate for tactical plans: **3 of 78 = 3.8%** (9-11.5% under the strictest non-Edit variants), not 42.1%. Zero of them are `BlockNeedUserTest`. Second, independent error: 17 of the 126 "tactical directories" hold no plan at all - crash logs, screenshots, an owner voice note - so the denominator was contaminated too. Only 110 of 460 tickets (23.9%) ever get a tactical plan: rare-and-used, not common-and-ignored, and 43% of used plans are read by a *different session* than wrote them (up to 9 days later), so the artifact is a real cross-context channel.
- **How to apply:** before claiming any artifact is unread, enumerate every consumption channel first (Read, Edit, Write, Grep, shell content verbs, subagent prompts) and report the sensitivity across them. A tool-name scan alone is off by an order of magnitude here. The owner's instinct that his parked tickets are fine was correct and my contrary recommendation was withdrawn.
- Still standing (these do not depend on the broken instrument): the tactical template is byte-for-byte unchanged (INDEX.md + 5 PHASE files); `/quick` has **never been invoked once** in the whole corpus and `/skill-fix` ~0 - the cheap tiers are dead letters, so "pick the smallest tier" is an ungated rule behaving exactly like every other ungated rule.
- Device-test backlog as of 2026-08-05: **97 `BlockNeedUserTest`, 2 Verified, ship-to-verify 48.5:1** (the 2026-07-31 audit recorded 87 and 6.9:1). 165 `Timber.d("Sxxxx:` probe lines from 87 tickets are live in source - R8 `-assumenosideeffects` strips `Timber.d`/`v` from release bytecode, so nothing reaches users; the cost is source clutter and debug logcat noise only. Do not overstate it as shipped-code risk.
- The `.claude/reference/` split is only a win where the companion goes unread - true for `/spec-next` alone (~-16% injected); the other five companions are read at >=1x per invocation, i.e. strictly worse than the single file they replaced.
- S1341 routing: agent frontmatter is fixed, but product days reverted to ~97% Opus - the Sonnet share lives entirely in the two meta days.
- **Do not re-measure before ~2026-08-15.** Five days cannot clear the PRE window's own variance; POST sits inside PRE's contiguous 5-day range on every headline metric (p41..p91).

Measurement method and its traps: [[transcript-cost-mining]].
