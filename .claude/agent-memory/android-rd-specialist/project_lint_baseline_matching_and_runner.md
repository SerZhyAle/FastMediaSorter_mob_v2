---
name: lint-baseline-matching-and-runner
description: app lint baseline entries match on message text FUZZILY, so exact-key pruning unhides warnings; and the only BUILD.LOCK-safe way to run any lint gradle task
metadata:
  type: project
---

Two facts about `app_v2/lint-baseline.xml` and running app lint, both learned the hard way in S1195
(2026-07-31).

**A lint baseline entry matches a finding on (issue id, file, message) with the LINE IGNORED and the
MESSAGE COMPARED LOOSELY.** Lint tolerates drift inside the message text - an entry reading
"a newer version of X than 2.59 is available: **2.60**" still filters today's finding that says
"available: **2.60.1**". Consequences that bite:

- Pruning a baseline by exact-key diff against a freshly regenerated one removes entries that were
  quietly doing work. In S1195 that unhid 3 `NewerVersionAvailable` warnings. Errors were unaffected,
  so the build stayed green, but "warnings went up by 3" is the only symptom and it is easy to
  misread as a regression.
- The inverse is the bigger trap, recorded in the S1195 plan: **changing a detector's message text
  invalidates every baseline entry for that rule at once.** Rewriting three detectors made 364
  entries stop matching and their findings resurface as live.
- After any prune, diff the live sets before and after (`build/reports/lint-results.xml` from a run
  with each baseline) and name every finding that moved. Do not report a count delta without naming
  the findings behind it.

**Why:** the baseline is the only place where "we decided this is acceptable" is recorded, and both
directions of this matching behaviour silently change what is hidden without changing any code.

**How to apply:** when touching `lint-baseline.xml` or any `lint-rules/` detector message, expect the
baseline population for that rule to be invalidated wholesale, and prove the live-findings delta
rather than the entry count.

---

**There is no `a.ps1` target for a full app lint run.** `flr` is only `:lint-rules:test`. The
BUILD.LOCK-safe runner is `scripts/utils/check-typo-lint.ps1`, whose `-LintTask` parameter takes any
gradle task:

```powershell
pwsh -NoProfile -File scripts/utils/check-typo-lint.ps1 -SkipTypo -SkipActivityChecks
pwsh -NoProfile -File scripts/utils/check-typo-lint.ps1 -SkipTypo -SkipActivityChecks -LintTask updateLintBaselineStandardDebug
```

`updateLintBaseline` overwrites the live baseline in place, so save and restore it around the call
when you only want a scratch copy to diff against.

A full run costs 4-7 minutes cold, but the `lintAnalyze*` tasks stay UP-TO-DATE while sources are
unchanged - so a re-run that only changes the baseline finishes in seconds. Editing any `.kt`
invalidates that and buys the full cost again. Sequence baseline experiments before code edits.

Related: [[detekt-baseline-signature-resurface]], [[project_detekt_baseline_hand_edit_daemon_stale]].
