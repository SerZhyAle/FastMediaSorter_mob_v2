# Audit Report: S0086 — bugfix-log-export-create-document-guard
**Date:** 2026-05-05
**Mode:** Full (strategic + tactical)
**Auditor:** /spec-check
**Overall score:** `Partial` — 0 FAIL · 2 WARN · all other checks PASS or MANUAL/EXEMPT

---

## Summary

| Category | PASS | WARN | FAIL | MANUAL | EXEMPT | UNCHECKABLE |
|----------|:----:|:----:|:----:|:------:|:------:|:-----------:|
| Strategic | 5 | 0 | 0 | 3 | 2 | 0 |
| Tactical Phase 01 | 4 | 1 | 0 | 0 | 0 | 1 |
| Tactical Phase 02 | 3 | 0 | 0 | 0 | 0 | 0 |
| INDEX Completion Gate | 4 | 1 | 0 | 0 | 1 | 0 |
| **Total** | **16** | **2** | **0** | **3** | **3** | **1** |

---

## Strategic Audit

### §2 Goals

| # | Goal (paraphrased) | Check | Status | Evidence |
|---|-------------------|-------|--------|----------|
| G1 | Fallback dialog shown without ActivityNotFoundException on devices without file manager | Phase 01 Objective + Step 01.1 address this directly | **PASS** | `PHASE_01__fix-intent-guard.md` Objective |
| G2 | WARNING + stacktrace no longer appears in log in normal scenario | `Timber.d` replaces any `Timber.w`/`Timber.e` in fallback path | **PASS** | `GeneralSettingsLogHelper.kt:64` — `Timber.d(...)` |
| G3 | ACTION_CREATE_DOCUMENT behavior unchanged on supported devices | Guard branches on `resolveActivity != null`; launcher called identically when non-null | **PASS** | `GeneralSettingsLogHelper.kt:61-62` |

### §3.2 Hard Constraints

| Constraint | Status | Evidence |
|-----------|--------|----------|
| Flavor coverage (standard/lite/photos/legacy) | **PASS** | No feature-flag gating on `launchSaveLogs()`; no `BuildConfig.FEATURE_*` in the method; `BuildConfig` refs are VERSION_NAME/CODE only |
| API level (minSdk 26/23): `resolveActivity()` works without `<queries>` for `ACTION_CREATE_DOCUMENT` | **PASS** (MANUAL — runtime) | Comment at line 51-55 records the rationale; `ACTION_CREATE_DOCUMENT` is a system intent exempt from package visibility restrictions |
| Wear OS not affected | **PASS** | File is in `app_v2/` module; `wear/` module not touched |

### §6 Research Items

No open research items declared in strategic spec. **EXEMPT.**

### §8 User-Facing Features

Internal fix only — no entry in `docs/FEATURES.md` / `_RU` / `_UK` required per strategic §8.  **EXEMPT.**

### §11 Completion Criteria

| # | Criterion | Status | Note |
|---|-----------|--------|------|
| 1 | SPRD device: no `ActivityNotFoundException` in log on "Save log" press | **MANUAL** | Requires physical SPRD device |
| 2 | Normal device: system file picker opens as before | **MANUAL** | Requires physical device test |
| 3 | Log contains no `ActivityNotFoundException` stacktrace in normal scenarios | **MANUAL** | Requires runtime log inspection |

---

## Tactical Audit

### Phase 01 — fix-intent-guard

**Phase status in INDEX:** ✅ Done · **Phase file header:** ✅ Done — consistent: PASS

#### Files Touched

| File | Exists | Lines (budget ≤ 200) | Status |
|------|:------:|:--------------------:|--------|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsLogHelper.kt` | ✓ | 150 / 200 | **PASS** |

#### Step 01.1 — Replace catch-and-fallback with resolveActivity guard

| Verification Predicate | Expected | Found | Status | Evidence |
|----------------------|---------|-------|--------|----------|
| `ActivityNotFoundException` — zero hits in file | 0 | 1 | **WARN** | `GeneralSettingsLogHelper.kt:54` — comment-only: `// cause ActivityNotFoundException if we launch the launcher directly.` — not a functional catch/throw; predicate specifies zero grep hits |
| `resolveActivity(` — exactly one hit | 1 | 1 | **PASS** | `GeneralSettingsLogHelper.kt:60` |
| `Timber.d("LogExport: CREATE_DOCUMENT not supported` — exactly one hit | 1 | 1 | **PASS** | `GeneralSettingsLogHelper.kt:64` |
| `Log.d(` — zero hits | 0 | 0 | **PASS** | No matches |

**Step status:** `[x] done` in phase file. All functional predicates pass; 1 WARN on comment-containing the exception class name.

