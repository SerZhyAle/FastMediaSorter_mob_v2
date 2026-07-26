---
name: archive-after-every-release
description: After every successful release, archive ALL Verified and Implemented specs - standing owner rule
metadata:
  type: feedback
---

After every successful release, archive **all** `Verified` and `Implemented` specs (move to `temp/done/`, journal status `Archived`, priority 0).

**Why:** Owner directive 2026-07-22 (right after the v2.60.7221.704 plateau release), re-stated 2026-07-26 right after v2.60.7260.252 even though Step 12c had already archived 15 specs and the final report named the count - so treat this as a rule the owner actively re-checks, not a one-off. Shipped specs sit at `Implemented`/`Verified` and no longer belong in the active `PLAN/` workspace; leaving them there clutters the backlog and muddies "what is actually still open". Archiving is pure workspace hygiene - `PLAN/` + `spec-catalog.jsonl` are gitignored, so it touches no tracked file, needs no commit, and is trivially reversible (files stay under `temp/done/`, `select.ps1 -Id` still resolves them).

**How to apply:** `/skill-release` Step 12c already runs this archive sweep automatically - trust it, but confirm `0 Implemented / 0 Verified` remain afterward. For any release NOT driven by `/skill-release` (manual / hotfix), run the sweep yourself: enumerate every `Implemented`+`Verified` id via `select.ps1 -Status`, `archive.ps1 -Id` each (continue on per-id failure), report `ARCHIVED: N`. Idempotent - already-archived specs are a no-op, so a confirming re-run is free.

Because the owner re-checks this, do not bury the result: report BOTH the archived count AND the post-sweep confirmation that nothing is left (`ARCHIVED: 0 (no Implemented/Verified specs)`), plus the archived ids themselves. A bare "Specs archived: 15" reads as a claim; the follow-up zero is the proof.
