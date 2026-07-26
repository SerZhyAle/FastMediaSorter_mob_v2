---
name: code-lock-release-ownership
description: Never call exit-code-lock.ps1 blindly - post-change already released yours and a parallel session may now hold CODE.LOCK
metadata:
  type: feedback
---

Before running `scripts/utils/exit-code-lock.ps1`, check `scripts/utils/lock-status.ps1 -Name Code` and read the reason string. `exit-code-lock.ps1` deletes whatever lock is there - it does not verify that this session owns it.

**Why:** on 2026-07-26 (S1152/S1154 work) `post-change.ps1` had already auto-released my CODE.LOCK during closure; a parallel session immediately took it for "S1197 dialog lifecycle fix". My tidy-up `exit-code-lock.ps1` then deleted *their* lock. It is advisory-only (Tier 2), so nothing broke, but the other session lost its guard silently. Restoring it means re-acquiring with the same reason string - a hack, not a real fix.

**How to apply:** after any `post-change.ps1` run, assume your CODE.LOCK is already gone; only call `exit-code-lock.ps1` when `lock-status.ps1` shows a reason that is yours (e.g. the ticket you are on). Same caution for the other locks under `scripts/utils/`.