#### Phase 01 Done Criteria

| Criterion | Status | Evidence |
|-----------|--------|----------|
| Step 01.1 `[x] done` | **PASS** | Phase file confirms |
| Project compiles | **UNCHECKABLE** | Static audit — no build run; delegate to `/build` |
| `TODO(phase-01)` — zero hits | **PASS** | Grep returned 0 hits in file |
| Dev log entry added | **PASS** | `dev/CHANGELOG.md:6221` — `2026-05-05 01:05:40` |
| `dev/CATALOG/app_v2.jsonl` regenerated | **PASS** | `scan.ps1` run 2026-05-05; `GeneralSettingsLogHelper` present in catalog |

---

### Phase 02 — docs-catalog-cleanup

**Phase status in INDEX:** ✅ Done · **Phase file header:** ✅ Done — consistent: PASS

#### Step 02.1 — Regenerate app_v2 catalog

| Verification Predicate | Status | Evidence |
|----------------------|--------|----------|
| `dev/CATALOG/app_v2.jsonl` exists with session-recent mtime | **PASS** | `scan.ps1` ran 2026-05-05; 923 records |
| `GeneralSettingsLogHelper` in `dev/CATALOG/app_v2.jsonl` | **PASS** | `app_v2.jsonl:800` — class present with `launchSaveLogs` in functions list |

#### Step 02.2 — Record dev log entries

| Verification Predicate | Status | Evidence |
|----------------------|--------|----------|
| `S0086` in `dev/CHANGELOG.md` | **PASS** | Lines 6221, 6223 — two entries for `GeneralSettingsLogHelper.kt` tagged `S0086` |

#### Phase 02 Done Criteria

| Criterion | Status | Evidence |
|-----------|--------|----------|
| Both steps `[x] done` | **PASS** | Phase file confirms |
| `dev/CATALOG/app_v2.jsonl` regenerated | **PASS** | As above |
| Dev log entry present for `GeneralSettingsLogHelper.kt` | **PASS** | As above |

---

### INDEX Completion Gate

| Item | Status | Evidence |
|------|--------|----------|
| All phases show ✅ Done | **PASS** | INDEX Phase Overview: Phase 01 ✅, Phase 02 ✅ |
| `docs/FEATURES.md` / `_RU` / `_UK` — no changes required | **EXEMPT** | Internal fix per strategic §8 |
| `dev/CHANGELOG.md` has entry for every modified file | **PASS** | 2 entries for `GeneralSettingsLogHelper.kt` tagged S0086 |
| `dev/CATALOG/app_v2.jsonl` regenerated | **PASS** | scan.ps1 + render.ps1 ran 2026-05-05 |
| `/spec-check S0086` returns Verified | N/A | This audit — result: Partial |
| Commit freshness: at least one commit newer than spec date (2026-05-05) | **WARN** | `git log` shows last commit `368332e` dated 2026-04-22; file has uncommitted changes (`M` in `git status`) — implementation is present in working tree but not yet committed |

---

## WARN Details (action items)

### WARN-1: `ActivityNotFoundException` in comment (Phase 01, Step 01.1)

- **Location:** `GeneralSettingsLogHelper.kt:54`
- **Evidence:** `// cause ActivityNotFoundException if we launch the launcher directly.`
- **Nature:** comment-only reference; no functional catch/throw/import of `ActivityNotFoundException` remains in the file.
- **Action:** The verification predicate as written specifies strict zero grep hits. The comment is intentional (WHY-explanation per coding standards). **Recommended action:** update the Phase 01 step's Verification text to specify "zero functional uses (no catch/throw/import)" instead of "zero hits" — or accept the WARN as inherent in the comment-preserving coding standard. No code change needed.
- **Severity:** Low — does not affect runtime behaviour.

### WARN-2: Implementation not committed

- **Location:** `GeneralSettingsLogHelper.kt` — `git status` shows `M` (modified, staged=no)
- **Evidence:** last commit `368332e` dated 2026-04-22; spec created 2026-05-05; no commit capturing the guard change.
- **Action:** Commit the working tree change before closing the spec. Suggested message:
  ```
  fix(S0086): replace ActivityNotFoundException catch with resolveActivity guard in launchSaveLogs
  ```
- **Severity:** Medium — the implementation is done but not persisted in version history.

---

## Score Rationale

- 0 FAIL → not `Broken`.
- 2 WARN → not `Verified` → **`Partial`**.
- WARN-1 is cosmetic (comment in code). WARN-2 is actionable (commit needed).
- After committing (WARN-2 resolved) and optionally clarifying the verification predicate (WARN-1), re-run `/spec-check S0086` to reach `Verified`.
