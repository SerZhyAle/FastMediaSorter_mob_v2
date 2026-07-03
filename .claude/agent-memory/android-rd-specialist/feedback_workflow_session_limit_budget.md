---
name: workflow-session-limit-budget
description: Low parallelism by default - owner's 5h session limit is shared with all subagents; big fan-outs burn it and die unfinished; hard cap ~6-8 agents without owner GO
metadata:
  type: feedback
---

Owner runs on a 5-hour rolling session limit shared by the main loop AND every subagent/workflow agent. Default to LOW parallelism: a handful of agents, staged, cheapest-first. Even a "moderate" ~14-agent run (7 critics + 7 verifiers) drew a correction - the realistic risk is agents burning the whole window and never completing.

**Why:** two incidents.
- 2026-07-02 mass audit: 101 agents / 3.66M tokens hit the limit mid-run - all 75 verify skeptics failed, zero confirmed findings for the full 5h budget. Auto-resume started re-burning the fresh window; owner stopped it: "сжигает за один раз мой 5-ти часовой лимит без результата".
- 2026-07-02 UAK site review: 7 critics + 7 verifiers launched under ultracode; owner mid-run: "поменьше параллелизма - огромный шанс, что твои агенты потратят все токены и так и не завершатся". Ultracode ON does NOT lift this constraint on this subscription.

**How to apply:**
- Hard default ceiling: ~6-8 agents per workflow run, few concurrent. Anything above -> state agent count + rough token estimate (agents x ~30-60k) in chat and get explicit owner GO first.
- Prefer: fewer, broader agents (one critic covering 2-3 dimensions) over many narrow ones; inline self-verification over a dedicated verifier per finding.
- Stage expensive pipelines: run Find, REPORT to owner, put any Verify fan-out behind an explicit "go".
- Verification default: self-check inline or 1 skeptic for top-N findings only - never per-finding skeptics, never panels unless owner explicitly asks for exhaustive.
- Big-run failure on "session limit" -> stop, report what is cached (journal resume is cheap for COMPLETED agents), ask before resuming; failed agents re-run at full price.
- Find-phase output is durable: journal + task output file survive, so deferring Verify loses nothing.
