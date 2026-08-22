---
name: probe-tag-collides-with-detekt-line-length
description: An Sxxxx probe must be one line AND under 120 chars - budget the message text, do not wrap and do not let detekt push you into wrapping
metadata:
  type: feedback
---

Two rules meet on the same line and neither yields: a `Timber.d("Sxxxx: ..")` probe must stay on **one
physical line** so the removal grep can find it, and detekt refuses a line over **120 characters**. The
only lever is the message text - shorten the wording and drop argument verbosity until it fits.

**Why:** the removal contract greps for the literal `Timber.d("Sxxxx:` prefix, so a wrapped call hides
the probe from its own cleanup and it ships into a permanent log. Writing an informative probe with
several `%s` arguments at a normal indent depth overruns 120 characters easily - measured 2026-08-20 on
S1285, where `Timber.d("S1285: resource grid span base=%d step=%s effective=%d", baseSpan,
resourceGridCellSize(), spanCount)` came to 123 characters at 12 spaces of indent and would have failed
the scoped detekt gate at closure.

**How to apply:** budget the line before writing it - indent depth plus roughly 110 characters is the
whole allowance. Compress the prose, not the data: `"S1285: span base=%d step=%s -> %d"` carries the same
three values as a sentence twice its length. Check with
`awk 'length>120 {print FILENAME": "FNR" len="length}' <file>` right after inserting probes, while the
lock is still worth taking, rather than discovering it in the closure gate. Related:
[[debug-probe-must-be-one-line]], [[write-detekt-clean-first-time]].
