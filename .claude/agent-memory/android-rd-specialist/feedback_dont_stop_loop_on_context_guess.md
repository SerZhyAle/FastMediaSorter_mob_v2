---
name: dont-stop-loop-on-context-guess
description: Never end a /spec-next or other long loop citing "context is running out" - there is no context meter; keep going until work is genuinely done or blocked
metadata:
  type: feedback
---

Do not stop a long-running loop (`/spec-next`, `/spec-sweep`, batch audits) with a rationale like "контекст сессии почти исчерпан". Keep driving tickets until each ends `Verified`, `Block*`, or genuinely has no autonomous next step.

**Why:** owner pushed back on 2026-07-24 after I closed a `/spec-next` session at 2 tickets citing context exhaustion. There is no token meter available to me - that judgement was a guess from the volume of accumulated tool output, not a measurement. Owner's expectation, set by past sessions, is 10-15 tickets per loop. `/spec-next` itself says explicitly: do NOT cut the session short to avoid a large context. `/compact` is a CLI command I cannot invoke as a tool, so the skill's compaction mechanism is unavailable - that is a reason to be economical, never a reason to stop.

**How to apply:** the real lever is per-ticket cost, not stopping. Prefer targeted `Grep` over full `Read` of large files; delegate broad research to `android-solution-researcher` (its report lands compact while the sweep cost stays in the subagent); write raw artifacts to `temp/Sxxxx/` instead of holding them in chat; avoid re-reading files already summarised. Heavy tickets - full `F1->F2->F3` spec pipelines, probe-test cycles with multi-minute gradle runs - legitimately cost more than the mechanical audit/drift/one-liner tickets that make a 15-ticket session possible; say that plainly instead of blaming context. Stop only on a real terminal: nothing eligible left, or every remaining ticket blocked on a human or a device. See [[verify-full-evidence]].
