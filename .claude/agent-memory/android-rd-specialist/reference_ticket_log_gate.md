---
name: reference-ticket-log-gate
description: Permanent-log Sxxxx audit script + fail-closed post-change gate added by S0381 Phase 01
metadata:
  type: reference
---

`scripts/quality/assert-no-ticket-logs.ps1` audits `app_v2/**` + `wear/**` for `Sxxxx` ticket ids inside permanent `Timber.i/w/e` and long-lived `Timber.d`. It resolves probe status against `PLAN/spec-catalog.jsonl` and allows ONLY `Timber.d("Sxxxx: ..")` whose ticket is currently `BlockNeedUserTest`. Default = report (exit 0); `-Gate` = fail-closed (exit 1 on any finding); `-Quiet` = summary only. It skips comment lines and ids embedded in identifiers (e.g. class `MigrateS0059UseCase`).

**How to apply:** Since S0381 Phase 01, `scripts/post-change.ps1` runs this audit with `-Gate` as the `ticket-log-audit` step for ChangeType `Kotlin`/`Mixed`. So any new `Timber.i/w/e("...Sxxxx...")` or non-probe `Timber.d("...Sxxxx...")` makes the next Kotlin post-change FAIL for the whole repo (global invariant, not per-file). When adding logs: never embed a ticket id in permanent log text - reserve `Sxxxx:` for `BlockNeedUserTest` probes only. If post-change fails on `ticket-log-audit`, run the script without `-Gate` to see the offending lines. Related: [[strings-tool-mutator]], [[feedback-persistent-logs-no-ticket-id]].
