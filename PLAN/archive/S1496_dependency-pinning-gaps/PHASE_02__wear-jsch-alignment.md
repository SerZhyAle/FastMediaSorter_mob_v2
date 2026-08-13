# Phase 02 - wear jsch alignment

**Strategic spec:** [`../S1496_dependency-pinning-gaps.md`](../S1496_dependency-pinning-gaps.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 1 / 1
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Bring `wear` onto the same `jsch` version as `app_v2`, removing the only cross-module coordinate divergence in the tree.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `research/02__jsch-module-split.md` read - it establishes that no technical barrier holds `wear` back.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/build.gradle.kts` | Modified | ≤ 4 net |

---

## Steps

### Step 02.1 - Raise jsch to the app_v2 version and record why the two are kept equal

**Files:** `wear/build.gradle.kts`
**Depends on:** - start of phase

**Prompt for developer:**

> Change `implementation("com.github.mwiede:jsch:0.2.17")` to `0.2.26`, matching `app_v2/build.gradle.kts`. Keep the existing `(S0111 Phase 04)` provenance marker and the note that jsch was chosen over sshj, and add that the version is deliberately kept equal to `app_v2` and enforced by `check-doc-vs-gradle.ps1`. Take a timestamped backup under `temp/S1496/` first if the file exceeds 500 lines.

**Why:**

Strategic §5 records the divergence as a forgotten bump rather than a decision - the existing comment justifies choosing jsch over sshj and not the version, and no reason for `0.2.17` is written anywhere - while §3.3 confirms `wear` at `minSdk 28` is covered with room to spare by a version `app_v2` already ships on `legacy` at `minSdk 23`.

**Verification:**

- `Grep` - `com.github.mwiede:jsch:0.2.26` matches exactly once in `wear/build.gradle.kts`.
- `Grep` - `0.2.17` returns zero hits in `wear/build.gradle.kts`.
- `pwsh -NoProfile -File temp/S1496/diff-module-coords.ps1` reports `diverged: 0`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-09 - Step 02.1 Verification 3/3 PASS. Files: `wear/build.gradle.kts` (+1 LOC, version literal + provenance note). expected: 1 hit `com.github.mwiede:jsch:0.2.26` | actual: 1. expected: 0 hits `0.2.17` | actual: 0. `temp/S1496/diff-module-coords.ps1` expected `diverged: 0` | actual `diverged: 0` over 19 shared coordinates.
- 2026-08-09 - **Rule 13 tooling fix inside the step.** The phase criterion requires the `wear` module to be part of a compile invocation, and `a.ps1` had no target reaching it: `check-standard-fast.ps1` hardcoded `:app_v2:` in every task name, and the only script touching `wear` is `build-and-push-all.ps1`, which cleans, builds both modules in release and copies to Google Drive. Added `-Module <app_v2|wear>` to `check-standard-fast.ps1` (wear declares no product flavors, so its task names carry no flavor segment; combining `-Flavor` with `-Module wear` exits 2) and an `fw` target to `a.ps1`. `.\a.ps1 fw` exit 0 - `:wear:compileDebugKotlin` green with jsch 0.2.26. This gap is why the drift went unnoticed: an active Gradle module had no fast check at all.
- 2026-08-09 - Closure: `-ChangeType Mixed` ended exit 2 on another ticket's uncommitted detekt debt in `BrowseDeleteManager.kt` / `StreamTilePackReader.kt`, neither in the changed set; the same run's `detekt-preflight` had already reported "no .kt file in the changed set". Re-closed correctly as two runs - `Config` for `wear/build.gradle.kts`, `Script` for the two scripts plus the regenerated `docs/SCRIPT_CHEATSHEET.md` - both `post-change: PASS`, exit 0. Facade defect parked as **S1553**.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fw` exit 0, the `wear` module compiled directly (2026-08-09 21:17).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: `dev/CATALOG/wear.jsonl` regenerated - not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The tree now has zero diverging coordinates among the 19 declared in both modules, which is the precondition for the Phase 03 rule to be introduced without turning the gate red.

---

## Rollback Plan

Revert the single version literal to `0.2.17`. No data migration or user-facing surface changed.
