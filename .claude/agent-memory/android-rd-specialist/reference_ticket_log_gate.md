---
name: reference-ticket-log-gate
description: Permanent-log Sxxxx audit script + fail-closed post-change gate added by S0381 Phase 01
metadata:
  type: reference
---

`scripts/quality/assert-no-ticket-logs.ps1` audits `app_v2/**` + `wear/**` for `Sxxxx` ticket ids inside permanent `Timber.i/w/e` and long-lived `Timber.d`. It resolves probe status against `PLAN/spec-catalog.jsonl` and allows ONLY `Timber.d("Sxxxx: ..")` whose ticket is currently `BlockNeedUserTest`. Default = report (exit 0); `-Gate` = fail-closed (exit 1 on any finding); `-Quiet` = summary only. It skips comment lines and ids embedded in identifiers (e.g. class `MigrateS0059UseCase`).

**How to apply:** Since S0381 Phase 01, `scripts/post-change.ps1` runs this audit with `-Gate` as the `ticket-log-audit` step for ChangeType `Kotlin`/`Mixed`. So any new `Timber.i/w/e("...Sxxxx...")` or non-probe `Timber.d("...Sxxxx...")` makes the next Kotlin post-change FAIL for the whole repo (global invariant, not per-file). When adding logs: never embed a ticket id in permanent log text - reserve `Sxxxx:` for `BlockNeedUserTest` probes only. If post-change fails on `ticket-log-audit`, run the script without `-Gate` to see the offending lines. Related: [[strings-tool-mutator]].

**Ordering trap - the probe must follow the status flip, not precede it (observed 2026-08-21, S1877).** `/spec-dev` tells you to insert the `Timber.d("Sxxxx: ..")` probes as the last code edits *before* the final phase's build, so one build validates code and probes together. But that phase still closes through `post-change.ps1`, and this gate resolves the probe against the catalog, where the ticket is still `In Progress` - so it fails the whole closure as `stale probe (ticket not BlockNeedUserTest)` and nothing is written. The two instructions cannot both be satisfied in that order.

The sequence that satisfies both, and the one to use:

1. Close the final code phase with the probe **absent** - `post-change` passes.
2. Run any doc/catalog phase left.
3. `update.ps1 -Status BlockNeedUserTest -StatusNote '..'` first, probe second.
4. Build once (`a.ps1 fw` for wear, `fk` for the phone), then `post-change` the probe file - the gate now reports `0 forbidden, 0 missing`.

The cost is one extra fast compile, which is the cheaper half of the trade: the alternative is a failed closure that writes no changelog row and has to be re-run anyway. Note also that the gate is two-sided - once the ticket **is** `BlockNeedUserTest`, a *missing* probe is equally a finding, so the flip and the insertion belong to the same working window.
