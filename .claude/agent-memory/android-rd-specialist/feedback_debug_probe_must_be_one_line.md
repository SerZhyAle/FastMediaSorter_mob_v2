---
name: debug-probe-must-be-one-line
description: A Sxxxx debug probe whose Timber.d( and its format string sit on different lines survives the removal grep CLAUDE.md mandates
metadata:
  type: feedback
---

Write every `Timber.d("Sxxxx: ..")` probe so the call and its format string share one physical line. If the argument list is too long for 120 columns, precompute the values into a local and format with one `%s` - never wrap the call as `Timber.d(` + newline + `"Sxxxx: .."`.

**Why:** CLAUDE.md's removal procedure for these probes is a literal grep - "grep all `.kt`, delete every `Timber.d("Sxxxx:` line". A wrapped call does not contain that literal on any single line, so the probe is invisible to the procedure and outlives the ticket that owns it. Observed on S1832 (2026-08-20): a merge probe written across six lines did not appear in the removal grep at all; the other four probes did.

**How to apply:** After placing probes, grep `Timber.d("Sxxxx:` and count - the number must equal the number of probes you intended. A short count means one is wrapped, not that one is missing. Fix by collapsing, e.g.

```kotlin
val counts = "added=$added updated=$updated pruned=${urls.size}"
Timber.d("S1832: merge done - %s", counts)
```

Related: [[ticket-log-audit-stale-right-after-status-flip]], [[ticket-log-gate]].
