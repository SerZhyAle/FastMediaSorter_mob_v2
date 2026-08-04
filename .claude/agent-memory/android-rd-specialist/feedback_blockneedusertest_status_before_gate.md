---
name: blockneedusertest-status-before-gate
description: Flip status to BlockNeedUserTest BEFORE running post-change / ticket-log-audit, else the gate fails on your own probe
type: feedback
---

When a spec needs a `Timber.d("Sxxxx: ..")` device-verification probe, set the catalog status to `BlockNeedUserTest` (via `update.ps1 -Status BlockNeedUserTest -StatusNote ..`) BEFORE running `post-change.ps1` or `assert-no-ticket-logs.ps1`. The ticket-log-audit is fail-closed: it allows `Sxxxx:` probes only for specs currently in `BlockNeedUserTest`. A probe whose spec is still `Draft`/`In Progress` counts as a stale ticket log and fails the gate (`actual: 1`).

**Why:** in S0822 the order was tag -> build -> post-change -> (audit FAIL) -> set status -> re-run audit. post-change also stops at the first failing gate, so neuroslop/detekt never ran and had to be invoked by hand afterward. Correct order avoids both.

**How to apply:** closing sequence for a BlockNeedUserTest ticket = insert probe tag -> `update.ps1 -Status BlockNeedUserTest -StatusNote` -> build -> closure/gates. Status first, then the gate sees a valid probe. See also [[per-phase-debug-tags-break-ticket-log-gate]] and [[timber-tags-before-test]].
