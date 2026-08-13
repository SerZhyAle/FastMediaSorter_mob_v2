# Tactical Plan: S1332 - bugfix-adb-log-hides-app-timber-lines

**Strategic spec:** [`../S1332_bugfix-adb-log-hides-app-timber-lines.md`](../S1332_bugfix-adb-log-hides-app-timber-lines.md)
**Research inputs:** none - findings are inlined in the phase files
**Feature:** `adb.ps1 log` selects app lines by process id instead of by package text
**Tier:** 2 - Small (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Complexity verdict

COMPLEX. Two of the seven PRIMITIVE criteria fail: the change adds three new files (a dot-sourced filter lib, a regression suite, a fixture) and the total delta is roughly 250 lines. The remaining five criteria pass.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | pid-aware-log-filter | - | ✅ Done | 6/6 | [PHASE_01__pid-aware-log-filter.md](PHASE_01__pid-aware-log-filter.md) |
| 02 | filter-regression-suite | 01 | ✅ Done | 3/3 | [PHASE_02__filter-regression-suite.md](PHASE_02__filter-regression-suite.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `✅ Done` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Invariants that bind every phase

- **No new exit code.** `adb.ps1` keeps its published table (0 / 1 / 2 / 3 / 4 / 7). A suppressed-line warning is a diagnostic, not a failure: `-Grep` legitimately matching nothing must stay exit 0. Adding a code would break `.\a.ps1 adb log` callers that branch on `$LASTEXITCODE`. Because no error path is added, `scripts/quality/assert-exit-contract.ps1` (CLAUDE.md section 7 / S1070) has nothing new to police - but any `Write-Error` introduced during implementation must still carry `-ErrorAction Continue` before its `exit N`.
- **Never interpolate `-Grep` into printed output.** `.claude/skills/run-fastmediasorter/smoke.ps1` and `scripts/devtest/prerelease-prepare.ps1` both stringify the whole stdout of `adb.ps1 log` and test it with `-match 'FATAL|beginning of crash|ANR in'`. A diagnostic line that echoed the caller's own pattern back would be read as a crash. Every new message must be pattern-free.
- **Buffer separator lines stay dropped.** `--------- beginning of crash` carries no pid column and no package text, so it is filtered out today and must remain filtered out. It is emitted whenever the crash buffer is non-empty for *any* process on the device, so keeping it would turn the two callers above into permanent false-positive crash detectors.
- **`-Tail N` keeps meaning "the last N lines of the whole buffer".** The window is chosen on the device before filtering, exactly as now. Callers have tuned their numbers (200, 400, 4000) against that meaning.
- **No `.kt` file is touched.** This ticket has no changed app flow, so it has no `Timber.d("S1332: ..")` probe. Do not add one to unrelated Kotlin to satisfy a `BlockNeedUserTest` habit.

---

## Pre-Implementation Blockers

None. The strategic spec has no open section 6 research items.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not updated. The strategic spec has no FEATURES section, and a developer-machine script is not a shipped app capability, so `docs/ALL_FEATURES.jsonl` is also out of scope.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` - not regenerated. No `.kt` changes, so the class catalog cannot drift.
- [x] `docs/SCRIPT_CHEATSHEET.md` regenerated - it is a generated document and `scripts/post-change.ps1` gates its drift with `scripts/quality/assert-script-cheatsheet-sync.ps1`.
- [x] `/spec-check S1332` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/3 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log, and set the journal status with `-StatusNote`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1332`.

---

## Blockers Log

- none yet

---

## Change Log

- 2026-07-31 - Initial tactical plan authored by `/spec-tech`.
