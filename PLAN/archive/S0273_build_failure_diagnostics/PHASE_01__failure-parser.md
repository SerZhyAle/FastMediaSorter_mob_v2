# Phase 01 - Failure Parser

**Strategic spec:** [`../S0273_build_failure_diagnostics.md`](../S0273_build_failure_diagnostics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Introduce a PowerShell helper that extracts the relevant failure section from the most recent saved build log or from an explicitly supplied log path.

---

## Prerequisites

- [ ] Working tree is clean enough for the targeted edits on the current feature branch.
- [ ] Strategic §6 research items are resolved.
- [ ] `temp/` exists as the diagnostic scratch zone.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/builders/get-last-build-failure.ps1` | New | ≤ 300 |
| `scripts/builders/testdata/build-failure-middle.log` | New | ≤ 120 |
| `scripts/builders/testdata/build-success.log` | New | ≤ 80 |
| `scripts/builders/testdata/build-truncated.log` | New | ≤ 80 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split via Manager pattern first.

---

## Steps

### Step 01.1 - Add the failure extraction script

**Files:** `scripts/builders/get-last-build-failure.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/builders/get-last-build-failure.ps1` as a standalone `pwsh -NoProfile` helper. It must auto-detect the newest `*build*.log` under `temp/` when `-LogPath` is absent, return exit code `2` with a concise guidance message when no log exists, and return exit code `3` when the selected log is empty. On failure logs, print the collected `e: file:///...` compiler lines, the failed task markers, and the `FAILURE:` section. On logs without failure markers, print the last `-MaxLines` lines with an explicit fallback note.

**Verification:**

- `Glob` - `scripts/builders/get-last-build-failure.ps1` exists.
- `Grep` - `param(` matches exactly once in that file.
- `Grep` - `\[int\]\$MaxLines = 200` present.
- `Grep` - `Get-ChildItem -Path \$tempDir -Filter '\*build\*\.log' -File` present.
- `Grep` - `exit 2` present.
- `Grep` - `exit 3` present.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 6/6 PASS. Files: scripts/builders/get-last-build-failure.ps1 (+105 LOC).

---

### Step 01.2 - Add deterministic fixture logs for script smoke coverage

**Files:** `scripts/builders/testdata/build-failure-middle.log`, `scripts/builders/testdata/build-success.log`, `scripts/builders/testdata/build-truncated.log`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add three short fixture logs under `scripts/builders/testdata/`: one with Kotlin `e:` lines and a `FAILURE:` block in the middle, one with `BUILD SUCCESSFUL`, and one truncated log without `FAILURE:`. Keep them human-readable and minimal so the parser behaviour can be smoke-tested without using live Gradle output.

**Verification:**

- `Glob` - `scripts/builders/testdata/build-failure-middle.log` exists.
- `Glob` - `scripts/builders/testdata/build-success.log` exists.
- `Glob` - `scripts/builders/testdata/build-truncated.log` exists.
- `Grep` - `FAILURE:` present in `scripts/builders/testdata/build-failure-middle.log`.
- `Grep` - `BUILD SUCCESSFUL` present in `scripts/builders/testdata/build-success.log`.
- `Grep` - `kaptStandardDebugKotlin` present in `scripts/builders/testdata/build-truncated.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-20 - Verification 6/6 PASS. Files: scripts/builders/testdata/build-failure-middle.log, scripts/builders/testdata/build-success.log, scripts/builders/testdata/build-truncated.log.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Smoke command `pwsh -NoProfile -File scripts/builders/get-last-build-failure.ps1 -LogPath scripts/builders/testdata/build-failure-middle.log` exits `0`.
- [x] Smoke command `pwsh -NoProfile -File scripts/builders/get-last-build-failure.ps1 -LogPath scripts/builders/testdata/build-success.log` exits `0`.
- [x] Smoke command `pwsh -NoProfile -File scripts/builders/get-last-build-failure.ps1 -LogPath scripts/builders/testdata/build-truncated.log` exits `0`.

---

## Handoff Notes to Next Phase

Phase 01 establishes the parser contract and fixture corpus. Phase 02 should wire the parser into the launcher and primary debug wrapper without changing the parser semantics.

---

## Rollback Plan

Revert phase commit(s) - no data migration or user-facing surface changed.
