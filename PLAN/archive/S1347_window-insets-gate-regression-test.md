# Стратегическая спецификация: S1347 - Regression test proving the window-insets gate fires

**Ticket:** S1347
**Status:** Archived
**Priority:** 40
**Date:** 2026-08-01
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-08-01
**Tactical spec:** none - Simple path, phase inline below.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-01

**Текст:**

S1338 package I claims (per its Last Audit, "Landed (phase 05)") that Rule 17's window-insets gate is complete: "scripts/quality/assert-window-insets.ps1.. baselined at 28 pre-existing sites". S1340's strategic acceptance criterion §5 bullet 3 goes further and requires "Rule 17 has a gate that fires at least once on a deliberately broken layout in a test" - i.e. an automated regression test proving the gate actually catches a new violation, not just that the gate exists with a baseline.

During S1340's /spec-check audit (2026-08-01) I searched the repo (Glob **/*Test*.{ps1,kt} for window-insets patterns, then a broader *.ps1 grep for "window-insets"/"assert-window-insets"/"assert-source-gates") and found no such test - only the gate script itself (scripts/quality/assert-window-insets.ps1, a thin wrapper over scripts/quality/assert-source-gates.ps1 -Only 'window-insets') and its baseline file. No fixture layout deliberately violating the safe-bounds contract, no test asserting the gate's exit code flips to FAIL against it.

This is out of S1340's contract (S1340's own strategic spec explicitly says "§3.1 requires no tactical work in this ticket" - the Rule 17 gate itself is S1338 package I's deliverable, not S1340's) and non-trivial (needs a deliberately-broken layout fixture plus a harness asserting the gate's exit code against it - more than a one-line fix). Draft a ticket to add that regression test, referencing S1338 (parent, where the gate was built) and S1340 (where the gap in test coverage was discovered during audit).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1338 (parent - built the gate), S1340 (where the test-coverage gap was discovered during audit)
- **Scope:** `scripts/quality/assert-window-insets.tests/Run-Tests.ps1` (new) only. No production
  code, no app-facing surface.
- **Flavors:** n/a - build tooling, not shipped in any APK.

---

## Goal

Доказываем, что gate Rule 17 (`assert-window-insets.ps1`) реально ловит нарушение, а не просто
существует с зафиксированным baseline. Тестовый харнесс двухуровневый: юнит-уровень напрямую
дёргает предикат `Measure-WindowInsetsText` из `scripts/quality/lib/source-matchers.ps1` на
синтетических строках (без риска для дерева репозитория) и покрывает все ветки предиката -
нарушение через слушателя, нарушение через edge-to-edge без слушателя, погашение через
`displayCutout()`, погашение через `applySystemBarInsetPadding()`, файл без inset-поверхности
вообще (score 0, исключение), и задокументированную лазейку - `displayCutout()` в комментарии тоже
гасит счётчик. End-to-end уровень - один реальный прогон `assert-window-insets.ps1 -Gate
-ChangedFiles` на temp-фикстуре, физически кладущейся под `app_v2/src/main/..` на время теста (под
`CODE.LOCK`, удаляется в `finally`), доказывающий, что весь скрипт целиком, а не только функция
предиката, возвращает exit 1 на новом нарушении и exit 0 после починки. Плюс живая регрессия -
реальное дерево репозитория остаётся в рамках текущего baseline.

## Phase 1 - Write the regression harness

- [x] Create `scripts/quality/assert-window-insets.tests/Run-Tests.ps1`, mirroring the existing
  `scripts/quality/assert-exit-contract.tests/Run-Tests.ps1` structure (hermetic where possible,
  `Assert-That` helper, PASS/FAIL tally, single exit code).
  - Unit-level cases (dot-source `scripts/quality/lib/source-matchers.ps1`, call
    `Measure-WindowInsetsText` directly with in-memory strings, no file I/O):
    1. A listener registration with no `displayCutout()`/helper mention -> count > 0.
    2. `setDecorFitsSystemWindows(window, false)` with no listener and no cutout/helper mention ->
       count > 0 (edge-to-edge counts as one uncovered surface).
    3. A listener registration that also names `displayCutout()` -> count 0.
    4. A listener registration that calls `applySystemBarInsetPadding(` -> count 0.
    5. Neither a listener nor edge-to-edge call -> count 0 (not a safe-bounds surface, exempt).
    6. `displayCutout()` appearing only inside a `//` comment above an otherwise-bad listener ->
       count 0 - this is the script's own documented known limitation, assert it stays true rather
       than silently regressing to something stricter without updating the header comment.
  - End-to-end case: acquire `temp/CODE.LOCK` (`scripts/utils/enter-code-lock.ps1`), write a small
    syntactically-valid, obviously-named fixture `.kt` file (unused top-level private function, e.g.
    `app_v2/src/main/java/com/sza/fastmediasorter/S1347WindowInsetsGateFixtureDoNotCommit.kt`) whose
    body is case 1 above; in a `finally` block delete it and release the lock regardless of outcome.
    - Run `pwsh -File scripts/quality/assert-window-insets.ps1 -Gate -ChangedFiles
      "app_v2/src/main/java/com/sza/fastmediasorter/S1347WindowInsetsGateFixtureDoNotCommit.kt"` ->
      assert exit code 1 (the file is untracked, so its HEAD version is empty and the whole count is
      "new").
    - Rewrite the same fixture to also name `displayCutout()`, re-run -> assert exit code 0.
  - Live-regression case: `assert-window-insets.ps1 -Gate` (full scan, no `-ChangedFiles`) against
    the real tree -> assert exit code 0 (current baseline holds).
  - **Verification:** `pwsh -NoProfile -File scripts/quality/assert-window-insets.tests/Run-Tests.ps1`
    exits 0, and its own output shows every case as PASS including the two exit-1/exit-0 end-to-end
    assertions - a harness that only ever prints green without ever exercising the FAIL path proves
    nothing per this ticket's whole premise.
- [x] Confirm no fixture file survives after a run - `Glob` for
  `app_v2/src/main/java/com/sza/fastmediasorter/S1347WindowInsetsGateFixtureDoNotCommit.kt` returns
  nothing once the harness exits, success or failure.
- **Verification:** no `.kt` production file changes in this ticket - `standard debug` is
  unaffected. The harness script itself has no compile step (PowerShell); running it IS the
  verification.

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (compact spec, Simple path)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

`scripts/quality/assert-window-insets.tests/Run-Tests.ps1` created and run clean: 10/10 cases pass,
including the two that matter most for this ticket's premise - E1 proves the real gate script exits
1 against a genuine new violation (untracked fixture under `app_v2/src/main/`, deleted in a `finally`
block under `CODE.LOCK`), E2 proves it exits 0 once the same fixture is cured. Six unit-level cases
cover every branch of `Measure-WindowInsetsText` directly (listener violation, edge-to-edge
violation, cutout cure, helper-delegation cure, not-a-surface exemption, and the script's own
documented comment-loophole). L1 confirms the live `app_v2/src/main` tree still passes the full-scan
gate at its current baseline. Fixture cleanup confirmed (`Glob` finds nothing after the run, both in
the success path and independently re-verified). `post-change.ps1 -ScopeToFile`: PASS on the script
(Script type) and PASS on the regenerated `docs/SCRIPT_CHEATSHEET.md` (Doc type, this ticket's new
`Run-Tests.ps1` needed a cheatsheet entry like every sibling harness). No `.kt` production file
changed, so no build/compile check applies.

### Manual / on-device

- None. This ticket has no on-device or build-verifiable surface.
