---
name: detekt-debt-ticket-premise-decays
description: A detekt/lint-debt ticket's "this finding is live, not baselined" claim rots fast - re-run the gated check before quizzing, planning or implementing it
metadata:
  type: feedback
---

Before acting on any detekt- or lint-debt ticket, re-establish whether its finding is still live.
Run `assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles <file>` and grep the baseline for the rule
id. Do not trust the ticket's recorded counts, the baseline line count in its tactical plan, or a
`build/reports/detekt/detekt.txt` on disk.

**Why:** S1328 (2026-08-02). Its whole premise - `LongParameterList 10/10` is live debt, and touching
`StreamsActivity.kt` drags in S1198's live `TooManyFunctions` - was true on 2026-07-31 and false two
days later. `config/detekt/baseline-app_v2.xml` had been rewritten wholesale at 2026-08-02 15:03 with
no dev-log row, freezing both findings; neither source file had changed. The tactical plan's
Pre-Implementation Blocker asked the owner to choose between three routes that had all become
unnecessary, and its verification predicates named a baseline of 12656 lines that no longer existed.
Two gated runs (exit 0, "baselines hold") plus a baseline grep settled it in minutes. Parked as
S1356. Reading `detekt.txt` would not have helped - it was 0 bytes, which reads as a pass.

**How to apply:** At the start of `/spec-quiz`, `/spec-dev` or `/spec-check` on a debt ticket, re-run
the gate first. If the finding turns out baselined, the ticket is not automatically dead - a
`LongParameterList` baseline id embeds the whole parameter list, and `ImportOrdering` embeds the whole
import block, so both re-key and resurface on the first edit that touches them (see
[[feedback-detekt-baseline-signature-resurface]]). Say so to the owner and let him decide whether to
still fix it; do not silently close or silently proceed. The mirror hazard is worth watching too: a
baseline regeneration run from your own ticket absorbs every sibling session's in-flight debt.
